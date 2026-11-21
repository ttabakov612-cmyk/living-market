package com.livingmarket.mixin;

import com.livingmarket.client.ClientMarketState;
import com.livingmarket.network.MarketNetwork;
import com.livingmarket.network.RerollTradesPacket;
import com.livingmarket.network.TogglePinPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    @Shadow
    private int shopItem;

    @Shadow
    int scrollOff;

    @Unique
    private Button livingmarket$pinButton;

    @Unique
    private Button livingmarket$rerollButton;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void livingmarket$initButtons(CallbackInfo ci) {
        int btnY = Math.max(2, this.topPos - 22);

        this.livingmarket$pinButton = Button.builder(Component.empty(), btn -> {
            if (ClientMarketState.isEnabledFor(this.menu.containerId)) {
                MerchantOffers offers = this.menu.getOffers();
                if (this.shopItem >= 0 && this.shopItem < offers.size()) {
                    MarketNetwork.sendToServer(new TogglePinPacket(this.shopItem));
                }
            }
        }).bounds(this.leftPos, btnY, 135, 20).build();

        this.livingmarket$rerollButton = Button.builder(Component.empty(), btn -> {
            if (ClientMarketState.isEnabledFor(this.menu.containerId)) {
                MarketNetwork.sendToServer(new RerollTradesPacket());
            }
        }).bounds(this.leftPos + 141, btnY, 135, 20).build();

        this.addRenderableWidget(this.livingmarket$pinButton);
        this.addRenderableWidget(this.livingmarket$rerollButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void livingmarket$updateButtonStates(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.livingmarket$pinButton == null || this.livingmarket$rerollButton == null) {
            return;
        }

        boolean enabled = ClientMarketState.isEnabledFor(this.menu.containerId);
        this.livingmarket$pinButton.visible = enabled;
        this.livingmarket$rerollButton.visible = enabled;

        if (!enabled) {
            return;
        }

        MerchantOffers offers = this.menu.getOffers();
        if (this.shopItem >= offers.size()) {
            this.shopItem = 0;
        }

        int pinnedCount = ClientMarketState.getPinnedCount();
        int maxPins = ClientMarketState.getMaxPins();
        boolean hasSelection = this.shopItem >= 0 && this.shopItem < offers.size();
        boolean isSelectedPinned = hasSelection && ClientMarketState.isPinned(this.shopItem);

        if (isSelectedPinned) {
            this.livingmarket$pinButton.setMessage(Component.translatable("gui.livingmarket.unpin", pinnedCount, maxPins));
            this.livingmarket$pinButton.active = true;
            this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.unpin.tooltip")));
        } else {
            this.livingmarket$pinButton.setMessage(Component.translatable("gui.livingmarket.pin", pinnedCount, maxPins));
            boolean canPin = hasSelection && pinnedCount < maxPins;
            this.livingmarket$pinButton.active = canPin;
            if (!canPin && hasSelection) {
                this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.pin.tooltip.limit", pinnedCount, maxPins)));
            } else {
                this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.pin.tooltip")));
            }
        }

        int cost = ClientMarketState.getRerollCost();
        boolean allPinned = offers.isEmpty() || pinnedCount >= offers.size();
        boolean hasEnoughEmeralds = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getAbilities().instabuild && cost > 0) {
            hasEnoughEmeralds = mc.player.getInventory().countItem(Items.EMERALD) >= cost;
        }

        if (cost <= 0) {
            this.livingmarket$rerollButton.setMessage(Component.translatable("gui.livingmarket.reroll.free"));
        } else {
            this.livingmarket$rerollButton.setMessage(Component.translatable("gui.livingmarket.reroll", cost));
        }

        this.livingmarket$rerollButton.active = !allPinned && hasEnoughEmeralds;
        if (allPinned) {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.reroll.tooltip.all_pinned")));
        } else if (!hasEnoughEmeralds) {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.reroll.tooltip.no_emeralds", cost)));
        } else {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.reroll.tooltip")));
        }
    }

    @Inject(method = "renderScroller", at = @At("TAIL"))
    private void livingmarket$renderPinMarkers(GuiGraphics graphics, int posX, int posY, MerchantOffers offers, CallbackInfo ci) {
        if (!ClientMarketState.isEnabledFor(this.menu.containerId)) {
            return;
        }
        int baseX = posX + 5;
        int baseY = posY + 18;

        for (int row = 0; row < 7; row++) {
            int offerIndex = row + this.scrollOff;
            if (offerIndex >= offers.size()) {
                break;
            }
            if (ClientMarketState.isPinned(offerIndex)) {
                int y = baseY + row * 20;
                int gold = 0xFFFFD700;
                graphics.fill(baseX, y, baseX + 3, y + 20, gold);
                graphics.fill(baseX, y, baseX + 88, y + 1, gold);
                graphics.fill(baseX, y + 19, baseX + 88, y + 20, gold);
                graphics.fill(baseX + 87, y, baseX + 88, y + 20, gold);
            }
        }
    }
}
