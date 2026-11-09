package com.hydrosmart.billing.infrastructure.payments;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.hydrosmart.billing.domain.services.PaymentProvider;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
@Component
public class StripeApiPaymentProvider implements PaymentProvider {
 private final String base;
 private final String serviceToken;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

 public StripeApiPaymentProvider(
     @Value("${STRIPE_PAYMENT_API_URL:https://apistripefinal.onrender.com}") String base,
     @Value("${STRIPE_PAYMENT_SERVICE_TOKEN:}") String serviceToken) {
  this.base=base.replaceAll("/+$","");
  this.serviceToken=serviceToken;
 }
 @SuppressWarnings("unchecked") private Map<String,Object> send(String path,String method,Map<String,String> headers,Object body){
  try{
   var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(70));
   if(!serviceToken.isBlank())builder.header("Authorization","Bearer "+serviceToken);
   headers.forEach(builder::header);
   builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(RpcBus.JSON.writeValueAsString(body)));
   var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()==401)throw new PaymentProvider.VerificationLost();
   if(response.statusCode()!=200)throw new IllegalStateException("Proveedor de pagos no disponible.");
   return RpcBus.JSON.readValue(response.body(),Map.class);
  }catch(PaymentProvider.VerificationLost error){throw error;}
  catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Pago interrumpido.");}
  catch(Exception error){throw new IllegalStateException("No se pudo verificar el pago con la API alojada.",error);}
 }
 private static int minor(Object value){try{return new java.math.BigDecimal(String.valueOf(value)).intValueExact();}catch(Exception error){throw new IllegalStateException("Importe inválido del proveedor.");}}
 public Session create(UUID order,int amount,String returnUrl){
  var result=send("/api/create-checkout-session","POST",Map.of("Content-Type","application/json","Idempotency-Key",order.toString()),
      Map.of("amount",amount,"currency","pen","reference",order.toString(),"successUrl",returnUrl,"cancelUrl",returnUrl+"&cancelled=1"));
  if(!"pen".equalsIgnoreCase(String.valueOf(result.get("currency")))||!order.toString().equals(result.get("reference"))||
     !(result.get("amount") instanceof Number)||minor(result.get("amount"))!=amount||!Boolean.FALSE.equals(result.get("livemode"))||
     result.get("sessionId")==null||result.get("verificationToken")==null||
     !String.valueOf(result.get("url")).startsWith("https://checkout.stripe.com/")){
   throw new IllegalStateException("La API de Stripe necesita el contrato de Checkout PEN y verificación segura.");
  }
  return new Session(result.get("sessionId").toString(),result.get("url").toString(),result.get("verificationToken").toString());
 }
 public Payment verify(String session,String token){
  var result=send("/api/checkout-sessions/"+session,"GET",Map.of("X-Verification-Token",token),null);
  return proof(session,result);
 }
 public Payment cancel(String session,String token){return proof(session,send("/api/checkout-sessions/"+session+"/cancel","POST",Map.of("X-Verification-Token",token),null));}
 public String recover(UUID order,int amount,String session){
  var result=send("/api/create-checkout-session","POST",Map.of("Content-Type","application/json"),Map.of("reference",order.toString(),"amount",amount,"currency","pen","sessionId",session));
  if(!session.equals(result.get("sessionId"))||!order.toString().equals(result.get("reference"))||minor(result.get("amount"))!=amount||!"pen".equalsIgnoreCase(String.valueOf(result.get("currency")))||!Boolean.FALSE.equals(result.get("livemode"))||!(result.get("verificationToken") instanceof String token)||!token.matches("[a-f0-9]{64}"))
   throw new IllegalStateException("No pudimos recuperar la verificación de la sesión original.");
  return token;
 }
 private Payment proof(String session,Map<String,Object> result){
  if(!session.equals(result.get("sessionId"))||!Boolean.FALSE.equals(result.get("livemode")))throw new IllegalStateException("La prueba no pertenece a una sesión de pruebas válida.");
  return new Payment("paid".equals(result.get("paymentStatus")),String.valueOf(result.get("reference")),
      minor(result.get("amount")),String.valueOf(result.get("currency")),Boolean.TRUE.equals(result.get("livemode")),"expired".equals(result.get("status")));
 }
}

