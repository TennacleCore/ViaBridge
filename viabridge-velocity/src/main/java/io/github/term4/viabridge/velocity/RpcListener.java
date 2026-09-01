package io.github.term4.viabridge.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import io.github.term4.viabridge.protocol.RpcCodec;
import io.github.term4.viabridge.protocol.RpcFrame;
import org.slf4j.Logger;

final class RpcListener {

    private final RpcHandler handler;
    private final Logger logger;

    RpcListener(Logger logger) {
        this.logger = logger;
        this.handler = new RpcHandler(logger);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!ViaBridgePlugin.CHANNEL.equals(event.getIdentifier())) return;

        // backend↔proxy only: drop client-origin frames (else relayed to the backend as a forged request/response)
        if (!(event.getSource() instanceof ServerConnection server)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }
        if (!(event.getTarget() instanceof Player player)) return;

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        byte[] data = event.getData();
        RpcFrame request;
        try {
            request = RpcCodec.decode(data);
        } catch (RuntimeException e) {
            logger.warn("ViaBridge: bad frame from backend for {}: {}", player.getUsername(), e.getMessage());
            return;
        }

        if (request.protocolVersion() != RpcFrame.WIRE_VERSION) {
            logger.warn("ViaBridge: unsupported wire version {} from backend for {}", request.protocolVersion(), player.getUsername());
            return;
        }

        RpcFrame response = handler.handle(player, request);
        if (response == null) return;

        server.sendPluginMessage(ViaBridgePlugin.CHANNEL, RpcCodec.encode(response));
    }
}
