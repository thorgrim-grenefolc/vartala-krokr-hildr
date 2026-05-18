package com.grenefolc.hildr;

import java.util.List;
import java.util.regex.Pattern;

public final class HildrModelInitialiser {

    private HildrModelInitialiser() {
    }

    public static void initialise(
            HildrModel model,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        if (model == null) {
            return;
        }

        for (HildrModelNode node : model.getTopLevelNodes()) {
            initialiseNode(node, behaviorConfig, delimiterProfile, logPackage);
        }
    }

    private static void initialiseNode(
            HildrModelNode node,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile,
            HildrExecutor.LogPackage logPackage
    ) {
        node.setEntityName(buildEntityName(node, behaviorConfig));
        normaliseOccurs(node);
        normaliseLength(node);

        if ("sd".equals(node.getNodeType())) {
            String recognitionRe = buildRecognitionRegex(node, behaviorConfig, delimiterProfile);
            node.setRecognitionRegex(recognitionRe);

            if (behaviorConfig.showRecognitionRe()) {
                logPackage.trace("recognitionRe " + node.getEntityName() + " = " + recognitionRe);
            }
        }

        List<HildrModelNode> children = node.getChildren();
        for (HildrModelNode child : children) {
            initialiseNode(child, behaviorConfig, delimiterProfile, logPackage);
        }
    }

    private static String buildEntityName(HildrModelNode node, HildrBehaviorConfig behaviorConfig) {
        String synonymStyle = behaviorConfig.getSynonymStyle();
        String id = safe(node.getId());
        String ds = safe(node.getDescription());

        String entity = node.getNodeType() + "_";
        if ("id".equals(synonymStyle) || ds.isEmpty()) {
            return entity + id;
        }

        if ("syns".equals(synonymStyle)) {
            return entity + toSynonymShort(ds);
        }

        if ("synl".equals(synonymStyle)) {
            return entity + toSynonymLong(ds);
        }

        if ("idsyns".equals(synonymStyle)) {
            return entity + id + "_" + toSynonymShort(ds);
        }

        if ("idsynl".equals(synonymStyle)) {
            return entity + id + "_" + toSynonymLong(ds);
        }

        if ("cdedidsyns".equals(synonymStyle)) {
            if ("cd".equals(node.getNodeType()) || "ed".equals(node.getNodeType())) {
                return entity + id + "_" + toSynonymShort(ds);
            }
            return entity + id;
        }

        if ("cdedidsynl".equals(synonymStyle)) {
            if ("cd".equals(node.getNodeType()) || "ed".equals(node.getNodeType())) {
                return entity + id + "_" + toSynonymLong(ds);
            }
            return entity + id;
        }

        return entity + id;
    }

    private static void normaliseOccurs(HildrModelNode node) {
        if ("md".equals(node.getNodeType())) {
            node.setMinOccurs(0L);
            node.setMaxOccurs(1L);
            return;
        }

        String occurs = safe(node.getOccurs());
        if (occurs.isEmpty()) {
            node.setMinOccurs(1L);
            node.setMaxOccurs(1L);
            return;
        }

        if (occurs.contains(".")) {
            String[] parts = occurs.split("\\.", 2);
            node.setMinOccurs(parseLong(parts[0], 1L));
            node.setMaxOccurs(parseOccursMax(parts[1]));
        } else {
            long value = parseOccursMax(occurs);
            node.setMinOccurs(value);
            node.setMaxOccurs(value);
        }
    }

    private static void normaliseLength(HildrModelNode node) {
        if (!"ed".equals(node.getNodeType())) {
            return;
        }

        String length = safe(node.getLength());
        if (length.isEmpty()) {
            node.setMinLength(1L);
            node.setMaxLength(1L);
            return;
        }

        if (length.contains(".")) {
            String[] parts = length.split("\\.", 2);
            node.setMinLength(parseLong(parts[0], 1L));
            node.setMaxLength(parseLong(parts[1], 1L));
        } else {
            long value = parseLong(length, 1L);
            node.setMinLength(value);
            node.setMaxLength(value);
        }
    }

    private static String buildRecognitionRegex(
            HildrModelNode segmentNode,
            HildrBehaviorConfig behaviorConfig,
            HildrDelimiterProfile delimiterProfile
    ) {
        String presentationClass = delimiterProfile.getPresentationClass();

        if ("ffv".equals(presentationClass)) {
            StringBuilder sb = new StringBuilder();
            appendFfvRecognition(segmentNode, sb);
            String re = sb.toString().replaceAll("(\\.\\{\\d+\\})+$", "");
            return re;
        }

        if ("csv".equals(presentationClass)) {
            StringBuilder sb = new StringBuilder();
            appendCsvRecognition(segmentNode, sb, true);
            sb.append("#st;");
            String re = sb.toString()
                    .replaceFirst("#cs;", "#ts;")
                    .replaceFirst("(#[a-z][a-z];)(#[a-z][a-z];)*$", "$1");
            while (re.matches(".*(#[tce]s;)(#[ce]s;).*")) {
                re = re.replaceAll("(#[tce]s;)(#[ce]s;)", "$1.*?$2");
            }
            re = re.replace("#ts;", "[" + Pattern.quote(delimiterProfile.getTagSeparator()) + "]");
            re = re.replace("#cs;", "[" + Pattern.quote(delimiterProfile.getCompositeSeparator()) + "]");
            re = re.replace("#es;", "[" + Pattern.quote(delimiterProfile.getElementSeparator()) + "]");
            re = re.replace("#st;", "[" + Pattern.quote(delimiterProfile.getSegmentTerminator()) + "]");
            return re;
        }

        return "";
    }

    private static void appendFfvRecognition(HildrModelNode node, StringBuilder sb) {
        if ("sd".equals(node.getNodeType())) {
            for (HildrModelNode child : node.getChildren()) {
                appendFfvRecognition(child, sb);
            }
            return;
        }

        if ("ed".equals(node.getNodeType())) {
            long maxLen = node.getMaxLength();
            String rc = safe(node.getRecognitionCode());
            if (!rc.isEmpty()) {
                long spaces = Math.max(0L, maxLen - rc.length());
                sb.append(Pattern.quote(rc)).append("[ ]{").append(spaces).append("}");
            } else {
                sb.append(".{").append(maxLen).append("}");
            }
            return;
        }

        for (HildrModelNode child : node.getChildren()) {
            appendFfvRecognition(child, sb);
        }
    }

    private static void appendCsvRecognition(HildrModelNode node, StringBuilder sb, boolean firstInSegment) {
        if ("sd".equals(node.getNodeType())) {
            boolean first = true;
            for (HildrModelNode child : node.getChildren()) {
                appendCsvRecognition(child, sb, first);
                first = false;
            }
            return;
        }

        if ("cd".equals(node.getNodeType())) {
            if (!firstInSegment) {
                sb.append("#cs;");
            }
            boolean first = true;
            for (HildrModelNode child : node.getChildren()) {
                appendCsvRecognition(child, sb, first);
                first = false;
            }
            return;
        }

        if ("ed".equals(node.getNodeType())) {
            if (!firstInSegment) {
                sb.append("#cs;");
            }
            String rc = safe(node.getRecognitionCode());
            if (!rc.isEmpty()) {
                sb.append(Pattern.quote(rc));
            }
        }
    }

    private static long parseOccursMax(String value) {
        String v = safe(value);
        if ("*".equals(v) || "-1".equals(v)) {
            return 99999L;
        }
        return parseLong(v, 1L);
    }

    private static long parseLong(String value, long defaultValue) {
        try {
            return Long.parseLong(safe(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String toSynonymShort(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Unknown";
        }
        String s = input.toLowerCase()
                .replaceAll("_(\\d+)$", "$1")
                .replaceAll("[^a-zA-Z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            String w = word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
            w = w.replaceAll("(.)\\1+", "$1");
            String lead = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[aeiouAEIOU]+").matcher(w);
            if (m.find()) {
                lead = m.group(0);
            }
            w = w.replaceAll("^[aeiouAEIOU]+", "").replaceAll("[aeiouAEIOU]", "");
            out.append(lead).append(w);
        }
        if (out.length() == 0) {
            return "Unknown";
        }
        return out.substring(0, 1).toUpperCase() + out.substring(1);
    }

    private static String toSynonymLong(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Unknown";
        }
        String s = input.toLowerCase()
                .replaceAll("_(\\d+)$", "$1")
                .replaceAll("[^a-zA-Z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                out.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
            }
        }
        if (out.length() == 0) {
            return "Unknown";
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
