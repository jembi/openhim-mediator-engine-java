package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Configuration required for registering the mediator with core.
 * <br/><br/>
 * At a minimum only the JSON registration content is required,
 * however other values (such as the API path) are available if they need to be overwritten.
 * <br/><br/>
 * See <a href="https://github.com/jembi/openhim-core-js/wiki/Creating-an-OpenHIM-mediator">the core documentation</a>
 */
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
