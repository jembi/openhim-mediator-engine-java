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

public class SimpleMediatorResponse<T>  extends MediatorResponseMessage {
    private T responseObject;

    public SimpleMediatorResponse(MediatorRequestMessage originalRequest, T responseObject) {
        super(originalRequest);
        this.responseObject = responseObject;
    }

    public T getResponseObject() {
        return responseObject;
    }

    public static boolean isInstanceOf(Class requestObjectClass, Object o) {
        if (!(o instanceof SimpleMediatorResponse)) {
            return false;
        }

        return requestObjectClass.isInstance(((SimpleMediatorResponse) o).getResponseObject());
    }
}
