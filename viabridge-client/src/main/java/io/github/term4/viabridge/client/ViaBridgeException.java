package io.github.term4.viabridge.client;

/** Thrown when the proxy-side ViaBridge plugin rejects or fails an RPC. */
public final class ViaBridgeException extends RuntimeException {

    public ViaBridgeException(String message) {
        super(message);
    }
}
