/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String urn;

    /**
     * @param content The JSON registration content
     */
    public RegistrationConfig(String content) {
        this.content = content;
    }

    /**
     * @see #RegistrationConfig(String)
     */
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

    /**
     * Reads and returns the mediator URN from the JSON content.
     *
     * @see #RegistrationConfig(String)
     */
    public String getURN() throws InvalidRegistrationContentException {
        if (urn==null) {
            String pattern = "\"urn\"\\s*:\\s*\"(.*)\"";
            Matcher matcher = Pattern.compile(pattern).matcher(content);
            if (matcher.find()) {
                urn = matcher.group(1);
            } else {
                throw new InvalidRegistrationContentException("Failed to parse 'urn'");
            }
        }

        return urn;
    }

    public static class InvalidRegistrationContentException extends Exception {
        public InvalidRegistrationContentException(String message) {
            super(message);
        }
    }
}
