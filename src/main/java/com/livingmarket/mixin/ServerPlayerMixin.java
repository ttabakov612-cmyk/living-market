package com.livingmarket.mixin;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.network.MarketNetwork;
import com.livingmarket.network.SyncMarketStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "sendMerchantOffers", at = @At("TAIL"))
    private void livingmarket$syncMarketState(
            int containerId,
            MerchantOffers offers,
            int villagerLevel,
            int villagerXp,
            boolean showProgress,
            boolean canRestock,
            CallbackInfo ci
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.containerMenu instanceof MerchantMenu menu && menu.containerId == containerId) {
            Merchant trader = ((MerchantMenuAccessor) menu).livingmarket$getTrader();
            if (trader instanceof Villager villager) {
                List<Boolean> pinnedFlags = new ArrayList<>(offers.size());
                for (MerchantOffer offer : offers) {
                    pinnedFlags.add(((IMerchantOfferExt) offer).livingmarket$isPinned());
                }
                int rerollCost = ((IVillagerMarketExt) villager).livingmarket$getCurrentRerollCost();
                int maxPins = MarketConfig.MAX_PINNED_TRADES.get();
                MarketNetwork.sendToPlayer(self, new SyncMarketStatePacket(containerId, true, pinnedFlags, rerollCost, maxPins));
                return;
            }
        }
        MarketNetwork.sendToPlayer(self, new SyncMarketStatePacket(containerId, false, Collections.emptyList(), 0, 0));
    }
}
