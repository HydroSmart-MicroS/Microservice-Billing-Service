package com.hydrosmart.billing.interfaces.rest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import com.hydrosmart.billing.domain.services.BillingService;
import com.hydrosmart.billing.domain.model.valueobjects.BillingRuleViolation;
import com.hydrosmart.billing.interfaces.rest.resources.PlanCheckoutResource;
import com.hydrosmart.billing.interfaces.rest.resources.WaterCheckoutResource;
@RestController
public class BillingController {
 private final BillingService service;
 public BillingController(BillingService service){this.service=service;}
 @GetMapping("/api/v1/billing/status")public Map<String,Object> status(@AuthenticationPrincipal Jwt jwt){return service.status(jwt.getSubject());}
 @GetMapping("/api/v1/billing/access")public ResponseEntity<?> access(@AuthenticationPrincipal Jwt jwt){
  return Boolean.TRUE.equals(service.entitlement(jwt.getSubject()).get("active"))?ResponseEntity.ok(Map.of("active",true)):ResponseEntity.status(402).body(Map.of("detail","Necesitas pagar o renovar tu plan mensual.","code","subscription_required"));
 }
 @GetMapping("/internal/v1/billing/users/{user}")public Map<String,Object> entitlement(@PathVariable String user){return service.entitlement(user);}
 @PostMapping("/api/v1/billing/checkout/plan")public Map<String,Object> plan(@AuthenticationPrincipal Jwt jwt,@RequestBody PlanCheckoutResource body){return service.plan(jwt.getSubject(),body.planCode());}
 @PostMapping("/api/v1/billing/checkout/water")public Map<String,Object> water(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Authorization")String authorization,@RequestBody WaterCheckoutResource body){
  return service.water(jwt.getSubject(),body.unitId(),body.month(),authorization.substring(7));
 }
 @GetMapping("/api/v1/billing/quote/water")public Map<String,Object> quote(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Authorization")String authorization,@RequestParam UUID unitId,@RequestParam String month){return service.quote(jwt.getSubject(),unitId,month,authorization.substring(7));}
 @PostMapping("/api/v1/billing/orders/{id}/cancel")public Map<String,Object> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.cancelOrder(jwt.getSubject(),id);}
 @PostMapping("/api/v1/billing/plan/cancellation")public Map<String,Object> cancellation(@AuthenticationPrincipal Jwt jwt){return service.cancellation(jwt.getSubject(),true);}
 @DeleteMapping("/api/v1/billing/plan/cancellation")public Map<String,Object> keep(@AuthenticationPrincipal Jwt jwt){return service.cancellation(jwt.getSubject(),false);}
 @GetMapping("/api/v1/billing/orders")public List<Map<String,Object>> orders(@AuthenticationPrincipal Jwt jwt){return service.history(jwt.getSubject());}
 @PostMapping("/api/v1/billing/orders/{id}/verify")public Map<String,Object> verify(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.verify(jwt.getSubject(),id);}
 @DeleteMapping("/api/v1/billing/devices/{id}")public ResponseEntity<?> disable(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Authorization")String authorization,@PathVariable UUID id){
  if(!"propietario".equals(jwt.getClaimAsString("profile_type")))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  service.disable(jwt.getSubject(),id,authorization.substring(7));return ResponseEntity.noContent().build();
 }
 @ExceptionHandler(com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus.BrokerStatus.class)public ResponseEntity<?> brokerStatus(com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus.BrokerStatus error){return ResponseEntity.status(Set.of(400,403,404,409).contains(error.status)?error.status:503).body(Map.of("detail","No se pudo completar la operación sobre esta unidad."));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)public ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","Ya existe una orden para este pago. Actualiza Mis pagos."));}
 @ExceptionHandler(IllegalStateException.class)public ResponseEntity<?> unavailable(){return ResponseEntity.status(503).body(Map.of("detail","No pudimos verificar la operación con el servicio. Inténtalo nuevamente."));}
 @ExceptionHandler(BillingRuleViolation.class)public ResponseEntity<?> rule(BillingRuleViolation error){
  int status=switch(error.kind()){case NOT_FOUND->404;case FORBIDDEN->403;case CONFLICT->409;case PAYMENT_REQUIRED->402;case UNAVAILABLE->503;};
  return ResponseEntity.status(status).body(Map.of("detail",Objects.requireNonNullElse(error.getMessage(),"Operación no disponible.")));
 }
 @ExceptionHandler(IllegalArgumentException.class)public ResponseEntity<?> invalid(IllegalArgumentException error){return ResponseEntity.badRequest().body(Map.of("detail",error.getMessage()==null?"Solicitud inválida.":error.getMessage()));}
}

