// 바닐라 RunAroundLikeCrazyGoal: 길들지 않은 말이 플레이어를 태우고 있으면 무작위로 날뛰며, 가끔 플레이어를 떨어뜨리거나(템퍼 판정) 길들여진다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.AbstractHorse;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public class RunAroundLikeCrazyGoal extends Goal {
    private final AbstractHorse horse;
    private final double speedModifier;
    private double posX, posY, posZ;

    public RunAroundLikeCrazyGoal(AbstractHorse horse, double speed) {
        this.horse = horse;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!horse.isTamed() && horse.isVehicle()) {
            double[] p = LandRandomPos.getPos(horse, 5, 4);
            if (p == null) return false;
            posX = p[0];
            posY = p[1];
            posZ = p[2];
            return true;
        }
        return false;
    }

    @Override public void start() { horse.getNavigation().moveTo(posX, posY, posZ, speedModifier); }

    @Override public boolean canContinueToUse() { return !horse.isTamed() && !horse.getNavigation().isDone() && horse.isVehicle(); }

    @Override
    public void tick() {
        if (!horse.isTamed() && horse.random().nextInt(adjustedTickDelay(50)) == 0) {
            var rider = horse.firstPassenger();
            if (rider == null) return;
            if (rider instanceof org.domi.furnace.server.entity.Player p) {   // 플레이어만 템퍼 판정
                int temper = horse.getTemper();
                int max = horse.getMaxTemper();
                // 플러그인 EntityTameEvent 취소면 실패 쪽(CraftBukkit 과 같은 자리)
                if (max > 0 && horse.random().nextInt(max) < temper && horse.level().gameEvents().entityTame(horse, p)) {
                    horse.tameWithName(p);
                    return;
                }
                horse.modifyTemper(5);
            }
            horse.ejectPassengers();
            horse.makeMad();
            horse.broadcastEntityEvent((byte) 6);
        }
    }
}
