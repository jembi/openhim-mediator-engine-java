/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class MediatorHTTPRequest extends MediatorRequestMessage {
    private final String method;
    private final String uri;
    private final String scheme;
    private final String host;
    private final Integer port;
    private final String path;
    private final String body;
    private final Map<String, String> headers;
    private final Map<String, String> params;

    private MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String uri, String scheme, String host, Integer port, String path, String body,
                               Map<String, String> headers, Map<String, String> params, String correlationId) {
        super(requestHandler, respondTo, orchestration, correlationId);
        this.method = method;
        this.uri = uri;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
        this.body = body;
        this.headers = headers;
        this.params = params;
    }

    /**
     * Construct a new mediator http request using a URI (string)
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String uri, String body, Map<String, String> headers, Map<String, String> params, String correlationId) {
        this(
                requestHandler, respondTo, orchestration, method, uri, null, null, null, null, body, headers, params, correlationId
        );
    }

    /**
     * Construct a new mediator http request using for a particular scheme, host, port and path
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path, String body,
                               Map<String, String> headers, Map<String, String> params, String correlationId) {
        this(
                requestHandler, respondTo, orchestration, method, null, scheme, host, port, path, body, headers, params, correlationId
        );
    }

    /**
     * Construct a new mediator http request using a URI (string)
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String uri, String body, Map<String, String> headers, Map<String, String> params) {
        this(
                requestHandler, respondTo, orchestration, method, uri, null, null, null, null, body, headers, params, null
        );
    }

    /**
     * Construct a new mediator http request using for a particular scheme, host, port and path
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path, String body,
                               Map<String, String> headers, Map<String, String> params) {
        this(
                requestHandler, respondTo, orchestration, method, null, scheme, host, port, path, body, headers, params, null
        );
    }

    /**
     * Construct a new mediator http request using a URI (string)
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration, String method, String uri) {
        this(
                requestHandler, respondTo, orchestration, method, uri, null, null, null, null,
                null, Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), null
        );
    }

    /**
     * Construct a new mediator http request using for a particular scheme, host, port and path
     */
    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path) {
        this(
                requestHandler, respondTo, orchestration, method, null, scheme, host, port, path,
                null, Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), null
        );
    }

    /**
     * Copy constructor
     */
    public MediatorHTTPRequest(MediatorHTTPRequest requestToCopy) {
        this(
                requestToCopy.getRequestHandler(),
                requestToCopy.getRespondTo(),
                requestToCopy.getOrchestration(),
                requestToCopy.getMethod(),
                requestToCopy.getUri(),
                requestToCopy.getScheme(),
                requestToCopy.getHost(),
                requestToCopy.getPort(),
                requestToCopy.getPath(),
                requestToCopy.getBody(),
                copyOfHeaders(requestToCopy.getHeaders()),
                requestToCopy.getParams()!=null ? new HashMap<>(requestToCopy.getParams()) : null,
                requestToCopy.getCorrelationId()
        );
    }

    /**
     * Copy constructor with a different respondTo and correlationId
     */
    public MediatorHTTPRequest(ActorRef respondTo, String correlationId, MediatorHTTPRequest requestToCopy) {
        this(
                requestToCopy.getRequestHandler(),
                respondTo,
                requestToCopy.getOrchestration(),
                requestToCopy.getMethod(),
                requestToCopy.getUri(),
                requestToCopy.getScheme(),
                requestToCopy.getHost(),
                requestToCopy.getPort(),
                requestToCopy.getPath(),
                requestToCopy.getBody(),
                copyOfHeaders(requestToCopy.getHeaders()),
                requestToCopy.getParams()!=null ? new HashMap<>(requestToCopy.getParams()) : null,
                correlationId
        );
    }

    private static Map<String, String> copyOfHeaders(Map<String, String> headers) {
        if (headers==null) {
            return null;
        }
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(headers);
        return copy;
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
