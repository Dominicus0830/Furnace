// 바닐라 PlayTagWithOtherKids: 아기 주민 술래잡기. 레벨 난수 1/10. 쫓기고 있으면 마을 안 무작위 점(20×8, 10번)으로 달아나고,
// 아니면 1~5명에게 쫓기는 아이(가장 적게 쫓기는 아이), 없으면 아무 아이나 쫓는다.
// 차이: 바닐라는 쫓기는 수를 HashMap(엔티티 identity 해시)으로 모아 같은 수일 때 순서가 실행마다 다르다. 여기서는 처음 본 순서.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public final class PlayTagWithOtherKids {
    private static final int MAX_FLEE_XZ_DIST = 20, MAX_FLEE_Y_DIST = 8, MAX_CHASERS_PER_TARGET = 5;
    private static final float FLEE_SPEED = 0.6F, CHASE_SPEED = 0.6F;

    private PlayTagWithOtherKids() {}

    public static OneShot<PathfinderMob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long time) {
                if (!present(mob, MemoryModuleType.VISIBLE_VILLAGER_BABIES) || !absent(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET)
                        || !registered(mob, MemoryModuleType.INTERACTION_TARGET)) return false;
                if (level.random().nextInt(10) != 0) return false;
                List<Targetable> kids = mob.getBrain().getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES).get();
                boolean chased = kids.stream().anyMatch(k -> k instanceof Mob m && m.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).filter(t -> t == mob).isPresent());
                if (chased) {
                    for (int i = 0; i < 10; i++) {
                        double[] v = LandRandomPos.getPos(mob, MAX_FLEE_XZ_DIST, MAX_FLEE_Y_DIST);
                        if (v != null && level.isVillage(new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2])))) {
                            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v, FLEE_SPEED, 0));
                            break;
                        }
                    }
                    return true;
                }
                Targetable target = findSomeoneBeingChased(kids);
                if (target == null && !kids.isEmpty()) target = kids.get(0);
                if (target != null) chaseKid(mob, target);
                return true;
            }
        };
    }

    private static void chaseKid(Mob mob, Targetable kid) {
        mob.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, kid);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(kid, true));
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(kid, false), CHASE_SPEED, 1));
    }

    private static Targetable findSomeoneBeingChased(List<Targetable> kids) {
        Map<Targetable, Integer> counts = new LinkedHashMap<>();
        for (Targetable k : kids) {
            if (k instanceof Mob m) m.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).ifPresent(t -> counts.merge(t, 1, Integer::sum));
        }
        Targetable best = null;
        int bestCount = Integer.MAX_VALUE;
        for (var e : counts.entrySet()) {
            int c = e.getValue();
            if (c > 0 && c <= MAX_CHASERS_PER_TARGET && c < bestCount) {
                best = e.getKey();
                bestCount = c;
            }
        }
        return best;
    }
}
