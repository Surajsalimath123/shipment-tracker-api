package com.argus.shipmenttracker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 3_600_000L;
    private long refreshExpirationMs = 604_800_000L;
    private String issuer = "argus-shipment-tracker";

    public String getSecret()              { return secret; }
    public void setSecret(String s)        { this.secret = s; }
    public long getExpirationMs()          { return expirationMs; }
    public void setExpirationMs(long ms)   { this.expirationMs = ms; }
    public long getRefreshExpirationMs()   { return refreshExpirationMs; }
    public void setRefreshExpirationMs(long ms) { this.refreshExpirationMs = ms; }
    public String getIssuer()              { return issuer; }
    public void setIssuer(String i)        { this.issuer = i; }
}
