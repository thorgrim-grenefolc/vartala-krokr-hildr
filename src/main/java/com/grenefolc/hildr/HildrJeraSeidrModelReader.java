package com.grenefolc.hildr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class HildrJeraSeidrModelReader implements HildrModelReader {

    @Override
    public boolean canRead(Document doc) {
        if (doc == null || doc.getDocumentElement() == null) {
            return false;
        }
        String rootName = logicalName(doc.getDocumentElement());
        return "md".equals(rootName) || "model".equals(rootName) || hasJeraSeidrChild(doc.getDocumentElement());
    }

    @Override
    public HildrModel read(
            Document doc,
            HildrExecutor.LogPackage logPackage
    ) {
        Element root = doc.getDocumentElement();

        HildrModel model = new HildrModel();
        model.setRootName(logicalName(root));

        int ordinal = 0;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                HildrModelNode childNode = readNode((Element) child);
                childNode.setOrdinal(ordinal++);
                model.getTopLevelNodes().add(childNode);
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
        return "jeraSeidr";
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

    private static boolean hasJeraSeidrChild(Element root) {
        if (root == null) {
            return false;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                String n = logicalName((Element) child);
                if ("gr".equals(n) || "sd".equals(n) || "cd".equals(n) || "ed".equals(n)) {
                    return true;
                }
            }
        }
        return false;
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
