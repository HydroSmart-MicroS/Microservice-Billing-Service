package com.hydrosmart.billing.shared.infrastructure.messaging;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

@Configuration
public class BrokerJwtConfiguration {
 @Bean JwtDecoder jwtDecoder(com.hydrosmart.billing.infrastructure.authorization.AccountLifecycle lifecycle,RpcBus bus,@Value("${JWT_ISSUER}")String issuer,@Value("${JWT_AUDIENCE:hydrosmart-api}")String audience){
  var source=new JWKSource<SecurityContext>(){
   private JWKSet keys;private long fetched;
   public synchronized List<JWK> get(JWKSelector selector,SecurityContext context)throws KeySourceException{
    try{
     if(keys==null||System.currentTimeMillis()-fetched>300000){
      keys=JWKSet.parse(bus.request("identity","GET","/api/auth/jwks",Map.of(),null).text());fetched=System.currentTimeMillis();
     }
     var selected=selector.select(keys);
     if(selected.isEmpty()&&System.currentTimeMillis()-fetched>5000){
      keys=JWKSet.parse(bus.request("identity","GET","/api/auth/jwks",Map.of(),null).text());fetched=System.currentTimeMillis();selected=selector.select(keys);
     }
     return selected;
    }catch(Exception error){throw new KeySourceException("Identity broker unavailable");}
   }
  };
  var processor=new DefaultJWTProcessor<SecurityContext>();
  processor.setJWSKeySelector(new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.ES256,source));
  var decoder=new NimbusJwtDecoder(processor);
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(JwtValidators.createDefaultWithIssuer(issuer),jwt->{
   boolean valid=jwt.getExpiresAt()!=null&&jwt.getSubject()!=null&&!jwt.getSubject().isBlank()&&jwt.getAudience().contains(audience)&&Set.of("propietario","inquilino").contains(jwt.getClaimAsString("profile_type"));
   return valid?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
  }));
  return raw->{var value=decoder.decode(raw);if(!lifecycle.active(value.getSubject()))throw new BadJwtException("Account is inactive");return value;};
 }
}

