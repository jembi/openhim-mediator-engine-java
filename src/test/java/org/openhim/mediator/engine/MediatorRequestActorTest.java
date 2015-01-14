/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import fi.iki.elonen.NanoHTTPD;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.engine.messages.*;
import org.openhim.mediator.engine.testing.MockHTTPConnector;
import org.openhim.mediator.engine.testing.MockLauncher;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class MediatorRequestActorTest {
    /**
     * Mocks a mediator route receiver like someone using the engine will implement in order to receive requests.
     */
    private abstract static class MockRouteActor extends UntypedActor {
        public abstract void executeOnReceive(MediatorHTTPRequest msg);

        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorHTTPRequest) {
                executeOnReceive((MediatorHTTPRequest) msg);
            } else {
                fail("MediatorRequestActor should never send any messages to a route other than MediatorHTTPRequest");
            }
        }
    }

    static ActorSystem system;
    MediatorConfig testConfig;

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
    public void before() throws IOException {
        testConfig = new MediatorConfig();
        testConfig.setName("request-actor-tests");

        InputStream regInfo = getClass().getClassLoader().getResourceAsStream("test-registration-info.json");
        testConfig.setRegistrationConfig(new RegistrationConfig(regInfo));
    }

    private static class BasicRoutingMock extends MockRouteActor {
        @Override
        public void executeOnReceive(MediatorHTTPRequest msg) {
            assertEquals("http", msg.getScheme());
            assertEquals("GET", msg.getMethod());
            assertEquals("/test", msg.getPath());

            FinishRequest fr = new FinishRequest("basic-routing", "text/plain", HttpStatus.SC_OK);
            msg.getRequestHandler().tell(fr, getSelf());
        }
    }

    @Test
    public void testMessage_BasicRouting() throws Exception {
        new JavaTestKit(system) {{
            RoutingTable table = new RoutingTable();
            table.addRoute("/test", BasicRoutingMock.class);
            testConfig.setRoutingTable(table);

            TestActorRef<MediatorRequestActor> actor = TestActorRef.create(system, Props.create(MediatorRequestActor.class, testConfig));
            NanoHTTPD.IHTTPSession testSession = buildMockIHTTPSession(actor, "/test", NanoHTTPD.Method.GET, Collections.<String, String>emptyMap());
            actor.tell(testSession, getRef());

            NanoHTTPD.Response response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), NanoHTTPD.Response.class);

            String body = IOUtils.toString(response.getData());
            assertTrue(body.contains("\"body\":\"basic-routing\""));
            assertTrue(body.contains("\"status\":200"));
        }};
    }

    private static class AsyncRoutingMock extends MockRouteActor {
        @Override
        public void executeOnReceive(MediatorHTTPRequest msg) {
            //accepted async
            msg.getRequestHandler().tell(new AcceptedAsyncRequest(), getSelf());

            //end request
            FinishRequest fr = new FinishRequest("async-routing", "text/plain", HttpStatus.SC_OK);
            msg.getRequestHandler().tell(fr, getSelf());
        }
    }

    private static class MockCoreAPI extends MockHTTPConnector {
        @Override
        public String getResponse() {
            return "Updated";
        }

        @Override
        public Integer getStatus() {
            return 200;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public void executeOnReceive(MediatorHTTPRequest req) {
            assertEquals("/transactions/test-async", req.getPath());
            assertEquals("PUT", req.getMethod());
        }
    }

    @Test
    public void testMessage_AsyncRouting() throws Exception {
        new JavaTestKit(system) {{
            //mock core api
            MockLauncher.ActorToLaunch mockCoreAPI = new MockLauncher.ActorToLaunch("core-api-connector", MockCoreAPI.class);
            MockLauncher.launchActors(system, testConfig.getName(), Collections.singletonList(mockCoreAPI));

            //route to mock receiver
            RoutingTable table = new RoutingTable();
            table.addRoute("/test", AsyncRoutingMock.class);
            testConfig.setRoutingTable(table);

            //route request
            TestActorRef<MediatorRequestActor> actor = TestActorRef.create(system, Props.create(MediatorRequestActor.class, testConfig));
            assertFalse(actor.underlyingActor().async);

            NanoHTTPD.IHTTPSession testSession = buildMockIHTTPSession(actor, "/test", NanoHTTPD.Method.GET, Collections.singletonMap("X-OpenHIM-TransactionID", "test-async"));
            actor.tell(testSession, getRef());

            //get response
            NanoHTTPD.Response response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), NanoHTTPD.Response.class);

            assertTrue("Async should be enabled", actor.underlyingActor().async);
            assertEquals("Core transaction ID should be set", "test-async", actor.underlyingActor().coreTransactionID);

            //request handler should respond with 202 Accepted
            String body = IOUtils.toString(response.getData());
            assertTrue(body.contains("\"status\":202"));

            //but update response token with final result
            assertNotNull(actor.underlyingActor().response);
            assertNotNull(actor.underlyingActor().response.getResponse());
            assertEquals(new Integer(200), actor.underlyingActor().response.getResponse().getStatus());
            assertEquals("async-routing", actor.underlyingActor().response.getResponse().getBody());

            MockLauncher.clearActors(system, testConfig.getName());
        }};
    }

    private static class ErrorRoutingMock extends MockRouteActor {
        public static class TestException extends Exception {
            public TestException() {
                super("test-exception (this is expected)");
            }
        }

        @Override
        public void executeOnReceive(MediatorHTTPRequest msg) {
            try {
                throw new TestException();
            } catch (TestException ex) {
                msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
            }
        }
    }

    @Test
    public void testMessage_ExceptError() throws Exception {
        new JavaTestKit(system) {{
            RoutingTable table = new RoutingTable();
            table.addRoute("/test", ErrorRoutingMock.class);
            testConfig.setRoutingTable(table);

            TestActorRef<MediatorRequestActor> actor = TestActorRef.create(system, Props.create(MediatorRequestActor.class, testConfig));
            NanoHTTPD.IHTTPSession testSession = buildMockIHTTPSession(actor, "/test", NanoHTTPD.Method.GET, Collections.<String, String>emptyMap());
            actor.tell(testSession, getRef());

            NanoHTTPD.Response response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), NanoHTTPD.Response.class);

            String body = IOUtils.toString(response.getData());
            assertTrue("The exception message should be returned in the body", body.contains("\"body\":\"test-exception (this is expected)\""));
            assertTrue("Expect status 500 Internal Server Error", body.contains("\"status\":500"));
        }};
    }

    @Test
    public void testMessage_AddOrchestrationToCoreResponse() throws Exception {
        new JavaTestKit(system) {{
            TestActorRef<MediatorRequestActor> actor = TestActorRef.create(system, Props.create(MediatorRequestActor.class, testConfig));

            CoreResponse.Orchestration testOrch = new CoreResponse.Orchestration();
            testOrch.setName("unit-test");

            actor.tell(new AddOrchestrationToCoreResponse(testOrch), getRef());
            expectNoMsg(Duration.create(500, TimeUnit.MILLISECONDS));

            assertNotNull(actor.underlyingActor().response);
            assertNotNull(actor.underlyingActor().response.getOrchestrations());
            assertEquals("unit-test", actor.underlyingActor().response.getOrchestrations().get(0).getName());
        }};
    }

    @Test
    public void testMessage_PutPropertyInCoreResponse() throws Exception {
        new JavaTestKit(system) {{
            TestActorRef<MediatorRequestActor> actor = TestActorRef.create(system, Props.create(MediatorRequestActor.class, testConfig));

            actor.tell(new PutPropertyInCoreResponse("test-property", "test-value"), getRef());
            expectNoMsg(Duration.create(500, TimeUnit.MILLISECONDS));

            assertNotNull(actor.underlyingActor().response);
            assertNotNull(actor.underlyingActor().response.getProperties());
            assertEquals("test-value", actor.underlyingActor().response.getProperties().get("test-property"));
        }};
    }


    /**
     * Mocks a received HTTP request
     */
    private NanoHTTPD.IHTTPSession buildMockIHTTPSession(final ActorRef requestHandler, final String path, final NanoHTTPD.Method method, final Map<String, String> headers) {
        return new NanoHTTPD.IHTTPSession() {
            @Override
            public void execute() throws IOException {}

            @Override
            public Map<String, String> getParms() {
                return null;
            }

            @Override
            public Map<String, String> getHeaders() {
                return headers;
            }

            @Override
            public String getUri() {
                return path;
            }

            @Override
            public String getQueryParameterString() {
                return null;
            }

            @Override
            public NanoHTTPD.Method getMethod() {
                return method;
            }

            @Override
            public InputStream getInputStream() {
                return null;
            }

            @Override
            public NanoHTTPD.CookieHandler getCookies() {
                return null;
            }

            @Override
            public void parseBody(Map<String, String> files) throws IOException, NanoHTTPD.ResponseException {

            }

            @Override
            public ActorRef getRequestActor() {
                return requestHandler;
            }
        };
    }
}