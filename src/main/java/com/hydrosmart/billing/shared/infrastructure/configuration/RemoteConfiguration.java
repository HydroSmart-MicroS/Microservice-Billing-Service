package com.hydrosmart.billing.shared.infrastructure.configuration;

import java.io.StringReader;
import com.hydrosmart.billing.shared.infrastructure.messaging.RpcBus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;

/** Startup configuration. Explicit environment/JVM settings take precedence. */
public final class RemoteConfiguration {
    private RemoteConfiguration() {}
    public static void load() {
        String base = System.getenv("CONFIG_SERVICE_URL");
        if (base == null || base.isBlank()) return;
        try {
            var response=new RpcBus("billing-configuration").request("config-service","GET","/v1/config/billing/local?format=properties",java.util.Map.of(),null);
            if(response.status()!=200)throw new IllegalStateException("Config Service unavailable");
            Properties values = new Properties();
            values.load(new StringReader(response.text()));
            Set<String> allowed = Set.of("JWT_ISSUER", "JWKS_URL", "JWT_AUDIENCE");
            if (!values.stringPropertyNames().equals(allowed)) throw new IllegalStateException("Invalid configuration keys");
            for (String key : allowed) {
                String value = values.getProperty(key);
                if (value == null || value.isBlank()) throw new IllegalStateException("Empty configuration");
                if (System.getenv(key) == null && System.getProperty(key) == null) System.setProperty(key, value);
            }
            System.out.println("Loaded billing configuration from Config Service");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load billing configuration", error);
        }
    }
}
