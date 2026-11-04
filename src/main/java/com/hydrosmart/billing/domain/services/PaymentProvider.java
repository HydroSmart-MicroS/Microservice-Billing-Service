package com.hydrosmart.billing.domain.services;
import java.util.*;
public interface PaymentProvider {
 record Session(String id,String url,String verificationToken){}
 record Payment(boolean paid,String reference,int amountMinor,String currency,boolean live,boolean expired){}
 Session create(UUID order,int amountMinor,String returnUrl);
 Payment verify(String sessionId,String verificationToken);
 Payment cancel(String sessionId,String verificationToken);
 String recover(UUID order,int amountMinor,String sessionId);
 final class VerificationLost extends IllegalStateException {}
}

