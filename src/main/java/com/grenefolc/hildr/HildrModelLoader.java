package com.grenefolc.hildr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class HildrModelLoader {

    private HildrModelLoader() {
    }

    public static HildrModel load(
            String modelXml,
            HildrExecutor.LogPackage logPackage
    ) throws Exception {

        if (modelXml == null || modelXml.trim().isEmpty()) {
            throw new IllegalStateException("ddp_hildr_modelXml is missing or blank.");
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);

        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Throwable ignored) {
            // parser support varies
        }

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(modelXml.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();

        if (HildrBoomiXmlProfileModelReader.canRead(doc)) {
            HildrModel model = HildrBoomiXmlProfileModelReader.read(doc, logPackage);
            logPackage.trace("Model loaded: format=boomiXmlProfile, root=" + model.getRootName()
                    + ", topLevelNodes=" + model.getTopLevelNodes().size());
            return model;
        }

        HildrModel model = new HildrModel();
        model.setRootName(logicalName(root));

        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                model.getTopLevelNodes().add(readNode((Element) child));
            }
        }

        logPackage.trace("Model loaded: format=jeraSeidr, root=" + model.getRootName()
                + ", topLevelNodes=" + model.getTopLevelNodes().size());

        return model;
    }

    private static HildrModelNode readNode(Element element) {
        HildrModelNode node = new HildrModelNode();
        node.setNodeType(logicalName(element));
        node.setId(element.getAttribute("id"));
        node.setDescription(element.getAttribute("ds"));
        node.setOccurs(element.getAttribute("oc"));
        node.setLength(element.getAttribute("ln"));
        node.setRecognitionCode(element.getAttribute("rc"));

        int ordinal = 0;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                HildrModelNode childNode = readNode((Element) child);
                childNode.setOrdinal(ordinal++);
                node.getChildren().add(childNode);
            }
        }

        return node;
    }

    private static String logicalName(Element element) {
        if (element == null) {
            return "";
        }
        String name = element.getTagName();
        int prefix = name == null ? -1 : name.indexOf(':');
        return prefix >= 0 ? name.substring(prefix + 1) : name;
    }
}
