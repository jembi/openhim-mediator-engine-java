/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.UntypedActor;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;

import java.io.IOException;
import java.net.URISyntaxException;

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

            CloseableHttpResponse response = httpGet("/basic");
            assertEquals(200, response.getStatusLine().getStatusCode());

            String body = IOUtils.toString(response.getEntity().getContent());
            assertTrue(body.contains("\"body\":\"basic-mediator\""));
            assertTrue(body.contains("\"status\":200"));

            IOUtils.closeQuietly(response);
        } finally {
            server.stop();
        }
    }

    private CloseableHttpResponse httpGet(String path) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(testConfig.getServerHost())
                .setPort(testConfig.getServerPort())
                .setPath(path);
        HttpGet get = new HttpGet(builder.build());

        RequestConfig.Builder reqConf = RequestConfig.custom()
                .setConnectTimeout(1000)
                .setConnectionRequestTimeout(1000);
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(reqConf.build())
                .build();

        CloseableHttpResponse response = client.execute(get);

        boolean foundContentType = false;
        for (Header hdr : response.getAllHeaders()) {
            if ("content-type".equalsIgnoreCase(hdr.getName())) {
                assertEquals("application/json+openhim", hdr.getValue());
                foundContentType = true;
            }
        }
        assertTrue("Content-Type must be included in the response", foundContentType);

        return response;
    }
}