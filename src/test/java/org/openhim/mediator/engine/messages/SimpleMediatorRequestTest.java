/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimpleMediatorRequestTest {

    @Test
    public void testIsInstanceOfTrue() throws Exception {
        SimpleMediatorRequest<String> test = new SimpleMediatorRequest<>(null, null, null, null, "test");
        assertTrue(SimpleMediatorRequest.isInstanceOf(String.class, test));
    }

    @Test
    public void testIsInstanceOfFalse() throws Exception {
        SimpleMediatorRequest<Integer> test = new SimpleMediatorRequest<>(null, null, null, null, 1234);
        assertFalse(SimpleMediatorRequest.isInstanceOf(String.class, test));
    }
}