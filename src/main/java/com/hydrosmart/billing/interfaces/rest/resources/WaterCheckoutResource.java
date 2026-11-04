package com.hydrosmart.billing.interfaces.rest.resources;
import java.util.UUID;
public record WaterCheckoutResource(UUID unitId,String month) {
 public WaterCheckoutResource {
  if(unitId==null||month==null||!month.matches("\\d{4}-(0[1-9]|1[0-2])"))throw new IllegalArgumentException("Selecciona una unidad y un mes válido (AAAA-MM).");
 }
}
