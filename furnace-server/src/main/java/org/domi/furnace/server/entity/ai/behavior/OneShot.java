// 바닐라 OneShot: 시작할 때 trigger 한 번만 실행하고 곧바로 끝나는 행동(선언형 BehaviorBuilder 행동들의 공통 바탕).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public abstract class OneShot<E extends Mob> implements BehaviorControl<E> {
    private Status status = Status.STOPPED;

    @Override public final Status getStatus() { return status; }

    @Override
    public final boolean tryStart(ChunkLevel level, E entity, long gameTime) {
        if (trigger(level, entity, gameTime)) {
            status = Status.RUNNING;
            return true;
        }
        return false;
    }

    @Override public final void tickOrStop(ChunkLevel level, E entity, long gameTime) { doStop(level, entity, gameTime); }

    @Override public final void doStop(ChunkLevel level, E entity, long gameTime) { status = Status.STOPPED; }

    @Override public String debugString() { return getClass().getSimpleName(); }

    protected abstract boolean trigger(ChunkLevel level, E entity, long gameTime);

    // ---- 기억 조건 도우미(BehaviorBuilder: present = 값 있음, absent = 값 없음, registered = 등록만)
    protected static boolean present(Mob m, MemoryModuleType<?> t) { return m.getBrain().checkMemory(t, org.domi.furnace.server.entity.ai.brain.MemoryStatus.VALUE_PRESENT); }

    protected static boolean absent(Mob m, MemoryModuleType<?> t) { return m.getBrain().checkMemory(t, org.domi.furnace.server.entity.ai.brain.MemoryStatus.VALUE_ABSENT); }

    protected static boolean registered(Mob m, MemoryModuleType<?> t) { return m.getBrain().checkMemory(t, org.domi.furnace.server.entity.ai.brain.MemoryStatus.REGISTERED); }

    /** BehaviorBuilder.triggerIf(predicate). */
    public static <E extends Mob> OneShot<E> triggerIf(Predicate<E> p) {
        return new OneShot<>() {
            @Override protected boolean trigger(ChunkLevel level, E entity, long gameTime) { return p.test(entity); }
        };
    }

    public static <E extends Mob> OneShot<E> triggerIf(BiPredicate<ChunkLevel, E> p) {
        return new OneShot<>() {
            @Override protected boolean trigger(ChunkLevel level, E entity, long gameTime) { return p.test(level, entity); }
        };
    }

    /** BehaviorBuilder.sequence(condition, action): condition 이 참일 때만 action 을 시작한다. */
    public static <E extends Mob> OneShot<E> sequence(BehaviorControl<? super E> condition, BehaviorControl<? super E> action) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E entity, long gameTime) {
                return condition.tryStart(level, entity, gameTime) && action.tryStart(level, entity, gameTime);
            }
        };
    }
}
