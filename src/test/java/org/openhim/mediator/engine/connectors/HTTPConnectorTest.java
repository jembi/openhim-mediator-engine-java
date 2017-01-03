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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.MediatorRequestHandler;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.engine.messages.PutPropertyInCoreResponse;
import org.openhim.mediator.engine.messages.SetupSSLContext;
import org.openhim.mediator.engine.messages.SetupSSLContextResponse;

import java.io.InputStream;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HTTPConnectorTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    @Rule
    public WireMockRule wireMockRuleHTTPS1 = new WireMockRule(
            wireMockConfig()
                    .dynamicPort()
                    .dynamicHttpsPort()
                    .keystorePath("src/test/resources/certs/localhost.jks")
    );
    @Rule
    public WireMockRule wireMockRuleHTTPS2 = new WireMockRule(
            wireMockConfig()
                    .dynamicPort()
                    .dynamicHttpsPort()
                    .keystorePath("src/test/resources/certs/localhost.jks")
                    .needClientAuth(true)
                    .trustStorePath("src/test/resources/certs/client.jks")
    );

    private abstract class HTTPConnectorTestKit extends JavaTestKit {
        protected final ActorRef httpConnector;

        public HTTPConnectorTestKit(ActorSystem actorSystem) {
            super(actorSystem);

            httpConnector = system.actorOf(Props.create(HTTPConnector.class));
        }

        protected void testHTTPMessage(MediatorHTTPRequest msg, int expectedStatus, String expectedContentType, String expectedBody) {
            httpConnector.tell(msg, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, dilated(duration("1 second"))) {
                        @Override
                        protected Object match(Object msg) throws Exception {
                            if (msg instanceof MediatorHTTPResponse ||
                                    msg instanceof AddOrchestrationToCoreResponse ||
                                    msg instanceof ExceptError) {
                                return msg;
                            }
                            throw noMatch();
                        }
                    }.get();

            boolean foundResponse = false;
            boolean foundAddOrchestration = false;

            for (Object o : out) {
                if (o instanceof MediatorHTTPResponse) {
                    assertEquals(expectedStatus, ((MediatorHTTPResponse) o).getStatusCode().intValue());
                    assertEquals(expectedBody, ((MediatorHTTPResponse) o).getBody());
                    assertEquals(expectedContentType, ((MediatorHTTPResponse) o).getHeaders().get("Content-Type"));
                    foundResponse = true;
                } else if (o instanceof AddOrchestrationToCoreResponse) {
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration());
                    assertEquals("unit-test", ((AddOrchestrationToCoreResponse) o).getOrchestration().getName());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getRequest());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse());
                    foundAddOrchestration = true;
                } else if (o instanceof ExceptError) {
                    ((ExceptError) o).getError().printStackTrace();
                    fail("Unexpected error: " + ((ExceptError) o).getError().getMessage());
                }
            }

            assertTrue("http-connector must send MediatorHTTPResponse", foundResponse);
            assertTrue("http-connector must send AddOrchestrationToCoreResponse", foundAddOrchestration);
        }
    }

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
        wireMockRule.stubFor(get(urlEqualTo("/test/get"))
                        .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "http",
                    "localhost",
                    wireMockRule.port(),
                    "/test/get"
            ), 200, "text/plain", "test");

            wireMockRule.verify(getRequestedFor(urlEqualTo("/test/get")));
        }};
    }

    @Test
    public void testPOSTRequest() throws Exception {
        wireMockRule.stubFor(post(urlEqualTo("/test/post"))
                        .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "text/plain").withBody("Created"))
        );

        new HTTPConnectorTestKit(system) {{
            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "POST",
                    "http",
                    "localhost",
                    wireMockRule.port(),
                    "/test/post",
                    "<message>a test message for post</message>",
                    Collections.singletonMap("Content-Type", "text/xml"),
                    null
            ), 201, "text/plain", "Created");

            wireMockRule.verify(
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

        wireMockRule.stubFor(get(urlEqualTo("/test/him/json"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", MediatorRequestHandler.OPENHIM_MIME_TYPE)
                        .withBody(coreResponse))
        );

        new HTTPConnectorTestKit(system) {{
            MediatorHTTPRequest GET_Request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "http",
                    "localhost",
                    wireMockRule.port(),
                    "/test/him/json"
            );

            httpConnector.tell(GET_Request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, dilated(duration("1 second"))) {
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
            wireMockRule.verify(getRequestedFor(urlEqualTo("/test/him/json")));
        }};
    }

    @Test
    public void testGETRequestWithURI() throws Exception {
        wireMockRule.stubFor(get(urlEqualTo("/test/get/with/uri"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "http://localhost:" + wireMockRule.port() + "/test/get/with/uri"
            ), 200, "text/plain", "test");

            wireMockRule.verify(getRequestedFor(urlEqualTo("/test/get/with/uri")));
        }};
    }

    @Test
    public void testBasicHTTPS() throws Exception {
        wireMockRuleHTTPS1.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/localhost.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS1.httpsPort(),
                    "/test/get"
            ), 200, "text/plain", "test");

            wireMockRuleHTTPS1.verify(getRequestedFor(urlEqualTo("/test/get")));
        }};
    }

    /**
     * Should fail if cert not in truststore
     */
    @Test
    public void testBasicHTTPS_Untrusted() throws Exception {
        wireMockRuleHTTPS1.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            // load 'other' not 'localhost'
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/other.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            MediatorHTTPRequest request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS1.httpsPort(),
                    "/test/get"
            );
            httpConnector.tell(request, getRef());

            ExceptError error = expectMsgClass(ExceptError.class);
            assertNotNull(error);
        }};
    }

    /**
     * Should fail if no cert in truststore
     */
    @Test
    public void testBasicHTTPS_UntrustedNone() throws Exception {
        wireMockRuleHTTPS1.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(false)
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            MediatorHTTPRequest request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS1.httpsPort(),
                    "/test/get"
            );
            httpConnector.tell(request, getRef());

            ExceptError error = expectMsgClass(ExceptError.class);
            assertNotNull(error);
        }};
    }

    @Test
    public void testBasicHTTPS_TrustAll() throws Exception {
        wireMockRuleHTTPS1.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(true)
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS1.httpsPort(),
                    "/test/get"
            ), 200, "text/plain", "test");

            wireMockRuleHTTPS1.verify(getRequestedFor(urlEqualTo("/test/get")));
        }};
    }

    @Test
    public void testMutualAuthHTTPS() throws Exception {
        wireMockRuleHTTPS2.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/client.jks", "password"),
                            new MediatorConfig.KeyStore("src/test/resources/certs/localhost.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            testHTTPMessage(new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS2.httpsPort(),
                    "/test/get"
            ), 200, "text/plain", "test");

            wireMockRuleHTTPS2.verify(getRequestedFor(urlEqualTo("/test/get")));
        }};
    }

    /**
     * Should fail if no client cert used
     */
    @Test
    public void testMutualAuthHTTPS_None() throws Exception {
        wireMockRuleHTTPS2.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/localhost.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            MediatorHTTPRequest request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS2.httpsPort(),
                    "/test/get"
            );
            httpConnector.tell(request, getRef());

            ExceptError error = expectMsgClass(ExceptError.class);
            assertNotNull(error);
        }};
    }

    /**
     * Should fail if incorrect client cert used
     */
    @Test
    public void testMutualAuthHTTPS_Incorrect() throws Exception {
        wireMockRuleHTTPS2.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/other.jks", "password"),
                            new MediatorConfig.KeyStore("src/test/resources/certs/localhost.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            MediatorHTTPRequest request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS2.httpsPort(),
                    "/test/get"
            );
            httpConnector.tell(request, getRef());

            ExceptError error = expectMsgClass(ExceptError.class);
            assertNotNull(error);
        }};
    }

    /**
     * Should fail if server cert not in truststore
     *
     * (Test of {@link #testBasicHTTPS_Untrusted()} under mutual auth conditions)
     */
    @Test
    public void testMutualAuthHTTPS_Untrusted() throws Exception {
        wireMockRuleHTTPS2.stubFor(get(urlEqualTo("/test/get"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );

        new HTTPConnectorTestKit(system) {{
            SetupSSLContext setupSSLContext = new SetupSSLContext(
                    getRef(),
                    getRef(),
                    new MediatorConfig.SSLContext(
                            new MediatorConfig.KeyStore("src/test/resources/certs/client.jks", "password"),
                            new MediatorConfig.KeyStore("src/test/resources/certs/other.jks", "password")
                    )
            );

            httpConnector.tell(setupSSLContext, getRef());
            SetupSSLContextResponse setupSSLContextResponse = expectMsgClass(SetupSSLContextResponse.class);
            assertTrue(setupSSLContextResponse.isSuccessful());

            MediatorHTTPRequest request = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "unit-test",
                    "GET",
                    "https",
                    "localhost",
                    wireMockRuleHTTPS2.httpsPort(),
                    "/test/get"
            );
            httpConnector.tell(request, getRef());

            ExceptError error = expectMsgClass(ExceptError.class);
            assertNotNull(error);
        }};
    }
}