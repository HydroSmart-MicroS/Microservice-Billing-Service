package com.hydrosmart.billing.shared.infrastructure.messaging;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
public class MessagingConfiguration {
 private RpcBus bus;
 @Bean(destroyMethod="close") RpcBus rpcBus(@Value("${spring.application.name}")String name){bus=new RpcBus(name);return bus;}
 @EventListener(ApplicationReadyEvent.class) public void ready()throws Exception{bus.startHttpReceiver();}
}

