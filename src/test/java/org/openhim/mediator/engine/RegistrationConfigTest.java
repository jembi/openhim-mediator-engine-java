/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

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
            RegistrationConfig config = new RegistrationConfig(testJson);
            config.getURN();
            fail("Failed to throw InvalidRegistrationContent exception");
        } catch (RegistrationConfig.InvalidRegistrationContentException ex) {
            //expected
        }
    }
}