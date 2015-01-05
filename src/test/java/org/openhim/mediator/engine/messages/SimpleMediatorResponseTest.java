/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimpleMediatorResponseTest {

    @Test
    public void testIsInstanceOfTrue() throws Exception {
        SimpleMediatorResponse<String> test = new SimpleMediatorResponse<>(null, "test");
        assertTrue(SimpleMediatorResponse.isInstanceOf(String.class, test));
    }

    @Test
    public void testIsInstanceOfFalse() throws Exception {
        SimpleMediatorResponse<Integer> test = new SimpleMediatorResponse<>(null, 1234);
        assertFalse(SimpleMediatorResponse.isInstanceOf(String.class, test));
    }
}