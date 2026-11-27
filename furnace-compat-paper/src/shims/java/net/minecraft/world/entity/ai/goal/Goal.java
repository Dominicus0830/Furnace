package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import ca.spottedleaf.moonrise.common.set.OptimizedSmallEnumSet;
import org.domi.furnace.compat.nms.NmsGoals;

/**
 * 몹 목표 shim(몹 목표 설계 M1). 서버 목표를 보는 shim(생성기 전용 생성자로 만든다 — 바닐라 목표 클래스의 객체)은 모든 몸통을 그 몹의 주인 스레드에서
 * 서버 목표에 넘긴다. 플러그인이 상속한 목표는 바닐라 몸통 그대로이고, 선택기에 넣으면 서버 어댑터가 그 메서드를 부른다(M2a).
 */
@org.domi.furnace.compat.nms.NmsInheritable
class Goal {

    /** 서버 목표를 보는 shim 이면 그 몹과 서버 목표, 플러그인이 만든 목표면 null. */
    private final NmsGoals.Backing furnaceBacking;
    /** 플러그인이 만든 목표의 깃발(Paper 처럼 살아 있는 집합 — 비면 UNKNOWN_BEHAVIOR). 서버 목표의 깃발은 서버가 갖는다. */
    private final OptimizedSmallEnumSet<Flag> goalTypes = new OptimizedSmallEnumSet<>(Flag.class);

    public Goal() {
        this.furnaceBacking = null;
        goalTypes.addUnchecked(Flag.UNKNOWN_BEHAVIOR);
    }

    protected Goal(org.domi.furnace.compat.nms.ShimCtor tag) {
        this.furnaceBacking = NmsGoals.backing(tag);
        NmsGoals.bindFresh(this, tag);
    }

    /** 호환 모듈 전용: 서버 목표를 보는 shim 이면 그 재료(아니면 null). */
    public final NmsGoals.Backing furnaceBacking() { return furnaceBacking; }

    public boolean canContinueToUse() { return furnaceBacking != null ? NmsGoals.canContinueToUse(furnaceBacking) : canUse(); }

    public boolean isInterruptable() { return furnaceBacking == null || NmsGoals.isInterruptable(furnaceBacking); }

    public void start() { if (furnaceBacking != null) NmsGoals.start(furnaceBacking); }

    public void stop() { if (furnaceBacking != null) NmsGoals.stop(furnaceBacking); }

    public boolean requiresUpdateEveryTick() { return furnaceBacking != null && NmsGoals.requiresUpdateEveryTick(furnaceBacking); }

    public void tick() { if (furnaceBacking != null) NmsGoals.tick(furnaceBacking); }

    public void setFlags(EnumSet<Flag> flags) {
        if (furnaceBacking != null) {
            NmsGoals.setFlags(furnaceBacking, flags);
            return;
        }
        goalTypes.clear();
        goalTypes.addAllUnchecked(flags);
        if (goalTypes.size() == 0) goalTypes.addUnchecked(Flag.UNKNOWN_BEHAVIOR);
    }

    /**
     * Paper: 플러그인이 만든 목표는 살아 있는 깃발 집합(서버가 확인할 때마다 읽는다). 서버 목표를 보는 shim 은 부를 때의 사본(고쳐도 서버 목표에 닿지 않는다 —
     * 고치는 길은 {@link #setFlags}).
     */
    public OptimizedSmallEnumSet<Flag> getFlags() {
        if (furnaceBacking == null) return goalTypes;
        OptimizedSmallEnumSet<Flag> out = new OptimizedSmallEnumSet<>(Flag.class);
        int m = NmsGoals.flags(furnaceBacking);
        for (int c = 0; c < 4; c++) if ((m & 1 << c) != 0) out.addUnchecked(NmsGoals.flagOfControl(c));
        if (out.size() == 0) out.addUnchecked(Flag.UNKNOWN_BEHAVIOR);
        return out;
    }

    protected int adjustedTickDelay(int ticks) { return requiresUpdateEveryTick() ? ticks : reducedTickDelay(ticks); }

    protected static int reducedTickDelay(int serverTicks) { return net.minecraft.util.Mth.positiveCeilDiv(serverTicks, 2); }

    @Override
    public String toString() { return getClass().getSimpleName(); }
}
