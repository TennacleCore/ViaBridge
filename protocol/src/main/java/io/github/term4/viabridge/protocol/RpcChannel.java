package io.github.term4.viabridge.protocol;

/**
 * Plugin-message channel for backend &harr; Velocity RPC.
 * Player protocol version is <em>not</em> carried here — use Via's {@code vv:proxy_details} on the backend.
 */
public final class RpcChannel {

    public static final String ID = "viabridge:rpc";

    private RpcChannel() {}
}
