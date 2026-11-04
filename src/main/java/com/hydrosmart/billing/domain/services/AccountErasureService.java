package com.hydrosmart.billing.domain.services;
import java.util.*;
public interface AccountErasureService {
 Map<String,Object> inspect(String user);Map<String,Object> prepare(String user);void release(String user);void erase(String user,List<String> units);
}
