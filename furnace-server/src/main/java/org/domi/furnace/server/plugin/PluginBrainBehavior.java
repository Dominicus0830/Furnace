// 플러그인 BrainBehavior 를 서버 행동 자리에 끼우는 어댑터. 브레인 틱(소유 region 스레드)에서 바닐라 행동과 같은 순서로 불린다.
// 플러그인이 꺼졌거나 콜백이 예외를 던졌으면 다시 부르지 않고 스스로 빠진다(예외는 로그 한 번).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.entity.ai.brain.BrainBehavior;
import org.domi.furnace.api.entity.ai.brain.MemoryCondition;
import org.domi.furnace.api.entity.ai.brain.MemoryKey;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.ExternalBehavior;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.slf4j.LoggerFactory;

final class PluginBrainBehavior implements ExternalBehavior {

    /** 시작 조건 하나: 바닐라 기억(type) 또는 플러그인 기억(extraKey). */
    private record Condition(MemoryModuleType<?> type, String extraKey, MemoryCondition want) {}

    final FurnacePlugin owner;
    final BrainBehavior behavior;
    final String name;
    private final MobBrainImpl brain;
    private final MobAiImpl ai;
    private final List<Condition> conditions = new ArrayList<>();
    private Status status = Status.STOPPED;
    private boolean broken;

    PluginBrainBehavior(FurnacePlugin owner, BrainBehavior behavior, MobBrainImpl brain) {
        this.owner = owner;
        this.behavior = behavior;
        String n = behavior.name();
        this.name = n == null ? behavior.getClass().getName() : n;
        this.brain = brain;
        this.ai = new MobAiImpl(brain.host(), brain.mob);
        Brain<Mob> b = brain.brain();
        for (Map.Entry<MemoryKey<?>, MemoryCondition> e : behavior.requiredMemories().entrySet()) {
            MemoryKey<?> k = e.getKey();
            MobBrainImpl.register(b, k);
            conditions.add(k.isVanilla() ? new Condition(MobBrainImpl.vanilla(k), null, e.getValue()) : new Condition(null, k.key(), e.getValue()));
        }
    }

    private boolean alive(ChunkLevel level, Mob mob) {
        if (!broken && owner.isEnabled()) return true;
        @SuppressWarnings("unchecked") Brain<Mob> b = (Brain<Mob>) mob.getBrain();
        b.removeBehaviors((a, x) -> x == this, level, mob);
        return false;
    }

    private void fail(String where, Throwable t) {
        broken = true;
        LoggerFactory.getLogger(PluginBrainBehavior.class).warn("플러그인 {} 의 두뇌 행동 {} 가 {} 에서 예외를 던져 뗀다", owner, name, where, t);
    }

    private boolean conditionsMet(Mob mob) {
        Brain<?> b = mob.getBrain();
        for (Condition c : conditions) {
            if (c.type != null) {
                MemoryStatus s = switch (c.want) {
                    case PRESENT -> MemoryStatus.VALUE_PRESENT;
                    case ABSENT -> MemoryStatus.VALUE_ABSENT;
                    case REGISTERED -> MemoryStatus.REGISTERED;
                };
                if (!b.checkMemory(c.type, s)) return false;
            } else {
                var v = b.extraMemoryEntries().get(c.extraKey);
                boolean ok = switch (c.want) {
                    case PRESENT -> v != null && v.isPresent();
                    case ABSENT -> v != null && v.isEmpty();
                    case REGISTERED -> v != null;
                };
                if (!ok) return false;
            }
        }
        return true;
    }

    @Override
    public Status getStatus() { return status; }

    @Override
    public boolean tryStart(ChunkLevel level, Mob entity, long gameTime) {
        if (!alive(level, entity) || !conditionsMet(entity)) return false;
        try {
            if (!behavior.shouldStart(ai, brain)) return false;
        } catch (Throwable t) {
            fail("shouldStart", t);
            alive(level, entity);
            return false;
        }
        status = Status.RUNNING;
        try {
            behavior.start(ai, brain);
        } catch (Throwable t) {
            fail("start", t);
            alive(level, entity);
        }
        return true;
    }

    @Override
    public void tickOrStop(ChunkLevel level, Mob entity, long gameTime) {
        boolean go;
        try {
            go = !broken && owner.isEnabled() && behavior.shouldContinue(ai, brain);
        } catch (Throwable t) {
            fail("shouldContinue", t);
            go = false;
        }
        if (go) {
            try {
                behavior.tick(ai, brain);
            } catch (Throwable t) {
                fail("tick", t);
                doStop(level, entity, gameTime);
            }
        } else {
            doStop(level, entity, gameTime);
        }
        if (broken || !owner.isEnabled()) alive(level, entity);
    }

    @Override
    public void doStop(ChunkLevel level, Mob entity, long gameTime) {
        if (status == Status.STOPPED) return;
        status = Status.STOPPED;
        if (broken || !owner.isEnabled()) return;
        try {
            behavior.stop(ai, brain);
        } catch (Throwable t) {
            fail("stop", t);
        }
    }

    @Override
    public String debugString() { return name; }

    @Override
    public String toString() { return "PluginBrainBehavior[" + name + "]"; }
}
