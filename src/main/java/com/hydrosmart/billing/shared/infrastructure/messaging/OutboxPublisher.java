package com.hydrosmart.billing.shared.infrastructure.messaging;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class OutboxPublisher {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final RpcBus bus;
 public OutboxPublisher(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager,RpcBus bus){this.jdbc=jdbc;tx=new TransactionTemplate(manager);this.bus=bus;}
 @Scheduled(fixedDelay=1000) public void flush(){
  try{tx.executeWithoutResult(status->{
   var rows=jdbc.queryForList("SELECT event_id,event_type,payload::text AS payload FROM outbox_events WHERE published_at IS NULL ORDER BY occurred_at FOR UPDATE SKIP LOCKED LIMIT 20");
   for(var row:rows){try{
    bus.publish(row.get("event_type").toString(),row.get("event_id").toString(),row.get("payload").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    jdbc.update("UPDATE outbox_events SET published_at=now(),attempts=attempts+1 WHERE event_id=?",row.get("event_id"));
   }catch(Exception error){jdbc.update("UPDATE outbox_events SET attempts=attempts+1 WHERE event_id=?",row.get("event_id"));break;}}
  });}catch(Exception error){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Outbox waiting for database/broker");}
 }
}

