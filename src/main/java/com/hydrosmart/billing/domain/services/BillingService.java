package com.hydrosmart.billing.domain.services;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public interface BillingService {
 Map<String,Object> entitlement(String user);
 Map<String,Object> status(String user);
 Map<String,Object> plan(String user,String code);
 Map<String,Object> water(String user,UUID unit,String month,String token);
 Map<String,Object> quote(String user,UUID unit,String month,String token);
 Map<String,Object> verify(String user,UUID id);
 Map<String,Object> cancelOrder(String user,UUID id);
 Map<String,Object> cancellation(String user,boolean requested);
 List<Map<String,Object>> history(String user);
 void disable(String user,UUID id,String token);
 void reconcile();
}
