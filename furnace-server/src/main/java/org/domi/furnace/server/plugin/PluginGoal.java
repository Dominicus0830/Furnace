// 플러그인 MobGoal 을 서버 목표 자리에 끼우는 어댑터. 몹 틱(소유 region 스레드)에서 불린다.
// 플러그인이 꺼졌거나 콜백이 예외를 던졌으면 다시 부르지 않고 스스로 빠진다(예외는 로그 한 번).
package org.domi.furnace.server.plugin;

import java.util.EnumSet;
import org.domi.furnace.api.entity.ai.GoalControl;
import org.domi.furnace.api.entity.ai.GoalKey;
import org.domi.furnace.api.entity.ai.GoalSet;
import org.domi.furnace.api.entity.ai.MobGoal;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.entity.ai.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PluginGoal extends Goal {

    private static final Logger LOG = LoggerFactory.getLogger(PluginGoal.class);

    final FurnacePlugin owner;
    final MobGoal goal;
    private final MobAiImpl ai;
    private final GoalSet set;
    private final GoalKey key;
    private final boolean interruptible, everyTick;
    private boolean broken;

    PluginGoal(FurnacePlugin owner, MobGoal goal, MobAiImpl ai, GoalSet set) {
        this.owner = owner;
        this.goal = goal;
        this.ai = ai;
        this.set = set;
        this.key = goal.key();
        if (key == null) throw new IllegalArgumentException("목표 이름표(key)가 null 이다");
        this.interruptible = goal.interruptible();
        this.everyTick = goal.tickEveryTick();
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        for (GoalControl c : goal.controls()) flags.add(Flag.valueOf(c.name()));
        setFlags(flags);
    }

    GoalKey key() { return key; }

    /** 계속 불러도 되는가. 아니면 몹에서 스스로 뺀다(돌기 도중이면 끝난 뒤 빠진다). */
    private boolean alive() {
        if (!broken && owner.isEnabled()) return true;
        ai.selector(set).removeGoal(this);
        return false;
    }

    private void fail(String where, Throwable t) {
        broken = true;
        LOG.warn("플러그인 {} 의 몹 목표 {} 가 {} 에서 예외를 던져 멈춘다", owner, key, where, t);
    }

    @Override
    public boolean canUse() {
        if (!alive()) return false;
        try {
            return goal.shouldActivate(ai);
        } catch (Throwable t) {
            fail("shouldActivate", t);
            return false;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (broken || !owner.isEnabled()) return false;
        try {
            return goal.shouldStayActive(ai);
        } catch (Throwable t) {
            fail("shouldStayActive", t);
            return false;
        }
    }

    @Override
    public boolean isInterruptable() { return interruptible; }

    @Override
    public boolean requiresUpdateEveryTick() { return everyTick; }

    @Override
    public void start() {
        if (broken || !owner.isEnabled()) return;
        try {
            goal.start(ai);
        } catch (Throwable t) {
            fail("start", t);
        }
    }

    @Override
    public void stop() {
        if (broken || !owner.isEnabled()) return;
        try {
            goal.stop(ai);
        } catch (Throwable t) {
            fail("stop", t);
        }
    }

    @Override
    public void tick() {
        if (broken || !owner.isEnabled()) return;
        try {
            goal.tick(ai);
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    /** 같은 MobGoal 객체는 같은 목표(목표 집합에 한 번만 걸린다). */
    @Override
    public boolean equals(Object o) { return o instanceof PluginGoal p && p.goal == goal; }

    @Override
    public int hashCode() { return System.identityHashCode(goal); }

    @Override
    public String toString() { return "PluginGoal[" + key + "]"; }
}
