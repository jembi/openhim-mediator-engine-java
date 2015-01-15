/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.engine.messages;

/**
 * Indicates that the mediator has accepted the request asyncronously.
 * The mediator http server should therefore respond to the client with an http status of 202,
 * and the final response will be updated to core at a later stage.
 */
public class AcceptedAsyncRequest {
}
