package com.grenefolc.hildr;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HildrExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DDP_STATUS_RESPONSE = "ddp_hildr_statusResponse";
    private static final String DDP_STATUS_CODE = "ddp_hildr_statusCode";
    private static final String DDP_LOG_JSON = "ddp_hildr_logJson";
    private static final String DDP_ROUTING_PROFILE = "ddp_hildr_routingProfile";
    private static final String DDP_PRESENTATION_CLASS = "ddp_hildr_presentationClass";
    private static final String DDP_MESSAGE_CLASS = "ddp_hildr_messageClass";

    public static Result execute(
            InputStream inputStream,
            String cliJson,
            String modelXml,
            boolean trace,
            Map<String, String> incomingUserDefinedProperties
    ) {
        return execute(
                inputStream,
                cliJson,
                "{}",
                modelXml,
                trace,
                incomingUserDefinedProperties
        );
    }

    public static Result execute(
            InputStream inputStream,
            String cliJson,
            String recogJson,
            String modelXml,
            boolean trace,
            Map<String, String> incomingUserDefinedProperties
    ) {
        Map<String, String> outputProperties = new LinkedHashMap<String, String>();
        LogPackage logPackage = new LogPackage();
        byte[] inputBytes = new byte[0];

        try {
            logPackage.ensureBoomi();
            logPackage.getBoomi().put("isoDateTimeUtc", Instant.now().toString());

            addCodeReference(logPackage);
            addBoomiHeaderFromIncomingProperties(logPackage, incomingUserDefinedProperties);

            inputBytes = readAllBytesSafe(inputStream);
            final String dataStream = new String(inputBytes, java.nio.charset.StandardCharsets.UTF_8);

            final HildrResolvedConfig resolvedConfig;
            try {
                resolvedConfig = HildrConfigResolver.resolve(cliJson, recogJson, modelXml, logPackage);
                logPackage.setTraceEnabled(trace || showFullTrace(resolvedConfig) || resolvedConfig.getBehaviorConfig().showProcessTrace());
                logPackage.trace("STAGE_OK: config_resolved");
            } catch (HildrConfigValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: config_resolved: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            try {
                if (trace || resolvedConfig.getBehaviorConfig().showCliSettings()) {
                    logPackage.put("cli", resolvedConfig.getBehaviorConfig().getRaw());
                }
                logPackage.trace("STAGE_OK: cli_logged");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: cli_logged: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrRecognitionResult recognitionResult;
            try {
                recognitionResult = HildrRecognitionService.recognise(
                        dataStream,
                        resolvedConfig.getBehaviorConfig(),
                        resolvedConfig.getRecognitionSpec(),
                        logPackage
                );
                logPackage.trace("STAGE_OK: recognition");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: recognition: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            try {
                if (recognitionResult.getPresentationClass().isEmpty()) {
                    throw new IllegalStateException(
                            "Unable to determine presentation class from recognition config."
                    );
                }
                outputProperties.put(DDP_ROUTING_PROFILE, recognitionResult.getRecognitionProfile());
                outputProperties.put(DDP_PRESENTATION_CLASS, recognitionResult.getPresentationClass());
                logPackage.trace("STAGE_OK: recognition_validated");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: recognition_validated: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrParseContext parseContext;
            try {
                parseContext = new HildrParseContext();
                parseContext.setRoutingProfile(recognitionResult.getRecognitionProfile());
                logPackage.trace("STAGE_OK: parse_context");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: parse_context: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrDelimiterProfile delimiterProfile;
            try {
                delimiterProfile = HildrDelimiterResolver.resolve(
                        dataStream,
                        recognitionResult,
                        resolvedConfig.getRecognitionSpec(),
                        logPackage
                );
                logPackage.trace("STAGE_OK: delimiters_resolved");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: delimiters_resolved: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            try {
                if (resolvedConfig.getBehaviorConfig().showDelimiterSettings()) {
                    Map<String, Object> delimiter = new LinkedHashMap<String, Object>();
                    delimiter.put("presentationClass", delimiterProfile.getPresentationClass());
                    delimiter.put("delimiterSource", delimiterProfile.getDelimiterSource());
                    delimiter.put("delimiterString", delimiterProfile.getDelimiterString());
                    delimiter.put("tagSeparator", delimiterProfile.getTagSeparator());
                    delimiter.put("elementSeparator", delimiterProfile.getElementSeparator());
                    delimiter.put("compositeSeparator", delimiterProfile.getCompositeSeparator());
                    delimiter.put("decimalNotation", delimiterProfile.getDecimalNotation());
                    delimiter.put("releaseIndicator", delimiterProfile.getReleaseIndicator());
                    delimiter.put("repetitionSeparator", delimiterProfile.getRepetitionSeparator());
                    delimiter.put("segmentTerminator", delimiterProfile.getSegmentTerminator());
                    delimiter.put("envelopeHeader", delimiterProfile.getEnvelopeHeader());
                    delimiter.put("envelopeFooter", delimiterProfile.getEnvelopeFooter());
                    logPackage.put("delimiter", delimiter);
                }
                logPackage.trace("STAGE_OK: delimiter_logging");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: delimiter_logging: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrModel model;
            try {
                model = HildrModelLoader.load(resolvedConfig.getModelXml(), logPackage);
                logPackage.trace("STAGE_OK: model_loaded");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: model_loaded: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            try {
                HildrModelInitialiser.initialise(
                        model,
                        resolvedConfig.getBehaviorConfig(),
                        delimiterProfile,
                        logPackage
                );
                logPackage.trace("STAGE_OK: model_initialised");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: model_initialised: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrModelIndex modelIndex;
            try {
                modelIndex = HildrModelIndex.build(model);
                logPackage.trace("STAGE_OK: model_indexed");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: model_indexed: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrPresentationHandler presentationHandler;
            try {
                presentationHandler = HildrPresentationHandlerFactory.resolve(
                        delimiterProfile.getPresentationClass()
                );
                logPackage.trace("STAGE_OK: presentation_handler");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: presentation_handler: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            final HildrParseResult parseResult;
            try {
                parseResult = HildrParserEngine.parse(
                        dataStream,
                        resolvedConfig.getBehaviorConfig(),
                        model,
                        delimiterProfile,
                        parseContext,
                        presentationHandler,
                        logPackage
                );
                logPackage.trace("STAGE_OK: parse_complete");
            } catch (Exception ex) {
                throw new IllegalStateException("STAGE_FAIL: parse_complete: "
                        + ex.getClass().getName() + ": " + ex.getMessage(), ex);
            }

            if (parseResult.getEventSupport() != null) {
                logPackage.put("eventSupport", parseResult.getEventSupport().toMap());
            } else if (!parseResult.isSuccess() && logPackage.getStatusError() != null) {
                Map<String, Object> err = logPackage.getStatusError();
                addFailure(
                        logPackage,
                        "Hildr parse failed",
                        String.valueOf(err.get("message")),
                        String.valueOf(err.get("sourceBuffer")),
                        "Review the model occurrence rules and the source segment at the reported ordinal."
                );
            }

            byte[] outputPayload = HildrOutputBuilder.build(parseResult, logPackage);
            String messageClass = firstNonBlank(
                    incomingUserDefinedProperties == null ? null : incomingUserDefinedProperties.get(DDP_MESSAGE_CLASS),
                    incomingUserDefinedProperties == null ? null : incomingUserDefinedProperties.get("document.dynamic.userdefined." + DDP_MESSAGE_CLASS)
            );
            outputPayload = annotateMessageClassPayload(outputPayload, messageClass);

            int statusCode = parseResult.isSuccess() ? 1 : 0;
            String statusResponse = parseResult.isSuccess() ? "success" : "error";

            if (statusCode == 0) {
                outputPayload = annotateFailurePayload(outputPayload, logPackage);
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("inputLength", Integer.valueOf(inputBytes.length));
            data.put("outputLength", Integer.valueOf(outputPayload.length));
            data.put("routingProfile", parseContext.getRoutingProfile());
            data.put("routingVariant", parseContext.getRoutingVariant());
            data.put("presentationClass", delimiterProfile.getPresentationClass());
            data.put("recognitionMask", recognitionResult.getRecognitionMask());
            data.put("indexedNodeCount", Integer.valueOf(modelIndex.getById().size()));
            data.put("parseMetrics", parseResult.getMetrics());
            logPackage.put("data", data);

            setStatus(logPackage, statusCode, statusResponse);
            String logJson = OBJECT_MAPPER.writeValueAsString(logPackage.toMap());

            outputProperties.put(DDP_STATUS_CODE, String.valueOf(statusCode));
            outputProperties.put(DDP_STATUS_RESPONSE, statusResponse);
            outputProperties.put(DDP_LOG_JSON, logJson);

            return new Result(
                    outputProperties,
                    logJson,
                    statusCode,
                    statusResponse,
                    outputPayload
            );

        } catch (Exception ex) {
            addFailure(
                    logPackage,
                    ex instanceof HildrConfigValidationException ? "Hildr configuration validation failed" : "Executor failure",
                    ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage(),
                    null,
                    ex instanceof HildrConfigValidationException
                            ? ((HildrConfigValidationException) ex).getHint()
                            : "Review ddp_hildr_cliJson, ddp_hildr_recogJson, ddp_hildr_modelXml and inbound payload structure."
            );
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("type", ex instanceof HildrConfigValidationException ? "configError" : "executorFailure");
            error.put("field", ex instanceof HildrConfigValidationException ? ((HildrConfigValidationException) ex).getField() : "");
            error.put("nodeId", "");
            error.put("nodePath", "");
            error.put("sourceBuffer", "");
            error.put("segmentOrdinal", Integer.valueOf(0));
            error.put("message", ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
            logPackage.setStatusError(error);
            return buildFailureResult(outputProperties, logPackage, inputBytes);
        }
    }

    private static byte[] annotateMessageClassPayload(
            byte[] outputPayload,
            String messageClass
    ) {
        if (outputPayload == null || outputPayload.length == 0) {
            return outputPayload == null ? new byte[0] : outputPayload;
        }
        if (messageClass == null || messageClass.trim().isEmpty()) {
            return outputPayload;
        }

        try {
            String xml = new String(outputPayload, java.nio.charset.StandardCharsets.UTF_8);
            int rootStart = xml.indexOf("<vartala:ediSemantic");
            if (rootStart < 0) {
                return outputPayload;
            }
            int rootEnd = xml.indexOf(">", rootStart);
            if (rootEnd < 0) {
                return outputPayload;
            }
            String rootOpenTag = xml.substring(rootStart, rootEnd);
            if (rootOpenTag.indexOf(" messageClass=\"") >= 0) {
                return outputPayload;
            }

            String insert = " messageClass=\""
                    + xmlAttributeEscape(messageClass.trim())
                    + "\"";
            xml = xml.substring(0, rootEnd) + insert + xml.substring(rootEnd);

            return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return outputPayload;
        }
    }

    private static byte[] annotateFailurePayload(
            byte[] outputPayload,
            LogPackage logPackage
    ) {
        if (outputPayload == null || outputPayload.length == 0) {
            return outputPayload == null ? new byte[0] : outputPayload;
        }

        try {
            String xml = new String(outputPayload, java.nio.charset.StandardCharsets.UTF_8);
            if (xml.indexOf("<vartala:ediSemantic") < 0) {
                return outputPayload;
            }

            Map<String, Object> err = logPackage == null ? null : logPackage.getStatusError();
            String errorType = err == null ? "error" : stringValueForXml(err.get("type"));
            String errorNode = err == null ? "" : stringValueForXml(err.get("nodeId"));
            String errorMessage = err == null ? "Hildr parse failed." : stringValueForXml(err.get("message"));

            if (xml.indexOf(" status=\"error\"") < 0) {
                xml = xml.replaceFirst(
                        "<vartala:ediSemantic",
                        "<vartala:ediSemantic status=\"error\" errorType=\""
                                + xmlAttributeEscape(errorType)
                                + "\" errorNode=\""
                                + xmlAttributeEscape(errorNode)
                                + "\""
                );
            }

            String comment = "<!-- Hildr parse failed: " + xmlCommentEscape(errorMessage) + " -->";
            if (xml.indexOf("<!-- Hildr parse failed:") < 0) {
                if (xml.indexOf("</vartala:ediSemantic>") >= 0) {
                    xml = xml.replace("</vartala:ediSemantic>", comment + "</vartala:ediSemantic>");
                } else {
                    xml = xml + comment;
                }
            }

            return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return outputPayload;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return "";
    }

    private static String stringValueForXml(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String xmlAttributeEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String xmlCommentEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("--", "- -");
    }

    @SuppressWarnings("unchecked")
    private static boolean showFullTrace(HildrResolvedConfig resolvedConfig) {
        if (resolvedConfig == null || resolvedConfig.getBehaviorConfig() == null) {
            return false;
        }
        Map<String, Object> raw = resolvedConfig.getBehaviorConfig().getRaw();
        if (raw == null) {
            return false;
        }
        Object direct = raw.get("showFullTrace");
        if (truthy(direct)) {
            return true;
        }
        Object logging = raw.get("logging");
        if (logging instanceof Map) {
            return truthy(((Map<String, Object>) logging).get("showFullTrace"));
        }
        return false;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private static void addCodeReference(LogPackage logPackage) {
        Map<String, Object> codeRef = new LinkedHashMap<String, Object>();
        try {
            Class<?> clazz = HildrExecutor.class;
            codeRef.put("executorClass", clazz.getName());
            Package pkg = clazz.getPackage();
            if (pkg != null) {
                String implVersion = pkg.getImplementationVersion();
                if (implVersion != null && !implVersion.trim().isEmpty()) {
                    codeRef.put("implementationVersion", implVersion);
                }
            }
        } catch (Exception ignored) {
        }
        logPackage.put("codeRef", codeRef);
    }

    private static Result buildFailureResult(
            Map<String, String> outputProperties,
            LogPackage logPackage,
            byte[] passthroughPayload
    ) {
        try {
            setStatus(logPackage, 0, "error");
            String logJson = OBJECT_MAPPER.writeValueAsString(logPackage.toMap());

            outputProperties.put(DDP_STATUS_CODE, "0");
            outputProperties.put(DDP_STATUS_RESPONSE, "error");
            outputProperties.put(DDP_LOG_JSON, logJson);

            return new Result(
                    outputProperties,
                    logJson,
                    0,
                    "error",
                    passthroughPayload == null ? new byte[0] : passthroughPayload
            );
        } catch (Exception ex) {
            return new Result(
                    outputProperties,
                    "{\"status\":{\"code\":0,\"response\":\"error\"}}",
                    0,
                    "error",
                    passthroughPayload == null ? new byte[0] : passthroughPayload
            );
        }
    }

    private static void setStatus(LogPackage logPackage, int code, String response) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("code", Integer.valueOf(code));
        status.put("response", response);
        Map<String, Object> error = logPackage.getStatusError();
        if (error != null && !error.isEmpty()) {
            status.put("error", error);
        }
        logPackage.put("status", status);
    }

    private static void addFailure(
            LogPackage logPackage,
            String title,
            String subject,
            String buffer,
            String hint
    ) {
        Map<String, Object> eventSupport = new LinkedHashMap<String, Object>();
        eventSupport.put("category", "error");
        eventSupport.put("title", title == null ? "" : title);
        eventSupport.put("subject", subject == null ? "" : subject);
        if (buffer != null && !buffer.isEmpty()) {
            eventSupport.put("buffer", buffer);
        }
        eventSupport.put("hint", hint == null ? "" : hint);
        logPackage.put("eventSupport", eventSupport);
    }

    private static void addBoomiHeaderFromIncomingProperties(
            LogPackage logPackage,
            Map<String, String> incomingUserDefinedProperties
    ) {
        Map<String, Object> boomi = logPackage.ensureBoomi();
        if (incomingUserDefinedProperties == null || incomingUserDefinedProperties.isEmpty()) {
            return;
        }

        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_atomId", "atomId");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_atomName", "atomName");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_processId", "processId");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_processName", "processName");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_executionId", "executionId");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_runtimeId", "runtimeId");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_runtimeName", "runtimeName");
        copyIfPresent(incomingUserDefinedProperties, boomi, "ddp_vartalaUUID", "vartalaUUID");
        addConfigSources(logPackage, incomingUserDefinedProperties);
    }

    @SuppressWarnings("unchecked")
    private static void addConfigSources(
            LogPackage logPackage,
            Map<String, String> incomingUserDefinedProperties
    ) {
        Map<String, Object> init;
        Object existing = logPackage.toMap().get("init");
        if (existing instanceof Map) {
            init = (Map<String, Object>) existing;
        } else {
            init = new LinkedHashMap<String, Object>();
            logPackage.put("init", init);
        }

        Map<String, Object> sources = new LinkedHashMap<String, Object>();
        putSource(sources, "dynamicDocumentCliJson", incomingUserDefinedProperties.get("ddp_hildr_cliJson"));
        putSource(sources, "dynamicDocumentCliJsonQualified", incomingUserDefinedProperties.get("document.dynamic.userdefined.ddp_hildr_cliJson"));
        putSource(sources, "dynamicOperationCliJson", incomingUserDefinedProperties.get("ddp_hildr_debug_dynamicOperationCliJson"));
        putSource(sources, "connectorPropertyCliJson", incomingUserDefinedProperties.get("ddp_hildr_debug_connectorPropertyCliJson"));
        putSource(sources, "operationCliJson", incomingUserDefinedProperties.get("ddp_hildr_debug_operationCliJson"));
        putSource(sources, "dynamicDocumentRecogJson", incomingUserDefinedProperties.get("ddp_hildr_recogJson"));
        putSource(sources, "dynamicDocumentRecogJsonQualified", incomingUserDefinedProperties.get("document.dynamic.userdefined.ddp_hildr_recogJson"));
        putSource(sources, "dynamicOperationRecogJson", incomingUserDefinedProperties.get("ddp_hildr_debug_dynamicOperationRecogJson"));
        putSource(sources, "connectorPropertyRecogJson", incomingUserDefinedProperties.get("ddp_hildr_debug_connectorPropertyRecogJson"));
        putSource(sources, "operationRecogJson", incomingUserDefinedProperties.get("ddp_hildr_debug_operationRecogJson"));
        putSource(sources, "dynamicDocumentModelXml", incomingUserDefinedProperties.get("ddp_hildr_modelXml"));
        putSource(sources, "dynamicDocumentModelXmlQualified", incomingUserDefinedProperties.get("document.dynamic.userdefined.ddp_hildr_modelXml"));
        putSource(sources, "dynamicOperationModelXml", incomingUserDefinedProperties.get("ddp_hildr_debug_dynamicOperationModelXml"));
        putSource(sources, "connectorPropertyModelXml", incomingUserDefinedProperties.get("ddp_hildr_debug_connectorPropertyModelXml"));
        putSource(sources, "operationModelXml", incomingUserDefinedProperties.get("ddp_hildr_debug_operationModelXml"));
        init.put("configSources", sources);
    }

    private static void putSource(Map<String, Object> sources, String key, String value) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("present", Boolean.valueOf(value != null && !value.trim().isEmpty()));
        item.put("value", value == null ? "" : value);
        sources.put(key, item);
    }

    private static void copyIfPresent(
            Map<String, String> source,
            Map<String, Object> target,
            String sourceKey,
            String targetKey
    ) {
        if (source != null && source.containsKey(sourceKey)) {
            String value = source.get(sourceKey);
            if (value != null && !value.trim().isEmpty()) {
                target.put(targetKey, value);
            }
        }
    }

    private static byte[] readAllBytesSafe(InputStream inputStream) {
        try {
            return readAllBytes(inputStream);
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        try {
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int read;
            while (inputStream != null && (read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static final class LogPackage {
        private final Map<String, Object> root = new LinkedHashMap<String, Object>();
        private final List<String> trace = new ArrayList<String>();
        private Map<String, Object> statusError;
        private boolean traceEnabled = false;

        public Map<String, Object> ensureBoomi() {
            @SuppressWarnings("unchecked")
            Map<String, Object> boomi = (Map<String, Object>) root.get("boomi");
            if (boomi == null) {
                boomi = new LinkedHashMap<String, Object>();
                root.put("boomi", boomi);
            }
            return boomi;
        }

        public Map<String, Object> getBoomi() {
            return ensureBoomi();
        }

        public void trace(String line) {
            if (!traceEnabled) {
                return;
            }
            trace.add(line == null ? "" : line);
            root.put("trace", trace);
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setStatusError(Map<String, Object> statusError) {
            if (this.statusError == null && statusError != null && !statusError.isEmpty()) {
                this.statusError = statusError;
            }
        }

        public Map<String, Object> getStatusError() {
            return statusError;
        }

        public void put(String key, Object value) {
            root.put(key, value);
        }

        public Map<String, Object> toMap() {
            return root;
        }
    }

    public static class Result {
        private final Map<String, String> documentProperties;
        private final String logJson;
        private final int statusCode;
        private final String statusResponse;
        private final byte[] payloadBytes;

        public Result(
                Map<String, String> documentProperties,
                String logJson,
                int statusCode,
                String statusResponse,
                byte[] payloadBytes
        ) {
            this.documentProperties =
                    documentProperties == null
                            ? Collections.<String, String>emptyMap()
                            : documentProperties;
            this.logJson = logJson == null ? "" : logJson;
            this.statusCode = statusCode;
            this.statusResponse = statusResponse == null ? "" : statusResponse;
            this.payloadBytes = payloadBytes == null ? new byte[0] : payloadBytes;
        }

        public Map<String, String> getDocumentProperties() {
            return documentProperties;
        }

        public String getLogJson() {
            return logJson;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusResponse() {
            return statusResponse;
        }

        public byte[] getPayloadBytes() {
            return payloadBytes;
        }
    }
}
