package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class HildrCsvPresentationHandler implements HildrPresentationHandler {

    @Override
    public void prepareInput(
            String dataStream,
            HildrDelimiterProfile delimiterProfile,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage
    ) {
        context.getStdin().clear();

        String s = dataStream
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\u2007', ' ');

        String segTerm = delimiterProfile.getSegmentTerminator();
        if (!"\n".equals(segTerm) && !"\r".equals(segTerm)) {
            s = s.replace("\r", "").replace("\n", "");
        }

        String release = delimiterProfile.getReleaseIndicator();
        String tag = delimiterProfile.getTagSeparator();
        String elem = delimiterProfile.getElementSeparator();
        String comp = delimiterProfile.getCompositeSeparator();

        StringBuilder out = new StringBuilder(s.length() + 128);
        int i = 0;

        while (i < s.length()) {
            String ch = s.substring(i, i + 1);

            if (!release.isEmpty() && ch.equals(release) && i + 1 < s.length()) {
                String nx = s.substring(i + 1, i + 2);

                if (nx.equals(tag)) {
                    out.append("#esc:tagSeparator;");
                    i += 2; continue;
                }
                if (nx.equals(elem)) {
                    out.append("#esc:elementSeparator;");
                    i += 2; continue;
                }
                if (nx.equals(comp)) {
                    out.append("#esc:compositeSeparator;");
                    i += 2; continue;
                }
                if (nx.equals(release)) {
                    out.append("#esc:releaseIndicator;");
                    i += 2; continue;
                }
                if (nx.equals(segTerm)) {
                    out.append("#esc:segmentTerminator;");
                    i += 2; continue;
                }
            }

            if (!segTerm.isEmpty() && ch.equals(segTerm)) {
                out.append(ch).append("#jeraSeidr:eol;");
                i++;
                while (i < s.length()) {
                    String ws = s.substring(i, i + 1);
                    if (" ".equals(ws) || "\t".equals(ws)) {
                        i++;
                    } else break;
                }
                continue;
            }

            out.append(ch);
            i++;
        }

        String[] parts = out.toString().split(Pattern.quote("#jeraSeidr:eol;"), -1);
        for (String part : parts) {
            context.getStdin().add(part);
        }

        logPackage.trace("CSV handler prepared stdin records=" + context.getStdin().size());
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
        String raw = buffer.replaceFirst("\\s+$", "");
        String segTerm = delimiterProfile.getSegmentTerminator();
        if (!segTerm.isEmpty() && raw.endsWith(segTerm)) {
            raw = raw.substring(0, raw.length() - segTerm.length());
        }

        String compSep = delimiterProfile.getCompositeSeparator();
        String[] components = raw.split(Pattern.quote(compSep), -1);
        Cursor componentCursor = new Cursor();
        StringBuilder segmentXml = new StringBuilder();
        segmentXml.append("<").append(segmentNode.getEntityName()).append(">");

        for (HildrModelNode child : segmentNode.getChildren()) {
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }

            if ("cd".equals(child.getNodeType())) {
                parseCompositeSeries(child, components, componentCursor, segmentXml, context, delimiterProfile, logPackage);
            } else if ("ed".equals(child.getNodeType())) {
                parseSegmentElementSeries(child, components, componentCursor, segmentXml, context, delimiterProfile, logPackage);
            }
        }

        if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
            return;
        }

        if (hasRemainingContent(components, componentCursor.index)) {
            recordStructuralError(
                    context,
                    logPackage,
                    segmentNode,
                    "excessCompositeContent",
                    "Source segment contains more composite/element content than model node "
                            + safe(segmentNode.getId()) + " allows."
            );
            return;
        }

        segmentXml.append("</").append(segmentNode.getEntityName()).append(">");
        context.getStream().append(segmentXml);
    }

    private static void parseCompositeSeries(
            HildrModelNode composite,
            String[] components,
            Cursor componentCursor,
            StringBuilder segmentXml,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        long occurrences = 0L;
        while (occurrences < composite.getMaxOccurs() && componentCursor.index < components.length) {
            String part = components[componentCursor.index];
            if (isEmptySource(part) && occurrences >= composite.getMinOccurs()) {
                componentCursor.index++;
                break;
            }

            StringBuilder compositeXml = parseCompositeOccurrence(
                    composite,
                    part,
                    context,
                    delimiterProfile,
                    logPackage
            );
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return;
            }

            componentCursor.index++;
            occurrences++;

            if (compositeXml.length() > 0) {
                segmentXml.append("<").append(composite.getEntityName()).append(">")
                        .append(compositeXml)
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
            return;
        }

    }

    private static StringBuilder parseCompositeOccurrence(
            HildrModelNode composite,
            String part,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        String elemSep = delimiterProfile.getElementSeparator();
        String[] elements = part.split(Pattern.quote(elemSep), -1);
        Cursor elementCursor = new Cursor();
        StringBuilder compositeBody = new StringBuilder();

        for (HildrModelNode element : composite.getChildren()) {
            parseCompositeElementSeries(element, elements, elementCursor, compositeBody, context, delimiterProfile, logPackage);
            if (context.hasTerminalError() || "error".equals(context.getStatusCode())) {
                return compositeBody;
            }
        }

        if (hasRemainingContent(elements, elementCursor.index)) {
            recordStructuralError(
                    context,
                    logPackage,
                    composite,
                    "excessElementContent",
                    "Composite " + safe(composite.getId())
                            + " contains more element content than its model allows."
            );
        }

        return compositeBody;
    }

    private static void parseCompositeElementSeries(
            HildrModelNode element,
            String[] elements,
            Cursor elementCursor,
            StringBuilder compositeBody,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        long occurrences = 0L;
        while (occurrences < element.getMaxOccurs() && elementCursor.index < elements.length) {
            String value = restoreEscapes(elements[elementCursor.index], delimiterProfile);
            if (isEmptySource(value) && occurrences >= element.getMinOccurs()) {
                elementCursor.index++;
                occurrences++;
                break;
            }

            if (!HildrFieldLengthValidator.validate(
                    element,
                    value,
                    context,
                    logPackage,
                    delimiterProfile.getPresentationClass()
            )) {
                return;
            }

            if (!value.isEmpty()) {
                compositeBody.append("<").append(element.getEntityName()).append(">")
                        .append(xmlEscape(value))
                        .append("</").append(element.getEntityName()).append(">");
            }

            elementCursor.index++;
            occurrences++;
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

    private static void parseSegmentElementSeries(
            HildrModelNode element,
            String[] components,
            Cursor componentCursor,
            StringBuilder segmentXml,
            HildrParseContext context,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        long occurrences = 0L;
        while (occurrences < element.getMaxOccurs() && componentCursor.index < components.length) {
            String value = restoreEscapes(components[componentCursor.index], delimiterProfile);
            if (isEmptySource(value) && occurrences >= element.getMinOccurs()) {
                componentCursor.index++;
                occurrences++;
                break;
            }

            if (!HildrFieldLengthValidator.validate(
                    element,
                    value,
                    context,
                    logPackage,
                    delimiterProfile.getPresentationClass()
            )) {
                return;
            }

            if (!value.isEmpty()) {
                segmentXml.append("<").append(element.getEntityName()).append(">")
                        .append(xmlEscape(value))
                        .append("</").append(element.getEntityName()).append(">");
            }

            componentCursor.index++;
            occurrences++;
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


    private static boolean hasRemainingContent(String[] values, int fromIndex) {
        if (values == null) {
            return false;
        }
        for (int i = Math.max(0, fromIndex); i < values.length; i++) {
            if (!isEmptySource(values[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmptySource(String value) {
        return value == null || value.length() == 0;
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
            logPackage.trace("CSV_STRUCTURE_ERROR type=" + safe(type)
                    + " nodeId=" + (node == null ? "" : safe(node.getId()))
                    + " message=" + safe(message));
        }
    }

    private static String restoreEscapes(String value, HildrDelimiterProfile d) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;

        return value
                .replace("#esc:tagSeparator;", d.getTagSeparator())
                .replace("#esc:elementSeparator;", d.getElementSeparator())
                .replace("#esc:compositeSeparator;", d.getCompositeSeparator())
                .replace("#esc:releaseIndicator;", d.getReleaseIndicator())
                .replace("#esc:segmentTerminator;", d.getSegmentTerminator());
    }

    private static String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class Cursor {
        int index = 0;
    }
}
