package com.hydrosmart.billing.interfaces.acl;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import com.hydrosmart.billing.application.internal.outboundservices.AssetDirectory;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
@Component
public class AssetsContextFacade implements AssetDirectory {
 private final RpcBus bus;
 public AssetsContextFacade(RpcBus bus){this.bus=bus;}
 public Map<String,Object> usage(String user){return bus.get("properties","/internal/v1/users/"+URLEncoder.encode(user,StandardCharsets.UTF_8)+"/devices",bus.internalHeaders());}
 public Map<String,Object> access(UUID unit,String token){return bus.get("properties","/internal/v1/units/"+unit+"/access",RpcBus.bearer(token));}
 public Map<String,Object> consumption(UUID unit,String from,String to,String token){return bus.get("analytics","/api/v1/analytics/dashboard?unitId="+unit+"&from="+URLEncoder.encode(from,StandardCharsets.UTF_8)+"&to="+URLEncoder.encode(to,StandardCharsets.UTF_8),RpcBus.bearer(token));}
 public void disable(UUID device,String token){var response=bus.request("properties","DELETE","/api/v1/devices/"+device,RpcBus.bearer(token),null);if(response.status()!=204)throw new RpcBus.BrokerStatus(response.status());}
}

