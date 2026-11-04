package com.hydrosmart.billing.infrastructure.persistence.jdbc.repositories;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import com.hydrosmart.billing.domain.services.*;
import com.hydrosmart.billing.domain.model.aggregates.Plan;
@Repository
public class PgBillingRepository implements BillingRepository {
 static Map<String,Object> toDomainRow(Map<String,Object> row){var result=new HashMap<>(row);result.replaceAll((key,value)->value instanceof Timestamp t?t.toInstant():value instanceof java.sql.Date d?d.toLocalDate():value);return result;}
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public PgBillingRepository(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager){this.jdbc=jdbc;tx=new TransactionTemplate(manager);}
 public Map<String,Object> membership(String user){return jdbc.queryForList("SELECT m.*,p.device_limit,p.configured_price_minor FROM memberships m JOIN plans p ON p.code=m.plan_code WHERE user_id=?",user).stream().findFirst().map(PgBillingRepository::toDomainRow).orElse(Map.of());}
 public Map<String,Object> order(UUID id){return jdbc.queryForList("SELECT * FROM payment_orders WHERE id=?",id).stream().findFirst().map(PgBillingRepository::toDomainRow).orElse(Map.of());}
 public List<Map<String,Object>> orders(String user){return jdbc.queryForList("SELECT id,user_id,kind,plan_code,unit_id,invoice_month,amount_minor,currency,status,created_at,paid_at,cancelled_at,checkout_url FROM payment_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 100",user).stream().map(PgBillingRepository::toDomainRow).toList();}
 public Map<String,Object> pendingPlan(String user){return jdbc.queryForList("SELECT * FROM payment_orders WHERE user_id=? AND kind='plan' AND status IN('creating','pending') ORDER BY created_at DESC LIMIT 1",user).stream().findFirst().map(PgBillingRepository::toDomainRow).orElse(Map.of());}
 public Map<String,Object> waterInvoice(UUID unit,String month){return jdbc.queryForList("SELECT * FROM payment_orders WHERE unit_id=? AND invoice_month=?::date AND status IN('creating','pending','paid')",unit,month+"-01").stream().findFirst().map(PgBillingRepository::toDomainRow).orElse(Map.of());}
 public void create(UUID id,String user,String kind,String plan,UUID unit,String month,int amount){
  jdbc.update("INSERT INTO payment_orders(id,user_id,kind,plan_code,unit_id,invoice_month,amount_minor) VALUES(?,?,?,?,?,?::date,?)",id,user,kind,plan,unit,month==null?null:month+"-01",amount);
 }
 public void prepared(UUID id,PaymentProvider.Session session){tx.executeWithoutResult(status->{
  var rows=jdbc.queryForList("UPDATE payment_orders SET provider_session_id=?,checkout_url=?,verification_token=?,status='pending',next_check_at=now()+interval '30 seconds' WHERE id=? AND status='creating' RETURNING user_id,kind,unit_id,invoice_month,amount_minor,currency",session.id(),session.url(),session.verificationToken(),id);
  if(!rows.isEmpty()&&"water".equals(rows.getFirst().get("kind"))){
   var row=rows.getFirst();
   event("billing.water.payment.pending.v1",row.get("user_id").toString(),Map.of("order_id",id.toString(),"unit_id",row.get("unit_id").toString(),"invoice_month",row.get("invoice_month").toString().substring(0,7),"amount_minor",row.get("amount_minor"),"currency",row.get("currency")));
  }
 });}
 public void verification(UUID id,String sessionId,String token){jdbc.update("UPDATE payment_orders SET verification_token=? WHERE id=? AND provider_session_id=? AND status='pending'",token,id,sessionId);}
 public void failed(UUID id){jdbc.update("UPDATE payment_orders SET status='failed' WHERE id=? AND status IN('creating','pending')",id);}
 public void confirm(UUID id){
  tx.executeWithoutResult(txStatus->{
   var row=jdbc.queryForMap("SELECT * FROM payment_orders WHERE id=? FOR UPDATE",id);
   if("paid".equals(row.get("status")))return;
   if(!"pending".equals(row.get("status")))throw new IllegalArgumentException("Pago no preparado.");
   String user=row.get("user_id").toString();
   jdbc.update("UPDATE payment_orders SET status='paid',paid_at=now(),verification_token=NULL WHERE id=?",id);
   var data=new HashMap<String,Object>();data.put("user_id",user);data.put("order_id",id.toString());data.put("kind",row.get("kind"));data.put("amount_minor",row.get("amount_minor"));data.put("currency","PEN");
   if("plan".equals(row.get("kind"))){
    jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,71))",Object.class,user);
    var current=membership(user);
    Instant now=Instant.now(),start=now;
    if(!current.isEmpty()){Instant end=(Instant)current.get("paid_through");if(end.isAfter(now))start=end;}
    Instant end=start.atZone(ZoneId.of("America/Lima")).plusMonths(1).toInstant();
    jdbc.update("INSERT INTO memberships(user_id,plan_code,paid_through,last_payment_id) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET plan_code=excluded.plan_code,paid_through=excluded.paid_through,last_payment_id=excluded.last_payment_id,cancel_at_period_end=false,cancellation_requested_at=NULL,updated_at=now()",user,row.get("plan_code"),Timestamp.from(end),id);
    data.put("plan_code",row.get("plan_code"));data.put("paid_through",end.toString());
   }else{data.put("unit_id",row.get("unit_id").toString());data.put("invoice_month",row.get("invoice_month").toString());}
   UUID event=UUID.randomUUID();
   var payload=Map.of("event_id",event.toString(),"event_type","billing.payment.confirmed.v1","schema_version",1,"producer","billing","occurred_at",Instant.now().toString(),"data",data);
   jdbc.update("INSERT INTO outbox_events(event_id,event_type,aggregate_id,payload) VALUES(?,'billing.payment.confirmed.v1',?,?::jsonb)",event,user,com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus.JSON.writeValueAsString(payload));
  });
 }
 public List<Map<String,Object>> pending(){return jdbc.queryForList("SELECT * FROM payment_orders WHERE status='pending' AND next_check_at<=now() ORDER BY next_check_at LIMIT 5").stream().map(PgBillingRepository::toDomainRow).toList();}
 public boolean cancelled(UUID id){return Boolean.TRUE.equals(tx.execute(status->{
  var row=jdbc.queryForMap("SELECT * FROM payment_orders WHERE id=? FOR UPDATE",id);
  if("cancelled".equals(row.get("status")))return true;
  if(!"pending".equals(row.get("status")))return false;
  jdbc.update("UPDATE payment_orders SET status='cancelled',cancelled_at=now(),verification_token=NULL WHERE id=?",id);
  event("billing.payment.cancelled.v1",row.get("user_id").toString(),Map.of("order_id",id.toString(),"kind",row.get("kind")));
  return true;
 }));}
 public void cancellation(String user,boolean requested){tx.executeWithoutResult(status->{
  jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,71))",Object.class,user);
  int changed=jdbc.update("UPDATE memberships SET cancel_at_period_end=?,cancellation_requested_at=CASE WHEN ? THEN now() ELSE NULL END,updated_at=now() WHERE user_id=? AND paid_through>now() AND cancel_at_period_end IS DISTINCT FROM ?",requested,requested,user,requested);
  if(changed>0)event(requested?"billing.plan.cancellation.requested.v1":"billing.plan.cancellation.reverted.v1",user,Map.of("cancel_at_period_end",requested));
 });}
 private void event(String type,String user,Map<String,Object> detail){
  UUID id=UUID.randomUUID();var data=new HashMap<String,Object>(detail);data.put("user_id",user);
  var payload=Map.of("event_id",id.toString(),"event_type",type,"schema_version",1,"producer","billing","occurred_at",Instant.now().toString(),"data",data);
  jdbc.update("INSERT INTO outbox_events(event_id,event_type,aggregate_id,payload) VALUES(?,?,?,?::jsonb)",id,type,user,com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus.JSON.writeValueAsString(payload));
 }
 public void defer(UUID id){jdbc.update("UPDATE payment_orders SET next_check_at=now()+interval '60 seconds' WHERE id=?",id);}
}

