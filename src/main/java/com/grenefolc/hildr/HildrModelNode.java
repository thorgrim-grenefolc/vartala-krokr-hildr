package com.grenefolc.hildr;

import java.util.ArrayList;
import java.util.List;

public final class HildrModelNode {

    private String nodeType = "";
    private String id = "";
    private String description = "";
    private String occurs = "";
    private String length = "";
    private String recognitionCode = "";

    private String entityName = "";
    private long minOccurs = 1L;
    private long maxOccurs = 1L;
    private long minLength = 1L;
    private long maxLength = 1L;
    private String recognitionRegex = "";

    private long occursCount = 0L;
    private boolean instantiated = false;
    private boolean printed = false;
    private boolean confirmed = false;
    private int ordinal = -1;

    private final List<HildrModelNode> children = new ArrayList<HildrModelNode>();

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType == null ? "" : nodeType; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null ? "" : id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }

    public String getOccurs() { return occurs; }
    public void setOccurs(String occurs) { this.occurs = occurs == null ? "" : occurs; }

    public String getLength() { return length; }
    public void setLength(String length) { this.length = length == null ? "" : length; }

    public String getRecognitionCode() { return recognitionCode; }
    public void setRecognitionCode(String recognitionCode) { this.recognitionCode = recognitionCode == null ? "" : recognitionCode; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName == null ? "" : entityName; }

    public long getMinOccurs() { return minOccurs; }
    public void setMinOccurs(long minOccurs) { this.minOccurs = minOccurs; }

    public long getMaxOccurs() { return maxOccurs; }
    public void setMaxOccurs(long maxOccurs) { this.maxOccurs = maxOccurs; }

    public long getMinLength() { return minLength; }
    public void setMinLength(long minLength) { this.minLength = minLength; }

    public long getMaxLength() { return maxLength; }
    public void setMaxLength(long maxLength) { this.maxLength = maxLength; }

    public String getRecognitionRegex() { return recognitionRegex; }
    public void setRecognitionRegex(String recognitionRegex) { this.recognitionRegex = recognitionRegex == null ? "" : recognitionRegex; }

    public long getOccursCount() { return occursCount; }
    public void setOccursCount(long occursCount) { this.occursCount = occursCount; }

    public boolean isInstantiated() { return instantiated; }
    public void setInstantiated(boolean instantiated) { this.instantiated = instantiated; }

    public boolean isPrinted() { return printed; }
    public void setPrinted(boolean printed) { this.printed = printed; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public List<HildrModelNode> getChildren() { return children; }
}
