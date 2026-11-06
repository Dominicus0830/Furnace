// 바닐라 Merchant(주민·떠돌이 상인이 구현): 거래 목록과 거래하는 플레이어, 거래 알림.
package org.domi.furnace.server.trade;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.menu.MenuPlayer;

public interface Merchant {

    void setTradingPlayer(MenuPlayer player);

    MenuPlayer tradingPlayer();

    MerchantOffers offers();

    /** 거래가 이뤄졌다(사용 횟수 증가 등). */
    void notifyTrade(MerchantOffer offer);

    /** 결과 칸이 바뀌었다(주민이 소리 낸다). */
    void notifyTradeUpdated(ItemStack stack);

    int villagerXp();

    void overrideXp(int xp);

    boolean showProgressBar();

    String notifyTradeSound();

    default boolean canRestock() { return false; }
}
