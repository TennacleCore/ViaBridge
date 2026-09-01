package io.github.term4.viabridge.client;

import io.github.term4.viabridge.protocol.RpcChannel;
import io.github.term4.viabridge.protocol.RpcCodec;
import io.github.term4.viabridge.protocol.RpcFrame;
import io.github.term4.viabridge.protocol.RpcOpcodes;
import io.github.term4.viabridge.protocol.RpcStatus;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minestom-side client for {@link RpcChannel#ID} RPC to a Velocity proxy running the ViaBridge plugin.
 *
 * <p>Player protocol version is <em>not</em> queried here — use Via's {@code vv:proxy_details} (e.g. MMC
 * {@code ClientInfoTracker}) for that.
 */
public final class ViaBridgeClient {

    private static final long DEFAULT_TIMEOUT_MS = 2_000;

    private static volatile ViaBridgeClient instance;

    private record Pending(CompletableFuture<RpcFrame> future, UUID playerId) {}

    private final AtomicInteger nextRequestId = new AtomicInteger(1);
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;

    private ViaBridgeClient(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /** Installs the global client and registers the inbound response listener. Idempotent. */
    public static @NotNull ViaBridgeClient install(@NotNull EventNode<@NotNull PlayerEvent> node) {
        return install(node, DEFAULT_TIMEOUT_MS);
    }

    public static @NotNull ViaBridgeClient install(@NotNull EventNode<@NotNull PlayerEvent> node, long timeoutMs) {
        ViaBridgeClient existing = instance;
        if (existing != null) return existing;
        synchronized (ViaBridgeClient.class) {
            if (instance == null) {
                instance = new ViaBridgeClient(timeoutMs);
                node.addListener(PlayerPluginMessageEvent.class, instance::onPluginMessage);
                node.addListener(PlayerDisconnectEvent.class, e -> instance.failPending(e.getPlayer(), "disconnect"));
            }
            return instance;
        }
    }

    public static @NotNull ViaBridgeClient get() {
        ViaBridgeClient client = instance;
        if (client == null) throw new IllegalStateException("ViaBridgeClient not installed");
        return client;
    }

    /** Verifies the ViaBridge plugin is reachable on the proxy. */
    public @NotNull CompletableFuture<byte[]> ping(@NotNull Player player, @NotNull byte[] echo) {
        return send(player, new RpcFrame(RpcFrame.WIRE_VERSION, nextId(), RpcOpcodes.PING, echo))
                .thenApply(RpcFrame::payload);
    }

    /**
     * Sends {@code SET_ENTITY_MOTION} at {@code inputProtocolId} with legacy short units (Via transforms to the client).
     *
     * @param inputProtocolId e.g. {@code 772} ({@code 1.21.7})
     */
    public @NotNull CompletableFuture<Void> sendEntityMotion(
            @NotNull Player player,
            int inputProtocolId,
            int entityId,
            short vx,
            short vy,
            short vz
    ) {
        byte[] body = encodeEntityMotionBody(entityId, vx, vy, vz);
        byte[] payload = RpcCodec.encodeSendClientbound(
                inputProtocolId,
                "ClientboundPackets1_21_6",
                "SET_ENTITY_MOTION",
                body
        );
        return send(player, new RpcFrame(RpcFrame.WIRE_VERSION, nextId(), RpcOpcodes.SEND_CLIENTBOUND, payload))
                .thenApply(frame -> {
                    RpcCodec.StatusResponse response = RpcCodec.decodeStatusResponse(frame.payload());
                    if (response.status() == RpcStatus.OK) return null;
                    if (response.status() == RpcStatus.CANCELLED) {
                        throw new ViaBridgeException("packet cancelled by Via pipeline");
                    }
                    String detail = response.message();
                    throw new ViaBridgeException(detail != null ? detail
                            : "SEND_CLIENTBOUND failed (status=" + response.status() + ")");
                });
    }

    private @NotNull CompletableFuture<RpcFrame> send(@NotNull Player player, @NotNull RpcFrame frame) {
        if (frame.requestId() == 0) throw new IllegalArgumentException("requestId must be non-zero");
        CompletableFuture<RpcFrame> future = new CompletableFuture<>();
        pending.put(frame.requestId(), new Pending(future, player.getUuid()));
        player.sendPluginMessage(RpcChannel.ID, RpcCodec.encode(frame));
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).whenComplete((ignored, err) -> {
            pending.remove(frame.requestId());
            if (err instanceof TimeoutException) {
                future.completeExceptionally(new ViaBridgeException("ViaBridge RPC timed out — is the Velocity plugin installed?"));
            }
        });
        return future;
    }

    private void onPluginMessage(@NotNull PlayerPluginMessageEvent event) {
        if (!RpcChannel.ID.equals(event.getIdentifier())) return;
        RpcFrame frame;
        try {
            frame = RpcCodec.decode(event.getMessage());
        } catch (RuntimeException ignored) {
            return;
        }
        if (frame.requestId() == 0) return;
        Pending req = pending.get(frame.requestId());
        // spoof guard: a frame from another connection must not complete or evict this pending RPC
        if (req == null || !req.playerId().equals(event.getPlayer().getUuid())) return;
        pending.remove(frame.requestId());
        req.future().complete(frame);
    }

    private void failPending(@NotNull Player player, @NotNull String reason) {
        pending.entrySet().removeIf(entry -> {
            if (!entry.getValue().playerId().equals(player.getUuid())) return false;
            entry.getValue().future().completeExceptionally(new ViaBridgeException(reason));
            return true;
        });
    }

    private int nextId() {
        int id;
        do {
            id = nextRequestId.getAndIncrement();
            if (id == 0) id = nextRequestId.getAndIncrement();
        } while (id == 0);
        return id;
    }

    static byte[] encodeEntityMotionBody(int entityId, short vx, short vy, short vz) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeVarInt(out, entityId);
            out.write((vx >> 8) & 0xFF);
            out.write(vx & 0xFF);
            out.write((vy >> 8) & 0xFF);
            out.write(vy & 0xFF);
            out.write((vz >> 8) & 0xFF);
            out.write(vz & 0xFF);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
