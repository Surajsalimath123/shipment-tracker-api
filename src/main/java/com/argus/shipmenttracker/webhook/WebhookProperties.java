package com.argus.shipmenttracker.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.webhook")
public class WebhookProperties {

    private int     timeoutSeconds = 5;
    private int     maxRetries = 5;
    private long    backoffInitialMs = 1_000;
    private double  backoffMultiplier = 2.0;
    private long    backoffMaxMs = 60_000;
    private int     poolCoreSize = 4;
    private int     poolMaxSize = 16;
    private int     poolQueueCapacity = 1_000;
    private String  signingSecret = "dev-only-webhook-signing-secret-change-me";

    public int getTimeoutSeconds()             { return timeoutSeconds; }
    public void setTimeoutSeconds(int v)       { this.timeoutSeconds = v; }
    public int getMaxRetries()                 { return maxRetries; }
    public void setMaxRetries(int v)           { this.maxRetries = v; }
    public long getBackoffInitialMs()          { return backoffInitialMs; }
    public void setBackoffInitialMs(long v)    { this.backoffInitialMs = v; }
    public double getBackoffMultiplier()       { return backoffMultiplier; }
    public void setBackoffMultiplier(double v) { this.backoffMultiplier = v; }
    public long getBackoffMaxMs()              { return backoffMaxMs; }
    public void setBackoffMaxMs(long v)        { this.backoffMaxMs = v; }
    public int getPoolCoreSize()               { return poolCoreSize; }
    public void setPoolCoreSize(int v)         { this.poolCoreSize = v; }
    public int getPoolMaxSize()                { return poolMaxSize; }
    public void setPoolMaxSize(int v)          { this.poolMaxSize = v; }
    public int getPoolQueueCapacity()          { return poolQueueCapacity; }
    public void setPoolQueueCapacity(int v)    { this.poolQueueCapacity = v; }
    public String getSigningSecret()           { return signingSecret; }
    public void setSigningSecret(String v)     { this.signingSecret = v; }
}
