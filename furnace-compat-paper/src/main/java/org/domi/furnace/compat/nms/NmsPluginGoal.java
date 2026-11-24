package org.domi.furnace.compat.nms;

import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 플러그인이 만든 목표({@code Goal} 상속)를 서버 선택기에 넣는 콜백(몹 목표 설계 M2a). 서버가 그 몹의 주인 스레드에서 부른다 —
 * 콜백은 플러그인 목표의 메서드를 그대로 부른다(재정의하지 않은 것은 바닐라 기본 몸통). 깃발은 확인할 때마다 {@code getFlags()} 를 읽는다(Paper 처럼
 * 플러그인이 받은 집합을 고쳐도 다음 확인에 보인다).
 */
final class NmsPluginGoal implements ChunkInternals.GoalCallbacks {

    private final Goal goal;
    /** 그 목표 클래스를 올린 플러그인(플러그인 jar 의 클래스가 아니면 null — 꺼짐이 없다). */
    private final JavaPlugin plugin;

    NmsPluginGoal(Goal goal) {
        this.goal = goal;
        this.plugin = providing(goal.getClass());
    }

    private static JavaPlugin providing(Class<?> c) {
        try {
            return JavaPlugin.getProvidingPlugin(c);
        } catch (IllegalArgumentException | IllegalStateException notFromAPlugin) {
            return null;
        }
    }

    @Override
    public Object target() { return goal; }

    @Override
    public String owner() { return plugin != null ? plugin.getName() : goal.getClass().getName(); }

    @Override
    public boolean alive() { return plugin == null || plugin.isEnabled(); }

    @Override
    public boolean canUse() { return goal.canUse(); }

    @Override
    public boolean canContinueToUse() { return goal.canContinueToUse(); }

    @Override
    public boolean isInterruptable() { return goal.isInterruptable(); }

    @Override
    public void start() { goal.start(); }

    @Override
    public void stop() { goal.stop(); }

    @Override
    public boolean requiresUpdateEveryTick() { return goal.requiresUpdateEveryTick(); }

    @Override
    public void tick() { goal.tick(); }

    /** Paper 깃발 집합의 비트(서수 UNKNOWN_BEHAVIOR·MOVE·LOOK·JUMP·TARGET) → 서버 순서(MOVE·LOOK·JUMP·TARGET). UNKNOWN_BEHAVIOR 는 서버가 쓰지 않는다. */
    @Override
    public int flags() { return (int) (goal.getFlags().getBackingSet() >>> 1) & 0xF; }
}
