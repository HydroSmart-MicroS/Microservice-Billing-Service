package com.hydrosmart.billing.domain.services;
import java.util.*;
/** Repository records expose Instant/LocalDate values, never JDBC date objects. */
public interface BillingRepository {
 Map<String,Object> membership(String user);
 Map<String,Object> order(UUID id);
 List<Map<String,Object>> orders(String user);
 Map<String,Object> pendingPlan(String user);
 Map<String,Object> waterInvoice(UUID unit,String month);
 void create(UUID id,String user,String kind,String plan,UUID unit,String month,int amount);
 void prepared(UUID id,PaymentProvider.Session session);
 void verification(UUID id,String sessionId,String token);
 void failed(UUID id);
 void confirm(UUID id);
 boolean cancelled(UUID id);
 void cancellation(String user,boolean requested);
 List<Map<String,Object>> pending();
 void defer(UUID id);
}

