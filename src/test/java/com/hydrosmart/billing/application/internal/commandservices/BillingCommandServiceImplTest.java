package com.hydrosmart.billing.application.internal.commandservices;
import java.util.*;
import java.time.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.hydrosmart.billing.domain.model.valueobjects.BillingRuleViolation;
import com.hydrosmart.billing.domain.services.*;
import com.hydrosmart.billing.application.internal.outboundservices.AssetDirectory;
class BillingCommandServiceImplTest {
 final BillingRepository repository=mock(BillingRepository.class);
 final PaymentProvider provider=mock(PaymentProvider.class);
 final AssetDirectory assets=mock(AssetDirectory.class);
 final BillingCommandServiceImpl service=new BillingCommandServiceImpl(repository,provider,assets,"http://localhost:8080");
 final UUID id=UUID.randomUUID();
 Map<String,Object> pending(){return Map.of("id",id,"user_id","owner","status","pending","provider_session_id","cs_test_1","verification_token","proof","amount_minor",4000);}
 Map<String,Object> pendingPlan(){
  var order=new HashMap<String,Object>(pending());order.put("kind","plan");order.put("plan_code","premium");order.put("checkout_url","https://checkout.stripe.com/old");
  var rows=new HashMap<UUID,Map<String,Object>>();rows.put(id,order);
  when(repository.pendingPlan("owner")).thenReturn(order);when(repository.order(any(UUID.class))).thenAnswer(call->rows.get(call.getArgument(0)));
  when(repository.membership("owner")).thenReturn(Map.of());when(assets.usage("owner")).thenReturn(Map.of("count",0));
  doAnswer(call->{order.put("status","failed");return null;}).when(repository).failed(id);
  doAnswer(call->{order.put("status","paid");return null;}).when(repository).confirm(id);
  doAnswer(call->{var row=new HashMap<String,Object>(order);row.put("id",call.getArgument(0));row.put("plan_code",call.getArgument(3));row.put("amount_minor",call.getArgument(6));row.put("status","creating");rows.put(call.getArgument(0),row);return null;}).when(repository).create(any(UUID.class),eq("owner"),eq("plan"),anyString(),isNull(),isNull(),anyInt());
  doAnswer(call->{var row=rows.get(call.getArgument(0));row.put("status","pending");row.put("checkout_url",((PaymentProvider.Session)call.getArgument(1)).url());return null;}).when(repository).prepared(any(UUID.class),any(PaymentProvider.Session.class));
  when(provider.create(any(UUID.class),anyInt(),anyString())).thenReturn(new PaymentProvider.Session("cs_test_new","https://checkout.stripe.com/new","new-proof"));return order;
 }
 @Test void openPlanCheckoutIsVerifiedAndReused(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),4000,"pen",false,false));
  assertEquals(id,service.plan("owner","premium").get("id"));verify(provider).verify("cs_test_1","proof");verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void expiredPlanCheckoutIsReplacedOnlyAfterVerifiedExpiry(){
  var old=pendingPlan();when(provider.verify("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),4000,"pen",false,true));
  var replacement=service.plan("owner","black");assertNotEquals(id,replacement.get("id"));assertEquals("pending",replacement.get("status"));assertEquals("failed",old.get("status"));
  verify(provider).create(any(UUID.class),eq(12000),anyString());verify(repository,never()).confirm(id);
 }
 @Test void completedCheckoutIsConfirmedWithoutCreatingAnotherPlanPayment(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(true,id.toString(),4000,"pen",false,false));
  assertEquals("paid",service.plan("owner","black").get("status"));verify(repository).confirm(id);verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void unverifiableCheckoutCannotBeReusedOrReplaced(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenThrow(new IllegalStateException("Provider unavailable"));
  assertThrows(IllegalStateException.class,()->service.plan("owner","premium"));verify(repository,never()).failed(id);verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void mismatchedExpiryProofCannotReleaseThePendingPlan(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),1,"pen",false,true));
  assertThrows(BillingRuleViolation.class,()->service.plan("owner","premium"));verify(repository,never()).failed(id);verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void foreignOrderCannotBeVerified(){
  when(repository.order(id)).thenReturn(pending());assertEquals(BillingRuleViolation.Kind.NOT_FOUND,assertThrows(BillingRuleViolation.class,()->service.verify("other",id)).kind());verifyNoInteractions(provider);
 }
 @Test void lostVerificationRecoversTheSameExpiredSessionBeforeReleasingThePlan(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenThrow(new PaymentProvider.VerificationLost());when(provider.recover(id,4000,"cs_test_1")).thenReturn("new-proof");
  when(provider.verify("cs_test_1","new-proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),4000,"pen",false,true));
  assertEquals("failed",service.verify("owner",id).get("status"));verify(repository).verification(id,"cs_test_1","new-proof");verify(repository).failed(id);verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void recoveryCannotGrantAccessWithoutAnExactProviderProof(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenThrow(new PaymentProvider.VerificationLost());when(provider.recover(id,4000,"cs_test_1")).thenReturn("new-proof");
  when(provider.verify("cs_test_1","new-proof")).thenReturn(new PaymentProvider.Payment(true,id.toString(),1,"pen",false,false));
  assertThrows(BillingRuleViolation.class,()->service.verify("owner",id));verify(repository,never()).verification(any(),anyString(),anyString());verify(repository,never()).confirm(id);verify(repository,never()).failed(id);
 }
 @Test void aRecoveredPaidSessionActivatesAccessWithoutAnotherCheckout(){
  pendingPlan();when(provider.verify("cs_test_1","proof")).thenThrow(new PaymentProvider.VerificationLost());when(provider.recover(id,4000,"cs_test_1")).thenReturn("new-proof");
  when(provider.verify("cs_test_1","new-proof")).thenReturn(new PaymentProvider.Payment(true,id.toString(),4000,"pen",false,false));
  assertEquals("paid",service.verify("owner",id).get("status"));verify(repository).confirm(id);verify(provider,never()).create(any(),anyInt(),anyString());
 }
 @Test void cancellationAlsoRecoversTheOriginalSession(){
  when(repository.order(id)).thenReturn(pending());when(provider.cancel("cs_test_1","proof")).thenThrow(new PaymentProvider.VerificationLost());when(provider.recover(id,4000,"cs_test_1")).thenReturn("new-proof");
  when(provider.cancel("cs_test_1","new-proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),4000,"pen",false,true));when(repository.cancelled(id)).thenReturn(true);
  service.cancelOrder("owner",id);verify(repository).cancelled(id);verify(repository).verification(id,"cs_test_1","new-proof");
 }
 @Test void cancellingPlanRetainsPaidAccess(){
  var until=Instant.now().plusSeconds(86400);
  when(repository.membership("owner")).thenReturn(Map.of("paid_through",until,"plan_code","premium","device_limit",1));
  when(assets.usage("owner")).thenReturn(Map.of("count",0,"items",List.of()));
  var result=service.cancellation("owner",true);
  assertEquals(true,result.get("active"));assertEquals(until.toString(),result.get("paidThrough"));
  verify(repository).cancellation("owner",true);
 }
 @Test void foreignOrderCannotBeCancelled(){
  when(repository.order(id)).thenReturn(pending());
  var error=assertThrows(BillingRuleViolation.class,()->service.cancelOrder("other",id));
  assertEquals(BillingRuleViolation.Kind.NOT_FOUND,error.kind());verifyNoInteractions(provider);
 }
 @Test void pendingOrderOnlyCancelsAfterProviderExpiresIt(){
  when(repository.order(id)).thenReturn(pending());when(provider.cancel("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),4000,"pen",false,true));when(repository.cancelled(id)).thenReturn(true);
  service.cancelOrder("owner",id);verify(repository).cancelled(id);verify(repository,never()).confirm(id);
 }
 @Test void alreadyPaidRaceIsConfirmedAndNeverCancelled(){
  when(repository.order(id)).thenReturn(pending());when(provider.cancel("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(true,id.toString(),4000,"pen",false,false));
  var error=assertThrows(BillingRuleViolation.class,()->service.cancelOrder("owner",id));
  assertEquals(BillingRuleViolation.Kind.CONFLICT,error.kind());verify(repository).confirm(id);verify(repository,never()).cancelled(id);
 }
 @Test void wrongAmountProofCannotCancel(){
  when(repository.order(id)).thenReturn(pending());when(provider.cancel("cs_test_1","proof")).thenReturn(new PaymentProvider.Payment(false,id.toString(),1,"pen",false,true));
  assertThrows(BillingRuleViolation.class,()->service.cancelOrder("owner",id));verify(repository,never()).cancelled(id);
 }
 @Test void quoteComputesMinorUnitsAndDoesNotAllowPayingCurrentMonth(){
  when(repository.membership("owner")).thenReturn(Map.of("paid_through",Instant.now().plusSeconds(86400),"plan_code","premium","device_limit",1));
  UUID unit=UUID.randomUUID();var month=YearMonth.now(ZoneId.of("America/Lima")).minusMonths(1).toString();
  when(assets.access(eq(unit),anyString())).thenReturn(Map.of("ownerUserId","owner"));
  when(assets.consumption(eq(unit),anyString(),anyString(),anyString())).thenReturn(Map.of("liters",5000,"estimate",Map.of("total","28.56","water","11.00","sewer","6.90","fixedCharge","6.30","tax","4.36","subtotal","24.20","taxRate","0.18")));
  when(repository.waterInvoice(eq(unit),anyString())).thenReturn(Map.of());
  var quote=service.quote("owner",unit,month,"token");
  assertEquals(2856,quote.get("amountMinor"));assertEquals(true,quote.get("payable"));
  assertEquals("4.36",((Map<?,?>)quote.get("estimate")).get("tax"));
  assertEquals(false,service.quote("owner",unit,YearMonth.now(ZoneId.of("America/Lima")).toString(),"token").get("payable"));
 }
 @Test void waterBusinessRequiresAnActivePlan(){
  when(repository.membership("owner")).thenReturn(Map.of());
  UUID unit=UUID.randomUUID();String month=YearMonth.now(ZoneId.of("America/Lima")).minusMonths(1).toString();
  assertEquals(BillingRuleViolation.Kind.PAYMENT_REQUIRED,assertThrows(BillingRuleViolation.class,()->service.quote("owner",unit,month,"token")).kind());
  assertEquals(BillingRuleViolation.Kind.PAYMENT_REQUIRED,assertThrows(BillingRuleViolation.class,()->service.water("owner",unit,month,"token")).kind());
  verifyNoInteractions(assets,provider);
 }
}
