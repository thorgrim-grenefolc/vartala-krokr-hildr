package com.grenefolc.hildr;

public final class HildrDelimiterProfile {

    private String presentationClass = "";
    private String delimiterSource = "";
    private String delimiterString = "";

    private String tagSeparator = "";
    private String elementSeparator = "";
    private String compositeSeparator = "";
    private String decimalNotation = "";
    private String releaseIndicator = "";
    private String repetitionSeparator = "";
    private String segmentTerminator = "";

    private String envelopeHeader = "";
    private String envelopeFooter = "";

    public String getPresentationClass() {
        return presentationClass;
    }

    public void setPresentationClass(String presentationClass) {
        this.presentationClass = safe(presentationClass);
    }

    public String getDelimiterSource() {
        return delimiterSource;
    }

    public void setDelimiterSource(String delimiterSource) {
        this.delimiterSource = safe(delimiterSource);
    }

    public String getDelimiterString() {
        return delimiterString;
    }

    public void setDelimiterString(String delimiterString) {
        this.delimiterString = safe(delimiterString);
    }

    public String getTagSeparator() {
        return tagSeparator;
    }

    public void setTagSeparator(String tagSeparator) {
        this.tagSeparator = safe(tagSeparator);
    }

    public String getElementSeparator() {
        return elementSeparator;
    }

    public void setElementSeparator(String elementSeparator) {
        this.elementSeparator = safe(elementSeparator);
    }

    public String getCompositeSeparator() {
        return compositeSeparator;
    }

    public void setCompositeSeparator(String compositeSeparator) {
        this.compositeSeparator = safe(compositeSeparator);
    }

    public String getDecimalNotation() {
        return decimalNotation;
    }

    public void setDecimalNotation(String decimalNotation) {
        this.decimalNotation = safe(decimalNotation);
    }

    public String getReleaseIndicator() {
        return releaseIndicator;
    }

    public void setReleaseIndicator(String releaseIndicator) {
        this.releaseIndicator = safe(releaseIndicator);
    }

    public String getRepetitionSeparator() {
        return repetitionSeparator;
    }

    public void setRepetitionSeparator(String repetitionSeparator) {
        this.repetitionSeparator = safe(repetitionSeparator);
    }

    public String getSegmentTerminator() {
        return segmentTerminator;
    }

    public void setSegmentTerminator(String segmentTerminator) {
        this.segmentTerminator = safe(segmentTerminator);
    }

    public String getEnvelopeHeader() {
        return envelopeHeader;
    }

    public void setEnvelopeHeader(String envelopeHeader) {
        this.envelopeHeader = safe(envelopeHeader);
    }

    public String getEnvelopeFooter() {
        return envelopeFooter;
    }

    public void setEnvelopeFooter(String envelopeFooter) {
        this.envelopeFooter = safe(envelopeFooter);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
