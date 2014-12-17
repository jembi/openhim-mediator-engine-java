package org.openhim.mediator.engine.messages;

import akka.actor.ActorRef;

import java.util.Collections;
import java.util.Map;

public class MediatorHTTPRequest extends MediatorRequestMessage {
    private final String orchestration;
    private final String method;
    private final String scheme;
    private final String host;
    private final Integer port;
    private final String path;
    private final String body;
    private final Map<String, String> headers;
    private final Map<String, String> params;

    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path, String body,
                               Map<String, String> headers, Map<String, String> params, String correlationId) {
        super(correlationId, requestHandler, respondTo);
        this.orchestration = orchestration;
        this.method = method;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
        this.body = body;
        this.headers = headers;
        this.params = params;
    }

    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path, String body,
                               Map<String, String> headers, Map<String, String> params) {
        this(
                requestHandler, respondTo, orchestration, method, scheme, host, port, path, body, headers, params, null
        );
    }

    public MediatorHTTPRequest(ActorRef requestHandler, ActorRef respondTo, String orchestration,
                               String method, String scheme, String host, Integer port, String path) {
        this(
                requestHandler, respondTo, orchestration, method, scheme, host, port, path,
                null, Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), null
        );
    }


    public String getOrchestration() {
        return orchestration;
    }

    public String getMethod() {
        return method;
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
