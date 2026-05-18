package com.grenefolc.hildr;

import com.boomi.connector.api.ConnectorException;
import com.boomi.connector.api.DynamicPropertyMap;
import com.boomi.connector.api.ObjectData;
import com.boomi.connector.api.OperationContext;
import com.boomi.connector.api.OperationResponse;
import com.boomi.connector.api.OperationStatus;
import com.boomi.connector.api.PayloadMetadata;
import com.boomi.connector.api.PropertyMap;
import com.boomi.connector.util.BaseUpdateOperation;
import com.boomi.connector.util.PayloadUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class HildrOperation extends BaseUpdateOperation {

    public static final String PROP_CLI_JSON = "cliJson";
    public static final String PROP_RECOG_JSON = "recogJson";
    public static final String PROP_MODEL_XML = "modelXml";
    public static final String PROP_TRACE = "trace";
    public static final String TRACKED_PROP_HILDR_LOG = "tracked_hildr_logJson";

    public HildrOperation(OperationContext context) {
        super(context);
    }

    @Override
    protected void executeUpdate(com.boomi.connector.api.UpdateRequest request, OperationResponse response) {
        PropertyMap operationProperties = getContext().getOperationProperties();
        String staticCliJson = getStringProperty(operationProperties, PROP_CLI_JSON);
        String staticRecogJson = getStringProperty(operationProperties, PROP_RECOG_JSON);
        String staticModelXml = getStringProperty(operationProperties, PROP_MODEL_XML);

        boolean trace = false;
        try {
            trace = getBooleanProperty(operationProperties, PROP_TRACE, false);
        } catch (Exception ignored) {
            trace = false;
        }

        for (ObjectData data : request) {
            try {
                byte[] inputBytes = readAllBytes(data.getData());

                Map<String, Object> incomingUserDefinedPropertiesRaw = new LinkedHashMap<String, Object>();
                Map<?, ?> udp = data.getUserDefinedProperties();
                if (udp != null) {
                    for (Map.Entry<?, ?> entry : udp.entrySet()) {
                        String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
                        if (key != null) {
                            incomingUserDefinedPropertiesRaw.put(key, entry.getValue());
                        }
                    }
                }

                String dynamicOperationCliJson = getDynamicOperationProperty(data, PROP_CLI_JSON);
                String connectorPropertyCliJson = getConnectorDynamicProperty(data, "CliJson");
                String operationCliJson = staticCliJson;
                String dynamicOperationRecogJson = getDynamicOperationProperty(data, PROP_RECOG_JSON);
                String connectorPropertyRecogJson = getConnectorDynamicProperty(data, "RecogJson");
                String operationRecogJson = staticRecogJson;
                String dynamicOperationModelXml = getDynamicOperationProperty(data, PROP_MODEL_XML);
                String connectorPropertyModelXml = getConnectorDynamicProperty(data, "ModelXml");
                String operationModelXml = staticModelXml;

                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_dynamicOperationCliJson",
                        nullToEmpty(dynamicOperationCliJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_connectorPropertyCliJson",
                        nullToEmpty(connectorPropertyCliJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_operationCliJson",
                        nullToEmpty(operationCliJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_dynamicOperationRecogJson",
                        nullToEmpty(dynamicOperationRecogJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_connectorPropertyRecogJson",
                        nullToEmpty(connectorPropertyRecogJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_operationRecogJson",
                        nullToEmpty(operationRecogJson)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_dynamicOperationModelXml",
                        nullToEmpty(dynamicOperationModelXml)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_connectorPropertyModelXml",
                        nullToEmpty(connectorPropertyModelXml)
                );
                incomingUserDefinedPropertiesRaw.put(
                        "ddp_hildr_debug_operationModelXml",
                        nullToEmpty(operationModelXml)
                );

                String cliJson = resolveEffectiveJson(
                        mapStringValue(incomingUserDefinedPropertiesRaw, "ddp_hildr_cliJson"),
                        mapStringValue(incomingUserDefinedPropertiesRaw, "document.dynamic.userdefined.ddp_hildr_cliJson"),
                        dynamicOperationCliJson,
                        connectorPropertyCliJson,
                        operationCliJson
                );

                String recogJson = resolveEffectiveJson(
                        mapStringValue(incomingUserDefinedPropertiesRaw, "ddp_hildr_recogJson"),
                        mapStringValue(incomingUserDefinedPropertiesRaw, "document.dynamic.userdefined.ddp_hildr_recogJson"),
                        dynamicOperationRecogJson,
                        connectorPropertyRecogJson,
                        operationRecogJson
                );

                String modelXml = resolveEffectiveText(
                        mapStringValue(incomingUserDefinedPropertiesRaw, "ddp_hildr_modelXml"),
                        mapStringValue(incomingUserDefinedPropertiesRaw, "document.dynamic.userdefined.ddp_hildr_modelXml"),
                        dynamicOperationModelXml,
                        connectorPropertyModelXml,
                        operationModelXml
                );

Map<String, String> incomingUserDefinedProperties = stringifyMap(incomingUserDefinedPropertiesRaw);

HildrExecutor.Result result;

incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_cliJson_type", typeAndValue(cliJson));
incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_recogJson_type", typeAndValue(recogJson));
incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_modelXml_type", typeAndValue(modelXml));
incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_dynamicOperationModelXml_type", typeAndValue(dynamicOperationModelXml));
incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_connectorPropertyModelXml_type", typeAndValue(connectorPropertyModelXml));
incomingUserDefinedPropertiesRaw.put("ddp_hildr_debug_operationModelXml_type", typeAndValue(operationModelXml));
incomingUserDefinedProperties = stringifyMap(incomingUserDefinedPropertiesRaw);

try {
    result = HildrExecutor.execute(
            new ByteArrayInputStream(inputBytes),
            cliJson,
            recogJson,
            modelXml,
            trace,
            incomingUserDefinedProperties
    );
} catch (Exception ex) {
    throw new ConnectorException(
            "HildrExecutor.execute failed: " + ex.getClass().getName() + ": " + ex.getMessage(),
            ex
    );
}

PayloadMetadata metadata = response.createMetadata();

try {
    Map<?, ?> rawDocumentProperties = result.getDocumentProperties();
    if (rawDocumentProperties != null) {
        for (Map.Entry<?, ?> entry : rawDocumentProperties.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            Object rawValue = entry.getValue();
            String value = rawValue == null ? null : String.valueOf(rawValue);
            if (key != null) {
                metadata.setUserDefinedProperty(key, value);
            }
        }
    }
} catch (Exception ex) {
    throw new ConnectorException(
            "Writing result document properties failed: " + ex.getClass().getName()
                    + ": " + ex.getMessage(),
            ex
    );
}

                metadata.setUserDefinedProperty("ddp_hildr_statusResponse", result.getStatusResponse());
                metadata.setUserDefinedProperty("ddp_hildr_statusCode", String.valueOf(result.getStatusCode()));
                metadata.setUserDefinedProperty("ddp_hildr_logJson", result.getLogJson());
                metadata.setTrackedProperty(TRACKED_PROP_HILDR_LOG, result.getLogJson());

                byte[] responsePayloadBytes = result.getPayloadBytes() != null
                        ? result.getPayloadBytes()
                        : inputBytes;

                response.addResult(
                        data,
                        OperationStatus.SUCCESS,
                        String.valueOf(result.getStatusCode()),
                        result.getStatusResponse(),
                        PayloadUtil.toPayload(new ByteArrayInputStream(responsePayloadBytes), metadata)
                );

            } catch (Exception ex) {
                try {
                    PayloadMetadata metadata = response.createMetadata();

                    String failJson =
                            "{\"status\":{\"code\":\"0\",\"response\":\"failure\"},"
                                    + "\"eventSupport\":{\"category\":\"error\",\"title\":\"Hildr operation failed\","
                                    + "\"subject\":\"" + escapeJson(ex.getMessage()) + "\"}}";

                    metadata.setUserDefinedProperty("ddp_hildr_statusResponse", "error");
                    metadata.setUserDefinedProperty("ddp_hildr_statusCode", "0");
                    metadata.setUserDefinedProperty("ddp_hildr_logJson", failJson);
                    metadata.setTrackedProperty(TRACKED_PROP_HILDR_LOG, failJson);

                    response.addResult(
                            data,
                            OperationStatus.APPLICATION_ERROR,
                            "0",
                            "error",
                            PayloadUtil.toPayload(new ByteArrayInputStream(new byte[0]), metadata)
                    );
                } catch (Exception nested) {
                    throw new ConnectorException("Hildr operation failed: " + ex.getMessage(), ex);
                }
            }
        }
    }

    private static Map<String, String> stringifyMap(Map<String, Object> source) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (source == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            out.put(entry.getKey(), stringValue(entry.getValue()));
        }
        return out;
    }

    private static String mapStringValue(Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return null;
        }
        return stringValue(source.get(key));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String resolveEffectiveJson(String... candidates) {
        if (candidates == null) {
            return "{}";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty() && !"{}".equals(candidate.trim())) {
                return candidate;
            }
        }
        return "{}";
    }

    private static String resolveEffectiveText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String getDynamicOperationProperty(ObjectData data, String key) {
        if (data == null || isBlank(key)) {
            return null;
        }
        DynamicPropertyMap properties = data.getDynamicOperationProperties();
        if (properties == null) {
            return null;
        }
        Object value = properties.getProperty(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String getConnectorDynamicProperty(ObjectData data, String key) {
        if (data == null || isBlank(key)) {
            return null;
        }
        try {
            Object mapLike = data.getDynamicProperties();
            return readStringFromMapLike(mapLike, key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readStringFromMapLike(Object mapLike, String key) {
        if (mapLike == null || isBlank(key)) {
            return null;
        }

        if (mapLike instanceof Map) {
            Object value = ((Map<?, ?>) mapLike).get(key);
            return value == null ? null : String.valueOf(value);
        }

        try {
            Object value = mapLike.getClass().getMethod("getProperty", String.class).invoke(mapLike, key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
        }

        try {
            Object value = mapLike.getClass().getMethod("get", Object.class).invoke(mapLike, key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String getStringProperty(PropertyMap propertyMap, String key) {
        Object value = propertyMap.getProperty(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean getBooleanProperty(PropertyMap propertyMap, String key, boolean defaultValue) {
        Object value = propertyMap.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String typeAndValue(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getName() + "=" + String.valueOf(value);
    }
}
