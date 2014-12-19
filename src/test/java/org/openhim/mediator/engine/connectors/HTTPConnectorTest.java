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
import org.junit.*;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;

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
        stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody("test"))
        );
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGETRequest() throws Exception {
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
                    "/test"
            );

            httpConnector.tell(GET_Request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, duration("1 second")) {
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
                    assertEquals("test", ((MediatorHTTPResponse) o).getContent());
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
            verify(getRequestedFor(urlEqualTo("/test")));
        }};
    }
}