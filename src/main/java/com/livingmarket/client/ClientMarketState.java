package com.livingmarket.client;

import java.util.Collections;
import java.util.List;

public final class ClientMarketState {
    private static int containerId = -1;
    private static boolean enabled = false;
    private static List<Boolean> pinnedFlags = Collections.emptyList();
    private static int rerollCost = 1;
    private static int maxPins = 2;

    private ClientMarketState() {}

    public static void update(int newContainerId, boolean isEnabled, List<Boolean> newPinnedFlags, int newRerollCost, int newMaxPins) {
        containerId = newContainerId;
        enabled = isEnabled;
        pinnedFlags = List.copyOf(newPinnedFlags);
        rerollCost = newRerollCost;
        maxPins = newMaxPins;
    }

    public static boolean isEnabledFor(int currentContainerId) {
        return enabled && containerId == currentContainerId;
    }

    public static boolean isPinned(int index) {
        return index >= 0 && index < pinnedFlags.size() && Boolean.TRUE.equals(pinnedFlags.get(index));
    }

    public static int getPinnedCount() {
        int count = 0;
        for (Boolean b : pinnedFlags) {
            if (Boolean.TRUE.equals(b)) {
                count++;
            }
        }
        return count;
    }

    public static int getTotalOffers() {
        return pinnedFlags.size();
    }

    public static int getRerollCost() {
        return rerollCost;
    }

    public static int getMaxPins() {
        return maxPins;
    }
}
