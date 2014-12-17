package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class RegistrationConfig {
    private String path = "/mediators";
    private String method = "POST";
    private String contentType = "application/json";
    private String content;

    public RegistrationConfig(String content) {
        this.content = content;
    }

    public RegistrationConfig(InputStream content) throws IOException {
        this.content = IOUtils.toString(content);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
