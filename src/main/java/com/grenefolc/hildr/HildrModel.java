package com.grenefolc.hildr;

import java.util.ArrayList;
import java.util.List;

public final class HildrModel {

    private String rootName = "";
    private final List<HildrModelNode> topLevelNodes = new ArrayList<HildrModelNode>();

    public String getRootName() {
        return rootName;
    }

    public void setRootName(String rootName) {
        this.rootName = rootName == null ? "" : rootName;
    }

    public List<HildrModelNode> getTopLevelNodes() {
        return topLevelNodes;
    }
}
