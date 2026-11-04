package com.hydrosmart.billing.interfaces.rest.resources;
public record PlanCheckoutResource(String planCode) {
 public PlanCheckoutResource {
  if(planCode==null||planCode.isBlank()||planCode.length()>32)throw new IllegalArgumentException("Selecciona un plan válido.");
 }
}
