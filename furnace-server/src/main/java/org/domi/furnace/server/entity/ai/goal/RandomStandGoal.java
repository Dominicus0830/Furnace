// 바닐라 RandomStandGoal: 말이 가끔(점점 높아지는 확률) 뒷발로 서서 소리를 낸다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.AbstractHorse;
import org.domi.furnace.server.entity.ai.Goal;

public class RandomStandGoal extends Goal {
    private final AbstractHorse horse;
    private int nextStand;

    public RandomStandGoal(AbstractHorse horse) {
        this.horse = horse;
        resetStandInterval(horse);
    }

    @Override
    public void start() {
        horse.standIfPossible();
        String s = horse.getAmbientStandSound();
        if (s != null) horse.playSound(s, horse.getSoundVolumePublic(), horse.getVoicePitch());
    }

    @Override public boolean canContinueToUse() { return false; }

    @Override
    public boolean canUse() {
        nextStand++;
        if (nextStand > 0 && horse.random().nextInt(1000) < nextStand) {
            resetStandInterval(horse);
            return !horse.isImmobile() && horse.random().nextInt(10) == 0;
        }
        return false;
    }

    private void resetStandInterval(AbstractHorse horse) { nextStand = -horse.getAmbientStandInterval(); }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
