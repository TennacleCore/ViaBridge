package io.github.term4.viabridge.protocol;

/**
 * One RPC frame on {@link RpcChannel#ID}.
 *
 * @param protocolVersion wire format version (currently {@code 1})
 * @param requestId       {@code 0} = no response expected; non-zero must be echoed in the reply
 * @param opcode          {@link RpcOpcodes}
 * @param payload         opcode-specific body (may be empty)
 */
public record RpcFrame(int protocolVersion, int requestId, int opcode, byte[] payload) {

    public static final int WIRE_VERSION = 1;

    public RpcFrame {
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
