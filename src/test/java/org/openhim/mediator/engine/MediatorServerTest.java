/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.UntypedActor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MediatorServerTest {

    private MediatorConfig testConfig;

    @Before
    public void before() {
        testConfig = new MediatorConfig();
        testConfig.setName("mediator-server-tests");
        testConfig.setServerHost("localhost");
        testConfig.setServerPort(8432);
    }

    private static class BasicMediatorActor extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("/basic", ((MediatorHTTPRequest) msg).getPath());
                assertEquals("GET", ((MediatorHTTPRequest) msg).getMethod());
                assertEquals("http", ((MediatorHTTPRequest) msg).getScheme());

                FinishRequest fr = new FinishRequest("basic-mediator", "text/plain", 200);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    @Test
    public void integrationTest_BasicMediator() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/basic", BasicMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            CloseableHttpResponse response = executeHTTPRequest("GET", "/basic", null, null, null);
            assertEquals(200, response.getStatusLine().getStatusCode());

            String body = IOUtils.toString(response.getEntity().getContent());
            assertTrue(body.contains("\"body\":\"basic-mediator\""));
            assertTrue(body.contains("\"status\":200"));

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }



    private static class POSTMediatorActor extends UntypedActor {
        public static final String TEST_MESSAGE =
                "a post message for testing\na post message for testing\na post message for testing";

        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("/post", ((MediatorHTTPRequest) msg).getPath());
                assertEquals("POST", ((MediatorHTTPRequest) msg).getMethod());
                assertEquals("http", ((MediatorHTTPRequest) msg).getScheme());
                assertEquals(TEST_MESSAGE, ((MediatorHTTPRequest) msg).getBody());

                FinishRequest fr = new FinishRequest(null, "text/plain", 201);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    @Test
    public void integrationTest_POST() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/post", POSTMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            CloseableHttpResponse response = executeHTTPRequest("POST", "/post", POSTMediatorActor.TEST_MESSAGE, null, null);
            assertEquals(201, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }

    private static class POSTBigMediatorActor extends UntypedActor {
        public static String TEST_MESSAGE;
        public static int ROGUE_B_POS = 1024*1024*7 + 1234;

        static {
            StringBuilder msg = new StringBuilder();
            for (int i=0; i<10*1024*1024; i++) {
                msg.append('a');
            }
            //set a rogue 'b' for validation
            msg.setCharAt(ROGUE_B_POS, 'b');

            TEST_MESSAGE = msg.toString();
        }

        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals(TEST_MESSAGE.length(), ((MediatorHTTPRequest) msg).getBody().length());
                assertEquals(TEST_MESSAGE, ((MediatorHTTPRequest) msg).getBody());
                assertEquals('b', ((MediatorHTTPRequest) msg).getBody().charAt(ROGUE_B_POS));

                FinishRequest fr = new FinishRequest(null, "text/plain", 201);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Can the server handle big requests?
     */
    @Test
    public void integrationTest_POST_Big() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/post/big", POSTBigMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            //httpclient will enable chunked transfer
            CloseableHttpResponse response = executeHTTPRequest("POST", "/post/big", POSTBigMediatorActor.TEST_MESSAGE, null, null);
            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }

    private static class PUTMediatorActor extends UntypedActor {
        public static final String TEST_MESSAGE =
                "a put message for testing\na put message for testing\na put message for testing";

        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("/put", ((MediatorHTTPRequest) msg).getPath());
                assertEquals("PUT", ((MediatorHTTPRequest) msg).getMethod());
                assertEquals("http", ((MediatorHTTPRequest) msg).getScheme());
                assertEquals(TEST_MESSAGE, ((MediatorHTTPRequest) msg).getBody());

                FinishRequest fr = new FinishRequest(null, "text/plain", 201);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    @Test
    public void integrationTest_PUT() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/put", PUTMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            CloseableHttpResponse response = executeHTTPRequest("PUT", "/put", PUTMediatorActor.TEST_MESSAGE, null, null);
            assertEquals(201, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }

    private static class HeaderTestMediatorActor extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("value1", ((MediatorHTTPRequest) msg).getHeaders().get("header1"));
                assertEquals("value2", ((MediatorHTTPRequest) msg).getHeaders().get("header2"));
                assertEquals("value3", ((MediatorHTTPRequest) msg).getHeaders().get("header3"));

                FinishRequest fr = new FinishRequest("basic-mediator", "text/plain", 200);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Validates that headers get sent through correctly
     */
    @Test
    public void integrationTest_ValidateHeaders() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/headerTest", HeaderTestMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            Map<String, String> headers = new HashMap<>();
            headers.put("header1", "value1");
            headers.put("header2", "value2");
            headers.put("header3", "value3");
            CloseableHttpResponse response = executeHTTPRequest("GET", "/headerTest", null, headers, null);
            assertEquals(200, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }

    private static class ParamTestMediatorActor extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("param1", ((MediatorHTTPRequest) msg).getParams().get(0).getKey());
                assertEquals("value1", ((MediatorHTTPRequest) msg).getParams().get(0).getValue());
                assertEquals("param2", ((MediatorHTTPRequest) msg).getParams().get(1).getKey());
                assertEquals("value2", ((MediatorHTTPRequest) msg).getParams().get(1).getValue());
                assertEquals("param3", ((MediatorHTTPRequest) msg).getParams().get(2).getKey());
                assertEquals("value3", ((MediatorHTTPRequest) msg).getParams().get(2).getValue());

                FinishRequest fr = new FinishRequest("basic-mediator", "text/plain", 200);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Validates that parameters get sent through correctly
     */
    @Test
    public void integrationTest_ValidateParams() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/paramTest", ParamTestMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            List<Pair<String, String>> params = new ArrayList<>();
            params.add(Pair.of("param1", "value1"));
            params.add(Pair.of("param2", "value2"));
            params.add(Pair.of("param3", "value3"));
            CloseableHttpResponse response = executeHTTPRequest("GET", "/paramTest", null, null, params);
            assertEquals(200, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }


    private static class MultiParamTestMediatorActor extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                assertEquals("param1", ((MediatorHTTPRequest) msg).getParams().get(0).getKey());
                assertEquals("value1", ((MediatorHTTPRequest) msg).getParams().get(0).getValue());
                assertEquals("param1", ((MediatorHTTPRequest) msg).getParams().get(1).getKey());
                assertEquals("value2", ((MediatorHTTPRequest) msg).getParams().get(1).getValue());
                assertEquals("param1", ((MediatorHTTPRequest) msg).getParams().get(2).getKey());
                assertEquals("value3", ((MediatorHTTPRequest) msg).getParams().get(2).getValue());

                FinishRequest fr = new FinishRequest("basic-mediator", "text/plain", 200);
                ((MediatorHTTPRequest) msg).getRequestHandler().tell(fr, getSelf());
            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Validates that multiple parameters with the same name get sent through correctly
     */
    @Test
    public void integrationTest_ValidateMultiParams() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/paramTest", MultiParamTestMediatorActor.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            List<Pair<String, String>> params = new ArrayList<>();
            params.add(Pair.of("param1", "value1"));
            params.add(Pair.of("param1", "value2"));
            params.add(Pair.of("param1", "value3"));
            CloseableHttpResponse response = executeHTTPRequest("GET", "/paramTest", null, null, params);
            assertEquals(200, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }


    private static class TestRuntimeException extends RuntimeException {}

    private static class BadlyBehavedActor_Runtime extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                throw new TestRuntimeException();

            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Server should respond to client with a 500 if an error occurs
     */
    @Test
    public void integrationTest_ErrorHandling_Runtime() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/error/runtime", BadlyBehavedActor_Runtime.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            CloseableHttpResponse response = executeHTTPRequest("GET", "/error/runtime", null, null, null);
            assertEquals(500, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }


    private static class TestCheckedException extends Exception {}

    private static class BadlyBehavedActor_Checked extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                throw new TestCheckedException();

            } else {
                fail("Unexpected message received " + msg);
            }
        }
    }

    /**
     * Server should respond to client with a 500 if an error occurs
     */
    @Test
    public void integrationTest_ErrorHandling_Checked() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/error/runtime", BadlyBehavedActor_Checked.class);
        testConfig.setRoutingTable(table);

        MediatorServer server = new MediatorServer(testConfig);

        try {
            server.start(false);

            CloseableHttpResponse response = executeHTTPRequest("GET", "/error/runtime", null, null, null);
            assertEquals(500, response.getStatusLine().getStatusCode());

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }


    private CloseableHttpResponse executeHTTPRequest(String method, String path, String body, Map<String, String> headers, List<Pair<String, String>> params) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(testConfig.getServerHost())
                .setPort(testConfig.getServerPort())
                .setPath(path);

        if (params!=null) {
            for (Pair<String, String> param : params) {
                builder.addParameter(param.getKey(), param.getValue());
            }
        }

        HttpUriRequest uriReq;
        switch (method) {
            case "GET":
                uriReq = new HttpGet(builder.build());
                break;
            case "POST":
                uriReq = new HttpPost(builder.build());
                StringEntity entity = new StringEntity(body);
                if (body.length()>1024) {
                    //always test big requests chunked
                    entity.setChunked(true);
                }
                ((HttpPost) uriReq).setEntity(entity);
                break;
            case "PUT":
                uriReq = new HttpPut(builder.build());
                StringEntity putEntity = new StringEntity(body);
                ((HttpPut) uriReq).setEntity(putEntity);
                break;
            case "DELETE":
                uriReq = new HttpDelete(builder.build());
                break;
            default:
                throw new UnsupportedOperationException(method + " requests not supported");
        }

        if (headers!=null) {
            Iterator<String> iter = headers.keySet().iterator();
            while (iter.hasNext()) {
                String header = iter.next();
                uriReq.addHeader(header, headers.get(header));
            }
        }

        RequestConfig.Builder reqConf = RequestConfig.custom()
                .setConnectTimeout(1000)
                .setConnectionRequestTimeout(1000);
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(reqConf.build())
                .build();

        CloseableHttpResponse response = client.execute(uriReq);

        boolean foundContentType = false;
        for (Header hdr : response.getAllHeaders()) {
            if ("content-type".equalsIgnoreCase(hdr.getName())) {
                assertTrue(hdr.getValue().contains("application/json+openhim"));
                foundContentType = true;
            }
        }
        assertTrue("Content-Type must be included in the response", foundContentType);

        return response;
    }
}