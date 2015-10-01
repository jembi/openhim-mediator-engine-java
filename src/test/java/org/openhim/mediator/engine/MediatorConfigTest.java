/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine;

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

/**
 */
public class MediatorConfigTest {

    @Test
    public void testSetRegistrationConfig_shouldInitDynamicConfig() throws Exception {
        InputStream testJson = RegistrationConfigTest.class.getClassLoader().getResourceAsStream("test-registration-info-w-config.json");
        RegistrationConfig regConfig = new RegistrationConfig(testJson);

        MediatorConfig mediatorConfig = new MediatorConfig();
        mediatorConfig.setRegistrationConfig(regConfig);

        assertEquals("default1", mediatorConfig.getDynamicConfig().get("Setting 1"));
        assertEquals(42, ((Double)mediatorConfig.getDynamicConfig().get("Setting 2")).intValue());
        assertTrue((Boolean) mediatorConfig.getDynamicConfig().get("Setting 3"));
        assertEquals("b", mediatorConfig.getDynamicConfig().get("Setting 4"));
    }
}