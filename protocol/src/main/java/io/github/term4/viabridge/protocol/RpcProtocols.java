package io.github.term4.viabridge.protocol;

/** Well-known input protocol ids for {@link RpcOpcodes#SEND_CLIENTBOUND}. */
public final class RpcProtocols {

    /** {@code ClientboundPackets1_21_6} — last shorts-format {@code SET_ENTITY_MOTION} before LpVec3 (1.21.9). */
    public static final int CLIENTBOUND_1_21_6 = 771;

    private RpcProtocols() {}
}
