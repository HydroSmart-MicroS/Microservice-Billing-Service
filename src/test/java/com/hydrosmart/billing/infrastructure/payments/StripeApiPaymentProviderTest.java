package com.hydrosmart.billing.infrastructure.payments;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
import com.hydrosmart.billing.domain.model.aggregates.Plan;
class StripeApiPaymentProviderTest {
 @Test void missingProviderRecordIsRecoverableOnlyForTheExactOriginalSession()throws Exception{
  var content=new AtomicReference<String>();var status=new java.util.concurrent.atomic.AtomicInteger(200);var requestBody=new AtomicReference<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var data=content.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),data.length);exchange.getResponseBody().write(data);exchange.close();});server.start();
  try{
   var provider=new StripeApiPaymentProvider("http://127.0.0.1:"+server.getAddress().getPort());var id=UUID.randomUUID();var token="a".repeat(64);
   var values=new HashMap<String,Object>(Map.of("sessionId","cs_test_old","reference",id.toString(),"amount",4000,"currency","pen","livemode",false,"verificationToken",token));values.put("url",null);
   content.set(RpcBus.JSON.writeValueAsString(values));assertEquals(token,provider.recover(id,4000,"cs_test_old"));
   assertTrue(requestBody.get().contains("cs_test_old"));assertTrue(requestBody.get().contains(id.toString()));
   values.put("sessionId","cs_test_new");content.set(RpcBus.JSON.writeValueAsString(values));assertThrows(IllegalStateException.class,()->provider.recover(id,4000,"cs_test_old"));
   values.put("sessionId","cs_test_old");values.put("amount",1);content.set(RpcBus.JSON.writeValueAsString(values));assertThrows(IllegalStateException.class,()->provider.recover(id,4000,"cs_test_old"));
   status.set(401);content.set("{}");assertThrows(com.hydrosmart.billing.domain.services.PaymentProvider.VerificationLost.class,()->provider.verify("cs_test_old","legacy"));
  }finally{server.stop(0);}
 }
 @Test void exactPricesAndCaps(){assertEquals(4000,Plan.of("premium").priceMinor());assertEquals(1,Plan.of("premium").deviceLimit());assertEquals(12000,Plan.of("black").priceMinor());assertEquals(4,Plan.of("black").deviceLimit());assertThrows(IllegalArgumentException.class,()->Plan.of("landlord"));}
 @Test void providerProofMustBeExactAndInTestMode()throws Exception{
  var content=new AtomicReference<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();var data=content.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();});server.start();
  try{
   var provider=new StripeApiPaymentProvider("http://127.0.0.1:"+server.getAddress().getPort());var id=UUID.randomUUID();
   var values=new HashMap<String,Object>(Map.of("amount",4000,"currency","pen","reference",id.toString(),"sessionId","cs_test_1","verificationToken","test-proof","url","https://checkout.stripe.com/test","livemode",false,"status","open","paymentStatus","unpaid"));
   content.set(RpcBus.JSON.writeValueAsString(values));assertEquals("cs_test_1",provider.create(id,4000,"http://localhost:8080/app/pagos").id());
   assertFalse(provider.verify("cs_test_1","test-proof").paid());
   values.put("amount",4000.5);content.set(RpcBus.JSON.writeValueAsString(values));assertThrows(IllegalStateException.class,()->provider.create(id,4000,"http://localhost:8080/app/pagos"));
   values.put("amount",4000);values.remove("livemode");content.set(RpcBus.JSON.writeValueAsString(values));assertThrows(IllegalStateException.class,()->provider.verify("cs_test_1","test-proof"));
   values.put("livemode",true);content.set(RpcBus.JSON.writeValueAsString(values));assertThrows(IllegalStateException.class,()->provider.verify("cs_test_1","test-proof"));
  }finally{server.stop(0);}
 }
}
