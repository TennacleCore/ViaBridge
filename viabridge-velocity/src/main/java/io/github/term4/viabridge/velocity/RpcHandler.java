package io.github.term4.viabridge.velocity;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import io.netty.channel.Channel;
import com.velocitypowered.api.proxy.Player;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.exception.InformativeException;
import io.github.term4.viabridge.protocol.RpcCodec;
import io.github.term4.viabridge.protocol.RpcFrame;
import io.github.term4.viabridge.protocol.RpcOpcodes;
import io.github.term4.viabridge.protocol.RpcStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;

final class RpcHandler {

    private enum Mode { SEND, SCHEDULE, TRANSFORM }

    private final Logger logger;

    RpcHandler(Logger logger) {
        this.logger = logger;
    }

    RpcFrame handle(Player player, RpcFrame request) {
        return switch (request.opcode()) {
            case RpcOpcodes.PING -> handlePing(request);
            case RpcOpcodes.SEND_CLIENTBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.CLIENTBOUND, Mode.SEND);
            case RpcOpcodes.SEND_SERVERBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.SERVERBOUND, Mode.SEND);
            case RpcOpcodes.SCHEDULE_SEND_CLIENTBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.CLIENTBOUND, Mode.SCHEDULE);
            case RpcOpcodes.SCHEDULE_SEND_SERVERBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.SERVERBOUND, Mode.SCHEDULE);
            case RpcOpcodes.TRANSFORM_CLIENTBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.CLIENTBOUND, Mode.TRANSFORM);
            case RpcOpcodes.TRANSFORM_SERVERBOUND -> handlePacket(player, request, PacketTypeRegistry.Direction.SERVERBOUND, Mode.TRANSFORM);
            default -> errorResponse(request, "unknown opcode " + request.opcode());
        };
    }

    private RpcFrame handlePing(RpcFrame request) {
        if (request.requestId() == 0) return null;
        return new RpcFrame(RpcFrame.WIRE_VERSION, request.requestId(), RpcOpcodes.PING, request.payload());
    }

    private RpcFrame handlePacket(Player player, RpcFrame request, PacketTypeRegistry.Direction direction, Mode mode) {
        RpcCodec.SendClientboundRequest spec;
        try {
            spec = RpcCodec.decodeSendClientbound(request.payload());
        } catch (RuntimeException e) {
            return errorResponse(request, "bad payload: " + e.getMessage());
        }

        try {
            if (PacketTypeRegistry.direction(spec.packetClass()) != direction) {
                return errorResponse(request, spec.packetClass() + " is not " + direction.name().toLowerCase());
            }

            UserConnection connection = resolveFrontendConnection(player);
            if (connection == null) {
                return errorResponse(request, "no Via frontend UserConnection for " + player.getUsername());
            }

            DispatchResult result = onEventLoop(connection, () -> dispatch(connection, spec, direction, mode));
            if (request.requestId() == 0) return null;
            return statusResponse(request, result.status(), result.message(), result.body());
        } catch (Throwable t) {
            UserConnection connection = resolveFrontendConnection(player);
            logger.error("ViaBridge: opcode {} failed for {} ({})", request.opcode(), player.getUsername(), describe(connection), t);
            if (request.requestId() == 0) return null;
            return errorResponse(request, rootMessage(t) + " [" + describe(connection) + "]");
        }
    }

    // Via's per-connection state is the channel's; a pipeline run off its event loop races the live one
    private static DispatchResult onEventLoop(UserConnection connection, Callable<DispatchResult> work) throws Exception {
        Channel channel = connection.getChannel();
        if (channel == null || channel.eventLoop().inEventLoop()) return work.call();
        return channel.eventLoop().submit(work).get(2, TimeUnit.SECONDS);
    }

    // what the failure saw: which side, which versions, which protocols, which state - the report a fix needs
    private static String describe(UserConnection connection) {
        if (connection == null) return "no connection";
        StringBuilder out = new StringBuilder();
        out.append(connection.isClientSide() ? "client-side" : "server-side");
        var info = connection.getProtocolInfo();
        if (info != null) {
            out.append(" client=").append(info.protocolVersion()).append(" server=").append(info.serverProtocolVersion());
            if (info.getPipeline() != null) {
                out.append(" pipes=");
                for (var pipe : info.getPipeline().pipes()) out.append(pipe.getClass().getSimpleName()).append(',');
            }
        }
        out.append(" stored=");
        for (Class<?> stored : connection.getStoredObjects().keySet()) out.append(stored.getSimpleName()).append(',');
        return out.toString();
    }

    // Velocity speaks each client's own version on the frontend, so that Via connection is a passthrough with no
    // pipeline state; the translation - and the trackers its handlers read - lives on the backend link, Via's
    // client-side connection. A clientbound raw send there fires up the proxy's backend pipeline toward the player.
    private static UserConnection resolveFrontendConnection(Player player) {
        var manager = Via.getManager().getConnectionManager();
        UserConnection front = manager.getServerConnection(player.getUniqueId());
        UserConnection back = manager.getClientConnection(player.getUniqueId());
        if (back != null && translates(back)) return back;
        if (front != null) return front;
        return back;
    }

    private static boolean translates(UserConnection connection) {
        var info = connection.getProtocolInfo();
        return info != null && info.protocolVersion() != null && !info.protocolVersion().equals(info.serverProtocolVersion());
    }

    private record DispatchResult(byte status, String message, byte[] body) {
        static DispatchResult ok() { return new DispatchResult(RpcStatus.OK, null, null); }
        static DispatchResult ok(byte[] body) { return new DispatchResult(RpcStatus.OK, null, body); }
        static DispatchResult cancelled() { return new DispatchResult(RpcStatus.CANCELLED, null, null); }
    }

    private static DispatchResult dispatch(
            UserConnection connection,
            RpcCodec.SendClientboundRequest spec,
            PacketTypeRegistry.Direction direction,
            Mode mode
    ) throws InformativeException {
        ProtocolVersion inputVersion = ProtocolVersion.getProtocol(spec.inputProtocolId());
        if (!inputVersion.isKnown()) {
            throw new IllegalArgumentException("unknown input protocol id: " + spec.inputProtocolId());
        }

        PacketType packetType = PacketTypeRegistry.resolve(spec.packetClass(), spec.packetType());
        ByteBuf body = Unpooled.wrappedBuffer(spec.packetBody());
        PacketWrapper packet = PacketWrapper.create(packetType, body, connection);
        VersionedPacketTransformer<?, ?> transformer = createTransformer(inputVersion, spec.packetClass(), direction);

        return switch (mode) {
            case SEND -> transformer.send(packet) ? DispatchResult.ok() : DispatchResult.cancelled();
            case SCHEDULE -> transformer.scheduleSend(packet) ? DispatchResult.ok() : DispatchResult.cancelled();
            case TRANSFORM -> {
                PacketWrapper transformed = transformer.transform(packet);
                if (transformed == null) yield DispatchResult.cancelled();
                ByteBuf out = Unpooled.buffer();
                transformed.writeToBuffer(out);
                byte[] bytes = new byte[out.readableBytes()];
                out.readBytes(bytes);
                yield DispatchResult.ok(bytes);
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static VersionedPacketTransformer<?, ?> createTransformer(
            ProtocolVersion inputVersion,
            String packetClass,
            PacketTypeRegistry.Direction direction
    ) {
        var manager = Via.getManager().getProtocolManager();
        if (direction == PacketTypeRegistry.Direction.CLIENTBOUND) {
            Class<? extends ClientboundPacketType> cls = PacketTypeRegistry.enumClass(packetClass);
            return manager.createPacketTransformer(inputVersion, cls, null);
        }
        Class<? extends ServerboundPacketType> cls = PacketTypeRegistry.enumClass(packetClass);
        return manager.createPacketTransformer(inputVersion, null, cls);
    }

    private static RpcFrame statusResponse(RpcFrame request, byte status, String message, byte[] body) {
        return new RpcFrame(RpcFrame.WIRE_VERSION, request.requestId(), request.opcode(),
                RpcCodec.encodeStatusResponse(status, message, body));
    }

    private static RpcFrame errorResponse(RpcFrame request, String message) {
        return statusResponse(request, RpcStatus.ERROR, message, null);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null || msg.isEmpty() ? "" : ": " + msg);
    }
}
