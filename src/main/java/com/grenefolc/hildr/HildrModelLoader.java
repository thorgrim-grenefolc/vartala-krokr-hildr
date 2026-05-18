package com.grenefolc.hildr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class HildrModelLoader {

    private static final HildrModelReader[] READERS = new HildrModelReader[] {
            new HildrBoomiEdiProfileModelReader(),
            new HildrJeraSeidrModelReader()
    };

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

        for (HildrModelReader reader : READERS) {
            if (reader.canRead(doc)) {
                HildrModel model = reader.read(doc, logPackage);
                if (logPackage != null) {
                    logPackage.trace("Model loaded: format=" + reader.formatName()
                            + ", sourceRoot=" + logicalName(root)
                            + ", runtimeRoot=" + model.getRootName()
                            + ", topLevelNodes=" + model.getTopLevelNodes().size());
                }
                return model;
            }
        }

        throw new IllegalStateException("Unsupported Hildr model source format: root=" + logicalName(root));
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
