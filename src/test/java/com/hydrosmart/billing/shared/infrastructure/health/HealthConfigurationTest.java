package com.hydrosmart.billing.shared.infrastructure.health;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
class HealthConfigurationTest {
 @Test void driverErrorsAreSanitizedAndSuccessfulDatabaseIsUp()throws Exception{
  var source=mock(DataSource.class);when(source.getConnection()).thenThrow(new SQLException("secret-password jdbc:private"));
  var indicator=new HealthConfiguration().databaseHealthIndicator(source);var down=indicator.health();
  assertEquals("DOWN",down.getStatus().getCode());assertFalse(down.getDetails().toString().contains("secret-password"));
  var connection=mock(Connection.class);var query=mock(Statement.class);var result=mock(ResultSet.class);
  doReturn(connection).when(source).getConnection();when(connection.createStatement()).thenReturn(query);when(query.executeQuery("SELECT 1")).thenReturn(result);when(result.next()).thenReturn(true);when(result.getInt(1)).thenReturn(1);
  assertEquals("UP",indicator.health().getStatus().getCode());verify(query).setQueryTimeout(1);
 }
 @Test void connectedBrokerWithMissingConsumerStillReportsDown()throws Exception{
  var bus=mock(RpcBus.class);var indicator=new HealthConfiguration().rabbitmqHealthIndicator(bus);
  when(bus.diagnostics()).thenReturn(Map.of("ready",false,"connection","UP"));assertEquals("DOWN",indicator.health().getStatus().getCode());
  when(bus.diagnostics()).thenReturn(Map.of("ready",true));assertEquals("UP",indicator.health().getStatus().getCode());
  when(bus.diagnostics()).thenThrow(new Exception("amqp://user:secret@private"));var down=indicator.health();assertEquals("DOWN",down.getStatus().getCode());assertFalse(down.getDetails().toString().contains("amqp://"));
 }
}
