package com.hydrosmart.billing.domain.model.valueobjects;
public final class BillingRuleViolation extends RuntimeException {
 public enum Kind { NOT_FOUND, FORBIDDEN, CONFLICT, PAYMENT_REQUIRED, UNAVAILABLE }
 private final Kind kind;
 public BillingRuleViolation(Kind kind){this(kind,"La operación no está disponible.");}
 public BillingRuleViolation(Kind kind,String message){super(message);this.kind=kind;}
 public Kind kind(){return kind;}
}
