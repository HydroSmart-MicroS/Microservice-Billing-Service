package com.hydrosmart.billing.shared.infrastructure.health;
import java.util.Map;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.*;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
@Configuration
public class HealthConfiguration {
 @Bean HealthIndicator applicationHealthIndicator(@Value("${spring.application.name}")String name){return ()->Health.up().withDetail("service",name).build();}
 @Bean HealthIndicator databaseHealthIndicator(DataSource source){return ()->{
  long start=System.nanoTime();boolean ready=false;
  try(var connection=source.getConnection();var query=connection.createStatement()){
   query.setQueryTimeout(1);try(var result=query.executeQuery("SELECT 1")){ready=result.next()&&result.getInt(1)==1;}
  }catch(Exception ignored){}
  return (ready?Health.up():Health.down()).withDetail("type","PostgreSQL").withDetail("check","SELECT 1")
   .withDetail("message",ready?"La base de datos responde.":"No se pudo consultar la base de datos; revisa su disponibilidad y acceso.")
   .withDetail("durationMs",(System.nanoTime()-start)/1000000.0).withDetail("checkedAt",Instant.now().toString()).build();
 };}
 @Bean HealthIndicator rabbitmqHealthIndicator(RpcBus bus){return ()->{
  long start=System.nanoTime();
  try{var details=bus.diagnostics();boolean ready=Boolean.TRUE.equals(details.get("ready"));return (ready?Health.up():Health.down()).withDetails(details)
   .withDetail("message",ready?"Conexión, canal y consumidores disponibles.":"El broker responde, pero falta un consumidor requerido.")
   .withDetail("durationMs",(System.nanoTime()-start)/1000000.0).withDetail("checkedAt",Instant.now().toString()).build();}
  catch(Exception ignored){return Health.down().withDetail("message","No se pudo comprobar RabbitMQ; revisa la conexión, permisos y las colas del servicio.")
   .withDetail("durationMs",(System.nanoTime()-start)/1000000.0).withDetail("checkedAt",Instant.now().toString()).build();}
 };}
}
