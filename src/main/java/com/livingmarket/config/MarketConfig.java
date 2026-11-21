package com.livingmarket.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MarketConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue TRADES_PER_SIDE_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue MAX_PINNED_TRADES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DAILY_REFRESH;
    public static final ForgeConfigSpec.IntValue BASE_REROLL_COST;
    public static final ForgeConfigSpec.DoubleValue REROLL_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_REROLL_COST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("trading");

        TRADES_PER_SIDE_PER_LEVEL = builder
                .comment("Number of BUY (Item -> Emerald) and SELL (Emerald -> Item) trades per villager level. 2 means 4 trades per level (20 at level 5).")
                .defineInRange("tradesPerSidePerLevel", 2, 1, 5);

        MAX_PINNED_TRADES = builder
                .comment("Maximum number of trades that can be pinned on a single villager at once.")
                .defineInRange("maxPinnedTrades", 2, 0, 20);

        ENABLE_DAILY_REFRESH = builder
                .comment("If true, all unpinned trades automatically rotate on each new Minecraft day (every 24000 ticks).")
                .define("enableDailyRefresh", true);

        builder.pop();

        builder.push("reroll");

        BASE_REROLL_COST = builder
                .comment("Emerald cost of the first manual reroll of the day (0 for free).")
                .defineInRange("baseRerollCost", 1, 0, 64);

        REROLL_COST_MULTIPLIER = builder
                .comment("Multiplier applied to the reroll cost after each reroll within the same day (e.g. 2.0 => 1, 2, 4, 8, 16, 32).")
                .defineInRange("rerollCostMultiplier", 2.0D, 1.0D, 10.0D);

        MAX_REROLL_COST = builder
                .comment("Maximum emerald cost cap for a single manual reroll.")
                .defineInRange("maxRerollCost", 32, 1, 64);

        builder.pop();

        SPEC = builder.build();
    }

    private MarketConfig() {}
}
