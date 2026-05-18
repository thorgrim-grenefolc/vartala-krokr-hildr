package com.grenefolc.hildr;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HildrEventSupport {

    private String category = "";
    private String title = "";
    private String subject = "";
    private String buffer = "";
    private String hint = "";

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = safe(category); }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = safe(title); }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = safe(subject); }

    public String getBuffer() { return buffer; }
    public void setBuffer(String buffer) { this.buffer = safe(buffer); }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = safe(hint); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("category", category);
        map.put("title", title);
        map.put("subject", subject);
        if (!buffer.isEmpty()) {
            map.put("buffer", buffer);
        }
        map.put("hint", hint);
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
