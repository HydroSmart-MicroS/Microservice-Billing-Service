package com.hydrosmart.billing.interfaces.rest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.hydrosmart.billing.domain.services.AccountErasureService;
@RestController
@RequestMapping("/internal/v1/privacy/{user}")
public class AccountErasureController {
 private final AccountErasureService repository;
 public AccountErasureController(AccountErasureService repository){this.repository=repository;}
 @GetMapping public Map<String,Object> check(@PathVariable String user){return repository.inspect(user);}
 @PostMapping("/prepare") public Map<String,Object> prepare(@PathVariable String user){return repository.prepare(user);}
 @PostMapping("/release") public Map<String,Object> release(@PathVariable String user){repository.release(user);return Map.of("released",true);}
 public record Erase(java.util.List<java.util.UUID> unitIds){}
 @PostMapping("/erase") public Map<String,Object> erase(@PathVariable String user,@RequestBody Erase body){if(body.unitIds()==null||body.unitIds().size()>10000)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);repository.erase(user,body.unitIds().stream().map(Object::toString).toList());return Map.of("erased",true);}
}
