package com.livingmarket.network;

import com.livingmarket.client.ClientMarketState;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleSync(SyncMarketStatePacket msg) {
        ClientMarketState.update(
                msg.getContainerId(),
                msg.isEnabled(),
                msg.getPinnedFlags(),
                msg.getRerollCost(),
                msg.getMaxPins()
        );
    }
}
