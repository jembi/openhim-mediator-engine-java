/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.connectors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.openhim.mediator.engine.MediatorRequestActor;
import org.openhim.mediator.engine.messages.*;

import java.io.InputStream;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class HTTPConnectorTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8200);

    static ActorSystem system;


    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGETRequest() throws Exception {
        stubFor(get(urlEqualTo("/test/get"))
                        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new JavaTestKit(system) {{
            final ActorRef httpConnector = system.actorOf(Props.create(HTTPConnector.class));

            MediatorHTTPRequest GET_Request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "http",
                    "localhost",
                    8200,
                    "/test/get"
            );

            httpConnector.tell(GET_Request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, duration("2 seconds")) {
                        @Override
                        protected Object match(Object msg) throws Exception {
                            if (msg instanceof MediatorHTTPResponse ||
                                    msg instanceof AddOrchestrationToCoreResponse) {
                                return msg;
                            }
                            throw noMatch();
                        }
                    }.get();

            boolean foundResponse = false;
            boolean foundAddOrchestration = false;

            for (Object o : out) {
                if (o instanceof MediatorHTTPResponse) {
                    assertEquals(200, ((MediatorHTTPResponse) o).getStatusCode().intValue());
                    assertEquals("test", ((MediatorHTTPResponse) o).getBody());
                    assertEquals("text/plain", ((MediatorHTTPResponse) o).getHeaders().get("Content-Type"));
                    foundResponse = true;
                } else if (o instanceof AddOrchestrationToCoreResponse) {
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration());
                    assertEquals("unit-test", ((AddOrchestrationToCoreResponse) o).getOrchestration().getName());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getRequest());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse());
                    foundAddOrchestration = true;
                }
            }

            assertTrue("http-connector must send MediatorHTTPResponse", foundResponse);
            assertTrue("http-connector must send AddOrchestrationToCoreResponse", foundAddOrchestration);
            verify(getRequestedFor(urlEqualTo("/test/get")));
        }};
    }

    @Test
    public void testPOSTRequest() throws Exception {
        stubFor(post(urlEqualTo("/test/post"))
                        .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "text/plain"))
        );

        new JavaTestKit(system) {{
            final ActorRef httpConnector = system.actorOf(Props.create(HTTPConnector.class));

            MediatorHTTPRequest POST_Request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "POST",
                    "http",
                    "localhost",
                    8200,
                    "/test/post",
                    "<message>a test message for post</message>",
                    Collections.singletonMap("Content-Type", "text/xml"),
                    null
            );

            httpConnector.tell(POST_Request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, duration("2 seconds")) {
                        @Override
                        protected Object match(Object msg) throws Exception {
                            if (msg instanceof MediatorHTTPResponse ||
                                    msg instanceof AddOrchestrationToCoreResponse) {
                                return msg;
                            }
                            throw noMatch();
                        }
                    }.get();

            boolean foundResponse = false;
            boolean foundAddOrchestration = false;

            for (Object o : out) {
                if (o instanceof MediatorHTTPResponse) {
                    assertEquals(201, ((MediatorHTTPResponse) o).getStatusCode().intValue());
                    foundResponse = true;
                } else if (o instanceof AddOrchestrationToCoreResponse) {
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration());
                    assertEquals("unit-test", ((AddOrchestrationToCoreResponse) o).getOrchestration().getName());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getRequest());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse());
                    foundAddOrchestration = true;
                }
            }

            assertTrue("http-connector must send MediatorHTTPResponse", foundResponse);
            assertTrue("http-connector must send AddOrchestrationToCoreResponse", foundAddOrchestration);
            verify(
                    postRequestedFor(urlEqualTo("/test/post"))
                    .withHeader("Content-Type", equalTo("text/xml"))
                    .withRequestBody(equalTo("<message>a test message for post</message>"))
            );
        }};
    }

    /**
     * Test that application/json+openhim responses from servers are processed correctly
     */
    @Test
    public void testOpenHIMJSONResponse() throws Exception {
        InputStream coreResponseIn = getClass().getClassLoader().getResourceAsStream("core-response.json");
        String coreResponse = IOUtils.toString(coreResponseIn);

        stubFor(get(urlEqualTo("/test/him/json"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", MediatorRequestActor.OPENHIM_MIME_TYPE)
                        .withBody(coreResponse))
        );

        new JavaTestKit(system) {{
            final ActorRef httpConnector = system.actorOf(Props.create(HTTPConnector.class));

            MediatorHTTPRequest GET_Request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "http",
                    "localhost",
                    8200,
                    "/test/him/json"
            );

            httpConnector.tell(GET_Request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, duration("2 seconds")) {
                        @Override
                        protected Object match(Object msg) throws Exception {
                            if (msg instanceof MediatorHTTPResponse ||
                                    msg instanceof AddOrchestrationToCoreResponse ||
                                    msg instanceof PutPropertyInCoreResponse) {
                                return msg;
                            }
                            throw noMatch();
                        }
                    }.get();

            boolean foundResponse = false;
            int foundOrchestrations = 0;
            int foundProperties = 0;

            for (Object o : out) {
                if (o instanceof MediatorHTTPResponse) {
                    assertEquals(200, ((MediatorHTTPResponse) o).getStatusCode().intValue());
                    assertEquals("a test response", ((MediatorHTTPResponse) o).getBody());
                    assertEquals("text/plain", ((MediatorHTTPResponse) o).getHeaders().get("Content-Type"));
                    foundResponse = true;
                } else if (o instanceof AddOrchestrationToCoreResponse) {
                    if ("orch1".equals(((AddOrchestrationToCoreResponse) o).getOrchestration().getName())) {
                        assertEquals(new Integer(201), ((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse().getStatus());
                        foundOrchestrations++;
                    } else if ("orch2".equals(((AddOrchestrationToCoreResponse) o).getOrchestration().getName())) {
                        assertEquals(new Integer(200), ((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse().getStatus());
                        foundOrchestrations++;
                    }
                } else if (o instanceof PutPropertyInCoreResponse) {
                    if ("pro1".equals(((PutPropertyInCoreResponse) o).getName())) {
                        assertEquals("val1", ((PutPropertyInCoreResponse) o).getValue());
                        foundProperties++;
                    } else if ("pro2".equals(((PutPropertyInCoreResponse) o).getName())) {
                        assertEquals("val2", ((PutPropertyInCoreResponse) o).getValue());
                        foundProperties++;
                    }
                }
            }

            assertTrue("http-connector must send MediatorHTTPResponse", foundResponse);
            assertTrue("http-connector must send AddOrchestrationToCoreResponse", foundOrchestrations==2);
            assertTrue("http-connector must send PutPropertyInCoreResponse", foundProperties==2);
            verify(getRequestedFor(urlEqualTo("/test/him/json")));
        }};
    }
}