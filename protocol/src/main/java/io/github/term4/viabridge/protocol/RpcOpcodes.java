package io.github.term4.viabridge.protocol;

public final class RpcOpcodes {

    public static final int PING = 0;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#send} clientbound. */
    public static final int SEND_CLIENTBOUND = 1;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#send} serverbound. */
    public static final int SEND_SERVERBOUND = 2;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#scheduleSend} clientbound. */
    public static final int SCHEDULE_SEND_CLIENTBOUND = 3;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#scheduleSend} serverbound. */
    public static final int SCHEDULE_SEND_SERVERBOUND = 4;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#transform} clientbound. */
    public static final int TRANSFORM_CLIENTBOUND = 5;
    /** {@link com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer#transform} serverbound. */
    public static final int TRANSFORM_SERVERBOUND = 6;

    private RpcOpcodes() {}
}
