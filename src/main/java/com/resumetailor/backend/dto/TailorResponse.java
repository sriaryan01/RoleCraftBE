package com.resumetailor.backend.dto;

import java.util.List;

public class TailorResponse {

    private String tailoredResume;
    private List<String> changes;
    private String format; // "latex" or "text"

    public TailorResponse() {
    }

    public TailorResponse(String tailoredResume, List<String> changes, String format) {
        this.tailoredResume = tailoredResume;
        this.changes = changes;
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTailoredResume() {
        return tailoredResume;
    }

    public void setTailoredResume(String tailoredResume) {
        this.tailoredResume = tailoredResume;
    }

    public List<String> getChanges() {
        return changes;
    }

    public void setChanges(List<String> changes) {
        this.changes = changes;
    }
}
