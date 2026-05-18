package com.grenefolc.hildr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class HildrBoomiEdiProfileModelReader implements HildrModelReader {

    @Override
    public boolean canRead(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) {
            return false;
        }
        Element root = doc.getDocumentElement();
        if ("profile.edi".equalsIgnoreCase(root.getAttribute("type")) && firstDescendant(root, "EdiProfile") != null) {
            return true;
        }
        return firstDescendant(root, "EdiProfile") != null && firstDescendant(root, "EdiSegment") != null;
    }

    @Override
    public HildrModel read(
            Document doc,
            HildrExecutor.LogPackage logPackage
    ) throws Exception {
        Element profile = firstDescendant(doc.getDocumentElement(), "EdiProfile");
        if (profile == null) {
            throw new IllegalStateException("Native Boomi EDI profile does not contain EdiProfile.");
        }

        Element dataElements = firstChild(profile, "DataElements");
        if (dataElements == null) {
            throw new IllegalStateException("Native Boomi EDI profile does not contain EdiProfile/DataElements.");
        }

        HildrModel model = new HildrModel();
        model.setRootName("md");

        int ordinal = 0;
        for (Node child = dataElements.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            Element element = (Element) child;
            HildrModelNode node = readSupportedNode(element);
            if (node != null) {
                node.setOrdinal(ordinal++);
                model.getTopLevelNodes().add(node);
            }
        }

        if (logPackage != null) {
            logPackage.trace("Model reader produced runtime model: format=" + formatName()
                    + ", root=" + model.getRootName()
                    + ", topLevelNodes=" + model.getTopLevelNodes().size());
        }

        return model;
    }

    @Override
    public String formatName() {
        return "boomiEdiProfile";
    }

    private static HildrModelNode readSupportedNode(Element element) {
        String name = logicalName(element);
        if ("EdiLoop".equals(name)) {
            return readLoop(element);
        }
        if ("EdiSegment".equals(name)) {
            return readSegment(element);
        }
        return null;
    }

    private static HildrModelNode readLoop(Element loop) {
        HildrModelNode node = new HildrModelNode();
        node.setNodeType("gr");
        node.setId(firstNonBlank(loop.getAttribute("name"), loop.getAttribute("loopId"), "Loop" + loop.getAttribute("key")));
        node.setDescription(firstNonBlank(loop.getAttribute("name"), loop.getAttribute("comments"), loop.getAttribute("loopId")));
        node.setOccurs(range(boolMin(loop.getAttribute("mandatory")), firstNonBlank(loop.getAttribute("loopRepeat"), loop.getAttribute("maxUse"), "1")));

        int ordinal = 0;
        for (Node child = loop.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            HildrModelNode childNode = readSupportedNode((Element) child);
            if (childNode != null) {
                childNode.setOrdinal(ordinal++);
                node.getChildren().add(childNode);
            }
        }
        return node;
    }

    private static HildrModelNode readSegment(Element segment) {
        HildrModelNode node = new HildrModelNode();
        node.setNodeType("sd");
        node.setId(firstNonBlank(segment.getAttribute("name"), "Segment" + segment.getAttribute("key")));
        node.setDescription(firstNonBlank(segment.getAttribute("segmentName"), segment.getAttribute("comments"), segment.getAttribute("name")));
        node.setOccurs(range(boolMin(segment.getAttribute("mandatory")), firstNonBlank(segment.getAttribute("maxUse"), "1")));
        node.setRecognitionCode(firstNonBlank(segment.getAttribute("name"), node.getId()));

        int ordinal = 0;
        HildrModelNode sdidNode = createSyntheticSegmentIdentifierNode(node.getId(), node.getRecognitionCode());
        sdidNode.setOrdinal(ordinal++);
        node.getChildren().add(sdidNode);

        HildrModelNode currentComposite = null;

        for (Node child = segment.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element) || !"EdiDataElement".equals(logicalName((Element) child))) {
                continue;
            }

            Element dataElement = (Element) child;
            String compositeMode = dataElement.getAttribute("composite");

            if ("start".equalsIgnoreCase(compositeMode)) {
                currentComposite = createCompositeNode(dataElement);
                currentComposite.setOrdinal(ordinal++);
                currentComposite.getChildren().add(readDataElement(dataElement));
                node.getChildren().add(currentComposite);
            } else if ("comp".equalsIgnoreCase(compositeMode) && currentComposite != null) {
                currentComposite.getChildren().add(readDataElement(dataElement));
            } else {
                currentComposite = null;
                HildrModelNode elementNode = readDataElement(dataElement);
                elementNode.setOrdinal(ordinal++);
                node.getChildren().add(elementNode);
            }
        }

        return node;
    }

    private static HildrModelNode createSyntheticSegmentIdentifierNode(String segmentId, String recognitionCode) {
        String id = firstNonBlank(segmentId, recognitionCode, "Segment") + "_SDID";
        String rc = firstNonBlank(recognitionCode, segmentId);
        String length = String.valueOf(rc.length() > 0 ? rc.length() : 1);

        HildrModelNode node = new HildrModelNode();
        node.setNodeType("ed");
        node.setId(id);
        node.setDescription("Segment identifier");
        node.setOccurs("1");
        node.setLength(length);
        node.setRecognitionCode(rc);
        return node;
    }

    private static HildrModelNode createCompositeNode(Element firstElement) {
        String firstName = firstNonBlank(firstElement.getAttribute("name"), "Composite" + firstElement.getAttribute("key"));
        String compositeId = compositeIdFromFirstLeafName(firstName);

        HildrModelNode node = new HildrModelNode();
        node.setNodeType("cd");
        node.setId(compositeId);
        node.setDescription(firstNonBlank(firstElement.getAttribute("elementPurpose"), firstElement.getAttribute("comments"), compositeId));
        node.setOccurs(range(boolMin(firstElement.getAttribute("mandatory")), firstNonBlank(firstElement.getAttribute("maxUse"), "1")));
        return node;
    }

    private static HildrModelNode readDataElement(Element dataElement) {
        HildrModelNode node = new HildrModelNode();
        node.setNodeType("ed");
        node.setId(firstNonBlank(dataElement.getAttribute("name"), "Element" + dataElement.getAttribute("key")));
        node.setDescription(firstNonBlank(dataElement.getAttribute("elementPurpose"), dataElement.getAttribute("comments"), dataElement.getAttribute("name")));
        node.setOccurs(range(boolMin(dataElement.getAttribute("mandatory")), firstNonBlank(dataElement.getAttribute("maxUse"), "1")));
        node.setLength(range(firstNonBlank(dataElement.getAttribute("minLength"), "1"), firstNonBlank(dataElement.getAttribute("maxLength"), "1")));
        return node;
    }

    private static String compositeIdFromFirstLeafName(String firstLeafName) {
        String value = firstNonBlank(firstLeafName, "Composite");
        int dot = value.indexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static String boolMin(String mandatory) {
        return "true".equalsIgnoreCase(safe(mandatory)) ? "1" : "0";
    }

    private static String range(String min, String max) {
        String a = normaliseOccursMax(safe(min));
        String b = normaliseOccursMax(safe(max));
        if (a.isEmpty() && b.isEmpty()) {
            return "";
        }
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty() || a.equals(b)) {
            return a;
        }
        return a + "." + b;
    }

    private static String normaliseOccursMax(String value) {
        if ("unbounded".equalsIgnoreCase(value) || "-1".equals(value)) {
            return "*";
        }
        return value;
    }

    private static Element firstDescendant(Element root, String localName) {
        if (root == null) {
            return null;
        }
        if (localName.equals(logicalName(root))) {
            return root;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                Element found = firstDescendant((Element) child, localName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Element firstChild(Element root, String localName) {
        if (root == null) {
            return null;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && localName.equals(logicalName((Element) child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String logicalName(Element element) {
        if (element == null) {
            return "";
        }
        String name = element.getTagName();
        int prefix = name == null ? -1 : name.indexOf(':');
        return prefix >= 0 ? name.substring(prefix + 1) : name;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
