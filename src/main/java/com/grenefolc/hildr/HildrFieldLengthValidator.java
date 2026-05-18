package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HildrFieldLengthValidator {

    private HildrFieldLengthValidator() {
    }

    public static boolean validate(
            HildrModelNode element,
            String logicalValue,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            String presentationClass
    ) {
        if (element == null || !"ed".equals(element.getNodeType())) {
            return true;
        }

        String value = logicalValue == null ? "" : logicalValue;
        int actualLength = value.length();
        long minLength = element.getMinLength();
        long maxLength = element.getMaxLength();
        boolean mandatory = element.getMinOccurs() > 0L;

        if ((mandatory || actualLength > 0) && actualLength < minLength) {
            recordError(
                    element,
                    value,
                    actualLength,
                    minLength,
                    maxLength,
                    context,
                    logPackage,
                    presentationClass,
                    "fieldLengthTooShort",
                    "Field " + safe(element.getId()) + " length " + actualLength
                            + " is below minimum length " + minLength + "."
            );
            return false;
        }

        if (actualLength > maxLength) {
            recordError(
                    element,
                    value,
                    actualLength,
                    minLength,
                    maxLength,
                    context,
                    logPackage,
                    presentationClass,
                    "fieldLengthTooLong",
                    "Field " + safe(element.getId()) + " length " + actualLength
                            + " exceeds maximum length " + maxLength + "."
            );
            return false;
        }

        return true;
    }

    private static void recordError(
            HildrModelNode element,
            String value,
            int actualLength,
            long minLength,
            long maxLength,
            HildrParseContext context,
            HildrExecutor.LogPackage logPackage,
            String presentationClass,
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

        if (logPackage == null) {
            return;
        }

        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", safe(type));
        error.put("nodeId", safe(element.getId()));
        error.put("nodePath", buildNodePath(context, element));
        error.put("sourceBuffer", context == null ? "" : safe(context.getSourceBuffer()));
        error.put("segmentOrdinal", Integer.valueOf(context == null ? 0 : context.getSourceIndex() + 1));
        error.put("presentationClass", safe(presentationClass));
        error.put("actualLength", Integer.valueOf(actualLength));
        error.put("minLength", Long.valueOf(minLength));
        error.put("maxLength", Long.valueOf(maxLength));
        error.put("message", safe(message));
        logPackage.setStatusError(error);
        logPackage.trace("FIELD_LENGTH_ERROR type=" + safe(type)
                + " nodeId=" + safe(element.getId())
                + " actualLength=" + actualLength
                + " minLength=" + minLength
                + " maxLength=" + maxLength
                + " presentationClass=" + safe(presentationClass)
                + " value=" + safe(value));
    }

    private static String buildNodePath(HildrParseContext context, HildrModelNode element) {
        String base = context == null ? "" : context.currentModelNodePathQualified();
        String suffix = "ed[@id='" + safe(element.getId()) + "']";
        if (base == null || base.trim().isEmpty() || "//".equals(base)) {
            return "//" + suffix;
        }
        return base + "/" + suffix;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
