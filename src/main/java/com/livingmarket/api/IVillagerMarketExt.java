package com.livingmarket.api;

public interface IVillagerMarketExt {
    long livingmarket$getLastRefreshDay();
    void livingmarket$setLastRefreshDay(long day);

    int livingmarket$getRerollsToday();
    void livingmarket$setRerollsToday(int count);

    int livingmarket$getCurrentRerollCost();

    void livingmarket$checkDailyRefresh();

    boolean livingmarket$refreshUnpinnedTrades();

    boolean livingmarket$togglePin(int offerIndex);

    int livingmarket$getPinnedCount();
}
