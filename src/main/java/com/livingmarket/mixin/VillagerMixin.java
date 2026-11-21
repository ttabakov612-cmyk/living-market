package com.livingmarket.mixin;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.logic.TradeGenerationHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillagerMarketExt {
    @Shadow
    public abstract VillagerData getVillagerData();

    @Unique
    private long livingmarket$lastRefreshDay = -1L;

    @Unique
    private int livingmarket$rerollsToday = 0;

    protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void livingmarket$saveData(CompoundTag tag, CallbackInfo ci) {
        tag.putLong("lmLastRefreshDay", this.livingmarket$lastRefreshDay);
        tag.putInt("lmRerollsToday", this.livingmarket$rerollsToday);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void livingmarket$loadData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("lmLastRefreshDay")) {
            this.livingmarket$lastRefreshDay = tag.getLong("lmLastRefreshDay");
        }
        if (tag.contains("lmRerollsToday")) {
            this.livingmarket$rerollsToday = tag.getInt("lmRerollsToday");
        }
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void livingmarket$updateTrades(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        VillagerData data = this.getVillagerData();
        VillagerProfession profession = data.getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return;
        }

        int level = Math.max(1, Math.min(5, data.getLevel()));
        if (this.offers == null) {
            this.offers = new MerchantOffers();
        }
        TradeGenerationHelper.populateLevelTrades(self, this.offers, level);

        if (!this.level().isClientSide() && this.livingmarket$lastRefreshDay < 0L) {
            this.livingmarket$lastRefreshDay = this.level().getDayTime() / 24000L;
        }
        ci.cancel();
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void livingmarket$onStartTrading(Player player, CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            this.livingmarket$checkDailyRefresh();
        }
    }

    @Inject(method = "restock", at = @At("TAIL"))
    private void livingmarket$onRestock(CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            this.livingmarket$checkDailyRefresh();
        }
    }

    @Override
    public long livingmarket$getLastRefreshDay() {
        return this.livingmarket$lastRefreshDay;
    }

    @Override
    public void livingmarket$setLastRefreshDay(long day) {
        this.livingmarket$lastRefreshDay = day;
    }

    @Override
    public int livingmarket$getRerollsToday() {
        return this.livingmarket$rerollsToday;
    }

    @Override
    public void livingmarket$setRerollsToday(int count) {
        this.livingmarket$rerollsToday = Math.max(0, count);
    }

    @Override
    public int livingmarket$getCurrentRerollCost() {
        return TradeGenerationHelper.calculateRerollCost(this.livingmarket$rerollsToday);
    }

    @Override
    public void livingmarket$checkDailyRefresh() {
        if (this.level().isClientSide()) {
            return;
        }
        VillagerProfession profession = this.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return;
        }

        long currentDay = this.level().getDayTime() / 24000L;
        if (this.offers == null) {
            this.getOffers();
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            return;
        }

        if (this.livingmarket$lastRefreshDay < 0L) {
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            return;
        }

        if (currentDay > this.livingmarket$lastRefreshDay) {
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            if (MarketConfig.ENABLE_DAILY_REFRESH.get()) {
                this.livingmarket$refreshUnpinnedTrades();
            }
        }
    }

    @Override
    public boolean livingmarket$refreshUnpinnedTrades() {
        Villager self = (Villager) (Object) this;
        return TradeGenerationHelper.rebuildUnpinnedTrades(self);
    }

    @Override
    public boolean livingmarket$togglePin(int offerIndex) {
        MerchantOffers currentOffers = this.getOffers();
        if (offerIndex < 0 || offerIndex >= currentOffers.size()) {
            return false;
        }
        MerchantOffer offer = currentOffers.get(offerIndex);
        IMerchantOfferExt ext = (IMerchantOfferExt) offer;
        if (ext.livingmarket$isPinned()) {
            ext.livingmarket$setPinned(false);
            return true;
        } else {
            int maxPins = MarketConfig.MAX_PINNED_TRADES.get();
            if (this.livingmarket$getPinnedCount() >= maxPins) {
                return false;
            }
            ext.livingmarket$setPinned(true);
            return true;
        }
    }

    @Override
    public int livingmarket$getPinnedCount() {
        MerchantOffers currentOffers = this.getOffers();
        int count = 0;
        for (MerchantOffer offer : currentOffers) {
            if (((IMerchantOfferExt) offer).livingmarket$isPinned()) {
                count++;
            }
        }
        return count;
    }
}
