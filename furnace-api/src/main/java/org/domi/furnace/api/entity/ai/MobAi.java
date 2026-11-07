// 몹 하나의 AI 를 그 자리에서 다루는 손잡이. 그 몹을 가진 region 스레드에서만 쓴다 — 다른 스레드에서 부르면 ThreadOwnershipException.
// 얻는 곳: Mob.editAi(다음 틱 소유 스레드에서), Mob.aiNow(지금 소유 스레드일 때), MobGoal 콜백 인자.
// 붙잡아 두었다가 나중에 써도 되지만 그때도 소유 스레드여야 하고, 몹이 사라졌으면(isValid false) 바꾸는 호출은 아무 일도 하지 않는다.
package org.domi.furnace.api.entity.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.entity.Mob;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;

public interface MobAi {

    UUID uniqueId();

    /** 엔티티 종류 키(예: minecraft:zombie). */
    String type();

    /** 지금 위치. */
    Location location();

    /** 아직 월드에 있는가. */
    boolean isValid();

    /** NoAI 표시가 붙었는가(붙으면 목표가 돌지 않는다). */
    boolean isNoAi();

    /** 두뇌(Brain)로 움직이는 몹인가(주민·피글린 등). 목표도 함께 돌지만 두뇌가 같은 일을 하면 서로 다툰다. */
    boolean usesBrain();

    /** 두뇌 손잡이(기억·센서·활동·행동). 두뇌로 움직이지 않는 몹이면 빈 값. 같은 소유 스레드 규칙. */
    Optional<org.domi.furnace.api.entity.ai.brain.MobBrain> brain();

    /** 지금 상태의 스냅샷 핸들. */
    Mob snapshot();

    // ------------------------------------------------------------------ 목표

    /** 걸린 목표들(우선순위 순서가 아니라 넣은 순서). */
    List<GoalInfo> goals(GoalSet set);

    /** 지금 돌고 있는 목표들. */
    default List<GoalInfo> runningGoals(GoalSet set) { return goals(set).stream().filter(GoalInfo::running).toList(); }

    boolean hasGoal(GoalSet set, GoalKey key);

    /** 플러그인 목표를 넣는다. priority 가 작을수록 먼저. owner 가 꺼지면 목표는 스스로 빠진다. 같은 목표 객체를 두 번 넣으면 한 번만 걸린다. */
    void addGoal(FurnacePlugin owner, GoalSet set, int priority, MobGoal goal);

    /** 바닐라 목표를 넣는다. 이 몹 종류가 쓸 수 없는 목표면 IllegalArgumentException. */
    void addGoal(GoalSet set, int priority, VanillaGoal goal);

    /** 그 이름표 목표를 모두 뺀다(돌고 있으면 멈춘 뒤). 뺀 개수. 바닐라가 처음부터 넣은 목표도 빠진다. */
    int removeGoals(GoalSet set, GoalKey key);

    /** 넣었던 그 플러그인 목표 객체를 뺀다. */
    boolean removeGoal(GoalSet set, MobGoal goal);

    /** 그 묶음 목표를 모두 뺀다. 뺀 개수. */
    int removeAllGoals(GoalSet set);

    // ------------------------------------------------------------------ 움직임

    /** 그 자리까지 길을 찾아 간다. speed 는 이동 속도 배수(바닐라 목표는 보통 1.0). 길이 없으면 false. */
    boolean moveTo(double x, double y, double z, double speed);

    /** 같은 region 에 있는 그 엔티티(플레이어 포함)에게 간다. 못 찾거나 길이 없으면 false. */
    boolean moveTo(UUID entity, double speed);

    /** 따라가던 길을 버린다. */
    void stopMoving();

    /** 길을 따라가는 중인가. */
    boolean isMoving();

    /** 그쪽을 쳐다본다(다음 틱 고개가 돈다). */
    void lookAt(double x, double y, double z);

    /** 다음 틱 점프한다. */
    void jump();

    // ------------------------------------------------------------------ 공격 대상

    Optional<UUID> target();

    /** 같은 region 에 있는 그 엔티티(플레이어 포함)를 공격 대상으로. null 이면 대상을 지운다. 못 찾았거나 EntityTargetEvent 가 취소하면 false. */
    boolean setTarget(UUID entity);

    // ------------------------------------------------------------------ 길찾기

    /** 그 칸 종류의 이 몹 비용(음수면 못 감). */
    float pathCost(PathNodeType type);

    /** 그 칸 종류의 이 몹 비용을 바꾼다. 바닐라 몹도 종류별로 바꾸는 값이다(예: 불 몹은 용암 비용 0). */
    void setPathCost(PathNodeType type, float cost);

    /** 칸 판정 훅을 건다(hook 이 null 이면 뗀다, 몹 하나에 하나). 다음에 찾는 길부터 쓴다. owner 가 꺼지면 훅은 스스로 떨어진다. */
    void setPathTypeHook(FurnacePlugin owner, PathTypeHook hook);
}
