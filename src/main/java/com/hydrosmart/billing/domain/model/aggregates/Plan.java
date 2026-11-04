package com.hydrosmart.billing.domain.model.aggregates;
import java.util.*;
public record Plan(String code,int priceMinor,int deviceLimit){
 public static Plan of(String code){
  if(code==null)throw new IllegalArgumentException("Selecciona un plan.");
  return switch(code){case "premium"->new Plan("premium",4000,1);case "black"->new Plan("black",12000,4);default->throw new IllegalArgumentException("Plan inválido.");};
 }
 public static List<Plan> all(){return List.of(of("premium"),of("black"));}
}

