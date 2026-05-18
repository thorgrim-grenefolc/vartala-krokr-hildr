package com.grenefolc.hildr;

import java.nio.charset.StandardCharsets;

public final class HildrOutputBuilder {

    private HildrOutputBuilder() {
    }

    public static byte[] build(HildrParseResult parseResult, HildrExecutor.LogPackage logPackage) {
        String payload = parseResult == null ? "" : parseResult.getPayloadXml();
        logPackage.trace("Output builder produced payload bytes=" + payload.length());
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
