// 바닐라 LlamaFollowCaravanGoal: 줄에 묶인(또는 캐러밴 꼬리인) 라마 뒤를 따라 일렬로 걷는다.
// TODO(보트·끈): 끈에 매인 라마(isLeashed)와 탑승 중 예외는 끈·탑승 시스템이 생기면.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Llama;
import org.domi.furnace.server.entity.ai.Goal;

public class LlamaFollowCaravanGoal extends Goal {
    public final Llama llama;
    private double speedModifier;
    private int distCheckCounter;

    public LlamaFollowCaravanGoal(Llama llama, double speed) {
        this.llama = llama;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!llama.isLeashed() && !llama.inCaravan()) {
            var list = llama.level().getEntities(llama, llama.boundingBox().inflate(9.0, 4.0, 9.0), e -> e.type() == EntityType.LLAMA || e.type() == EntityType.TRADER_LLAMA);
            Llama found = null;
            double best = Double.MAX_VALUE;
            for (var e : list) {
                Llama l = (Llama) e;
                if (l.inCaravan() && !l.hasCaravanTail()) {
                    double d = llama.distSqr(l);
                    if (!(d > best)) {
                        best = d;
                        found = l;
                    }
                }
            }
            if (found == null) {
                for (var e : list) {
                    Llama l = (Llama) e;
                    if (l.isLeashed() && !l.hasCaravanTail()) {
                        double d = llama.distSqr(l);
                        if (!(d > best)) {
                            best = d;
                            found = l;
                        }
                    }
                }
            }
            if (found == null) return false;
            if (best < 4.0) return false;
            if (!found.isLeashed() && !firstIsLeashed(found, 1)) return false;
            llama.joinCaravan(found);
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (llama.inCaravan() && llama.getCaravanHead().isAlive() && firstIsLeashed(llama, 0)) {
            double d = llama.distSqr(llama.getCaravanHead());
            if (d > 676.0) {
                if (speedModifier <= 3.0) {
                    speedModifier *= 1.2;
                    distCheckCounter = reducedTickDelay(40);
                    return true;
                }
                if (distCheckCounter == 0) return false;
            }
            if (distCheckCounter > 0) distCheckCounter--;
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        llama.leaveCaravan();
        speedModifier = 2.1;
    }

    @Override
    public void tick() {
        if (llama.inCaravan()) {
            Llama head = llama.getCaravanHead();
            double dist = Math.sqrt(llama.distSqr(head));
            double dx = head.x() - llama.x(), dy = head.y() - llama.y(), dz = head.z() - llama.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double s = len < 1.0E-5F ? 0.0 : Math.max(dist - 2.0, 0.0) / len;
            llama.getNavigation().moveTo(llama.x() + dx * s, llama.y() + dy * s, llama.z() + dz * s, speedModifier);
        }
    }

    private boolean firstIsLeashed(Llama l, int depth) {
        if (depth > 8) return false;
        if (l.inCaravan()) return l.getCaravanHead().isLeashed() ? true : firstIsLeashed(l.getCaravanHead(), ++depth);
        return false;
    }
}
