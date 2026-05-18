package com.grenefolc.hildr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HildrConfigResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HildrConfigResolver() {
    }

    public static HildrResolvedConfig resolve(
            String cliJson,
            String recognitionSpecJson,
            String modelXml,
            HildrExecutor.LogPackage logPackage
    ) throws Exception {

        Map<String, Object> validation = validationBase();
        Map<String, Object> init = ensureLogMap(logPackage, "init");

        Map<String, Object> defaultBehavior = parseRequiredObject(
                HildrDefaults.defaultBehaviorConfigJson(),
                "default cliJson",
                validation
        );

        Map<String, Object> defaultRecognition = parseRequiredObject(
                HildrDefaults.defaultRecognitionSpecJson(),
                "default recogJson",
                validation
        );

        Map<String, Object> effectiveBehavior = deepCopy(defaultBehavior);
        if (hasText(cliJson) && !"{}".equals(cliJson.trim())) {
            Map<String, Object> cliOverride = parseRequiredObject(cliJson, "cliJson", validation);
            validateCliJson(cliOverride, validation);
            effectiveBehavior = deepMerge(effectiveBehavior, cliOverride);
        }

        Map<String, Object> effectiveRecognition = deepCopy(defaultRecognition);
        if (hasText(recognitionSpecJson) && !"{}".equals(recognitionSpecJson.trim())) {
            effectiveRecognition = mergeRecognitionSpec(
                    effectiveRecognition,
                    parseRequiredObject(recognitionSpecJson, "recogJson", validation)
            );
        }

        validateRecognitionSpec(effectiveRecognition, validation);
        validateModelXmlPresence(modelXml, validation);

        boolean valid = getErrors(validation).isEmpty();
        validation.put("isValid", Boolean.valueOf(valid));
        init.put("validation", validation);
        init.put("cliJsonResolved", effectiveBehavior);
        init.put("recogJsonResolved", effectiveRecognition);
        init.put("modelXmlPresent", Boolean.valueOf(modelXml != null && !modelXml.trim().isEmpty()));
        init.put("modelXmlLength", Integer.valueOf(modelXml == null ? 0 : modelXml.length()));

        if (!valid) {
            Map<String, Object> first = getErrors(validation).get(0);
            String field = stringValue(first.get("field"));
            String message = stringValue(first.get("message"));
            throw new HildrConfigValidationException(
                    message,
                    field,
                    "Review cliJson, recogJson, and ddp_hildr_modelXml. Configuration validity failures are handled before parsing begins.",
                    validation
            );
        }

        return new HildrResolvedConfig(
                new HildrBehaviorConfig(effectiveBehavior),
                new HildrRecognitionSpec(effectiveRecognition),
                modelXml
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureLogMap(HildrExecutor.LogPackage logPackage, String key) {
        Object existing = logPackage.toMap().get(key);
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new LinkedHashMap<String, Object>();
        logPackage.put(key, created);
        return created;
    }

    private static Map<String, Object> parseRequiredObject(
            String json,
            String label,
            Map<String, Object> validation
    ) throws HildrConfigValidationException {
        if (!hasText(json)) {
            addError(validation, "configError", label, label + " is missing or blank.");
            throw new HildrConfigValidationException(
                    label + " is missing or blank.",
                    label,
                    "Provide a JSON object. Optional cliJson and recogJson may be omitted by passing {}.",
                    validation
            );
        }

        try {
            Object value = OBJECT_MAPPER.readValue(json, Object.class);
            if (!(value instanceof Map)) {
                addError(validation, "configError", label, label + " root JSON must be an object.");
                throw new HildrConfigValidationException(
                        label + " root JSON must be an object.",
                        label,
                        "Use a JSON object, not an array, string, number, boolean, or null.",
                        validation
                );
            }
            return OBJECT_MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (HildrConfigValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            addError(validation, "configError", label, "Failed to parse " + label + ": " + ex.getMessage());
            throw new HildrConfigValidationException(
                    "Failed to parse " + label + ": " + ex.getMessage(),
                    label,
                    "Check JSON syntax, quotes, commas, braces, and escaping.",
                    validation
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateCliJson(Map<String, Object> cliJson, Map<String, Object> validation) {
        if (cliJson == null || cliJson.isEmpty()) {
            return;
        }
        Object cli = cliJson.get("cli");
        if (cli != null && !(cli instanceof Map)) {
            addError(validation, "configError", "cliJson.cli", "cliJson.cli must be an object when supplied.");
            return;
        }
        Map<String, Object> cliMap = cli instanceof Map ? (Map<String, Object>) cli : cliJson;
        validateEnum(cliMap, "presentationClass", new String[] {"", "csv", "ffv"}, validation, "cliJson.cli.presentationClass");
        validateEnum(cliMap, "synonymStyle", new String[] {"id", "syns", "synl", "idsyns", "idsynl", "cdedidsyns", "cdedidsynl"}, validation, "cliJson.cli.synonymStyle");
        validateEnum(cliMap, "maxOccurs", new String[] {"none", "strict"}, validation, "cliJson.cli.maxOccurs");
    }

    private static void validateEnum(
            Map<String, Object> map,
            String key,
            String[] allowed,
            Map<String, Object> validation,
            String field
    ) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return;
        }
        String value = String.valueOf(map.get(key)).trim().toLowerCase();
        for (String item : allowed) {
            if (item.equals(value)) {
                return;
            }
        }
        addError(validation, "configError", field, "Invalid enum value '" + value + "' for " + field + ".");
    }

    @SuppressWarnings("unchecked")
    private static void validateRecognitionSpec(Map<String, Object> spec, Map<String, Object> validation) {
        Object recognition = spec.get("-recognition");
        if (!(recognition instanceof List) || ((List<Object>) recognition).isEmpty()) {
            addError(validation, "configError", "recogJson.-recognition", "recogJson requires a non-empty -recognition list after defaults and overrides are merged.");
        }

        Object profiles = spec.get("-profile");
        if (!(profiles instanceof Map) || ((Map<String, Object>) profiles).isEmpty()) {
            addError(validation, "configError", "recogJson.-profile", "recogJson requires a non-empty -profile object after defaults and overrides are merged.");
            return;
        }

        if (recognition instanceof List) {
            for (Object item : (List<Object>) recognition) {
                if (!(item instanceof Map)) {
                    addError(validation, "configError", "recogJson.-recognition", "Each -recognition entry must be an object.");
                    continue;
                }
                Map<String, Object> rule = (Map<String, Object>) item;
                String mask = stringValue(rule.get("-mask")).trim();
                String profile = stringValue(rule.get("-profile")).trim();
                if (mask.isEmpty()) {
                    addError(validation, "configError", "recogJson.-recognition.-mask", "Recognition entries require non-blank -mask values.");
                }
                if (profile.isEmpty()) {
                    addError(validation, "configError", "recogJson.-recognition.-profile", "Recognition entries require non-blank -profile values.");
                } else if (profiles instanceof Map && !((Map<String, Object>) profiles).containsKey(profile)) {
                    addError(validation, "configError", "recogJson.-profile." + profile, "Recognition profile '" + profile + "' is referenced but not defined.");
                }
            }
        }
    }

    private static void validateModelXmlPresence(String modelXml, Map<String, Object> validation) {
        if (!hasText(modelXml)) {
            addError(validation, "configError", "modelXml", "ddp_hildr_modelXml is missing or blank.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeRecognitionSpec(
            Map<String, Object> base,
            Map<String, Object> override
    ) {
        Map<String, Object> merged = new LinkedHashMap<String, Object>();

        if (base != null) {
            for (Map.Entry<String, Object> entry : base.entrySet()) {
                merged.put(entry.getKey(), deepCopyValue(entry.getValue()));
            }
        }

        if (override == null) {
            return merged;
        }

        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();

            if ("-profile".equals(key) && overrideValue instanceof Map) {
                Map<String, Object> existing = castMap(merged.get("-profile"));
                if (existing == null) {
                    existing = new LinkedHashMap<String, Object>();
                }
                for (Map.Entry<String, Object> e : castMap(overrideValue).entrySet()) {
                    existing.put(e.getKey(), deepCopyValue(e.getValue()));
                }
                merged.put("-profile", existing);

            } else if ("-recognition".equals(key) && overrideValue instanceof List) {
                merged.put(
                        "-recognition",
                        mergeRecognitionList(
                                castList(base == null ? null : base.get("-recognition")),
                                castList(overrideValue)
                        )
                );

            } else {
                merged.put(key, deepCopyValue(overrideValue));
            }
        }

        return merged;
    }

    private static List<Map<String, Object>> mergeRecognitionList(
            List<Map<String, Object>> baseList,
            List<Map<String, Object>> overrideList
    ) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        List<String> overrideMasks = new ArrayList<String>();

        if (overrideList != null) {
            for (Map<String, Object> entry : overrideList) {
                String mask = stringValue(entry.get("-mask"));
                if (!mask.isEmpty()) {
                    overrideMasks.add(mask);
                }
                result.add(deepCopyMap(entry));
            }
        }

        if (baseList != null) {
            for (Map<String, Object> entry : baseList) {
                String mask = stringValue(entry.get("-mask"));
                if (!mask.isEmpty() && overrideMasks.contains(mask)) {
                    continue;
                }
                result.add(deepCopyMap(entry));
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object item : (List<Object>) value) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                copy.put(e.getKey(), deepCopyValue(e.getValue()));
            }
            return copy;
        }

        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<Object>) value) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> value) {
        return value == null
                ? new LinkedHashMap<String, Object>()
                : (Map<String, Object>) deepCopyValue(value);
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        return source == null
                ? new LinkedHashMap<String, Object>()
                : deepCopyMap(source);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> merged = deepCopy(base);
        if (override == null) {
            return merged;
        }
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object existing = merged.get(entry.getKey());
            Object incoming = entry.getValue();
            if (existing instanceof Map && incoming instanceof Map) {
                merged.put(entry.getKey(), deepMerge((Map<String, Object>) existing, (Map<String, Object>) incoming));
            } else {
                merged.put(entry.getKey(), deepCopyValue(incoming));
            }
        }
        return merged;
    }

    private static Map<String, Object> validationBase() {
        Map<String, Object> validation = new LinkedHashMap<String, Object>();
        validation.put("isValid", Boolean.TRUE);
        validation.put("errors", new ArrayList<Map<String, Object>>());
        validation.put("warnings", new ArrayList<Map<String, Object>>());
        return validation;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getErrors(Map<String, Object> validation) {
        Object errors = validation.get("errors");
        if (errors instanceof List) {
            return (List<Map<String, Object>>) errors;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static void addError(Map<String, Object> validation, String type, String field, String message) {
        Object errors = validation.get("errors");
        if (!(errors instanceof List)) {
            errors = new ArrayList<Map<String, Object>>();
            validation.put("errors", errors);
        }
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", type == null ? "configError" : type);
        error.put("field", field == null ? "" : field);
        error.put("message", message == null ? "" : message);
        ((List<Map<String, Object>>) errors).add(error);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
