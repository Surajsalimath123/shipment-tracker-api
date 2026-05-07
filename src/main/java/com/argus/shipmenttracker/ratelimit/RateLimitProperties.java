package com.argus.shipmenttracker.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Tier authenticated = new Tier(1000, 100);
    private Tier unauthenticated = new Tier(100, 20);

    public boolean isEnabled()                 { return enabled; }
    public void setEnabled(boolean v)          { this.enabled = v; }
    public Tier getAuthenticated()             { return authenticated; }
    public void setAuthenticated(Tier v)       { this.authenticated = v; }
    public Tier getUnauthenticated()           { return unauthenticated; }
    public void setUnauthenticated(Tier v)     { this.unauthenticated = v; }

    public static class Tier {
        private int requestsPerMinute;
        private int burstCapacity;

        public Tier() {}
        public Tier(int requestsPerMinute, int burstCapacity) {
            this.requestsPerMinute = requestsPerMinute;
            this.burstCapacity     = burstCapacity;
        }

        public int getRequestsPerMinute()       { return requestsPerMinute; }
        public void setRequestsPerMinute(int v) { this.requestsPerMinute = v; }
        public int getBurstCapacity()           { return burstCapacity; }
        public void setBurstCapacity(int v)     { this.burstCapacity = v; }
    }
}
