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
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.engine.messages.MediatorSocketResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.*;

public class MLLPConnectorTest {
    private class MockMLLPServer extends Thread {
        ServerSocket socket;

        public MockMLLPServer() throws IOException {
            socket = new ServerSocket(8501);
        }

        public void kill() {
            IOUtils.closeQuietly(socket);
        }

        @Override
        public void run() {
            try {
                //we'll just handle one connection, so no while loop
                Socket conn = socket.accept();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024*1024);
                InputStream in = conn.getInputStream();
                int lastByte = -1;
                int lastLastByte;
                do {
                    lastLastByte = lastByte;
                    lastByte = in.read();
                    if (lastByte!=-1) {
                        buffer.write(lastByte);
                    }
                } while (lastByte!=-1 && lastLastByte!=MLLPConnector.MLLP_FOOTER_FS && lastByte!=MLLPConnector.MLLP_FOOTER_CR);

                String receivedMessage = buffer.toString();
                assertNotNull(receivedMessage);
                assertTrue(!receivedMessage.isEmpty());
                assertEquals(MLLPConnector.wrapMLLP("test"), receivedMessage);

                conn.getOutputStream().write(MLLPConnector.wrapMLLP("test\r\nresponse").getBytes());
                conn.close();
            } catch (IOException e) {
                fail();
                e.printStackTrace();
            }
        }
    }


    static ActorSystem system;

    private MockMLLPServer mockServer;

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
        mockServer = new MockMLLPServer();
        mockServer.start();
    }

    @After
    public void tearDown() throws Exception {
        mockServer.kill();
    }

    @Test
    public void testValidRequest() throws Exception {
        new JavaTestKit(system) {{
            final ActorRef tcpConnector = system.actorOf(Props.create(MLLPConnector.class));

            MediatorSocketRequest request = new MediatorSocketRequest(
                    getRef(), getRef(), "unit-test", "localhost", 8501, "test"
            );
            tcpConnector.tell(request, getRef());

            final Object[] out =
                    new ReceiveWhile<Object>(Object.class, duration("1 second")) {
                        @Override
                        protected Object match(Object msg) throws Exception {
                            if (msg instanceof MediatorSocketResponse ||
                                    msg instanceof AddOrchestrationToCoreResponse) {
                                return msg;
                            }
                            throw noMatch();
                        }
                    }.get();

            boolean foundResponse = false;
            boolean foundAddOrchestration = false;

            for (Object o : out) {
                if (o instanceof MediatorSocketResponse) {
                    assertEquals("test\r\nresponse", ((MediatorSocketResponse) o).getBody());
                    foundResponse = true;
                } else if (o instanceof AddOrchestrationToCoreResponse) {
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration());
                    assertEquals("unit-test", ((AddOrchestrationToCoreResponse) o).getOrchestration().getName());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getRequest());
                    assertNotNull(((AddOrchestrationToCoreResponse) o).getOrchestration().getResponse());
                    foundAddOrchestration = true;
                }
            }

            assertTrue("mllp-connector must send MediatorSocketResponse", foundResponse);
            assertTrue("mllp-connector must send AddOrchestrationToCoreResponse", foundAddOrchestration);
        }};
    }

}