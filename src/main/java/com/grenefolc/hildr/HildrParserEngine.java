package com.grenefolc.hildr;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HildrParserEngine {

    private static final Map<HildrModelNode, Boolean> STRUCTURAL_BLOCKED = new IdentityHashMap<>();

    private HildrParserEngine() {
    }

    public static HildrParseResult parse(
            String dataStream,
            HildrBehaviorConfig behaviorConfig,
            HildrModel model,
            HildrDelimiterProfile delimiterProfile,
            HildrParseContext context,
            HildrPresentationHandler presentationHandler,
            HildrExecutor.LogPackage logPackage
    ) {
        HildrParseResult result = new HildrParseResult();

        try {
            STRUCTURAL_BLOCKED.clear();

            presentationHandler.prepareInput(dataStream, delimiterProfile, context, logPackage);
            readSourceBuffer(context, logPackage, behaviorConfig, delimiterProfile);

            context.getStream().append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            context.getStream().append("<vartala:ediSemantic")
                    .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
                    .append(" xmlns:vartala=\"urn:grenefolc:vartala:ediSemantic\"")
                    .append(" xsi:schemaLocation=\"urn:grenefolc:vartala:ediSemantic vartala:ediSemantic.xsd\"")
                    .append(" presentationClass=\"").append(xmlEscape(delimiterProfile.getPresentationClass())).append("\"")
                    .append(" routingProfile=\"").append(xmlEscape(context.getRoutingProfile())).append("\"")
                    .append(">");

            recurseModel(model, context, behaviorConfig, presentationHandler, logPackage, delimiterProfile);

            context.getStream().append("</vartala:ediSemantic>");

            normalizeFinalStatus(context, logPackage, "normalizeFinalStatus");
            validateNoUnconsumedSource(context, logPackage, delimiterProfile);

            result.setSuccess("eof".equals(context.getStatusCode())
                    && logPackage.getStatusError() == null);
            result.setMessage(
                    result.isSuccess()
                            ? "Hildr parse completed."
                            : "Hildr parse stopped with statusCode=" + context.getStatusCode()
            );
            result.setPayloadXml(context.getStream().toString());

        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        } finally {
            STRUCTURAL_BLOCKED.clear();
        }

        return result;
    }

    private static void recurseModel(
            HildrModel model,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrPresentationHandler presentationHandler,
            HildrExecutor.LogPackage logPackage,
            HildrDelimiterProfile delimiterProfile
    ) {
        if (model == null) {
            setStatusCode(context, "error", logPackage, "recurseModel:model == null");
            return;
        }

        for (HildrModelNode node : model.getTopLevelNodes()) {
            recurseNode(node, context, behaviorConfig, presentationHandler, logPackage, delimiterProfile);
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }
        }

        if ("continue".equals(context.getStatusCode())) {
            context.setModelComplete(true);
            if (behaviorConfig.envelopeFilterEnabled()) {
                context.setEnvelopeTailLive(true);
                trace(logPackage, "MODEL_COMPLETE: envelope tail filter enabled");
                readRemainingTailEnvelope(context, logPackage, behaviorConfig, delimiterProfile);
            }
            setStatusCode(context, "eof", logPackage, "recurseModel:top-level complete");
        }
    }

    private static void recurseNode(
            HildrModelNode node,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrPresentationHandler presentationHandler,
            HildrExecutor.LogPackage logPackage,
            HildrDelimiterProfile delimiterProfile
    ) {
        if (context.hasTerminalError()) {
            return;
        }
        pushNode(node, context);
        traceNodeState(logPackage, context, node, "ENTER_NODE");

        try {
            node.setOccursCount(0L);

            while ("continue".equals(context.getStatusCode()) && !context.hasTerminalError()) {
                node.setInstantiated(false);
                node.setPrinted(false);
                node.setConfirmed(false);
                setStructuralBlocked(node, false);

                traceNodeState(logPackage, context, node, "LOOP_BEGIN");

                boolean returnCode = nodeBegin(
                        node,
                        context,
                        behaviorConfig,
                        presentationHandler,
                        logPackage,
                        delimiterProfile
                );
                traceNodeState(logPackage, context, node, "AFTER_NODE_BEGIN");
                if (!returnCode) {
                    setStatusCode(context, "error", logPackage, "recurseNode:nodeBegin returned false");
                    return;
                }
                if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                    return;
                }

                if ("continue".equals(context.getStatusCode())
                        && ("md".equals(node.getNodeType()) || "gr".equals(node.getNodeType()))) {

                    for (int i = 0; i < node.getChildren().size(); i++) {
                        HildrModelNode child = node.getChildren().get(i);
                        child.setOrdinal(i);
                        traceNodeState(logPackage, context, node, "BEFORE_CHILD[" + i + "] -> " + safe(child.getId()));
                        returnCode = recurseNodeChild(
                                child,
                                context,
                                behaviorConfig,
                                presentationHandler,
                                logPackage,
                                delimiterProfile
                        );
                        traceNodeState(logPackage, context, node, "AFTER_CHILD[" + i + "] -> " + safe(child.getId()));
                        if (!returnCode) {
                            setStatusCode(context, "error", logPackage, "recurseNode:child returned error");
                            return;
                        }
                        if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                            return;
                        }

                        if (!node.isInstantiated() && currentMissing(context) != null) {
                            setStructuralBlocked(node, true);
                            traceNodeState(logPackage, context, node, "STOP_CHILDREN_STRUCTURAL_BLOCK");
                            break;
                        }
                    }
                }

                traceNodeState(logPackage, context, node, "BEFORE_NODE_END");
                if (!"break".equals(context.getStatusCode())) {
                    returnCode = nodeEnd(node, context, behaviorConfig, logPackage);
                    traceNodeState(logPackage, context, node, "AFTER_NODE_END");
                    if (!returnCode) {
                        setStatusCode(context, "error", logPackage, "recurseNode:nodeEnd returned false");
                        return;
                    }
                }

                if (!node.isInstantiated()) {
                    setStatusCode(context, "break", logPackage, "recurseNode:node not instantiated after loop body");
                }

                traceNodeState(logPackage, context, node, "LOOP_END");
            }

            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }

            traceNodeState(logPackage, context, node, "BEFORE_MINOCCURS");
            if (!nodeInstantiationMinOccurs(node, context, logPackage)) {
                setStatusCode(context, "error", logPackage, "recurseNode:nodeInstantiationMinOccurs returned false");
                return;
            }
            traceNodeState(logPackage, context, node, "AFTER_MINOCCURS");

            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }

            if (!"eof".equals(context.getStatusCode())) {
                setStatusCode(context, "continue", logPackage, "recurseNode:resume parent traversal");
            }

        } finally {
            traceNodeState(logPackage, context, node, "EXIT_NODE");
            popNode(context);
        }
    }

    private static boolean recurseNodeChild(
            HildrModelNode child,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrPresentationHandler presentationHandler,
            HildrExecutor.LogPackage logPackage,
            HildrDelimiterProfile delimiterProfile
    ) {
        recurseNode(child, context, behaviorConfig, presentationHandler, logPackage, delimiterProfile);
        return !context.hasTerminalError() && !"error".equals(context.getStatusCode());
    }

    private static boolean nodeBegin(
            HildrModelNode node,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrPresentationHandler presentationHandler,
            HildrExecutor.LogPackage logPackage,
            HildrDelimiterProfile delimiterProfile
    ) {
        if ("sd".equals(node.getNodeType())) {
            String recognitionRe = safe(node.getRecognitionRegex());
            String sourceBuffer = safe(context.getSourceBuffer());

            trace(logPackage, "MATCH_ATTEMPT path=" + xpathPath(context)
                    + " nodeId=" + safe(node.getId())
                    + " sourceTag=" + sourceTag(sourceBuffer, delimiterProfile)
                    + " sourceBuffer=" + sourceBuffer
                    + " recognitionRe=" + recognitionRe);

            if (!recognitionRe.isEmpty() && sourceBuffer.matches("^" + recognitionRe + ".*")) {
                node.setInstantiated(true);
                if (!context.isModelStarted()) {
                    context.setModelStarted(true);
                    context.setEnvelopeHeadLive(false);
                    trace(logPackage, "MODEL_STARTED: envelope head filter disabled");
                }
                trace(logPackage, "MATCH_OK path=" + xpathPath(context)
                        + " nodeId=" + safe(node.getId())
                        + " sourceTag=" + sourceTag(sourceBuffer, delimiterProfile)
                        + " sourceBuffer=" + sourceBuffer);
            } else {
                setStatusCode(context, "mismatch", logPackage, "nodeBegin:sd mismatch @" + safe(node.getId()));
                appendError(
                        context,
                        "invalid data stream structure encountered at " + context.currentModelNodePathQualified(),
                        context.getSourceBuffer()
                );
                return true;
            }
        }

        if (maxOccursEnabled(behaviorConfig) && "sd".equals(node.getNodeType())) {
            if (!nodeInstantiationMaxOccurs(node, context, logPackage)) {
                return false;
            }
        }

        if (!"continue".equals(context.getStatusCode())) {
            return true;
        }

        if ("sd".equals(node.getNodeType())) {
            instantiateAncestorChain(context);
            traceNodeState(logPackage, context, node, "BEFORE_PARSE_SEGMENT");
            presentationHandler.parseSegment(node, context, behaviorConfig, delimiterProfile, logPackage);
            traceNodeState(logPackage, context, node, "AFTER_PARSE_SEGMENT");
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return false;
            }
            if (!"continue".equals(context.getStatusCode())) {
                return true;
            }
            readSourceBuffer(context, logPackage, behaviorConfig, delimiterProfile);
        }

        return true;
    }

    private static boolean nodeEnd(
            HildrModelNode node,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrExecutor.LogPackage logPackage
    ) {
        if (!node.isInstantiated()) {
            setStatusCode(context, "break", logPackage, "nodeEnd:node not instantiated @" + safe(node.getId()));
            return true;
        }

        if (maxOccursEnabled(behaviorConfig) && ("md".equals(node.getNodeType()) || "gr".equals(node.getNodeType()))) {
            if (!nodeInstantiationMaxOccurs(node, context, logPackage)) {
                return false;
            }
        }

        long before = node.getOccursCount();
        node.setOccursCount(node.getOccursCount() + 1L);
        trace(logPackage, "NODE_END_OCCURS path=" + currentPath(context)
                + " id=" + safe(node.getId())
                + " before=" + before
                + " after=" + node.getOccursCount());

        if (context.getNodeStack().size() > 1) {
            HildrModelNode parent = context.getNodeStack().get(context.getNodeStack().size() - 2);
            parent.setInstantiated(true);
            trace(logPackage, "NODE_END_PARENT_INSTANTIATED child=" + safe(node.getId())
                    + " parent=" + safe(parent.getId())
                    + " parentPath=" + parentPath(context));
        }

        if (!"sd".equals(node.getNodeType())) {
            context.getStream().append("</").append(node.getEntityName()).append(">");
        }

        return true;
    }

    private static boolean nodeInstantiationMinOccurs(
            HildrModelNode node,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage
    ) {
        HildrModelNode currentMissing = currentMissing(context);

        trace(logPackage, "MINOCCURS_CHECK path=" + currentPath(context)
                + " id=" + safe(node.getId())
                + " nodeType=" + safe(node.getNodeType())
                + " nodeInstantiated=" + node.isInstantiated()
                + " occursCount=" + node.getOccursCount()
                + " minOccurs=" + node.getMinOccurs()
                + " currentMissing=" + (currentMissing == null ? "" : safe(currentMissing.getId()))
                + " structuralBlocked=" + isStructuralBlocked(node)
                + " status=" + safe(context.getStatusCode())
                + " sourceBuffer=" + safe(context.getSourceBuffer()));

        // Release 16 behavior retained
        if (node.isInstantiated() && currentMissing != null) {
            if (context.getNodeStack().size() > 1) {
                int parentIndex = context.getMissingMandatoryChild().size() - 2;
                if (parentIndex >= 0 && context.getMissingMandatoryChild().get(parentIndex) == null) {
                    context.getMissingMandatoryChild().set(parentIndex, currentMissing);
                    trace(logPackage, "MINOCCURS_PROPAGATE_CURRENT_MISSING path=" + currentPath(context)
                            + " fromNode=" + safe(node.getId())
                            + " toParentIndex=" + parentIndex
                            + " missing=" + safe(currentMissing.getId()));
                }
            }
            return true;
        }

        if (context.getNodeStack().size() > 1) {
            HildrModelNode parent = context.getNodeStack().get(context.getNodeStack().size() - 2);

            if (node.getOccursCount() < node.getMinOccurs()) {
                if (parent.isInstantiated()) {
                    if (node.getMinOccurs() > 0L) {
                        trace(logPackage, "MINOCCURS_FAIL_PARENT_INSTANTIATED path=" + currentPath(context)
                                + " node=" + safe(node.getId())
                                + " parent=" + safe(parent.getId()));
                        recordTerminalError(
                                logPackage,
                                context,
                                node,
                                "mandatorySegmentMismatch",
                                buildMandatoryMismatchMessage(node, context)
                        );
                        appendError(
                                context,
                                "node minimum occurrence error @ " + xpathPath(context),
                                context.getSourceBuffer()
                        );
                        return false;
                    }
                } else {
                    int parentIndex = context.getMissingMandatoryChild().size() - 2;
                    if (node.getMinOccurs() > 0L && parentIndex >= 0
                            && context.getMissingMandatoryChild().get(parentIndex) == null) {
                        context.getMissingMandatoryChild().set(parentIndex, node);
                        trace(logPackage, "MINOCCURS_RECORD_PARENT path=" + currentPath(context)
                                + " node=" + safe(node.getId())
                                + " parent=" + safe(parent.getId())
                                + " parentIndex=" + parentIndex);
                    }
                }
            }
        }

        if (isStructuralBlocked(node) && context.getNodeStack().size() > 1) {
            int parentIndex = context.getMissingMandatoryChild().size() - 2;
            if (parentIndex >= 0 && context.getMissingMandatoryChild().get(parentIndex) == null) {
                HildrModelNode currentMissingNode = currentMissing(context);
                if (currentMissingNode != null) {
                    context.getMissingMandatoryChild().set(parentIndex, currentMissingNode);
                    trace(logPackage, "MINOCCURS_PROPAGATE_STRUCTURAL_BLOCK path=" + currentPath(context)
                            + " node=" + safe(node.getId())
                            + " parentIndex=" + parentIndex
                            + " missing=" + safe(currentMissingNode.getId()));
                }
            }
        }

        return true;
    }

    private static boolean nodeInstantiationMaxOccurs(
            HildrModelNode node,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage
    ) {
        trace(logPackage, "MAXOCCURS_CHECK path=" + currentPath(context)
                + " id=" + safe(node.getId())
                + " occursCount=" + node.getOccursCount()
                + " maxOccurs=" + node.getMaxOccurs()
                + " status=" + safe(context.getStatusCode()));

        if (node.getOccursCount() < node.getMaxOccurs()) {
            return true;
        }

        if (context.getNodeStack().size() > 1) {
            HildrModelNode parent = context.getNodeStack().get(context.getNodeStack().size() - 2);
            int currentOrdinal = node.getOrdinal();

            if (parent.isInstantiated() && !parent.isConfirmed()) {
                for (int o = currentOrdinal - 1; o >= 0; o--) {
                    HildrModelNode sibling = parent.getChildren().get(o);
                    if (sibling.getMinOccurs() > 0L) {
                        trace(logPackage, "MAXOCCURS_FAIL_REQUIRED_PRIOR path=" + currentPath(context)
                                + " node=" + safe(node.getId())
                                + " sibling=" + safe(sibling.getId()));
                        recordTerminalError(
                                logPackage,
                                context,
                                node,
                                "maxOccursExceeded",
                                buildMaxOccursMessage(node, context)
                        );
                        appendError(
                                context,
                                "node maximum occurrence error @ " + xpathPath(context),
                                context.getSourceBuffer()
                        );
                        return false;
                    }
                }
                parent.setConfirmed(true);
                trace(logPackage, "MAXOCCURS_PARENT_CONFIRMED path=" + currentPath(context)
                        + " parent=" + safe(parent.getId()));
            }
        }

        setStatusCode(context, "break", logPackage, "nodeInstantiationMaxOccurs:break @" + safe(node.getId()));
        return true;
    }

    private static void instantiateAncestorChain(HildrParseContext context) {
        for (HildrModelNode ancestor : context.getNodeStack()) {
            ancestor.setInstantiated(true);
            if ("sd".equals(ancestor.getNodeType())) {
                continue;
            }
            if (!ancestor.isPrinted()) {
                context.getStream().append("<").append(ancestor.getEntityName()).append(">");
                ancestor.setPrinted(true);
            }
        }
    }

    private static void pushNode(HildrModelNode node, HildrParseContext context) {
        context.getNodeStack().add(node);

        String nodeName = safe(node.getNodeType());
        String nodeId = safe(node.getId());
        String qualified = nodeName + "[@id=\"" + nodeId + "\"]";

        context.getNodePath().add(nodeName);
        context.getNodePathQualified().add(qualified);
        context.getEntityPath().add(nodeId);
        context.getMissingMandatoryChild().add(null);
    }

    private static void popNode(HildrParseContext context) {
        if (!context.getNodeStack().isEmpty()) {
            context.getNodeStack().remove(context.getNodeStack().size() - 1);
        }
        if (!context.getNodePath().isEmpty()) {
            context.getNodePath().remove(context.getNodePath().size() - 1);
        }
        if (!context.getNodePathQualified().isEmpty()) {
            context.getNodePathQualified().remove(context.getNodePathQualified().size() - 1);
        }
        if (!context.getEntityPath().isEmpty()) {
            context.getEntityPath().remove(context.getEntityPath().size() - 1);
        }
        if (!context.getMissingMandatoryChild().isEmpty()) {
            context.getMissingMandatoryChild().remove(context.getMissingMandatoryChild().size() - 1);
        }
    }

    private static HildrModelNode currentMissing(HildrParseContext context) {
        if (context.getMissingMandatoryChild().isEmpty()) {
            return null;
        }
        return context.getMissingMandatoryChild().get(context.getMissingMandatoryChild().size() - 1);
    }

    private static void readSourceBuffer(
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile
    ) {
        context.setSourceIndex(context.getSourceIndex() + 1);

        while (context.getSourceIndex() < context.getStdin().size()) {
            String record = context.getStdin().get(context.getSourceIndex());

            if (shouldSkipEnvelope(record, context, behaviorConfig, delimiterProfile)) {
                trace(logPackage, "READ_SOURCEBUFFER_SKIP_ENVELOPE record=" + record);
                context.setSourceIndex(context.getSourceIndex() + 1);
                continue;
            }

            context.setSourceBuffer(record);
            trace(logPackage, "readSourceBuffer[" + record + "]");
            return;
        }

        context.setSourceBuffer(null);
        setStatusCode(context, "eof", logPackage, "readSourceBuffer:end of stdin");
    }

    private static boolean shouldSkipEnvelope(
            String record,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile
    ) {
        if (!behaviorConfig.envelopeFilterEnabled()) {
            return false;
        }

        if (record == null || record.isEmpty()) {
            return false;
        }

        String tag = sourceTag(record, delimiterProfile);

        if (context.isEnvelopeHeadLive()) {
            String header = safe(delimiterProfile.getEnvelopeHeader());
            return !header.isEmpty() && tag.matches("^(?:" + header + ")$");
        }

        if (context.isEnvelopeTailLive()) {
            String footer = safe(delimiterProfile.getEnvelopeFooter());
            return !footer.isEmpty() && tag.matches("^(?:" + footer + ")$");
        }

        return false;
    }

    private static void readRemainingTailEnvelope(
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile
    ) {
        while (context.getSourceIndex() >= 0 && context.getSourceIndex() < context.getStdin().size()) {
            String record = context.getStdin().get(context.getSourceIndex());

            if (shouldSkipEnvelope(record, context, behaviorConfig, delimiterProfile)) {
                trace(logPackage, "READ_SOURCEBUFFER_SKIP_TAIL_ENVELOPE record=" + record);
                context.setSourceIndex(context.getSourceIndex() + 1);
                continue;
            }

            context.setSourceBuffer(record);
            trace(logPackage, "readTailSourceBuffer[" + record + "]");
            return;
        }

        context.setSourceBuffer(null);
    }

    private static void normalizeFinalStatus(HildrParseContext context, HildrExecutor.LogPackage logPackage, String reason) {
        if ("error".equals(context.getStatusCode())) {
            return;
        }
        if (logPackage != null && logPackage.getStatusError() != null) {
            setStatusCode(context, "error", logPackage, reason + ":statusErrorPresent");
            return;
        }
        if ("eof".equals(context.getStatusCode())) {
            return;
        }
        if ("continue".equals(context.getStatusCode()) || "break".equals(context.getStatusCode())) {
            setStatusCode(context, "eof", logPackage, reason);
        }
    }


    private static void validateNoUnconsumedSource(
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            HildrDelimiterProfile delimiterProfile
    ) {
        if (context == null || logPackage == null || logPackage.getStatusError() != null || context.hasTerminalError()) {
            return;
        }

        String sourceBuffer = safe(context.getSourceBuffer());
        if (sourceBuffer.isEmpty()) {
            return;
        }

        if (context.getSourceIndex() >= 0 && context.getSourceIndex() < context.getStdin().size()) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("type", "unconsumedSourceBuffer");
            error.put("nodeId", "");
            error.put("nodePath", xpathPath(context));
            error.put("sourceBuffer", sourceBuffer);
            error.put("segmentOrdinal", Integer.valueOf(currentSegmentOrdinal(context)));
            error.put("message", "Parse stopped before consuming source segment " + sourceTag(sourceBuffer, delimiterProfile)
                    + ". The model did not accept the remaining input structure.");
            logPackage.setStatusError(error);
            setStatusCode(context, "error", logPackage, "validateNoUnconsumedSource");
        }
    }

    private static boolean maxOccursEnabled(HildrBehaviorConfig behaviorConfig) {
        return behaviorConfig != null;
    }

    private static void recordTerminalError(
            HildrExecutor.LogPackage logPackage,
            HildrParseContext context,
            HildrModelNode node,
            String type,
            String message
    ) {
        if (logPackage == null || context == null || node == null) {
            return;
        }
        if (logPackage.getStatusError() != null) {
            context.markTerminalError("existing terminal error retained");
            return;
        }
        context.markTerminalError(type);
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", safe(type));
        error.put("nodeId", safe(node.getId()));
        error.put("nodePath", xpathPath(context));
        error.put("sourceBuffer", safe(context.getSourceBuffer()));
        error.put("segmentOrdinal", Integer.valueOf(currentSegmentOrdinal(context)));
        error.put("message", safe(message));
        logPackage.setStatusError(error);
        trace(logPackage, "TERMINAL_EVENT type=" + safe(type)
                + " nodeId=" + safe(node.getId())
                + " nodePath=" + xpathPath(context)
                + " sourceTag=" + sourceTag(context.getSourceBuffer())
                + " segmentOrdinal=" + currentSegmentOrdinal(context)
                + " message=" + safe(message));
    }

    private static String buildMaxOccursMessage(HildrModelNode node, HildrParseContext context) {
        return "Parse failure at " + safe(node.getId())
                + ": segment \"" + safe(context.getSourceBuffer())
                + "\" exceeds maxOccurs=" + node.getMaxOccurs();
    }

    private static String buildMandatoryMismatchMessage(HildrModelNode node, HildrParseContext context) {
        return "Parse failure at " + safe(node.getId())
                + ": source segment \"" + safe(context.getSourceBuffer())
                + "\" could not be matched to mandatory segment " + expectedSegmentTag(node);
    }

    private static String expectedSegmentTag(HildrModelNode node) {
        if (node == null) {
            return "";
        }
        String recognitionRe = safe(node.getRecognitionRegex());
        if (!recognitionRe.isEmpty()) {
            if (recognitionRe.startsWith("\\Q")) {
                int quotedEnd = recognitionRe.indexOf("\\E", 2);
                if (quotedEnd > 2) {
                    return recognitionRe.substring(2, quotedEnd);
                }
            }
            String cleaned = recognitionRe;
            if (cleaned.startsWith("(")) {
                int end = cleaned.indexOf(')');
                if (end > 1) {
                    cleaned = cleaned.substring(1, end);
                }
            }
            int alt = cleaned.indexOf('|');
            if (alt >= 0) {
                cleaned = cleaned.substring(0, alt);
            }
            cleaned = cleaned.replace("^", "").replace("\\", "").replace("'", "").replace("$", "");
            int cut = firstNonTagChar(cleaned);
            if (cut > 0) {
                cleaned = cleaned.substring(0, cut);
            }
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        String id = safe(node.getId());
        int i = 0;
        while (i < id.length() && !Character.isDigit(id.charAt(i))) {
            i++;
        }
        return i == 0 ? id : id.substring(0, i);
    }

    private static int firstNonTagChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return i;
            }
        }
        return value.length();
    }

    private static int currentSegmentOrdinal(HildrParseContext context) {
        if (context == null) {
            return 0;
        }
        return context.getSourceIndex() + 1;
    }

    private static String sourceTag(String sourceBuffer) {
        return sourceTag(sourceBuffer, null);
    }

    private static String sourceTag(String sourceBuffer, HildrDelimiterProfile delimiterProfile) {
        String value = safe(sourceBuffer);
        if (value.isEmpty()) {
            return "";
        }

        String tagSeparator = delimiterProfile == null ? "" : safe(delimiterProfile.getTagSeparator());
        String compositeSeparator = delimiterProfile == null ? "" : safe(delimiterProfile.getCompositeSeparator());
        String sep = !tagSeparator.isEmpty() ? tagSeparator : compositeSeparator;

        if (sep.isEmpty()) {
            return value;
        }

        int pos = value.indexOf(sep);
        return pos < 0 ? value : value.substring(0, pos);
    }

    private static void appendError(HildrParseContext context, String message, String buffer) {
        context.getStderr()
                .append(message == null ? "" : message)
                .append(buffer == null ? "" : " :: " + buffer)
                .append("\n");
    }

    private static void setStructuralBlocked(HildrModelNode node, boolean value) {
        if (node != null) {
            STRUCTURAL_BLOCKED.put(node, value);
        }
    }

    private static boolean isStructuralBlocked(HildrModelNode node) {
        if (node == null) {
            return false;
        }
        Boolean value = STRUCTURAL_BLOCKED.get(node);
        return Boolean.TRUE.equals(value);
    }

    private static void setStatusCode(
            HildrParseContext context,
            String newStatus,
            HildrExecutor.LogPackage logPackage,
            String reason
    ) {
        String oldStatus = context.getStatusCode();
        if (context.hasTerminalError() && !"error".equals(newStatus)) {
            trace(logPackage, "STATUS_RETAIN_TERMINAL requested=" + safe(newStatus)
                    + " reason=" + safe(reason)
                    + " path=" + xpathPath(context)
                    + " sourceTag=" + sourceTag(context.getSourceBuffer())
                    + " sourceBuffer=" + safe(context.getSourceBuffer()));
            return;
        }
        if ("error".equals(newStatus)) {
            context.markTerminalError(reason);
        }
        if ((oldStatus == null && newStatus == null) || (oldStatus != null && oldStatus.equals(newStatus))) {
            return;
        }
        trace(logPackage, "STATUS_CHANGE [" + safe(oldStatus) + " -> " + safe(newStatus) + "]"
                + " reason=" + safe(reason)
                + " path=" + xpathPath(context)
                + " sourceTag=" + sourceTag(context.getSourceBuffer())
                + " sourceBuffer=" + safe(context.getSourceBuffer()));
        context.setStatusCode(newStatus);
    }

    private static void traceNodeState(
            HildrExecutor.LogPackage logPackage,
            HildrParseContext context,
            HildrModelNode node,
            String event
    ) {
        HildrModelNode currentMissing = currentMissing(context);
        trace(logPackage, event
                + " path=" + xpathPath(context)
                + " nodeType=" + safe(node.getNodeType())
                + " nodeId=" + safe(node.getId())
                + " isInstantiated=" + node.isInstantiated()
                + " isConfirmed=" + node.isConfirmed()
                + " isPrinted=" + node.isPrinted()
                + " occursCount=" + node.getOccursCount()
                + " minOccurs=" + node.getMinOccurs()
                + " maxOccurs=" + node.getMaxOccurs()
                + " structuralBlocked=" + isStructuralBlocked(node)
                + " currentMissing=" + (currentMissing == null ? "" : safe(currentMissing.getId()))
                + " status=" + safe(context.getStatusCode())
                + " sourceTag=" + sourceTag(context.getSourceBuffer())
                + " sourceBuffer=" + safe(context.getSourceBuffer()));
    }

    private static void trace(HildrExecutor.LogPackage logPackage, String message) {
        if (logPackage != null) {
            logPackage.trace(message);
        }
    }

    private static String xpathPath(HildrParseContext context) {
        if (context == null || context.getNodePathQualified() == null || context.getNodePathQualified().isEmpty()) {
            return "";
        }
        return "/md/" + String.join("/", context.getNodePathQualified()).replace("\"", "'");
    }

    private static String currentPath(HildrParseContext context) {
        if (context == null || context.getNodePathQualified() == null || context.getNodePathQualified().isEmpty()) {
            return "";
        }
        return String.join("/", context.getNodePathQualified());
    }

    private static String parentPath(HildrParseContext context) {
        if (context == null || context.getNodePathQualified() == null || context.getNodePathQualified().size() < 2) {
            return "";
        }
        return String.join("/", context.getNodePathQualified().subList(0, context.getNodePathQualified().size() - 1));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
