package com.grenefolc.hildr;

import java.util.Collections;
import java.util.Map;

public final class HildrRecognitionSpec {

    private final Map<String, Object> raw;

    public HildrRecognitionSpec(Map<String, Object> raw) {
        this.raw = raw == null ? Collections.<String, Object>emptyMap() : raw;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }
}
