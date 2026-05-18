package com.grenefolc.hildr;

public final class HildrRecognitionResult {

    private final String recognitionProfile;
    private final String presentationClass;
    private final String recognitionMask;

    public HildrRecognitionResult(
            String recognitionProfile,
            String presentationClass,
            String recognitionMask
    ) {
        this.recognitionProfile = recognitionProfile == null ? "" : recognitionProfile;
        this.presentationClass = presentationClass == null ? "" : presentationClass;
        this.recognitionMask = recognitionMask == null ? "" : recognitionMask;
    }

    public String getRecognitionProfile() {
        return recognitionProfile;
    }

    public String getPresentationClass() {
        return presentationClass;
    }

    public String getRecognitionMask() {
        return recognitionMask;
    }
}
