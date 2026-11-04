package com.hydrosmart.billing.infrastructure.persistence.jdbc.repositories;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import com.hydrosmart.billing.infrastructure.authorization.AccountLifecycle;
@Repository
public class AccountErasureRepository implements com.hydrosmart.billing.domain.services.AccountErasureService {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public AccountErasureRepository(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager){this.jdbc=jdbc;tx=new TransactionTemplate(manager);}
 private void check(String user){if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_orders WHERE user_id=? AND status IN('creating','pending'))",Boolean.class,user)))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Verifica o anula tus pagos pendientes antes de eliminar la cuenta.");}
 public Map<String,Object> inspect(String user){check(user);return Map.of("unitIds",List.<String>of());}
 public Map<String,Object> prepare(String user){AccountLifecycle.GATE.writeLock().lock();try{return tx.execute(status->{var result=inspect(user);jdbc.update("INSERT INTO privacy_receipts VALUES(encode(sha256(convert_to(?,'UTF8')),'hex'),false,now()+interval '5 minutes') ON CONFLICT(user_hash) DO UPDATE SET expires_at=CASE WHEN privacy_receipts.completed THEN privacy_receipts.expires_at ELSE excluded.expires_at END",user);return result;});}finally{AccountLifecycle.GATE.writeLock().unlock();}}
 public void release(String user){jdbc.update("DELETE FROM privacy_receipts WHERE user_hash=encode(sha256(convert_to(?,'UTF8')),'hex') AND completed=false",user);}
 public void erase(String user,List<String> units){AccountLifecycle.GATE.writeLock().lock();try{tx.executeWithoutResult(status->{
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM privacy_receipts WHERE user_hash=encode(sha256(convert_to(?,'UTF8')),'hex') AND completed)",Boolean.class,user)))return;
  check(user);
   jdbc.update("INSERT INTO anonymous_payment_archive(kind,plan_code,invoice_month,amount_minor,currency,paid_date,expires_at) SELECT kind,plan_code,invoice_month,amount_minor,currency,(paid_at AT TIME ZONE 'America/Lima')::date,now()+interval '365 days' FROM payment_orders WHERE user_id=? AND status='paid'",user);
   jdbc.update("DELETE FROM memberships WHERE user_id=?",user);jdbc.update("DELETE FROM payment_orders WHERE user_id=?",user);
   jdbc.update("DELETE FROM subscriptions WHERE user_id=?",user);jdbc.update("DELETE FROM billing_customers WHERE user_id=?",user);
  jdbc.update("DELETE FROM outbox_events WHERE aggregate_id=? OR strpos(payload::text,?)>0",user,user);
  for(String unit:units)jdbc.update("DELETE FROM outbox_events WHERE strpos(payload::text,?)>0",unit);
  jdbc.update("INSERT INTO privacy_receipts VALUES(encode(sha256(convert_to(?,'UTF8')),'hex'),true,now()+interval '365 days') ON CONFLICT(user_hash) DO UPDATE SET completed=true,expires_at=excluded.expires_at",user);
 });}finally{AccountLifecycle.GATE.writeLock().unlock();}}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000) public void purge(){jdbc.update("DELETE FROM privacy_receipts WHERE expires_at<=now()");jdbc.update("DELETE FROM anonymous_payment_archive WHERE expires_at<=now()");}
}
