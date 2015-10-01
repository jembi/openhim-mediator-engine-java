/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import com.google.gson.JsonParseException;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class RegistrationConfigTest {

    @Test
    public void testGetURN() throws Exception {
        InputStream testJson = RegistrationConfigTest.class.getClassLoader().getResourceAsStream("test-registration-info.json");
        RegistrationConfig config = new RegistrationConfig(testJson);
        assertEquals("urn:mediator:test.mediator", config.getURN());
    }

    @Test
    public void testGetURN_invalidJSON() throws Exception {
        String testJson = "a bad message!";
        try {
            new RegistrationConfig(testJson);
            fail("Failed to throw exception");
        } catch (JsonParseException ex) {
            //expected
        }
    }


    @Test
    public void testDefaultConfig() throws Exception {
        InputStream testJson = RegistrationConfigTest.class.getClassLoader().getResourceAsStream("test-registration-info-w-config.json");
        RegistrationConfig config = new RegistrationConfig(testJson);

        assertNotNull(config.getDefaultConfig());

        assertTrue(config.getDefaultConfig().containsKey("Setting 1"));
        assertTrue(config.getDefaultConfig().containsKey("Setting 2"));
        assertTrue(config.getDefaultConfig().containsKey("Setting 3"));
        assertTrue(config.getDefaultConfig().containsKey("Setting 4"));

        assertTrue(config.getDefaultConfig().get("Setting 1") instanceof String);
        assertTrue(config.getDefaultConfig().get("Setting 2") instanceof Double);
        assertTrue(config.getDefaultConfig().get("Setting 3") instanceof Boolean);
        assertTrue(config.getDefaultConfig().get("Setting 4") instanceof String);

        assertEquals("default1", config.getDefaultConfig().get("Setting 1"));
        assertEquals(42, ((Double)config.getDefaultConfig().get("Setting 2")).intValue());
        assertTrue((Boolean) config.getDefaultConfig().get("Setting 3"));
        assertEquals("b", config.getDefaultConfig().get("Setting 4"));
    }
}