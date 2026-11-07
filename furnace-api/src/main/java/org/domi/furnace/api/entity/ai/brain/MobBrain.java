// 두뇌(Brain)로 움직이는 몹(주민·피글린·아홀로틀·워든·염소·개구리 등)의 기억·센서·활동·행동 손잡이. MobAi.brain() 으로 얻는다.
// 그 몹을 가진 region 스레드에서만 쓴다 — 다른 스레드에서 부르면 ThreadOwnershipException. 몹이 사라졌으면 바꾸는 호출은 아무 일도 하지 않는다.
// 내부 구조(기억 종류 객체·행동 클래스)는 드러내지 않고 이름표 문자열 + 버전 중립 값(MemoryValueType)으로 다룬다.
// 몹이 두뇌를 새로 만들어도(주민 직업·나이 변화) 플러그인 센서·행동·플러그인 기억·고정 활동은 새 두뇌로 옮겨 간다.
package org.domi.furnace.api.entity.ai.brain;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface MobBrain {

    // ------------------------------------------------------------------ 기억

    /** 등록된 기억 전부(바닐라 + 플러그인, 등록 순서). */
    List<MemoryInfo> memories();

    /** 이 두뇌에 그 기억이 등록되어 있는가. */
    boolean hasMemory(MemoryKey<?> key);

    /** 기억 값. 비었거나 등록되지 않았으면 빈 값. 바닐라 기억인데 모양이 다르면 IllegalArgumentException. */
    <T> Optional<T> memory(MemoryKey<T> key);

    /** 남은 유효 틱. 비었거나 영구면 빈 값. */
    OptionalLong memoryExpiry(MemoryKey<?> key);

    /**
     * 영구 값으로 쓴다. 등록되지 않은 기억이면 false(바닐라처럼 무시 — registerMemory 로 먼저 등록). value 가 null 이거나 빈 목록이면 지운다.
     * 엔티티 모양은 같은 region 에서 살아 있는 그 엔티티를 찾아 쓴다(못 찾거나 그 기억이 받는 종류가 아니면 false).
     */
    <T> boolean setMemory(MemoryKey<T> key, T value);

    /** ttlTicks 틱 뒤 저절로 지워지는 값으로 쓴다(ttlTicks ≥ 1). 나머지는 setMemory 와 같다. */
    <T> boolean setMemory(MemoryKey<T> key, T value, long ttlTicks);

    /** 지운다. 등록되지 않았으면 false. */
    boolean eraseMemory(MemoryKey<?> key);

    /**
     * 이 두뇌에 기억을 등록한다(이미 있으면 아무 일도 없다). 바닐라 기억을 원래 쓰지 않는 몹에 등록하면 바닐라 행동은 그 값을 보지 않지만
     * 저장 방식이 있는 기억이면 저장은 된다(다시 불러올 때는 그 몹이 원래 쓰는 기억만 읽는다 — 바닐라 규칙). 플러그인 기억은 저장되지 않는다.
     */
    void registerMemory(MemoryKey<?> key);

    // ------------------------------------------------------------------ 센서

    /** 붙은 센서(바닐라 다음 플러그인, 도는 순서). */
    List<SensorKey> sensors();

    /** 플러그인 센서를 넣는다. 같은 이름표 센서가 이미 있으면 IllegalArgumentException. owner 가 꺼지면 스스로 빠진다. */
    void addSensor(FurnacePlugin owner, BrainSensor sensor);

    /** 그 이름표 센서를 뺀다(바닐라 센서도 — 그 센서가 채우던 기억은 등록된 채 남고, 더는 갱신되지 않는다). */
    boolean removeSensor(SensorKey key);

    // ------------------------------------------------------------------ 활동

    /** 행동이 붙은 활동 전부. */
    Set<ActivityKey> activities();

    /** 지금 켜진 활동(핵심 활동 포함). */
    Set<ActivityKey> activeActivities();

    /** 늘 켜져 있는 핵심 활동(보통 minecraft:core). */
    Set<ActivityKey> coreActivities();

    /** 다른 활동을 고를 수 없을 때 돌아가는 활동. */
    ActivityKey defaultActivity();

    /**
     * 그 활동의 조건(바닐라가 활동마다 거는 기억 조건)이 맞으면 바로 그 활동으로 바꾸고 true. 안 맞으면 바꾸지 않고 false.
     * 몹은 매 틱 스스로 활동을 다시 고르므로(주민 시간표, 피글린 상황 판단 등) 다음 틱에 되돌아갈 수 있다 — 붙잡아 두려면 forceActivity.
     */
    boolean trySetActivity(ActivityKey activity);

    /**
     * 그 활동을 조건 없이 켜고 붙잡아 둔다: releaseActivity 까지 몹이 스스로 다른 활동으로 바꾸려 하면 이 활동으로 머문다(핵심 활동은 계속 돈다).
     * 바꿀 때 바닐라처럼 꺼지는 활동이 지우는 기억은 지운다. 행동이 없는 활동(플러그인 활동 등)도 된다. 저장되지 않는다.
     */
    void forceActivity(ActivityKey activity);

    /** 붙잡은 활동을 놓는다. 다음 틱부터 몹이 스스로 활동을 고른다. 붙잡은 게 없었으면 false. */
    boolean releaseActivity();

    Optional<ActivityKey> forcedActivity();

    // ------------------------------------------------------------------ 행동

    /** 붙은 행동 전부(우선순위 순서). */
    List<BehaviorInfo> behaviors();

    /** 그 활동에 플러그인 행동을 붙인다. 없던 활동이면 조건 없는 새 활동이 된다. 같은 행동 객체를 두 번 넣으면 한 번만 붙는다. owner 가 꺼지면 스스로 빠진다. */
    void addBehavior(FurnacePlugin owner, ActivityKey activity, int priority, BrainBehavior behavior);

    /** 넣었던 그 플러그인 행동을 뺀다(돌고 있으면 멈춘 뒤). */
    boolean removeBehavior(BrainBehavior behavior);

    /** 그 활동의 행동을 바닐라 것까지 모두 뺀다(돌고 있으면 멈춘 뒤). 뺀 개수. 활동 자체와 그 조건은 남는다. */
    int clearBehaviors(ActivityKey activity);
}
