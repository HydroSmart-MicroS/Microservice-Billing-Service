package com.hydrosmart.billing.shared.infrastructure.messaging;
import com.rabbitmq.client.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** AMQP request/reply; the receiver invokes only this service's local HTTP adapter. */
public final class RpcBus implements AutoCloseable {
 public static final JsonMapper JSON=JsonMapper.builder().findAndAddModules().build();
 private final String name;
 private final Map<String,Channel> consumers=new ConcurrentHashMap<>();
 private final List<Connection> listeners=new CopyOnWriteArrayList<>();
 private final HttpClient local=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public RpcBus(String name){this.name=name;}
 private Connection connect(boolean recover)throws Exception{return connect(recover,4000);}
 private Connection connect(boolean recover,int timeout)throws Exception{
  var f=new ConnectionFactory();f.setUri(System.getenv("RABBITMQ_URL"));f.setConnectionTimeout(timeout);f.setHandshakeTimeout(timeout);
  f.setRequestedHeartbeat(30);f.setAutomaticRecoveryEnabled(recover);f.setNetworkRecoveryInterval(2000);return f.newConnection(name);
 }
 public record Reply(int status,Map<String,String> headers,String body){
  public byte[] bytes(){return Base64.getDecoder().decode(body);}
  public String text(){return new String(bytes(),StandardCharsets.UTF_8);}
  @SuppressWarnings("unchecked") public Map<String,Object> json(){return JSON.readValue(bytes(),Map.class);}
 }
 public Reply request(String service,String method,String path,Map<String,String> headers,Object body){
  if(!Set.of("identity","properties","telemetry","analytics","reports","notifications","billing","config-service").contains(service))throw new IllegalArgumentException("Unknown broker service");
  try(var connection=connect(false);var ch=connection.createChannel()){
   var queue=ch.queueDeclare("",false,true,true,null).getQueue();String id=UUID.randomUUID().toString();
   var answer=new CompletableFuture<byte[]>();
   ch.basicConsume(queue,true,(tag,msg)->{if(id.equals(msg.getProperties().getCorrelationId()))answer.complete(msg.getBody());},tag->answer.completeExceptionally(new IOException("Reply consumer cancelled")));
   ch.addReturnListener(r->answer.completeExceptionally(new IOException("RPC route unavailable")));
   byte[] data=body==null?new byte[0]:JSON.writeValueAsBytes(body);
   var request=Map.of("traceId",RequestTrace.id(),"method",method,"path",path,"headers",headers,"body",Base64.getEncoder().encodeToString(data),"deadline",System.currentTimeMillis()+12000);
   ch.basicPublish("hydrosmart.rpc",service,true,new AMQP.BasicProperties.Builder().contentType("application/json").correlationId(id).replyTo(queue).expiration("15000").deliveryMode(2).build(),JSON.writeValueAsBytes(request));
   var raw=answer.get(12,TimeUnit.SECONDS);if(raw.length>16*1024*1024)throw new IOException("RPC response too large");
   return JSON.readValue(raw,Reply.class);
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("broker_interrupted");}
  catch(Exception e){throw new IllegalStateException("broker_unavailable",e);}
 }
 public Map<String,Object> get(String service,String path,Map<String,String> headers){
  var response=request(service,"GET",path,headers,null);if(response.status()!=200)throw new BrokerStatus(response.status());return response.json();
 }
 public Map<String,String> internalHeaders(){return Map.of("X-Service-Token",System.getenv("INTERNAL_SERVICE_TOKEN"));}
 public static Map<String,String> bearer(String token){return Map.of("Authorization","Bearer "+token);}
 public static final class BrokerStatus extends RuntimeException{public final int status;public BrokerStatus(int s){super("broker_status_"+s);status=s;}}
 @SuppressWarnings("unchecked") public void startHttpReceiver()throws Exception{
  var c=connect(true);listeners.add(c);var ch=c.createChannel();ch.exchangeDeclare("hydrosmart.rpc","direct",true);
  ch.queueDeclare("rpc."+name,true,false,false,null);ch.queueBind("rpc."+name,"hydrosmart.rpc",name);ch.basicQos(8);
  ch.basicConsume("rpc."+name,false,(tag,d)->{
   var p=d.getProperties();
   if(d.getBody().length>16*1024*1024||p.getReplyTo()==null||p.getCorrelationId()==null){ch.basicReject(d.getEnvelope().getDeliveryTag(),false);return;}
   Reply answer;
   try{
    var request=JSON.readValue(d.getBody(),Map.class);String path=String.valueOf(request.get("path"));
    if(!path.startsWith("/")||path.startsWith("//")||path.contains("\r")||path.contains("\n"))throw new IllegalArgumentException();
    if(((Number)request.get("deadline")).longValue()<System.currentTimeMillis())throw new TimeoutException();
    var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080"+path)).timeout(Duration.ofSeconds(20));
    var headers=(Map<String,String>)request.getOrDefault("headers",Map.of());
    headers.forEach((k,v)->{if(Set.of("Authorization","X-Service-Token","Content-Type","X-Request-ID").contains(k))builder.header(k,v);});
    if(request.get("traceId") instanceof String trace&&trace.matches("[0-9a-fA-F-]{36}"))builder.setHeader("X-Request-ID",trace);
    builder.method(String.valueOf(request.get("method")),HttpRequest.BodyPublishers.ofByteArray(Base64.getDecoder().decode(String.valueOf(request.getOrDefault("body","")))));
    var response=local.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
    answer=new Reply(response.statusCode(),Map.of("Content-Type",response.headers().firstValue("content-type").orElse("application/json")),Base64.getEncoder().encodeToString(response.body()));
   }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
   catch(Exception e){answer=new Reply(e instanceof TimeoutException?504:503,Map.of("Content-Type","application/json"),Base64.getEncoder().encodeToString("{\"error\":\"broker_request_failed\"}".getBytes(StandardCharsets.UTF_8)));}
   ch.basicPublish("",p.getReplyTo(),false,new AMQP.BasicProperties.Builder().contentType("application/json").correlationId(p.getCorrelationId()).build(),JSON.writeValueAsBytes(answer));
   ch.basicAck(d.getEnvelope().getDeliveryTag(),false);
  },tag->consumers.remove("rpc."+name));
  consumers.put("rpc."+name,ch);
 }
 public void publish(String type,String eventId,byte[] body)throws Exception{
  try(var c=connect(false);var ch=c.createChannel()){
   ch.confirmSelect();var returned=new java.util.concurrent.atomic.AtomicBoolean();ch.addReturnListener(r->returned.set(true));
   ch.basicPublish("hydrosmart.events",type,true,new AMQP.BasicProperties.Builder().messageId(eventId).contentType("application/json").deliveryMode(2).build(),body);
   ch.waitForConfirmsOrDie(5000);if(returned.get())throw new IOException("Event has no route");
  }
 }
 @SuppressWarnings("unchecked") public void consume(String queue,List<String> bindings,Consumer<Map<String,Object>> handler)throws Exception{
  var c=connect(true);listeners.add(c);var ch=c.createChannel();ch.exchangeDeclare("hydrosmart.events","topic",true);
  ch.queueDeclare(queue,true,false,false,Map.of("x-dead-letter-exchange","hydrosmart.dead"));
  for(String binding:bindings)ch.queueBind(queue,"hydrosmart.events",binding);
  ch.queueDeclare("retry."+queue,true,false,false,Map.of("x-message-ttl",5000,"x-dead-letter-exchange","","x-dead-letter-routing-key",queue));ch.basicQos(8);ch.confirmSelect();
  ch.basicConsume(queue,false,(tag,d)->{
   try{
    if(d.getBody().length>16*1024*1024)throw new IllegalArgumentException("event_too_large");
    var event=JSON.readValue(d.getBody(),Map.class);
    if(event.get("event_id")==null||event.get("event_type")==null)throw new IllegalArgumentException("invalid_event");
    UUID.fromString(event.get("event_id").toString());handler.accept(event);System.out.println(JSON.writeValueAsString(Map.of("event","message_consumed","service",name,"eventId",event.get("event_id"),"eventType",event.get("event_type"))));ch.basicAck(d.getEnvelope().getDeliveryTag(),false);
   }catch(IllegalArgumentException e){ch.basicReject(d.getEnvelope().getDeliveryTag(),false);}
   catch(Exception e){
    var headers=new HashMap<String,Object>(d.getProperties().getHeaders()==null?Map.of():d.getProperties().getHeaders());int retry=((Number)headers.getOrDefault("attempt",0)).intValue();
    if(retry>=5){ch.basicReject(d.getEnvelope().getDeliveryTag(),false);return;}
    headers.put("attempt",retry+1);ch.basicPublish("","retry."+queue,true,d.getProperties().builder().headers(headers).deliveryMode(2).build(),d.getBody());
    boolean confirmed;
    try{confirmed=ch.waitForConfirms(5000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();confirmed=false;}catch(TimeoutException timeout){confirmed=false;}
    if(confirmed)ch.basicAck(d.getEnvelope().getDeliveryTag(),false);else ch.basicNack(d.getEnvelope().getDeliveryTag(),false,true);
   }
  },tag->consumers.remove(queue));
  consumers.put(queue,ch);
 }
 public Map<String,Object> diagnostics()throws Exception{
  try(var connection=connect(false,1500);var ch=connection.createChannel()){
   ch.exchangeDeclarePassive("hydrosmart.rpc");ch.exchangeDeclarePassive("hydrosmart.events");ch.confirmSelect();
   var required=new ArrayList<String>();required.add("rpc."+name);
   if(name.equals("reports"))required.add("reports.jobs");
   if(name.equals("notifications")){required.add("notifications.alerts");if(!System.getenv().getOrDefault("BREVO_API_KEY","").isBlank())required.add("notifications.email");}
   var states=new TreeMap<String,String>();boolean ready=true;
   for(String queue:required){var own=consumers.get(queue);boolean active=own!=null&&own.isOpen()&&ch.queueDeclarePassive(queue).getConsumerCount()>0;states.put(queue,active?"UP":"DOWN");ready&=active;}
   return Map.of("ready",ready,"connection","UP","consumers",states,"publisherChannel","READY");
  }
 }
 public void close(){for(var c:listeners)try{c.close();}catch(Exception ignored){}}
}

