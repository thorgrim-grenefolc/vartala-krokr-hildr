package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class HildrFfvPresentationHandler implements HildrPresentationHandler {

    @Override
    public void prepareInput(
            String dataStream,
            HildrDelimiterProfile delimiterProfile,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage
    ) {
        context.getStdin().clear();

        String normalized = dataStream.replaceAll("(\\r\\n|\\n\\r|\\n|\\r)", "#jeraSeidr:eol;");
        String[] parts = normalized.split(Pattern.quote("#jeraSeidr:eol;"), -1);
        for (String part : parts) {
            context.getStdin().add(part);
        }

        logPackage.trace("FFV handler prepared stdin records=" + context.getStdin().size());
    }

    @Override
    public void parseSegment(
            HildrModelNode segmentNode,
            HildrParseContext context,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        String buffer = context.getSourceBuffer() == null ? "" : context.getSourceBuffer();
        Cursor cursor = new Cursor();
        StringBuilder segmentXml = new StringBuilder();

        segmentXml.append("<").append(segmentNode.getEntityName()).append(">");

        for (HildrModelNode child : segmentNode.getChildren()) {
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }
            if ("cd".equals(child.getNodeType())) {
                parseCompositeSeries(child, buffer, cursor, segmentXml, context, delimiterProfile, logPackage);
            } else if ("ed".equals(child.getNodeType())) {
                parseElementSeries(child, buffer, cursor, segmentXml, context, delimiterProfile, logPackage);
            }
        }

        if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
            return;
        }

        if (hasRemainingContent(buffer, cursor.index)) {
            recordStructuralError(
                    context,
                    logPackage,
                    segmentNode,
                    "excessFixedLengthContent",
                    "Source segment contains more fixed-length content than model node "
                            + safe(segmentNode.getId()) + " allows."
            );
            return;
        }

        segmentXml.append("</").append(segmentNode.getEntityName()).append(">");
        context.getStream().append(segmentXml);
    }

    private static void parseCompositeSeries(
            HildrModelNode composite,
            String buffer,
            Cursor cursor,
            StringBuilder segmentXml,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        long occurrences = 0L;
        int occurrenceWidth = compositeWidth(composite);

        while (occurrences < composite.getMaxOccurs() && hasRemainingContent(buffer, cursor.index)) {
            if (occurrenceWidth <= 0) {
                break;
            }
            StringBuilder body = new StringBuilder();
            parseCompositeOccurrence(composite, buffer, cursor, body, context, delimiterProfile, logPackage);
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }
            occurrences++;
            if (body.length() > 0) {
                segmentXml.append("<").append(composite.getEntityName()).append(">")
                        .append(body)
                        .append("</").append(composite.getEntityName()).append(">");
            }
        }

        if (occurrences < composite.getMinOccurs()) {
            recordStructuralError(
                    context,
                    logPackage,
                    composite,
                    "mandatoryCompositeMissing",
                    "Composite " + safe(composite.getId()) + " occurrence count " + occurrences
                            + " is below minOccurs=" + composite.getMinOccurs() + "."
            );
        }
    }

    private static void parseCompositeOccurrence(
            HildrModelNode composite,
            String buffer,
            Cursor cursor,
            StringBuilder body,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        for (HildrModelNode element : composite.getChildren()) {
            parseElementSeries(element, buffer, cursor, body, context, delimiterProfile, logPackage);
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }
        }
    }

    private static void parseElementSeries(
            HildrModelNode element,
            String buffer,
            Cursor cursor,
            StringBuilder xml,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        long occurrences = 0L;
        int len = (int) element.getMaxLength();

        while (occurrences < element.getMaxOccurs() && len > 0 && cursor.index < buffer.length()) {
            String rawSlice = slice(buffer, cursor.index, len);
            String value = rtrim(rawSlice);

            if (value.isEmpty() && occurrences >= element.getMinOccurs()) {
                cursor.index += len;
                occurrences++;
                break;
            }

            cursor.index += len;
            occurrences++;

            if (!HildrFieldLengthValidator.validate(
                    element,
                    rawSlice,
                    context,
                    logPackage,
                    delimiterProfile.getPresentationClass()
            )) {
                return;
            }

            if (!value.isEmpty()) {
                xml.append("<").append(element.getEntityName()).append(">")
                        .append(xmlEscape(value))
                        .append("</").append(element.getEntityName()).append(">");
            }
        }

        if (occurrences < element.getMinOccurs()) {
            recordStructuralError(
                    context,
                    logPackage,
                    element,
                    "mandatoryElementMissing",
                    "Element " + safe(element.getId()) + " occurrence count " + occurrences
                            + " is below minOccurs=" + element.getMinOccurs() + "."
            );
        }
    }

    private static int compositeWidth(HildrModelNode composite) {
        int total = 0;
        if (composite == null) {
            return total;
        }
        for (HildrModelNode element : composite.getChildren()) {
            total += (int) (element.getMaxLength() * element.getMaxOccurs());
        }
        return total;
    }

    private static boolean hasRemainingContent(String source, int fromIndex) {
        if (source == null || fromIndex >= source.length()) {
            return false;
        }
        for (int i = Math.max(0, fromIndex); i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void recordStructuralError(
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            HildrModelNode node,
            String type,
            String message
    ) {
        if (context != null) {
            context.setStatusCode("error");
            context.getStderr()
                    .append(message == null ? "" : message)
                    .append(" :: ")
                    .append(context.getSourceBuffer() == null ? "" : context.getSourceBuffer())
                    .append("\n");
        }

        if (logPackage != null) {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("type", safe(type));
            error.put("nodeId", node == null ? "" : safe(node.getId()));
            error.put("nodePath", context == null ? "" : context.currentModelNodePathQualified());
            error.put("sourceBuffer", context == null ? "" : safe(context.getSourceBuffer()));
            error.put("segmentOrdinal", Integer.valueOf(context == null ? 0 : context.getSourceIndex() + 1));
            error.put("message", safe(message));
            logPackage.setStatusError(error);
            logPackage.trace("FFV_STRUCTURE_ERROR type=" + safe(type)
                    + " nodeId=" + (node == null ? "" : safe(node.getId()))
                    + " message=" + safe(message));
        }
    }

    private static String rtrim(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String slice(String source, int start, int len) {
        if (source == null || start >= source.length() || len <= 0) {
            return "";
        }
        int end = Math.min(source.length(), start + len);
        return source.substring(start, end);
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Cursor {
        int index = 0;
    }
}
