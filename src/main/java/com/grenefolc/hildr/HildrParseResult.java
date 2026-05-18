package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HildrParseResult {

    private boolean success;
    private String message = "";
    private String payloadXml = "";
    private final Map<String, Object> metrics = new LinkedHashMap<String, Object>();
    private HildrEventSupport eventSupport;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }

    public String getPayloadXml() { return payloadXml; }
    public void setPayloadXml(String payloadXml) { this.payloadXml = payloadXml == null ? "" : payloadXml; }

    public Map<String, Object> getMetrics() { return metrics; }

    public HildrEventSupport getEventSupport() { return eventSupport; }
    public void setEventSupport(HildrEventSupport eventSupport) { this.eventSupport = eventSupport; }
}
