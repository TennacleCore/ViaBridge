package io.github.term4.viabridge.velocity;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class PacketTypeRegistry {

    enum Direction { CLIENTBOUND, SERVERBOUND }

    private record Entry(Class<? extends Enum<?>> enumClass, Direction direction) {}

    private static final Map<String, Entry> BY_SIMPLE_NAME = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    private PacketTypeRegistry() {}

    static Direction direction(String packetClass) {
        ensureReady();
        return require(packetClass).direction();
    }

    static PacketType resolve(String packetClass, String packetType) {
        ensureReady();
        Entry entry = require(packetClass);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Enum<?> constant = Enum.valueOf((Class<? extends Enum>) entry.enumClass(), packetType);
        return (PacketType) constant;
    }

    @SuppressWarnings("unchecked")
    static <T extends PacketType> Class<T> enumClass(String packetClass) {
        ensureReady();
        return (Class<T>) require(packetClass).enumClass();
    }

    private static void ensureReady() {
        if (initialized) return;
        synchronized (PacketTypeRegistry.class) {
            if (initialized) return;
            ClassLoader loader = Via.getManager().getClass().getClassLoader();
            scan(loader);
            if (BY_SIMPLE_NAME.isEmpty()) {
                throw new IllegalStateException("no Via packet types found — is ViaVersion loaded?");
            }
            initialized = true;
        }
    }

    private static Entry require(String packetClass) {
        Entry entry = BY_SIMPLE_NAME.get(packetClass);
        if (entry == null) throw new IllegalArgumentException("unknown packet class: " + packetClass);
        return entry;
    }

    private static void scan(ClassLoader loader) {
        String anchor = "com/viaversion/viaversion/protocols/v1_21_5to1_21_6/packet/ClientboundPackets1_21_6.class";
        URL url = loader.getResource(anchor);
        if (url == null) {
            throw new IllegalStateException("ViaVersion packet classes not visible (wrong classloader?)");
        }
        try {
            if ("jar".equals(url.getProtocol())) {
                scanJar(jarPath(url), loader);
                return;
            }
            if ("file".equals(url.getProtocol())) {
                Path root = Path.of(url.toURI()).getParent().getParent().getParent().getParent();
                scanDirectory(root, loader);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to scan Via packet types", e);
        }
    }

    private static String jarPath(URL url) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        return connection.getJarFile().getName();
    }

    private static void scanJar(String jarPath, ClassLoader loader) throws IOException {
        try (JarFile jar = new JarFile(jarPath)) {
            for (JarEntry entry : jar.stream().toList()) {
                registerEntry(entry.getName(), loader);
            }
        }
    }

    private static void scanDirectory(Path protocolsRoot, ClassLoader loader) {
        try {
            Files.walk(protocolsRoot)
                    .filter(p -> p.toString().replace('\\', '/').contains("/packet/"))
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .filter(p -> !p.getFileName().toString().contains("$"))
                    .forEach(p -> {
                        String relative = protocolsRoot.relativize(p).toString().replace('\\', '/');
                        registerClass(loader, "com.viaversion.viaversion.protocols." + relative.replace('/', '.').replace(".class", ""));
                    });
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan Via packet types", e);
        }
    }

    private static void registerEntry(String entryName, ClassLoader loader) {
        if (!entryName.startsWith("com/viaversion/viaversion/protocols/")) return;
        if (!entryName.contains("/packet/")) return;
        if (!entryName.endsWith(".class") || entryName.contains("$")) return;
        String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
        registerClass(loader, className);
    }

    private static void registerClass(ClassLoader loader, String className) {
        try {
            registerClass(Class.forName(className, false, loader));
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void registerClass(Class<?> cls) {
        if (!cls.isEnum() || !PacketType.class.isAssignableFrom(cls)) return;
        Direction direction = directionOf(cls);
        if (direction == null) return;
        BY_SIMPLE_NAME.putIfAbsent(cls.getSimpleName(), new Entry(asEnumClass(cls), direction));
    }

    private static Direction directionOf(Class<?> cls) {
        if (ClientboundPacketType.class.isAssignableFrom(cls)) return Direction.CLIENTBOUND;
        if (ServerboundPacketType.class.isAssignableFrom(cls)) return Direction.SERVERBOUND;
        String name = cls.getSimpleName();
        if (name.startsWith("Clientbound")) return Direction.CLIENTBOUND;
        if (name.startsWith("Serverbound")) return Direction.SERVERBOUND;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Enum<?>> asEnumClass(Class<?> cls) {
        return (Class<? extends Enum<?>>) cls;
    }
}
