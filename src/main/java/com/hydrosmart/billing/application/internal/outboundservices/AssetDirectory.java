package com.hydrosmart.billing.application.internal.outboundservices;
import java.util.*;
public interface AssetDirectory {
 Map<String,Object> usage(String user);
 Map<String,Object> access(UUID unit,String token);
 Map<String,Object> consumption(UUID unit,String from,String to,String token);
 void disable(UUID device,String token);
}

