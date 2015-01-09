/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

public class SetupHTTPSCertificate {
    private final String keyStoreName;
    private final String keyStorePassword;
    private final String trustStoreName;
    private final boolean trustSelfSigned;

    public SetupHTTPSCertificate(String keyStoreName, String keyStorePassword, String trustStoreName, boolean trustSelfSigned) {
        this.keyStoreName = keyStoreName;
        this.keyStorePassword = keyStorePassword;
        this.trustStoreName = trustStoreName;
        this.trustSelfSigned = trustSelfSigned;
    }

    public String getKeyStoreName() {
        return keyStoreName;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getTrustStoreName() {
        return trustStoreName;
    }

    public boolean getTrustSelfSigned() {
        return trustSelfSigned;
    }
}
