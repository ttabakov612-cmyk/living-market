package com.livingmarket.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncMarketStatePacket {
    private final int containerId;
    private final boolean enabled;
    private final List<Boolean> pinnedFlags;
    private final int rerollCost;
    private final int maxPins;

    public SyncMarketStatePacket(int containerId, boolean enabled, List<Boolean> pinnedFlags, int rerollCost, int maxPins) {
        this.containerId = containerId;
        this.enabled = enabled;
        this.pinnedFlags = pinnedFlags;
        this.rerollCost = rerollCost;
        this.maxPins = maxPins;
    }

    public int getContainerId() {
        return containerId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Boolean> getPinnedFlags() {
        return pinnedFlags;
    }

    public int getRerollCost() {
        return rerollCost;
    }

    public int getMaxPins() {
        return maxPins;
    }

    public static void encode(SyncMarketStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.pinnedFlags.size());
        for (Boolean pinned : msg.pinnedFlags) {
            buf.writeBoolean(Boolean.TRUE.equals(pinned));
        }
        buf.writeVarInt(msg.rerollCost);
        buf.writeVarInt(msg.maxPins);
    }

    public static SyncMarketStatePacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        List<Boolean> flags = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            flags.add(buf.readBoolean());
        }
        int rerollCost = buf.readVarInt();
        int maxPins = buf.readVarInt();
        return new SyncMarketStatePacket(containerId, enabled, flags, rerollCost, maxPins);
    }

    public static void handle(SyncMarketStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
        ctx.get().setPacketHandled(true);
    }
}
