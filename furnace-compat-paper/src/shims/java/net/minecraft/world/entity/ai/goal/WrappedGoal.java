package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet;
import org.domi.furnace.compat.nms.NmsGoals;

/**
 * 우선순위가 붙은 목표 shim(몹 목표 설계 M1). 서버 감싼 목표 하나에 하나 — 우선순위·안의 목표는 바뀌지 않아 만들 때 담고, 실행 여부·바꿔치기 판정은
 * 그 몹의 주인 스레드에서 지금 값을 읽는다(앞서 받은 객체도 {@code tick()} 뒤의 값을 본다). 플러그인이 만든 것은 바닐라 몸통 그대로다.
 */
class WrappedGoal {

    private final Goal furnaceGoal;
    private final int furnacePriority;
    /** 플러그인이 만든 감싼 목표의 실행 여부(서버 것은 서버가 갖는다). */
    private boolean furnaceRunning;

    public WrappedGoal(int priority, Goal goal) {
        super();
        this.furnacePriority = priority;
        this.furnaceGoal = goal;
    }

    protected WrappedGoal(org.domi.furnace.compat.nms.ShimCtor tag) {
        super(tag);
        NmsGoals.Made m = NmsGoals.made(tag);
        this.furnacePriority = m.priority();
        this.furnaceGoal = m.inner();
    }

    public boolean canBeReplacedBy(WrappedGoal other) {
        if (furnaceBacking() != null && other.furnaceBacking() != null) {
            Boolean r = NmsGoals.canBeReplacedBy(furnaceBacking(), other.furnaceBacking());
            if (r != null) return r;
        }
        return isInterruptable() && other.getPriority() < getPriority();
    }

    @Override
    public boolean canUse() { return furnaceBacking() != null ? NmsGoals.canUse(this) : furnaceGoal.canUse(); }

    @Override
    public boolean canContinueToUse() { return furnaceBacking() != null ? super.canContinueToUse() : furnaceGoal.canContinueToUse(); }

    @Override
    public boolean isInterruptable() { return furnaceBacking() != null ? super.isInterruptable() : furnaceGoal.isInterruptable(); }

    @Override
    public void start() {
        if (furnaceBacking() != null) {
            super.start();
        } else if (!furnaceRunning) {
            furnaceRunning = true;
            furnaceGoal.start();
        }
    }

    @Override
    public void stop() {
        if (furnaceBacking() != null) {
            super.stop();
        } else if (furnaceRunning) {
            furnaceRunning = false;
            furnaceGoal.stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() { return furnaceBacking() != null ? super.requiresUpdateEveryTick() : furnaceGoal.requiresUpdateEveryTick(); }

    @Override
    protected int adjustedTickDelay(int ticks) { return furnaceGoal.adjustedTickDelay(ticks); }

    @Override
    public void tick() {
        if (furnaceBacking() != null) super.tick();
        else furnaceGoal.tick();
    }

    @Override
    public void setFlags(EnumSet<Goal.Flag> controls) { furnaceGoal.setFlags(controls); }

    @Override
    public OptimizedSmallEnumSet<Goal.Flag> getFlags() { return furnaceGoal.getFlags(); }

    public boolean isRunning() { return furnaceBacking() != null ? NmsGoals.isRunning(furnaceBacking()) : furnaceRunning; }

    public int getPriority() { return furnacePriority; }

    public Goal getGoal() { return furnaceGoal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass() && furnaceGoal.equals(((WrappedGoal) o).furnaceGoal);
    }

    @Override
    public int hashCode() { return furnaceGoal.hashCode(); }
}
