package com.grenefolc.hildr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class HildrRecognitionService {

    private HildrRecognitionService() {
    }

    public static HildrRecognitionResult recognise(
            String inputText,
            HildrBehaviorConfig behaviorConfig,
            HildrRecognitionSpec recognitionSpec,
            HildrExecutor.LogPackage logPackage
    ) {
        String forcedPresentationClass = behaviorConfig.getPresentationClassOverride();
        if (!forcedPresentationClass.isEmpty()) {
            logPackage.trace("Presentation class forced by cliJson: " + forcedPresentationClass);
        }

        String source = inputText == null ? "" : inputText;
        Map<String, Object> spec = recognitionSpec.getRaw();

        int headLimit = 160;
        try {
            Object initObj = spec.get("-init");
            if (initObj instanceof Map) {
                Object hl = ((Map<?, ?>) initObj).get("-headLimit");
                if (hl != null) {
                    headLimit = Integer.parseInt(String.valueOf(hl));
                }
            }
        } catch (Exception ignored) {
        }

        String head = source.length() > headLimit ? source.substring(0, headLimit) : source;
        String headForMatch = head
                .replaceFirst("^\\uFEFF", "")
                .replaceFirst("^[\\r\\n\\t ]+", "");

        List<Map<String, Object>> recognitionList = safeRecognitionList(spec.get("-recognition"));
        for (Map<String, Object> rule : recognitionList) {
            String mask = stringValue(rule.get("-mask"));
            String profile = stringValue(rule.get("-profile"));
            if (mask.isEmpty() || profile.isEmpty()) {
                continue;
            }

            try {
                String regex = normaliseGroovyStyleMask(mask);
                Pattern p = Pattern.compile("^" + regex);
                if (p.matcher(headForMatch).find()) {
                    String resolvedFormat = resolveFormat(spec, profile);
                    String format = !forcedPresentationClass.isEmpty()
                            ? forcedPresentationClass
                            : resolvedFormat;

                    logPackage.trace(
                            "Recognition matched: profile=" + profile
                                    + ", format=" + format
                                    + ", mask=" + mask
                    );

                    return new HildrRecognitionResult(profile, format, mask);
                }
            } catch (Exception ex) {
                logPackage.trace(
                        "Recognition rule skipped: " + mask + " (" + ex.getMessage() + ")"
                );
            }
        }

        if (!forcedPresentationClass.isEmpty()) {
            return new HildrRecognitionResult("", forcedPresentationClass, "");
        }

        return new HildrRecognitionResult("", "", "");
    }

    private static String resolveFormat(Map<String, Object> spec, String profile) {
        try {
            Object profilesObj = spec.get("-profile");
            if (!(profilesObj instanceof Map)) {
                return "";
            }

            Object pObj = ((Map<?, ?>) profilesObj).get(profile);
            if (!(pObj instanceof Map)) {
                return "";
            }

            return stringValue(((Map<?, ?>) pObj).get("-format")).toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<Map<String, Object>> safeRecognitionList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }

        List<?> rawList = (List<?>) value;
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

        for (Object item : rawList) {
            if (item instanceof Map) {
                Map<String, Object> converted = new LinkedHashMap<String, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(converted);
            }
        }

        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normaliseGroovyStyleMask(String mask) {
        if (mask == null) {
            return "";
        }

        return mask
                .replace("[[:punct:]]", "[^A-Za-z0-9\\s]")
                .replace("[[:alnum:]]", "[A-Za-z0-9]")
                .replace("[[:alpha:]]", "[A-Za-z]")
                .replace("[[:space:]]", "\\s")
                .replace("[[:digit:]]", "\\d");
    }
}
