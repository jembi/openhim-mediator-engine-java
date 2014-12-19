/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import akka.actor.UntypedActor;
import org.junit.Test;

import static org.junit.Assert.*;

public class RoutingTableTest {

    @Test
    public void testAddRoute() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/test", TestActor1.class);
        assertNotNull("/test should be mapped", table.getActorClassForPath("/test"));
        assertTrue("/test should be mapped to correct actor class", table.getActorClassForPath("/test").equals(TestActor1.class));
        assertNull("/test2 should not be mapped", table.getActorClassForPath("/test2"));
    }

    @Test
    public void testAddRegexRoute() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRegexRoute("/test.+/\\d", TestActor1.class);
        assertNotNull("/tester/1 should be mapped", table.getActorClassForPath("/tester/1"));
        assertTrue("/tester/1 should be mapped to correct actor class", table.getActorClassForPath("/tester/1").equals(TestActor1.class));
        assertNull("/tester/two should not be mapped", table.getActorClassForPath("/tester/two"));
    }

    @Test
    public void testMappingFIFO() throws Exception {
        //routes added should be retrieved on FIFO basis
        RoutingTable table = new RoutingTable();
        table.addRoute("/test", TestActor1.class);
        table.addRegexRoute("/t.+", TestActor2.class);
        assertNotNull("/test should be mapped", table.getActorClassForPath("/test"));
        assertTrue("/test should be mapped to actor 1", table.getActorClassForPath("/test").equals(TestActor1.class));

        RoutingTable table2 = new RoutingTable();
        table2.addRegexRoute("/t.+", TestActor2.class);
        table2.addRoute("/test", TestActor1.class);
        assertNotNull("/test should be mapped", table2.getActorClassForPath("/test"));
        assertTrue("/test should be mapped to actor 2", table2.getActorClassForPath("/test").equals(TestActor2.class));
    }

    @Test
    public void testRemoveRoute() throws Exception {
        RoutingTable table = new RoutingTable();
        table.addRoute("/test", TestActor1.class);
        assertNotNull("/test should be mapped", table.getActorClassForPath("/test"));
        table.removeRoute("/test");
        assertNull("/test should be removed", table.getActorClassForPath("/test"));

        RoutingTable table2 = new RoutingTable();
        table2.addRegexRoute("/test.+", TestActor1.class);
        assertNotNull("/tester should be mapped", table2.getActorClassForPath("/tester"));
        table2.removeRoute("/test.+");
        assertNull("/tester should be removed", table2.getActorClassForPath("/tester"));
    }


    private static class TestActor1 extends UntypedActor {
        @Override public void onReceive(Object o) throws Exception {}
    }

    private static class TestActor2 extends UntypedActor {
        @Override public void onReceive(Object o) throws Exception {}
    }
}