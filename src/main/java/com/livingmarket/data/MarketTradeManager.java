package com.livingmarket.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.livingmarket.LivingMarketMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketTradeManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final MarketTradeManager INSTANCE = new MarketTradeManager();

    private Map<ResourceLocation, Map<Integer, LevelPool>> tradePools = Collections.emptyMap();

    public static class LevelPool {
        public final List<TradeDefinition> buy = new ArrayList<>();
        public final List<TradeDefinition> sell = new ArrayList<>();
    }

    private MarketTradeManager() {
        super(GSON, "market_trades");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<Integer, LevelPool>> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "market_trade");
                String professionStr = GsonHelper.getAsString(root, "profession");
                ResourceLocation profId = ResourceLocation.tryParse(professionStr);
                if (profId == null) {
                    LivingMarketMod.LOGGER.warn("Invalid profession '{}' in {}", professionStr, fileId);
                    continue;
                }

                boolean replace = GsonHelper.getAsBoolean(root, "replace", false);
                Map<Integer, LevelPool> profLevels = loaded.computeIfAbsent(profId, k -> new HashMap<>());
                if (replace) {
                    profLevels.clear();
                }

                JsonObject levelsObj = GsonHelper.getAsJsonObject(root, "levels");
                for (Map.Entry<String, JsonElement> lvlEntry : levelsObj.entrySet()) {
                    int level;
                    try {
                        level = Integer.parseInt(lvlEntry.getKey());
                    } catch (NumberFormatException ex) {
                        LivingMarketMod.LOGGER.warn("Invalid level key '{}' in {}", lvlEntry.getKey(), fileId);
                        continue;
                    }
                    if (level < 1 || level > 5) {
                        continue;
                    }

                    JsonObject poolObj = GsonHelper.convertToJsonObject(lvlEntry.getValue(), "level_pool");
                    LevelPool pool = profLevels.computeIfAbsent(level, k -> new LevelPool());

                    if (poolObj.has("buy")) {
                        JsonArray buyArr = GsonHelper.getAsJsonArray(poolObj, "buy");
                        for (JsonElement el : buyArr) {
                            pool.buy.add(TradeDefinition.fromJson(GsonHelper.convertToJsonObject(el, "buy_entry")));
                        }
                    }
                    if (poolObj.has("sell")) {
                        JsonArray sellArr = GsonHelper.getAsJsonArray(poolObj, "sell");
                        for (JsonElement el : sellArr) {
                            pool.sell.add(TradeDefinition.fromJson(GsonHelper.convertToJsonObject(el, "sell_entry")));
                        }
                    }
                }
            } catch (Exception e) {
                LivingMarketMod.LOGGER.error("Failed to parse market trades from {}", fileId, e);
            }
        }

        this.tradePools = loaded;
        LivingMarketMod.LOGGER.info("Living Market loaded custom trade tables for {} professions.", loaded.size());
    }

    @Nullable
    public LevelPool getPool(VillagerProfession profession, int level) {
        ResourceLocation profId = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);
        if (profId == null) {
            return null;
        }
        Map<Integer, LevelPool> byLevel = tradePools.get(profId);
        if (byLevel == null) {
            return null;
        }
        return byLevel.get(level);
    }

    public boolean hasCustomTrades(VillagerProfession profession) {
        ResourceLocation profId = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);
        return profId != null && tradePools.containsKey(profId);
    }
}
