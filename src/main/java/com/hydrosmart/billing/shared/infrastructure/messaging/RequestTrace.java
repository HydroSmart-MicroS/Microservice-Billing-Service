package com.hydrosmart.billing.shared.infrastructure.messaging;
import java.util.*;
public final class RequestTrace {
 private static final ThreadLocal<String> CURRENT=new ThreadLocal<>();
 public static String id(){String value=CURRENT.get();return value==null?UUID.randomUUID().toString():value;}
 private static String valid(String value){return value!=null&&value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")?value:UUID.randomUUID().toString();}
 public static class Filter extends org.springframework.web.filter.OncePerRequestFilter {
  protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{
   String id=valid(req.getHeader("X-Request-ID"));CURRENT.set(id);res.setHeader("X-Request-ID",id);long start=System.nanoTime();
   try{chain.doFilter(req,res);}finally{
    System.out.println(RpcBus.JSON.writeValueAsString(Map.of("event","http_request","service","billing","requestId",id,"method",req.getMethod(),"path",req.getRequestURI().replaceAll("/[A-Za-z0-9_-]{20,}","/:id"),"status",res.getStatus(),"durationMs",(System.nanoTime()-start)/1000000.0)));
    CURRENT.remove();
   }
  }
 }
}
