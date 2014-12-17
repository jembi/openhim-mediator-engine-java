package org.openhim.mediator.engine.messages;

/**
 * A message indicating that an error has occurred and the request should terminate
 */
public class ExceptError {
    private final Throwable error;

    public ExceptError(Throwable error) {
        this.error = error;
    }

    public Throwable getError() {
        return error;
    }
}
