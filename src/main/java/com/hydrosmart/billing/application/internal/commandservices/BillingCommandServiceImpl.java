package com.hydrosmart.billing.application.internal.commandservices;
import java.util.*;
import com.hydrosmart.billing.domain.model.valueobjects.BillingRuleViolation;
import java.time.*;
import java.math.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import com.hydrosmart.billing.domain.model.aggregates.Plan;
import com.hydrosmart.billing.domain.services.*;
import com.hydrosmart.billing.application.internal.outboundservices.AssetDirectory;
@Service
public class BillingCommandServiceImpl implements BillingService {
 private final BillingRepository repository;private final PaymentProvider provider;private final AssetDirectory assets;private final String publicUrl;
 public BillingCommandServiceImpl(BillingRepository repository,PaymentProvider provider,AssetDirectory assets,@Value("${PUBLIC_APP_URL:http://localhost:8080}")String publicUrl){this.repository=repository;this.provider=provider;this.assets=assets;this.publicUrl=publicUrl;}
 public Map<String,Object> entitlement(String user){
  var membership=repository.membership(user);boolean active=!membership.isEmpty()&&((Instant)membership.get("paid_through")).isAfter(Instant.now());
  var result=new HashMap<String,Object>();result.put("active",active);result.put("planCode",membership.get("plan_code"));result.put("paidThrough",membership.isEmpty()?null:((Instant)membership.get("paid_through")).toString());result.put("cancelAtPeriodEnd",Boolean.TRUE.equals(membership.get("cancel_at_period_end")));result.put("deviceLimit",active?((Number)membership.get("device_limit")).intValue():0);return result;
 }
 public Map<String,Object> status(String user){
  var result=entitlement(user);result.put("plans",Plan.all());
  try{result.put("devices",assets.usage(user));}catch(Exception error){result.put("devices",null);}
  return result;
 }
 public Map<String,Object> plan(String user,String code){
  Plan plan=Plan.of(code);
  var pending=repository.pendingPlan(user);
  if("pending".equals(pending.get("status"))){
   verifyOrder(pending);pending=repository.order((UUID)pending.get("id"));
   if("paid".equals(pending.get("status")))return safe(pending);
   if(!"pending".equals(pending.get("status")))pending=Map.of();
  }
  var current=entitlement(user);
  if(Boolean.TRUE.equals(current.get("active"))&&!code.equals(current.get("planCode")))throw new IllegalArgumentException("Puedes cambiar de plan al vencer tu período; la renovación mantiene el plan actual.");
  if(Boolean.TRUE.equals(current.get("active"))&&Boolean.TRUE.equals(current.get("cancelAtPeriodEnd")))throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"Anula la cancelación del plan antes de renovarlo.");
  var usage=assets.usage(user);
  if(((Number)usage.get("count")).intValue()>plan.deviceLimit())throw new IllegalArgumentException("Tu cuenta supera los "+plan.deviceLimit()+" dispositivos de este plan. Reduce los dispositivos antes de continuar.");
  if(!pending.isEmpty()){
   if(!code.equals(pending.get("plan_code")))throw new IllegalArgumentException("Tienes un pago pendiente de otro plan. Completa ese pago antes de crear otro.");
   return "creating".equals(pending.get("status"))?prepare(pending):safe(pending);
  }
  UUID id=UUID.randomUUID();repository.create(id,user,"plan",code,null,null,plan.priceMinor());return prepare(repository.order(id));
 }
 public Map<String,Object> water(String user,UUID unit,String month,String token){
  requireActive(user);
  YearMonth period;
  try{period=YearMonth.parse(month);}catch(Exception error){throw new IllegalArgumentException("Mes inválido: usa AAAA-MM.");}
  var now=YearMonth.now(ZoneId.of("America/Lima"));
  if(!period.isBefore(now)||period.isBefore(now.minusMonths(12)))throw new IllegalArgumentException("Solo se pagan meses cerrados de los últimos 12 meses.");
  var access=assets.access(unit,token);
  var start=period.atDay(1).atStartOfDay(ZoneId.of("America/Lima"));
  if(access.get("visibleFrom")!=null&&Instant.parse(access.get("visibleFrom").toString()).isAfter(start.toInstant()))
   throw new BillingRuleViolation(BillingRuleViolation.Kind.FORBIDDEN,"El propietario debe gestionar el mes con alquiler parcial.");
  var existing=repository.waterInvoice(unit,month);
  if(!existing.isEmpty()){
   if(!user.equals(existing.get("user_id")))throw new IllegalArgumentException("El otro responsable ya preparó o pagó la factura de esta unidad y mes.");
   return "paid".equals(existing.get("status"))?safe(existing):prepare(existing);
  }
  var data=assets.consumption(unit,start.toOffsetDateTime().toString(),period.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("America/Lima")).toOffsetDateTime().toString(),token);
  @SuppressWarnings("unchecked") var estimate=(Map<String,Object>)data.get("estimate");
  int amount=amount(estimate);
  if(amount<=0)throw new IllegalArgumentException("Este período no tiene consumo pendiente de cobro.");
  UUID id=UUID.randomUUID();repository.create(id,user,"water",null,unit,month,amount);return prepare(repository.order(id));
 }
 private Map<String,Object> prepare(Map<String,Object> order){
  UUID id=(UUID)order.get("id");
  if("pending".equals(order.get("status")))verifyOrder(order);
  else if("creating".equals(order.get("status"))){
   try{var session=provider.create(id,((Number)order.get("amount_minor")).intValue(),publicUrl+"/app/pagos?orderId="+id);repository.prepared(id,session);}
   catch(Exception error){repository.failed(id);throw new BillingRuleViolation(BillingRuleViolation.Kind.UNAVAILABLE,error.getMessage());}
  }
  return safe(repository.order(id));
 }
 public Map<String,Object> verify(String user,UUID id){
  var order=repository.order(id);if(order.isEmpty()||!user.equals(order.get("user_id")))throw new BillingRuleViolation(BillingRuleViolation.Kind.NOT_FOUND);
  if("pending".equals(order.get("status")))verifyOrder(order);
  return safe(repository.order(id));
 }
 private void verifyOrder(Map<String,Object> order){
  UUID id=(UUID)order.get("id");var proof=paymentProof(order,false);
  validateProof(order,proof);
  if(proof.paid())repository.confirm(id);else if(proof.expired())repository.failed(id);else repository.defer(id);
 }
 private PaymentProvider.Payment paymentProof(Map<String,Object> order,boolean cancel){
  UUID id=(UUID)order.get("id");String session=order.get("provider_session_id").toString(),token=order.get("verification_token").toString();
  try{return cancel?provider.cancel(session,token):provider.verify(session,token);}
  catch(PaymentProvider.VerificationLost error){
   token=provider.recover(id,((Number)order.get("amount_minor")).intValue(),session);
   var proof=cancel?provider.cancel(session,token):provider.verify(session,token);
   validateProof(order,proof);repository.verification(id,session,token);return proof;
  }
 }
 private void validateProof(Map<String,Object> order,PaymentProvider.Payment proof){
  UUID id=(UUID)order.get("id");
  if(proof.live()||!id.toString().equals(proof.reference())||proof.amountMinor()!=((Number)order.get("amount_minor")).intValue()||!"pen".equalsIgnoreCase(proof.currency()))
   throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"La prueba de pago no coincide con la orden.");
 }
 public Map<String,Object> cancelOrder(String user,UUID id){
  var order=repository.order(id);
  if(order.isEmpty()||!user.equals(order.get("user_id")))throw new BillingRuleViolation(BillingRuleViolation.Kind.NOT_FOUND);
  if("cancelled".equals(order.get("status")))return safe(order);
  if(!"pending".equals(order.get("status")))throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"Solo se puede anular un pago pendiente.");
  var proof=paymentProof(order,true);
  validateProof(order,proof);
  if(proof.paid()){repository.confirm(id);throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"El pago ya fue confirmado; no se puede anular como pendiente.");}
  if(!proof.expired())throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"El proveedor todavía no cerró la sesión de pago.");
  if(!repository.cancelled(id))throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"El estado del pago cambió. Actualiza Mis pagos.");
  return safe(repository.order(id));
 }
 public Map<String,Object> cancellation(String user,boolean requested){
  if(!Boolean.TRUE.equals(entitlement(user).get("active")))throw new BillingRuleViolation(BillingRuleViolation.Kind.CONFLICT,"No tienes un plan vigente para cancelar.");
  repository.cancellation(user,requested);return status(user);
 }
 public Map<String,Object> quote(String user,UUID unit,String month,String token){
  requireActive(user);
  YearMonth period;try{period=YearMonth.parse(month);}catch(Exception error){throw new IllegalArgumentException("Mes inválido: usa AAAA-MM.");}
  var zone=ZoneId.of("America/Lima");var now=ZonedDateTime.now(zone);var current=YearMonth.from(now);
  if(period.isAfter(current)||period.isBefore(current.minusMonths(12)))throw new IllegalArgumentException("Selecciona el mes actual o uno de los últimos 12 meses.");
  var access=assets.access(unit,token);Instant start=period.atDay(1).atStartOfDay(zone).toInstant(),end=period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
  if(end.isAfter(now.toInstant()))end=now.toInstant();
  boolean partial=false;
  if(access.get("visibleFrom")!=null){var visible=Instant.parse(access.get("visibleFrom").toString());if(visible.isAfter(start)){partial=true;start=visible;}}
  var result=new HashMap<String,Object>();result.put("unitId",unit.toString());result.put("month",month);result.put("partial",partial);
  if(!start.isBefore(end)){result.put("liters",0);result.put("amountMinor",0);result.put("estimate",Map.of("water","0.00","sewer","0.00","fixedCharge","0.00","subtotal","0.00","tax","0.00","taxRate","0","total","0.00"));result.put("payable",false);result.put("reason","No tienes lecturas visibles en este período.");return result;}
  var data=assets.consumption(unit,start.toString(),end.toString(),token);
  @SuppressWarnings("unchecked")var estimate=(Map<String,Object>)data.get("estimate");
  int amount=amount(estimate);result.put("estimate",estimate);result.put("liters",data.get("liters"));result.put("amountMinor",amount);
  boolean payable=period.isBefore(current)&&!partial&&amount>0;
  String reason=!period.isBefore(current)?"El mes está en curso; podrás pagarlo al cerrar el período.":partial?"El propietario gestiona los meses con alquiler parcial.":amount<=0?"No hay consumo pendiente de pago.":"Importe calculado automáticamente con la tarifa actual.";
  var invoice=repository.waterInvoice(unit,month);
  if(!invoice.isEmpty()){
   String state=invoice.get("status").toString();result.put("invoiceStatus",state);result.put("amountMinor",invoice.get("amount_minor"));
   if("paid".equals(state)){payable=false;reason="Este mes ya está pagado.";}
   else if(!user.equals(invoice.get("user_id"))){payable=false;reason="El otro responsable ya preparó el pago de este mes.";}
   else reason="Existe un pago pendiente: se conserva el importe con el que se emitió.";
  }
  result.put("payable",payable);result.put("reason",reason);return result;
 }
 private void requireActive(String user){
  if(!Boolean.TRUE.equals(entitlement(user).get("active")))throw new BillingRuleViolation(BillingRuleViolation.Kind.PAYMENT_REQUIRED,"Activa o renueva tu plan para consultar y pagar consumo de agua.");
 }
 private int amount(Map<String,Object> estimate){
  try{int value=new BigDecimal(estimate.get("total").toString()).movePointRight(2).intValueExact();if(value<0||value>100000000)throw new ArithmeticException();return value;}
  catch(ArithmeticException error){throw new IllegalArgumentException("El importe excede el límite admitido para el pago.");}
 }
 public List<Map<String,Object>> history(String user){return repository.orders(user).stream().map(this::safe).toList();}
 public void disable(String user,UUID id,String token){assets.disable(id,token);}
 public Map<String,Object> safe(Map<String,Object> row){
  var out=new HashMap<String,Object>();for(String key:List.of("id","kind","plan_code","unit_id","invoice_month","amount_minor","currency","status","created_at","paid_at","cancelled_at","checkout_url"))out.put(key,row.get(key) instanceof java.time.temporal.TemporalAccessor value?value.toString():row.get(key));return out;
 }
 @Scheduled(fixedDelay=30000)public void reconcile(){for(var order:repository.pending())try{verifyOrder(order);}catch(Exception ignored){repository.defer((UUID)order.get("id"));}}
}

