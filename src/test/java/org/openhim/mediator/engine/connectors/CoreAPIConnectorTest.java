/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.connectors;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import com.google.gson.GsonBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.RegistrationConfig;
import org.openhim.mediator.engine.RegistrationConfigTest;
import org.openhim.mediator.engine.messages.*;
import org.openhim.mediator.engine.testing.MockHTTPConnector;
import org.openhim.mediator.engine.testing.TestingUtils;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CoreAPIConnectorTest {
    private static class HeartbeatRequest {
        long uptime;
        boolean config = false;
    }

    private static class CoreAPITestMock extends MockHTTPConnector {
        String response;
        Integer status;
        Map<String, String> headers;

        @Override
        public String getResponse() {
            return response;
        }

        @Override
        public Integer getStatus() {
            return status;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }

        private void handleAuth() {
            response = "{\"salt\":\"theSaltUsedForTheTest\",\"ts\":\"2015-01-16T13:00:53.418Z\"}";
            status = 200;
            headers = Collections.singletonMap("Content-Type", "application/json");
        }

        private void handleInvalidAuth() {
            response = "Could not find user by email nosuch@openhim.org";
            status = 404;
            headers = Collections.singletonMap("Content-Type", "text/plain");
        }

        private void handleHeartbeat(MediatorHTTPRequest req) {
            HeartbeatRequest hbReq = new GsonBuilder().create().fromJson(req.getBody(), HeartbeatRequest.class);
            assertEquals(3600, hbReq.uptime);

            response = hbReq.config ? "{\"Setting 1\":\"New Setting 1\"}" : "";

            status = 200;
            headers = Collections.singletonMap("Content-Type", "application/json");
        }

        private void handleDestination(MediatorHTTPRequest req) {
            response = "a test response";
            status = 200;
            headers = Collections.singletonMap("Content-Type", "text/plain");

            assertEquals("test@openhim.org", req.getHeaders().get("auth-username"));
            assertNotNull(req.getHeaders().get("auth-ts"));
            assertNotNull(req.getHeaders().get("auth-salt"));
            assertNotNull(req.getHeaders().get("auth-token"));

            try {
                String passHash = CoreAPIConnector.hash("theSaltUsedForTheTest" + "password");
                String expectedToken = CoreAPIConnector.hash(passHash + req.getHeaders().get("auth-salt") + req.getHeaders().get("auth-ts"));

                assertEquals("Expected correct auth-token", expectedToken, req.getHeaders().get("auth-token"));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                fail();
            }
        }

        @Override
        public void executeOnReceive(MediatorHTTPRequest req) {
            if ("/authenticate/test@openhim.org".equals(req.getPath())) {
                handleAuth();

            } else if ("/authenticate/nosuchuser@openhim.org".equals(req.getPath())) {
                handleInvalidAuth();

            } else if ("/mediators/urn:mediator:test.mediator/heartbeat".equals(req.getPath())) {
                handleHeartbeat(req);

            } else if ("/destination".equals(req.getPath())) {
                handleDestination(req);

            } else {
                fail("Unexpected HTTP request sent: " + req.getPath());
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
        testConfig.setCoreAPIUsername("test@openhim.org");
        testConfig.setCoreAPIPassword("password");

        InputStream regConf = RegistrationConfigTest.class.getClassLoader().getResourceAsStream("test-registration-info-w-config.json");
        RegistrationConfig config = new RegistrationConfig(regConf);
        testConfig.setRegistrationConfig(config);
    }


    @Test
    public void testCoreAPIConnector() {
        new JavaTestKit(system) {{
            TestingUtils.launchMockHTTPConnector(system, testConfig.getName(), CoreAPITestMock.class);
            TestActorRef<CoreAPIConnector> actor = TestActorRef.create(system, Props.create(CoreAPIConnector.class, testConfig));

            MediatorHTTPRequest testMsg = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "core-api-connector-test",
                    "GET",
                    "https",
                    "localhost",
                    8080,
                    "/destination"
            );
            actor.tell(testMsg, getRef());

            MediatorHTTPResponse response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), MediatorHTTPResponse.class);

            assertEquals(new Integer(200), response.getStatusCode());
            assertEquals("a test response", response.getBody());
            assertEquals("text/plain", response.getHeaders().get("Content-Type"));

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }

    @Test
    public void testInvalidUser() {
        new JavaTestKit(system) {{
            TestingUtils.launchMockHTTPConnector(system, testConfig.getName(), CoreAPITestMock.class);
            testConfig.setCoreAPIUsername("nosuchuser@openhim.org");

            TestActorRef<CoreAPIConnector> actor = TestActorRef.create(system, Props.create(CoreAPIConnector.class, testConfig));

            MediatorHTTPRequest testMsg = new MediatorHTTPRequest(
                    getRef(),
                    getRef(),
                    "core-api-connector-test",
                    "GET",
                    "https",
                    "localhost",
                    8080,
                    "/destination"
            );
            actor.tell(testMsg, getRef());

            ExceptError response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), ExceptError.class);
            assertNotNull(response.getError());
            assertTrue(response.getError() instanceof CoreAPIConnector.CoreGetAuthenticationDetailsError);

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }

    @Test
    public void testSendHeartbeatToCore() {
        new JavaTestKit(system) {{
            TestingUtils.launchMockHTTPConnector(system, testConfig.getName(), CoreAPITestMock.class);
            TestActorRef<CoreAPIConnector> actor = TestActorRef.create(system, Props.create(CoreAPIConnector.class, testConfig));

            SendHeartbeatToCore testMsg = new SendHeartbeatToCore(3600);
            actor.tell(testMsg, getRef());

            SendHeartbeatToCoreResponse response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), SendHeartbeatToCoreResponse.class);
            assertTrue(response.requestSucceeded());
            assertFalse(response.receivedConfigUpdate());
            assertEquals("", response.getRawResponse());
            assertNull(response.getConfig());

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }

    @Test
    public void testSendHeartbeatToCore_wConfigForce() {
        new JavaTestKit(system) {{
            TestingUtils.launchMockHTTPConnector(system, testConfig.getName(), CoreAPITestMock.class);
            TestActorRef<CoreAPIConnector> actor = TestActorRef.create(system, Props.create(CoreAPIConnector.class, testConfig));

            SendHeartbeatToCore testMsg = new SendHeartbeatToCore(3600, true);
            actor.tell(testMsg, getRef());

            SendHeartbeatToCoreResponse response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), SendHeartbeatToCoreResponse.class);
            assertTrue(response.requestSucceeded());
            assertTrue(response.receivedConfigUpdate());
            assertNotNull(response.getConfig());
            assertTrue(response.getConfig().containsKey("Setting 1"));
            assertEquals("New Setting 1", response.getConfig().get("Setting 1"));

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }
}