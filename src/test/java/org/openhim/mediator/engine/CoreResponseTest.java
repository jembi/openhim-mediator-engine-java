/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.InputStream;
import java.text.SimpleDateFormat;

import static org.junit.Assert.*;

public class CoreResponseTest {

    @Test
    public void testParse() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        InputStream in = CoreResponseTest.class.getClassLoader().getResourceAsStream("core-response.json");
        String json = IOUtils.toString(in);

        CoreResponse response = CoreResponse.parse(json);

        assertEquals("urn:mediator:test-mediator", response.getUrn());
        assertEquals("Successful", response.getStatus());
        assertEquals(new Integer(200), response.getResponse().getStatus());
        assertEquals("text/plain", response.getResponse().getHeaders().get("Content-Type"));
        assertEquals("a test response", response.getResponse().getBody());
        assertEquals("2015-01-15 14:51", format.format(response.getResponse().getTimestamp()));

        assertEquals(2, response.getOrchestrations().size());
        assertEquals("orch1", response.getOrchestrations().get(0).getName());
        assertEquals("/orch1", response.getOrchestrations().get(0).getRequest().getPath());
        assertEquals("orchestration 1", response.getOrchestrations().get(0).getRequest().getBody());
        assertEquals("POST", response.getOrchestrations().get(0).getRequest().getMethod());
        assertEquals("2015-01-15 14:51", format.format(response.getOrchestrations().get(0).getRequest().getTimestamp()));
        assertEquals(new Integer(201), response.getOrchestrations().get(0).getResponse().getStatus());
        assertEquals("text/plain", response.getOrchestrations().get(0).getResponse().getHeaders().get("Content-Type"));
        assertEquals("created", response.getOrchestrations().get(0).getResponse().getBody());
        assertEquals("2015-01-15 14:51", format.format(response.getOrchestrations().get(0).getResponse().getTimestamp()));

        assertEquals("orch2", response.getOrchestrations().get(1).getName());
        assertEquals("/orch2", response.getOrchestrations().get(1).getRequest().getPath());
        assertNull(response.getOrchestrations().get(1).getRequest().getBody());
        assertEquals("GET", response.getOrchestrations().get(1).getRequest().getMethod());
        assertEquals("2015-01-15 14:51", format.format(response.getOrchestrations().get(1).getRequest().getTimestamp()));
        assertEquals(new Integer(200), response.getOrchestrations().get(1).getResponse().getStatus());
        assertEquals("text/xml", response.getOrchestrations().get(1).getResponse().getHeaders().get("Content-Type"));
        assertEquals("<data>test orchestration 2</data>", response.getOrchestrations().get(1).getResponse().getBody());
        assertEquals("2015-01-15 14:51", format.format(response.getOrchestrations().get(1).getResponse().getTimestamp()));

        assertEquals(2, response.getProperties().size());
        assertEquals("val1", response.getProperties().get("pro1"));
        assertEquals("val2", response.getProperties().get("pro2"));
    }

    @Test
    public void testParse_BadContent() throws Exception {
        String json = "bad content!";

        try {
            CoreResponse.parse(json);
            fail("Failed to throw JsonSyntaxException");
        } catch (CoreResponse.ParseException ex) {
            //expected
        }
    }
}