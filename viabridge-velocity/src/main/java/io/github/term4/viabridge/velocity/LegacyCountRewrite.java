package io.github.term4.viabridge.velocity;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import org.slf4j.Logger;

/**
 * A stack count only a 1.8 client can draw. The modern wire cannot carry a count of 0 (it is the empty slot), so a
 * backend that wants a "0" badge marks the item instead: custom data {@value #TAG} = the count to show. This appends
 * to ViaRewind's 1.9-to-1.8 slot and contents handlers, after its own item translation, and patches the 1.8 count
 * in place - every set-slot and every window resend leaves the proxy already at 0, so nothing ever flashes.
 */
final class LegacyCountRewrite {

    /** The item NBT key (custom data on the modern side): a number, the count a legacy client should display. */
    static final String TAG = "viabridge:legacy_count";

    private static final String VIAREWIND_1_9_TO_1_8 = "com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8";

    private LegacyCountRewrite() {}

    /** {@code true} once appended; {@code false} while ViaRewind's protocol is not registered yet. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean arm(Logger logger) {
        Class<? extends Protocol> type;
        try {
            type = (Class<? extends Protocol>) Class.forName(VIAREWIND_1_9_TO_1_8);
        } catch (ClassNotFoundException e) {
            logger.info("ViaBridge: no ViaRewind on this proxy - legacy count rewrite off");
            return true;
        }
        Protocol protocol = Via.getManager().getProtocolManager().getProtocol(type);
        if (protocol == null) return false;
        protocol.appendClientbound(ClientboundPackets1_9.CONTAINER_SET_SLOT, wrapper -> {
            if (wrapper.isCancelled()) return;
            patch(wrapper.get(Types.ITEM1_8, 0));
        });
        protocol.appendClientbound(ClientboundPackets1_9.CONTAINER_SET_CONTENT, wrapper -> {
            if (wrapper.isCancelled()) return;
            Item[] items = wrapper.get(Types.ITEM1_8_SHORT_ARRAY, 0);
            if (items == null) return;
            for (Item item : items) patch(item);
        });
        logger.info("ViaBridge: legacy count rewrite armed ({} on 1.8 items)", TAG);
        return true;
    }

    private static void patch(Item item) {
        if (item == null) return;
        CompoundTag tag = item.tag();
        if (tag == null) return;
        Tag count = tag.get(TAG);
        if (count instanceof NumberTag number) item.setAmount(number.asInt());
    }
}
