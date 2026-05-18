package com.grenefolc.hildr;

public final class HildrResolvedConfig {

    private final HildrBehaviorConfig behaviorConfig;
    private final HildrRecognitionSpec recognitionSpec;
    private final String modelXml;

    public HildrResolvedConfig(
            HildrBehaviorConfig behaviorConfig,
            HildrRecognitionSpec recognitionSpec,
            String modelXml
    ) {
        this.behaviorConfig = behaviorConfig;
        this.recognitionSpec = recognitionSpec;
        this.modelXml = modelXml == null ? "" : modelXml;
    }

    public HildrBehaviorConfig getBehaviorConfig() {
        return behaviorConfig;
    }

    public HildrRecognitionSpec getRecognitionSpec() {
        return recognitionSpec;
    }

    public String getModelXml() {
        return modelXml;
    }
}
