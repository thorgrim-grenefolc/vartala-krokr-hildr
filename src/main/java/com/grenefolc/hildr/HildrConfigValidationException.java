package com.grenefolc.hildr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HildrConfigValidationException extends Exception {

    private final Map<String, Object> validation;
    private final String field;
    private final String hint;

    public HildrConfigValidationException(
            String message,
            String field,
            String hint,
            Map<String, Object> validation
    ) {
        super(message == null ? "Hildr configuration validation failed." : message);
        this.field = field == null ? "" : field;
        this.hint = hint == null ? "" : hint;
        this.validation = validation == null
                ? defaultValidation(message, field)
                : validation;
    }

    public Map<String, Object> getValidation() {
        return validation;
    }

    public String getField() {
        return field;
    }

    public String getHint() {
        return hint;
    }

    private static Map<String, Object> defaultValidation(String message, String field) {
        Map<String, Object> validation = new LinkedHashMap<String, Object>();
        validation.put("isValid", Boolean.FALSE);
        List<Map<String, Object>> errors = new ArrayList<Map<String, Object>>();
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", "configError");
        error.put("field", field == null ? "" : field);
        error.put("message", message == null ? "Hildr configuration validation failed." : message);
        errors.add(error);
        validation.put("errors", errors);
        validation.put("warnings", Collections.emptyList());
        return validation;
    }
}
