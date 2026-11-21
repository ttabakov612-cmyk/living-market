package com.livingmarket.api;

public interface IMerchantOfferExt {
    boolean livingmarket$isPinned();
    void livingmarket$setPinned(boolean pinned);

    int livingmarket$getTradeLevel();
    void livingmarket$setTradeLevel(int level);

    String livingmarket$getTradeType();
    void livingmarket$setTradeType(String type);
}
