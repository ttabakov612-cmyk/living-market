package com.livingmarket.logic;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.data.MarketTradeManager;
import com.livingmarket.data.TradeDefinition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TradeGenerationHelper {
    private TradeGenerationHelper() {}

    public static void populateLevelTrades(Villager villager, MerchantOffers offers, int level) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return;
        }

        int perSide = MarketConfig.TRADES_PER_SIDE_PER_LEVEL.get();
        Set<String> usedKeys = collectUsedKeys(offers);

        int existingBuy = 0;
        int existingSell = 0;
        for (MerchantOffer offer : offers) {
            ensureMetadata(offer, level);
            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (ext.livingmarket$getTradeLevel() == level) {
                if ("BUY".equals(ext.livingmarket$getTradeType())) {
                    existingBuy++;
                } else {
                    existingSell++;
                }
            }
        }

        int needBuy = Math.max(0, perSide - existingBuy);
        int needSell = Math.max(0, perSide - existingSell);

        if (needBuy > 0) {
            offers.addAll(generateSideOffers(villager, profession, level, true, needBuy, usedKeys));
        }
        if (needSell > 0) {
            offers.addAll(generateSideOffers(villager, profession, level, false, needSell, usedKeys));
        }
    }

    public static boolean rebuildUnpinnedTrades(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return false;
        }

        MerchantOffers currentOffers = villager.getOffers();
        int maxLevel = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        int perSide = MarketConfig.TRADES_PER_SIDE_PER_LEVEL.get();

        Map<Integer, List<MerchantOffer>> pinnedByLevel = new HashMap<>();
        int totalPinned = 0;

        for (int i = 0; i < currentOffers.size(); i++) {
            MerchantOffer offer = currentOffers.get(i);
            int fallbackLevel = Math.min(maxLevel, Math.max(1, (i / (perSide * 2)) + 1));
            ensureMetadata(offer, fallbackLevel);

            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (ext.livingmarket$isPinned()) {
                int lvl = Math.min(maxLevel, Math.max(1, ext.livingmarket$getTradeLevel()));
                ext.livingmarket$setTradeLevel(lvl);
                pinnedByLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(offer);
                totalPinned++;
            }
        }

        if (totalPinned >= currentOffers.size() && currentOffers.size() >= maxLevel * perSide * 2) {
            return false;
        }

        Set<String> usedKeys = new HashSet<>();
        for (List<MerchantOffer> list : pinnedByLevel.values()) {
            for (MerchantOffer pinned : list) {
                usedKeys.add(getOfferKey(pinned));
            }
        }

        List<MerchantOffer> rebuilt = new ArrayList<>();

        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            List<MerchantOffer> lvlPinned = pinnedByLevel.getOrDefault(lvl, Collections.emptyList());
            List<MerchantOffer> pinnedBuy = new ArrayList<>();
            List<MerchantOffer> pinnedSell = new ArrayList<>();

            for (MerchantOffer offer : lvlPinned) {
                if ("BUY".equals(((IMerchantOfferExt) offer).livingmarket$getTradeType())) {
                    pinnedBuy.add(offer);
                } else {
                    pinnedSell.add(offer);
                }
            }

            int needBuy = Math.max(0, perSide - pinnedBuy.size());
            int needSell = Math.max(0, perSide - pinnedSell.size());

            rebuilt.addAll(pinnedBuy);
            if (needBuy > 0) {
                rebuilt.addAll(generateSideOffers(villager, profession, lvl, true, needBuy, usedKeys));
            }

            rebuilt.addAll(pinnedSell);
            if (needSell > 0) {
                rebuilt.addAll(generateSideOffers(villager, profession, lvl, false, needSell, usedKeys));
            }
        }

        currentOffers.clear();
        currentOffers.addAll(rebuilt);
        return true;
    }

    private static List<MerchantOffer> generateSideOffers(
            Villager villager,
            VillagerProfession profession,
            int level,
            boolean isBuy,
            int countNeeded,
            Set<String> usedKeys
    ) {
        List<MerchantOffer> result = new ArrayList<>();
        RandomSource random = villager.getRandom();

        // 1. Primary source: custom JSON datapack pool for this profession and level
        MarketTradeManager.LevelPool pool = MarketTradeManager.INSTANCE.getPool(profession, level);
        if (pool != null) {
            List<TradeDefinition> candidates = new ArrayList<>(isBuy ? pool.buy : pool.sell);
            shuffleList(candidates, random);

            for (TradeDefinition def : candidates) {
                if (result.size() >= countNeeded) {
                    break;
                }
                String defKey = def.getDeduplicationKey(isBuy);
                if (usedKeys.contains(defKey)) {
                    continue;
                }
                MerchantOffer offer = def.createOffer(villager, random, level, isBuy);
                if (offer != null) {
                    result.add(offer);
                    usedKeys.add(defKey);
                    usedKeys.add(getOfferKey(offer));
                }
            }
        }

        // 2. Fallback: vanilla/modded VillagerTrades registry (filtered by BUY/SELL)
        if (result.size() < countNeeded) {
            fillFromVanillaRegistry(villager, profession, level, isBuy, countNeeded, usedKeys, result, random);
        }

        // 3. Final fallback: allow duplicates from our JSON pool if player lowered pool size below perSide
        if (result.size() < countNeeded && pool != null) {
            List<TradeDefinition> candidates = new ArrayList<>(isBuy ? pool.buy : pool.sell);
            shuffleList(candidates, random);
            for (TradeDefinition def : candidates) {
                if (result.size() >= countNeeded) {
                    break;
                }
                MerchantOffer offer = def.createOffer(villager, random, level, isBuy);
                if (offer != null) {
                    result.add(offer);
                }
            }
        }

        return result;
    }

    private static void fillFromVanillaRegistry(
            Villager villager,
            VillagerProfession profession,
            int level,
            boolean isBuy,
            int countNeeded,
            Set<String> usedKeys,
            List<MerchantOffer> result,
            RandomSource random
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> vanillaMap = VillagerTrades.TRADES.get(profession);
        if (vanillaMap == null || vanillaMap.isEmpty()) {
            return;
        }

        // Check exact level first, then nearby levels
        int[] searchOrder = new int[]{level, Math.max(1, level - 1), Math.min(5, level + 1), 1, 2, 3, 4, 5};
        Set<Integer> visitedLevels = new HashSet<>();

        for (int candidateLevel : searchOrder) {
            if (result.size() >= countNeeded) {
                break;
            }
            if (!visitedLevels.add(candidateLevel)) {
                continue;
            }
            VillagerTrades.ItemListing[] listings = vanillaMap.get(candidateLevel);
            if (listings == null || listings.length == 0) {
                continue;
            }

            List<VillagerTrades.ItemListing> shuffled = new ArrayList<>();
            Collections.addAll(shuffled, listings);
            shuffleList(shuffled, random);

            for (VillagerTrades.ItemListing listing : shuffled) {
                if (result.size() >= countNeeded) {
                    break;
                }
                MerchantOffer offer = listing.getOffer(villager, random);
                if (offer == null) {
                    continue;
                }
                boolean offerIsBuy = inferIsBuy(offer);
                if (offerIsBuy != isBuy) {
                    continue;
                }
                String key = getOfferKey(offer);
                if (usedKeys.contains(key)) {
                    continue;
                }
                IMerchantOfferExt ext = (IMerchantOfferExt) offer;
                ext.livingmarket$setPinned(false);
                ext.livingmarket$setTradeLevel(level);
                ext.livingmarket$setTradeType(isBuy ? "BUY" : "SELL");
                result.add(offer);
                usedKeys.add(key);
            }
        }
    }

    public static void ensureMetadata(MerchantOffer offer, int defaultLevel) {
        IMerchantOfferExt ext = (IMerchantOfferExt) offer;
        if (ext.livingmarket$getTradeLevel() <= 0) {
            ext.livingmarket$setTradeLevel(Math.max(1, Math.min(5, defaultLevel)));
        }
        String type = ext.livingmarket$getTradeType();
        if (type == null || type.isEmpty()) {
            ext.livingmarket$setTradeType(inferIsBuy(offer) ? "BUY" : "SELL");
        }
    }

    public static boolean inferIsBuy(MerchantOffer offer) {
        return offer.getResult().is(Items.EMERALD) && !offer.getBaseCostA().is(Items.EMERALD);
    }

    private static Set<String> collectUsedKeys(MerchantOffers offers) {
        Set<String> keys = new HashSet<>();
        for (MerchantOffer offer : offers) {
            keys.add(getOfferKey(offer));
        }
        return keys;
    }

    private static String getOfferKey(MerchantOffer offer) {
        boolean isBuy = inferIsBuy(offer);
        ItemStack primary = isBuy ? offer.getBaseCostA() : offer.getResult();
        if (primary.is(Items.ENCHANTED_BOOK)) {
            return (isBuy ? "BUY:" : "SELL:") + "enchanted_book";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(primary.getItem());
        return (isBuy ? "BUY:" : "SELL:") + (id != null ? id.toString() : primary.getItem().toString());
    }

    private static <T> void shuffleList(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    public static int calculateRerollCost(int rerollsToday) {
        int base = MarketConfig.BASE_REROLL_COST.get();
        if (base <= 0) {
            return 0;
        }
        double multiplier = MarketConfig.REROLL_COST_MULTIPLIER.get();
        int cap = MarketConfig.MAX_REROLL_COST.get();
        double raw = base * Math.pow(multiplier, Math.max(0, rerollsToday));
        return (int) Math.min(cap, Math.max(1L, Math.round(raw)));
    }

    public static boolean consumeEmeralds(ServerPlayer player, int amount) {
        if (amount <= 0 || player.getAbilities().instabuild) {
            return true;
        }
        if (player.getInventory().countItem(Items.EMERALD) < amount) {
            return false;
        }

        int remaining = amount;
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (int i = 0; i < player.getInventory().offhand.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }
}
