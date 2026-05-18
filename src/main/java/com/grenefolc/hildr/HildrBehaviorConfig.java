package com.grenefolc.hildr;

import java.util.Collections;
import java.util.Map;

public final class HildrBehaviorConfig {

    private final Map<String, Object> raw;

    public HildrBehaviorConfig(Map<String, Object> raw) {
        this.raw = raw == null ? Collections.<String, Object>emptyMap() : raw;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public String getString(String key, String defaultValue) {
        Object value = raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? defaultValue : s;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String getPresentationClassOverride() {
        return getCliString("presentationClass", "").toLowerCase();
    }

    public String getSynonymStyle() {
        return getCliString("synonymStyle", "id").toLowerCase();
    }

    public String getMaxOccursMode() {
        return getCliString("maxOccurs", "none").toLowerCase();
    }

    public boolean showCliSettings() {
        return getCliBoolean("showCliSettings", false);
    }

    public boolean showDelimiterSettings() {
        return getCliBoolean("showDelimiterSettings", false);
    }

    public boolean showEnvelopeFilter() {
        return getCliBoolean("showEnvelopeFilter", false);
    }

    public boolean showRecognitionRe() {
        return getCliBoolean("showRecognitionRe", false);
    }

    public boolean showSourceRecord() {
        return getCliBoolean("showSourceRecord", false);
    }

    public boolean showProcessTrace() {
        return getCliBoolean("showProcessTrace", false);
    }

    public boolean envelopeFilterEnabled() {
        return getCliBoolean("envelopeFilter", false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String key) {
        Object value = raw.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private String getCliString(String key, String defaultValue) {
        Map<String, Object> cli = getMap("cli");
        Object value = cli.containsKey(key) ? cli.get(key) : raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private boolean getCliBoolean(String key, boolean defaultValue) {
        Map<String, Object> cli = getMap("cli");
        Object value = cli.containsKey(key) ? cli.get(key) : raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean getNestedBoolean(String parent, String key, boolean defaultValue) {
        Map<String, Object> map = getMap(parent);
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public boolean showRawConfigJson() {
        return showRawCliJson();
    }

    public boolean showRawCliJson() {
        return getNestedBoolean("logging", "showRawCliJson",
                getNestedBoolean("logging", "showRawConfigJson", false));
    }

    public boolean showRawRecogJson() {
        return getNestedBoolean("logging", "showRawRecogJson", false);
    }
}
