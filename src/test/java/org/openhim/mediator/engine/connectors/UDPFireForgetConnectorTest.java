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
import org.openhim.mediator.engine.messages.MediatorSocketRequest;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import static org.junit.Assert.*;

public class UDPFireForgetConnectorTest {
    private class MockUDPServer extends Thread {
        DatagramSocket server;
        boolean receivedResponse;


        public MockUDPServer() throws IOException {
            server = new DatagramSocket(8502);
            receivedResponse = false;
        }

        public void kill() {
            IOUtils.closeQuietly(server);
        }

        @Override
        public void run() {
            byte[] buffer = new byte[12];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                server.receive(packet);
                assertNotNull(packet.getData());
                String result = new String(packet.getData());
                assertEquals("test message", result);
                receivedResponse = true;
            } catch (IOException e) {
                fail();
                e.printStackTrace();
            }
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

    @Test
    public void testValidRequest() throws Exception {
        new JavaTestKit(system) {{
            final ActorRef udpConnector = system.actorOf(Props.create(UDPFireForgetConnector.class));
            MockUDPServer server = new MockUDPServer();
            server.start();

            MediatorSocketRequest request = new MediatorSocketRequest(
                    getRef(), getRef(), "localhost", 8502, "test message"
            );
            udpConnector.tell(request, getRef());
            expectNoMsg(duration("1 second"));

            assertTrue(server.receivedResponse);

            server.kill();
        }};
    }
}