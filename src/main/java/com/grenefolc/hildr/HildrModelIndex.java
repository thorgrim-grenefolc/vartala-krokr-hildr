package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HildrModelIndex {

    private final Map<String, HildrModelNode> byId = new LinkedHashMap<String, HildrModelNode>();

    public static HildrModelIndex build(HildrModel model) {
        HildrModelIndex index = new HildrModelIndex();
        if (model != null) {
            for (HildrModelNode node : model.getTopLevelNodes()) {
                index.walk(node);
            }
        }
        return index;
    }

    private void walk(HildrModelNode node) {
        if (node == null) {
            return;
        }
        if (node.getId() != null && !node.getId().trim().isEmpty()) {
            byId.put(node.getId(), node);
        }
        for (HildrModelNode child : node.getChildren()) {
            walk(child);
        }
    }

    public Map<String, HildrModelNode> getById() {
        return byId;
    }
}
