package com.grenefolc.hildr;

import java.util.Map;

public final class HildrDelimiterResolver {

    private HildrDelimiterResolver() {
    }

    public static HildrDelimiterProfile resolve(
            String dataStream,
            HildrRecognitionResult recognitionResult,
            HildrRecognitionSpec recognitionSpec,
            HildrExecutor.LogPackage logPackage
    ) {
        HildrDelimiterProfile profile = new HildrDelimiterProfile();

        String presentationClass = safe(recognitionResult.getPresentationClass()).toLowerCase();
        profile.setPresentationClass(presentationClass);

        if ("ffv".equals(presentationClass)) {
            profile.setDelimiterSource("default");
            profile.setDelimiterString("");
            logPackage.trace("Delimiter profile resolved for FFV.");
            return profile;
        }

        Map<String, Object> specRaw = recognitionSpec.getRaw();
        Map<String, Object> profileMap =
                getProfileMap(specRaw, recognitionResult.getRecognitionProfile());

        if (profileMap == null) {
            logPackage.trace(
                    "No profile map found for recognition profile "
                            + recognitionResult.getRecognitionProfile()
            );
            return profile;
        }

        Map<String, Object> envelope = mapValue(profileMap.get("-envelope"));
        if (envelope != null) {
            profile.setEnvelopeHeader(stringValue(envelope.get("-head")));
            profile.setEnvelopeFooter(stringValue(envelope.get("-tail")));
        }

        Map<String, Object> separators = mapValue(profileMap.get("-separators"));
        if (separators != null) {
            Map<String, Object> defaults = mapValue(separators.get("-default"));
            if (defaults != null) {
                profile.setTagSeparator(stringValue(defaults.get("&tag;")));
                profile.setElementSeparator(stringValue(defaults.get("&element;")));
                profile.setCompositeSeparator(stringValue(defaults.get("&composite;")));
                profile.setDecimalNotation(stringValue(defaults.get("&decimal;")));
                profile.setReleaseIndicator(stringValue(defaults.get("&release;")));
                profile.setRepetitionSeparator(stringValue(defaults.get("&repetition;")));
                profile.setSegmentTerminator(stringValue(defaults.get("&segment;")));
            }

            if (profile.getTagSeparator().isEmpty()) {
                profile.setTagSeparator(profile.getCompositeSeparator());
            }

            String dynamicSpec = stringValue(separators.get("-dynamic"));
            if (!dynamicSpec.isEmpty()) {
                boolean applied = applyDynamicSeparators(
                        dataStream,
                        dynamicSpec,
                        profile,
                        recognitionSpec,
                        logPackage
                );
                profile.setDelimiterSource(applied ? "dynamic" : "default");
                profile.setDelimiterString(dynamicSpec);
            } else {
                profile.setDelimiterSource("default");
            }
        }

        if (profile.getTagSeparator().isEmpty()) {
            profile.setTagSeparator(profile.getCompositeSeparator());
        }

        logPackage.trace(
                "Delimiter profile resolved: profile="
                        + recognitionResult.getRecognitionProfile()
                        + ", format="
                        + recognitionResult.getPresentationClass()
                        + ", source="
                        + profile.getDelimiterSource()
        );

        return profile;
    }

    private static boolean applyDynamicSeparators(
            String dataStream,
            String dynamicSpec,
            HildrDelimiterProfile profile,
            HildrRecognitionSpec recognitionSpec,
            HildrExecutor.LogPackage logPackage
    ) {
        int headLimit = readHeadLimit(recognitionSpec.getRaw());
        String source = dataStream == null ? "" : dataStream;
        String head = source.substring(0, Math.min(source.length(), headLimit));

        DynamicSpecParts parts = parseDynamicSpec(dynamicSpec);
        if (parts.anchor.isEmpty() || parts.mask.isEmpty()) {
            logPackage.trace("Dynamic delimiter spec ignored because anchor or mask is empty: " + dynamicSpec);
            return false;
        }

        DynamicMaskResult result = resolveDynamicSeparatorsByMaskWalk(head, parts.anchor, parts.mask);
        if (!result.applied) {
            logPackage.trace("Dynamic delimiter spec did not match data head: " + dynamicSpec);
            return false;
        }

        for (Map.Entry<String, DynamicCapture> entry : result.captures.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().value;

            if ("tag".equals(key)) {
                profile.setTagSeparator(value);
            } else if ("element".equals(key)) {
                profile.setElementSeparator(value);
            } else if ("composite".equals(key)) {
                profile.setCompositeSeparator(value);
            } else if ("decimal".equals(key)) {
                profile.setDecimalNotation(value);
            } else if ("release".equals(key)) {
                profile.setReleaseIndicator(value);
            } else if ("repetition".equals(key)) {
                profile.setRepetitionSeparator(value);
            } else if ("segment".equals(key)) {
                profile.setSegmentTerminator(value);
            }
        }

        if (profile.getTagSeparator().isEmpty()) {
            profile.setTagSeparator(profile.getCompositeSeparator());
        }

        logPackage.trace("Dynamic delimiters applied from profile mask: " + dynamicSpec);
        return true;
    }

    private static int readHeadLimit(Map<String, Object> specRaw) {
        try {
            Object initObj = specRaw.get("-init");
            if (initObj instanceof Map) {
                Object hl = ((Map<?, ?>) initObj).get("-headLimit");
                if (hl != null) {
                    return Integer.parseInt(String.valueOf(hl));
                }
            }
        } catch (Exception ignored) {
        }
        return 160;
    }

    private static DynamicSpecParts parseDynamicSpec(String dynamicSpec) {
        DynamicSpecParts parts = new DynamicSpecParts();
        if (dynamicSpec == null || dynamicSpec.trim().isEmpty()) {
            return parts;
        }

        String spec = dynamicSpec.trim();
        int firstSlash = spec.indexOf('/');
        int secondSlash = firstSlash < 0 ? -1 : spec.indexOf('/', firstSlash + 1);

        if (firstSlash < 0 || secondSlash < 0) {
            parts.mask = spec;
            return parts;
        }

        parts.filter = spec.substring(0, firstSlash);
        parts.anchor = spec.substring(firstSlash + 1, secondSlash);
        parts.mask = spec.substring(secondSlash + 1);
        return parts;
    }

    private static DynamicMaskResult resolveDynamicSeparatorsByMaskWalk(
            String head,
            String anchorText,
            String mask
    ) {
        DynamicMaskResult result = new DynamicMaskResult();

        if (head == null || anchorText == null || anchorText.isEmpty() || mask == null || mask.isEmpty()) {
            return result;
        }

        int anchor = head.indexOf(anchorText);
        if (anchor < 0) {
            return result;
        }

        result.anchorIndex = anchor;

        int headPos = anchor;
        int maskPos = 0;

        while (maskPos < mask.length() && headPos < head.length()) {
            if (mask.charAt(maskPos) == '&') {
                int tokenEnd = mask.indexOf(';', maskPos);
                if (tokenEnd > maskPos) {
                    String token = mask.substring(maskPos, tokenEnd + 1);
                    DynamicCapture capture = new DynamicCapture();
                    capture.token = token;
                    capture.headIndex = headPos;
                    capture.value = String.valueOf(head.charAt(headPos));

                    result.captures.put(tokenKey(token), capture);

                    headPos++;
                    maskPos = tokenEnd + 1;
                    result.applied = true;
                    continue;
                }
            }

            headPos++;
            maskPos++;
        }

        result.endIndex = headPos;
        return result;
    }

    private static String tokenKey(String token) {
        if ("&tag;".equals(token)) return "tag";
        if ("&element;".equals(token)) return "element";
        if ("&composite;".equals(token)) return "composite";
        if ("&decimal;".equals(token)) return "decimal";
        if ("&release;".equals(token)) return "release";
        if ("&repetition;".equals(token)) return "repetition";
        if ("&segment;".equals(token)) return "segment";
        return token.replace("&", "").replace(";", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getProfileMap(
            Map<String, Object> specRaw,
            String recognitionProfile
    ) {
        Map<String, Object> profiles = mapValue(specRaw.get("-profile"));
        return profiles == null ? null : mapValue(profiles.get(recognitionProfile));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return (Map<String, Object>) value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class DynamicSpecParts {
        String filter = "";
        String anchor = "";
        String mask = "";
    }

    private static final class DynamicCapture {
        String token = "";
        int headIndex = -1;
        String value = "";
    }

    private static final class DynamicMaskResult {
        boolean applied = false;
        int anchorIndex = -1;
        int endIndex = -1;
        final Map<String, DynamicCapture> captures = new java.util.LinkedHashMap<String, DynamicCapture>();
    }
}
