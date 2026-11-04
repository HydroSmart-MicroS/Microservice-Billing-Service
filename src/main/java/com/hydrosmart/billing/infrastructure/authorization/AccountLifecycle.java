package com.hydrosmart.billing.infrastructure.authorization;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
@Component
public class AccountLifecycle {
 // shortcut: the local runtime has one instance per service; coordinate this gate before scaling horizontally.
 public static final ReentrantReadWriteLock GATE=new ReentrantReadWriteLock(true);
 private final JdbcTemplate jdbc;private final RpcBus bus;
 public AccountLifecycle(JdbcTemplate jdbc,RpcBus bus){this.jdbc=jdbc;this.bus=bus;}
 public boolean blocked(String user){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM privacy_receipts WHERE user_hash=encode(sha256(convert_to(?,'UTF8')),'hex') AND expires_at>now())",Boolean.class,user));}
 public boolean active(String user){return !blocked(user)&&bus.request("identity","GET","/internal/v1/users/"+java.net.URLEncoder.encode(user,java.nio.charset.StandardCharsets.UTF_8),bus.internalHeaders(),null).status()==200;}
 public static class Filter extends org.springframework.web.filter.OncePerRequestFilter {
  protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException {
   boolean user=req.getHeader("Authorization")!=null;
   if(user)GATE.readLock().lock();
   try{chain.doFilter(req,res);}finally{if(user)GATE.readLock().unlock();}
  }
 }
}
