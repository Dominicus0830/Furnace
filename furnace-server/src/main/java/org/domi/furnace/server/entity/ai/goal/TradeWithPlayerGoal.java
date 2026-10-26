// 바닐라 TradeWithPlayerGoal: 거래 중이면 서서 거래 상대만 본다. 끝나면 거래 상대를 비운다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.trade.Merchant;

public class TradeWithPlayerGoal<T extends PathfinderMob & Merchant> extends Goal {
    private final T mob;

    public TradeWithPlayerGoal(T mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!mob.isAlive() || mob.isInWater() || !mob.onGround() || mob.hurtMarked) return false;
        var trader = mob.tradingPlayer();
        var player = trader == null ? null : trader.entityPlayer();
        if (player == null) return false;
        double dx = mob.x() - player.x(), dy = mob.y() - player.y(), dz = mob.z() - player.z();
        // 바닐라는 player.containerMenu != null 도 보지만 containerMenu 는 늘 있다
        return dx * dx + dy * dy + dz * dz <= 16.0;
    }

    @Override public void start() { mob.getNavigation().stop(); }

    @Override public void stop() { mob.setTradingPlayer(null); }
}
