// 바닐라 LookAtTradingPlayerGoal: 거래 중이면 거래 상대를 바라본다(LookAtPlayerGoal 8칸).
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.trade.Merchant;

public class LookAtTradingPlayerGoal<T extends Mob & Merchant> extends LookAtPlayerGoal {
    private final T merchant;

    public LookAtTradingPlayerGoal(T merchant) {
        super(merchant, 8.0F);
        this.merchant = merchant;
    }

    @Override
    public boolean canUse() {
        var trader = merchant.tradingPlayer();
        if (trader == null) return false;
        lookAt = trader.entityPlayer();
        return true;
    }
}
