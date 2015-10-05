/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

public class SendHeartbeatToCore {
    private final long uptimeSeconds;
    private final boolean forceConfig;

    public SendHeartbeatToCore(long uptimeSeconds, boolean forceConfig) {
        this.uptimeSeconds = uptimeSeconds;
        this.forceConfig = forceConfig;
    }

    public SendHeartbeatToCore(long uptimeSeconds) {
        this(uptimeSeconds, false);
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public boolean getForceConfig() {
        return forceConfig;
    }
}
