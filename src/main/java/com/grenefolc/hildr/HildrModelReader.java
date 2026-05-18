package com.grenefolc.hildr;

import org.w3c.dom.Document;

interface HildrModelReader {

    boolean canRead(Document doc);

    HildrModel read(
            Document doc,
            HildrExecutor.LogPackage logPackage
    ) throws Exception;

    String formatName();
}
