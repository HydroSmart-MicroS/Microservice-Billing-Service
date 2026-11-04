package com.hydrosmart.billing.infrastructure.persistence.jdbc.repositories;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BillingRecordMappingTest {
 @Test void repositoryExposesPortableDatesAndPreservesMoneyAndNulls(){
  var instant=Instant.parse("2026-10-26T05:00:00Z");var month=LocalDate.of(2026,9,1);
  var row=new HashMap<String,Object>();row.put("paid_through",java.sql.Timestamp.from(instant));row.put("invoice_month",java.sql.Date.valueOf(month));row.put("amount_minor",2856);row.put("cancelled_at",null);
  var mapped=PgBillingRepository.toDomainRow(row);
  assertEquals(instant,mapped.get("paid_through"));assertEquals(month,mapped.get("invoice_month"));assertEquals(2856,mapped.get("amount_minor"));assertTrue(mapped.containsKey("cancelled_at"));assertNull(mapped.get("cancelled_at"));
  assertInstanceOf(java.sql.Timestamp.class,row.get("paid_through"));
 }
}
