package io.github.term4.viabridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.github.term4.viabridge.protocol.RpcChannel;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "viabridge",
        name = "ViaBridge",
        version = "0.1.0-SNAPSHOT",
        description = "Exposes ViaVersion APIs to trusted backend servers (e.g. Minestom) over plugin messages",
        authors = {"Term4"},
        dependencies = {
                @Dependency(id = "viaversion")
        }
)
public final class ViaBridgePlugin {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(RpcChannel.ID);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public ViaBridgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(this, new RpcListener(logger));
        logger.info("ViaBridge ready on channel {}", RpcChannel.ID);
    }
}
