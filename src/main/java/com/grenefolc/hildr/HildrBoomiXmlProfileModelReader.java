package com.grenefolc.hildr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;

final class HildrBoomiXmlProfileModelReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> COMMENT_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {};

    private HildrBoomiXmlProfileModelReader() {
    }

    static boolean canRead(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) {
            return false;
        }
        Element root = doc.getDocumentElement();
        if ("XMLElement".equals(logicalName(root)) && "md".equals(commentString(root, "en"))) {
            return true;
        }
        return containsElementNamed(root, "XMLProfile") && findLogicalRoot(root) != null;
    }

    static HildrModel read(
            Document doc,
            HildrExecutor.LogPackage logPackage
    ) throws Exception {
        Element logicalRoot = findLogicalRoot(doc.getDocumentElement());
        if (logicalRoot == null) {
            throw new IllegalStateException("Boomi XML profile does not contain a logical jeraSeidr md XMLElement.");
        }

        HildrModel model = new HildrModel();
        model.setRootName("md");

        int ordinal = 0;
        for (Node child = logicalRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "XMLElement".equals(logicalName((Element) child))) {
                HildrModelNode childNode = readNode((Element) child);
                childNode.setOrdinal(ordinal++);
                model.getTopLevelNodes().add(childNode);
            }
        }

        if (logPackage != null) {
            logPackage.trace("Boomi XML profile converted to jeraSeidr model nodes: logicalRoot="
                    + logicalRoot.getAttribute("name")
                    + ", topLevelNodes=" + model.getTopLevelNodes().size());
        }
        return model;
    }

    private static HildrModelNode readNode(Element element) throws Exception {
        Map<String, Object> comments = comments(element);
        String nodeType = stringValue(comments.get("en"));
        if (nodeType.isEmpty()) {
            throw new IllegalStateException("Boomi XMLElement '" + element.getAttribute("name")
                    + "' is missing comments.en logical node type.");
        }

        HildrModelNode node = new HildrModelNode();
        node.setNodeType(nodeType);
        node.setId(element.getAttribute("name"));
        node.setDescription(stringValue(comments.get("ds")));
        node.setOccurs(range(element.getAttribute("minOccurs"), element.getAttribute("maxOccurs")));
        node.setLength(range(element.getAttribute("minLength"), element.getAttribute("maxLength")));
        node.setRecognitionCode(stringValue(comments.get("rc")));

        int ordinal = 0;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "XMLElement".equals(logicalName((Element) child))) {
                HildrModelNode childNode = readNode((Element) child);
                childNode.setOrdinal(ordinal++);
                node.getChildren().add(childNode);
            }
        }

        return node;
    }

    private static Element findLogicalRoot(Element root) {
        if (root == null) {
            return null;
        }
        if ("XMLElement".equals(logicalName(root)) && "md".equals(commentString(root, "en"))) {
            return root;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                Element found = findLogicalRoot((Element) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsElementNamed(Element root, String name) {
        if (root == null) {
            return false;
        }
        if (name.equals(logicalName(root))) {
            return true;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && containsElementNamed((Element) child, name)) {
                return true;
            }
        }
        return false;
    }

    private static String range(String min, String max) {
        String minValue = safe(min);
        String maxValue = normaliseUnbounded(safe(max));
        if (minValue.isEmpty() && maxValue.isEmpty()) {
            return "";
        }
        if (minValue.isEmpty()) {
            return maxValue;
        }
        if (maxValue.isEmpty() || minValue.equals(maxValue)) {
            return minValue;
        }
        return minValue + "." + maxValue;
    }

    private static String normaliseUnbounded(String value) {
        return "unbounded".equalsIgnoreCase(value) ? "*" : value;
    }

    private static String commentString(Element element, String key) {
        return stringValue(comments(element).get(key));
    }

    private static Map<String, Object> comments(Element element) {
        String raw = element == null ? "" : element.getAttribute("comments");
        if (raw == null || raw.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, COMMENT_MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Boomi XMLElement '" + element.getAttribute("name")
                    + "' has invalid comments JSON: " + ex.getMessage(), ex);
        }
    }

    private static String logicalName(Element element) {
        if (element == null) {
            return "";
        }
        String name = element.getTagName();
        int prefix = name == null ? -1 : name.indexOf(':');
        return prefix >= 0 ? name.substring(prefix + 1) : name;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
