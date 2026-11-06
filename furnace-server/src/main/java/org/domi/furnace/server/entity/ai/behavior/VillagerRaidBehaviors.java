// 바닐라 주민 레이드·숨기 행동(1.21.1): ReactToBell, SetRaidStatus, RingBell, ResetRaidStatus, SetHiddenState, LocateHidingPlace, MoveToSkySeeingSpot.
// 모두 BehaviorBuilder 한 번짜리 행동(OneShot)이다. 난수는 바닐라와 같은 곳(레벨 난수·엔티티 난수)에서 같은 순서로 뽑는다.
// 차이: RingBell 의 종 울림 game_event 원인 엔티티는 넘기지 않는다(BlockActor 아님).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BellBlock;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiTypes;
import org.domi.furnace.server.world.raid.Raid;

public final class VillagerRaidBehaviors {
    private VillagerRaidBehaviors() {}

    /** ServerLevel.getRaidAt(블록 위치). */
    public static Raid raidAt(ChunkLevel level, Mob mob) { return level.raids() == null ? null : level.raids().getRaidAt(mob.blockPosition()); }

    /** VillagerGoalPackages.raidExistsAndActive: 레이드가 진행 중이고 이기거나 지지 않았다. */
    public static boolean raidExistsAndActive(ChunkLevel level, Mob mob) {
        Raid r = raidAt(level, mob);
        return r != null && r.isActive() && !r.isVictory() && !r.isLoss();
    }

    /** VillagerGoalPackages.raidExistsAndNotVictory(이름과 달리 이긴 레이드인지 본다). */
    public static boolean raidVictory(ChunkLevel level, Mob mob) {
        Raid r = raidAt(level, mob);
        return r != null && r.isVictory();
    }

    /** ReactToBell: 종소리를 들었고(HEARD_BELL_TIME) 레이드가 없으면 숨기(HIDE). */
    public static OneShot<Mob> reactToBell() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!present(mob, MemoryModuleType.HEARD_BELL_TIME)) return false;
                if (raidAt(level, mob) == null) mob.getBrain().setActiveActivityIfPossible(Activity.HIDE);
                return true;
            }
        };
    }

    /** SetRaidStatus: 레벨 난수 1/20 로, 레이드가 있으면 웨이브 중 = RAID, 아니면 PRE_RAID 를 기본·현재 활동으로. */
    public static OneShot<Mob> setRaidStatus() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (level.random().nextInt(20) != 0) return false;
                var brain = mob.getBrain();
                Raid r = raidAt(level, mob);
                if (r != null) {
                    Activity a = r.hasFirstWaveSpawned() && !r.isBetweenWaves() ? Activity.RAID : Activity.PRE_RAID;
                    brain.setDefaultActivity(a);
                    brain.setActiveActivityIfPossible(a);
                }
                return true;
            }
        };
    }

    /** RingBell: 레벨 난수 nextFloat 가 0.95 초과일 때, 모임 장소가 3칸 안(블록 거리 제곱 < 9)이고 종이면 울린다. */
    public static OneShot<Mob> ringBell() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!present(mob, MemoryModuleType.MEETING_POINT)) return false;
                if (level.random().nextFloat() <= 0.95F) return false;
                Pos p = mob.getBrain().getMemory(MemoryModuleType.MEETING_POINT).get().pos();
                Pos m = mob.blockPosition();
                long dx = p.x() - m.x(), dy = p.y() - m.y(), dz = p.z() - m.z();
                if (dx * dx + dy * dy + dz * dz < 9.0) {
                    int st = level.getBlockState(p);
                    if (B.is(st, "minecraft:bell") && B.behavior(st) instanceof BellBlock bell) bell.attemptToRing(null, level, p, -1);
                }
                return true;
            }
        };
    }

    /** ResetRaidStatus: 레벨 난수 1/20 로, 레이드가 없거나 멈췄거나 졌으면 기본 활동을 IDLE 로 하고 시간표대로. */
    public static OneShot<Mob> resetRaidStatus() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (level.random().nextInt(20) != 0) return false;
                var brain = mob.getBrain();
                Raid r = raidAt(level, mob);
                if (r == null || r.isStopped() || r.isLoss()) {
                    brain.setDefaultActivity(Activity.IDLE);
                    brain.updateActivityFromSchedule(level.dayTime(), level.gameTime());
                }
                return true;
            }
        };
    }

    /**
     * SetHiddenState(초, 가까움): 숨을 곳에 가까이 있던 틱을 세다가 (초×20)를 넘거나 종소리 뒤 300틱이 지나면
     * 종소리·숨을 곳 기억을 지우고 시간표대로 활동을 되돌린다. 세는 값은 이 행동 인스턴스 하나에 묶인다(바닐라 MutableInt).
     */
    public static OneShot<Mob> setHiddenState(int seconds, int closeEnough) {
        int duration = seconds * 20;
        int[] counter = {0};
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!present(mob, MemoryModuleType.HIDING_PLACE) || !present(mob, MemoryModuleType.HEARD_BELL_TIME)) return false;
                var brain = mob.getBrain();
                long heard = brain.getMemory(MemoryModuleType.HEARD_BELL_TIME).get();
                boolean timeout = heard + 300L <= time;
                if (counter[0] <= duration && !timeout) {
                    Pos p = brain.getMemory(MemoryModuleType.HIDING_PLACE).get().pos();
                    if (ValidateNearbyPoi.closerToCenterThan(p, mob, closeEnough)) counter[0]++;
                    return true;
                }
                brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
                brain.eraseMemory(MemoryModuleType.HIDING_PLACE);
                brain.updateActivityFromSchedule(level.dayTime(), level.gameTime());
                counter[0] = 0;
                return true;
            }
        };
    }

    /**
     * LocateHidingPlace(반경, 속도, 가까움): 걸어갈 곳이 없을 때, 가까운(가까움+1 안, 가운데까지 가까움 미만) 집 → 반경 안 무작위 집(엔티티 난수)
     * → 기억한 집 순으로 숨을 곳을 정하고 길·시선·번식·상호작용 기억을 지운다. 멀면 그리로 걷는다.
     */
    public static OneShot<Mob> locateHidingPlace(int radius, float speed, int closeEnough) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.HOME) || !registered(mob, MemoryModuleType.HIDING_PLACE)
                        || !registered(mob, MemoryModuleType.PATH) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !registered(mob, MemoryModuleType.BREED_TARGET)
                        || !registered(mob, MemoryModuleType.INTERACTION_TARGET)) return false;
                var brain = mob.getBrain();
                PoiManager poi = level.poiManager();
                Optional<Pos> found = Optional.ofNullable(poi.find(t -> t == PoiTypes.HOME, p -> true, mob.blockPosition(), closeEnough + 1, PoiManager.Occupancy.ANY))
                        .filter(p -> ValidateNearbyPoi.closerToCenterThan(p, mob, closeEnough));
                if (found.isEmpty()) found = Optional.ofNullable(poi.getRandom(t -> t == PoiTypes.HOME, p -> true, PoiManager.Occupancy.ANY, mob.blockPosition(), radius, mob.random()));
                if (found.isEmpty()) found = brain.getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
                found.ifPresent(p -> {
                    brain.eraseMemory(MemoryModuleType.PATH);
                    brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                    brain.eraseMemory(MemoryModuleType.BREED_TARGET);
                    brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
                    brain.setMemory(MemoryModuleType.HIDING_PLACE, new GlobalPos(level.dimension(), p));
                    if (!ValidateNearbyPoi.closerToCenterThan(p, mob, closeEnough)) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, speed, closeEnough));
                });
                return true;
            }
        };
    }

    /** MoveToSkySeeingSpot: 하늘이 안 보이면 엔티티 난수로 10번 (±10, ±3, ±10) 칸을 골라 하늘이 보이는 첫 칸으로 걷는다. */
    public static OneShot<Mob> moveToSkySeeingSpot(float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                if (level.canSeeSky(mob.blockPosition())) return false;
                var r = mob.random();
                Pos base = mob.blockPosition();
                for (int i = 0; i < 10; i++) {
                    Pos p = base.offset(r.nextInt(20) - 10, r.nextInt(6) - 3, r.nextInt(20) - 10);
                    if (hasNoBlocksAbove(level, mob, p)) {
                        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new double[] {p.x() + 0.5, p.y(), p.z() + 0.5}, speed, 0));
                        break;
                    }
                }
                return true;
            }
        };
    }

    /** MoveToSkySeeingSpot.hasNoBlocksAbove: 하늘이 보이고 MOTION_BLOCKING 높이가 몹 발 높이 이하. */
    public static boolean hasNoBlocksAbove(ChunkLevel level, Mob mob, Pos p) {
        return level.canSeeSky(p) && level.getMotionBlockingHeight(p.x(), p.z()) <= mob.y();
    }
}
