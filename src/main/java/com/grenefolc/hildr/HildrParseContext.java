package com.grenefolc.hildr;

import java.util.ArrayList;
import java.util.List;

public final class HildrParseContext {

    private String routingProfile = "";
    private String routingVariant = "";
    private int sourceIndex = -1;
    private String sourceBuffer = "";
    private String statusCode = "continue";
    private boolean terminalError = false;
    private String terminalErrorReason = "";

    private final List<HildrModelNode> nodeStack = new ArrayList<HildrModelNode>();
    private final List<String> nodePath = new ArrayList<String>();
    private final List<String> nodePathQualified = new ArrayList<String>();
    private final List<String> entityPath = new ArrayList<String>();
    private final List<HildrModelNode> missingMandatoryChild = new ArrayList<HildrModelNode>();

    private final List<String> stdin = new ArrayList<String>();

    private final StringBuilder stream = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();

    private final List<String> envelopeFilterHeader = new ArrayList<String>();
    private final List<String> envelopeFilterFooter = new ArrayList<String>();

    private boolean envelopeHeadLive = true;
    private boolean envelopeTailLive = false;
    private boolean modelStarted = false;
    private boolean modelComplete = false;

    public String getRoutingProfile() { return routingProfile; }
    public void setRoutingProfile(String routingProfile) { this.routingProfile = safe(routingProfile); }

    public String getRoutingVariant() { return routingVariant; }
    public void setRoutingVariant(String routingVariant) { this.routingVariant = safe(routingVariant); }

    public int getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(int sourceIndex) { this.sourceIndex = sourceIndex; }

    public String getSourceBuffer() { return sourceBuffer; }
    public void setSourceBuffer(String sourceBuffer) { this.sourceBuffer = sourceBuffer; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) {
        String next = safe(statusCode);
        if (terminalError && !"error".equals(next)) {
            return;
        }
        this.statusCode = next;
        if ("error".equals(next)) {
            this.terminalError = true;
        }
    }

    public boolean hasTerminalError() { return terminalError; }
    public String getTerminalErrorReason() { return terminalErrorReason; }
    public void markTerminalError(String reason) {
        if (!this.terminalError) {
            this.terminalError = true;
            this.terminalErrorReason = safe(reason);
            this.statusCode = "error";
        }
    }

    public List<HildrModelNode> getNodeStack() { return nodeStack; }
    public List<String> getNodePath() { return nodePath; }
    public List<String> getNodePathQualified() { return nodePathQualified; }
    public List<String> getEntityPath() { return entityPath; }
    public List<HildrModelNode> getMissingMandatoryChild() { return missingMandatoryChild; }
    public List<String> getStdin() { return stdin; }
    public StringBuilder getStream() { return stream; }
    public StringBuilder getStderr() { return stderr; }
    public List<String> getEnvelopeFilterHeader() { return envelopeFilterHeader; }
    public List<String> getEnvelopeFilterFooter() { return envelopeFilterFooter; }

    public boolean isEnvelopeHeadLive() { return envelopeHeadLive; }
    public void setEnvelopeHeadLive(boolean envelopeHeadLive) { this.envelopeHeadLive = envelopeHeadLive; }

    public boolean isEnvelopeTailLive() { return envelopeTailLive; }
    public void setEnvelopeTailLive(boolean envelopeTailLive) { this.envelopeTailLive = envelopeTailLive; }

    public boolean isModelStarted() { return modelStarted; }
    public void setModelStarted(boolean modelStarted) { this.modelStarted = modelStarted; }

    public boolean isModelComplete() { return modelComplete; }
    public void setModelComplete(boolean modelComplete) { this.modelComplete = modelComplete; }

    public String currentModelNodePath() {
        return "//" + String.join("/", nodePath);
    }

    public String currentModelNodePathQualified() {
        return "//" + String.join("/", nodePathQualified);
    }

    public String currentEntityPath() {
        return "//" + String.join("/", entityPath);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
