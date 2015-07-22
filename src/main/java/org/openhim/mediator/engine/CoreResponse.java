/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The OpenHIM Core response message
 */
public class CoreResponse implements Serializable {
    private static final long serialVersionUID = 792029209702844889L;

    public static class Request implements Serializable {
        private static final long serialVersionUID = 8237061791963580185L;

        private String host;
        private String port;
        private String path;
        private Map<String, String> headers = new HashMap<String, String>();
        private String queryString;
        private String body;
        private String method;
        private Date timestamp = new Date();

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public void putHeader(String name, String value) {
            headers.put(name, value);
        }

        public String getQueryString() {
            return queryString;
        }

        public void setQueryString(String queryString) {
            this.queryString = queryString;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class Response implements Serializable {
        private static final long serialVersionUID = -2910898384372979044L;

        private Integer status;
        private Map<String, String> headers = new HashMap<String, String>();
        private String body;
        private Date timestamp = new Date();

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public void putHeader(String name, String value) {
            headers.put(name, value);
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class Orchestration implements Serializable {
        private static final long serialVersionUID = -5774459111752593383L;

        private String name;
        private Request request;
        private Response response;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Request getRequest() {
            return request;
        }

        public void setRequest(Request request) {
            this.request = request;
        }

        public Response getResponse() {
            return response;
        }

        public void setResponse(Response response) {
            this.response = response;
        }
    }

    @SerializedName("x-mediator-urn")
    private String urn;
    private String status;
    private Response response;
    private List<Orchestration> orchestrations = new ArrayList<Orchestration>();
    private Map<String, String> properties = new HashMap<String, String>();


    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Get a descriptive status string based on the status code:
     * 'Successful', 'Completed', 'Completed with error(s)' or 'Failed'
     */
    public String getDescriptiveStatus() {
        if (response!=null && response.getStatus()!=null) {
            if (response.getStatus()>=500 && response.getStatus()<600) {
                return "Failed";
            } else if (response.getStatus()>=400 && response.getStatus()<500) {
                return "Completed";
            } else if (response.getStatus()>=200 && response.getStatus()<300) {
                if (orchestrations!=null) {
                    for (Orchestration orch : orchestrations) {
                        if (orch.getResponse()!=null && orch.getResponse().getStatus()!=null) {
                            if (orch.getResponse().getStatus()>=400 && orch.getResponse().getStatus()<600) {
                                return "Completed with error(s)";
                            }
                        }
                    }
                }
            }
        }
        return "Successful";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public List<Orchestration> getOrchestrations() {
        return orchestrations;
    }

    public void setOrchestrations(List<Orchestration> orchestrations) {
        this.orchestrations = orchestrations;
    }

    public void addOrchestration(Orchestration orchestration) {
        orchestrations.add(orchestration);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void putProperty(String name, String value) {
        properties.put(name, value);
    }

    public String toJSON() {
        return new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create().toJson(this);
    }

    @Override
    public String toString() {
        return toJSON();
    }


    public static CoreResponse parse(String content) throws ParseException {
        try {
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(content, CoreResponse.class);
        } catch (JsonParseException ex) {
            throw new ParseException(ex);
        }
    }

    public static class ParseException extends Exception {
        public ParseException(Throwable cause) {
            super(cause);
        }
    }
}
