// 바닐라 VillagerGoalPackages(1.21.1): 활동별 행동 묶음과 우선순위·가중치를 그대로 옮긴다.
// 아직 옮기지 않은 행동(Unported: 밭 갈기·뼛가루·거래품 보여 주기·영웅 선물·레이드/숨기 묶음)은 시작하지 않는 자리표시로 두어
// 무작위 묶음(RunOne)의 가중치·순서는 유지한다. 레이드·숨기 활동(PRE_RAID/RAID/HIDE)은 들어가게 하는 행동(종 울림·레이드 상태)이 없어 등록하지 않는다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.spawn.EntityMeta;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.world.poi.PoiTypes;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class VillagerGoalPackages {
    public static final int INTERACT_DIST_SQR = 5;
    private static final float STROLL_SPEED_MODIFIER = 0.4F;

    private VillagerGoalPackages() {}

    /** 아직 옮기지 않은 바닐라 행동: 시작하지 않는다(이름만 남긴다). */
    static final class Unported<E extends Mob> extends OneShot<E> {
        private final String name;

        Unported(String name) { this.name = name; }

        @Override protected boolean trigger(ChunkLevel level, E entity, long time) { return false; }

        @Override public String debugString() { return "Unported(" + name + ")"; }
    }

    private static Map.Entry<Integer, BehaviorControl<? super Villager>> p(int prio, BehaviorControl b) { return Map.entry(prio, (BehaviorControl<? super Villager>) b); }

    private static Map.Entry<BehaviorControl<? super Villager>, Integer> w(BehaviorControl b, int weight) { return Map.entry((BehaviorControl<? super Villager>) b, weight); }

    private static Map.Entry<OneShot<? super Villager>, Integer> t(OneShot b, int weight) { return Map.entry((OneShot<? super Villager>) b, weight); }

    private static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> list(Map.Entry... e) {
        List out = new ArrayList<>();
        for (Map.Entry x : e) out.add(x);
        return out;
    }

    private static boolean isType(Targetable t, EntityType type) { return t instanceof Entity e && e.type() == type; }

    private static OneShot lookAt(EntityType type, float dist) { return SetEntityLookTarget.create(x -> isType(x, type), dist); }

    private static OneShot lookAtPlayer(float dist) { return SetEntityLookTarget.create(Targetable::isPlayer, dist); }

    private static OneShot lookAt(MobCategory cat, float dist) {
        return SetEntityLookTarget.create(x -> x instanceof Entity e && !x.isPlayer() && EntityMeta.category(e.type().name()) == cat, dist);
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getCorePackage(String profession, float speed) {
        return list(
                p(0, new Swim(0.8F)),
                p(0, InteractWithDoor.create()),
                p(0, new LookAtTargetSink(45, 90)),
                p(0, new VillagerPanicTrigger()),
                p(0, WakeUp.create()),
                p(0, new Unported<>("ReactToBell")),
                p(0, new Unported<>("SetRaidStatus")),
                p(0, ValidateNearbyPoi.create(Villager.heldJobSite(profession), MemoryModuleType.JOB_SITE)),
                p(0, ValidateNearbyPoi.create(Villager.acquirableJobSite(profession), MemoryModuleType.POTENTIAL_JOB_SITE)),
                p(1, new MoveToTargetSink()),
                p(2, PoiCompetitorScan.create()),
                p(3, new LookAndFollowTradingPlayerSink(speed)),
                p(5, GoToWantedItem.create(speed, false, 4)),
                p(6, AcquirePoi.create(Villager.acquirableJobSite(profession), MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE, true, Optional.empty())),
                p(7, new GoToPotentialJobSite(speed)),
                p(8, YieldJobSite.create(speed)),
                p(10, AcquirePoi.create(t -> t == PoiTypes.HOME, MemoryModuleType.HOME, false, Optional.of((byte) 14))),
                p(10, AcquirePoi.create(t -> t == PoiTypes.MEETING, MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
                p(10, AssignProfessionFromJobSite.create()),
                p(10, ResetProfession.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getWorkPackage(String profession, float speed) {
        boolean farmer = profession.equals("farmer");
        WorkAtPoi work = farmer ? new WorkAtComposter() : new WorkAtPoi();
        return list(
                getMinimalLookBehavior(),
                p(5, new RunOne<Villager>(List.of(
                        w(work, 7),
                        w(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED_MODIFIER, 4), 2),
                        w(StrollToPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED_MODIFIER, 1, 10), 5),
                        w(StrollToPoiList.create(MemoryModuleType.SECONDARY_JOB_SITE, speed, 1, 6, MemoryModuleType.JOB_SITE), 5),
                        w(new Unported<>("HarvestFarmland"), farmer ? 2 : 5),
                        w(new Unported<>("UseBonemeal"), farmer ? 4 : 7)))),
                p(10, new Unported<>("ShowTradesToPlayer")),
                p(10, SetLookAndInteract.create(Targetable::isPlayer, 4)),
                p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, speed, 9, 100, 1200)),
                p(3, new Unported<>("GiveGiftToHero")),
                p(99, UpdateActivityFromSchedule.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getPlayPackage(float speed) {
        return list(
                p(0, new MoveToTargetSink(80, 120)),
                getFullLookBehavior(),
                p(5, PlayTagWithOtherKids.create()),
                p(5, new RunOne<Villager>(Map.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES, MemoryStatus.VALUE_ABSENT), List.of(
                        w(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speed, 2), 2),
                        w(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speed, 2), 1),
                        w(VillageBoundRandomStroll.create(speed), 1),
                        w(SetWalkTargetFromLookTarget.create(speed, 2), 1),
                        w(new JumpOnBed(speed), 2),
                        w(new DoNothing(20, 40), 2)))),
                p(99, UpdateActivityFromSchedule.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getRestPackage(String profession, float speed) {
        return list(
                p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, speed, 1, 150, 1200)),
                p(3, ValidateNearbyPoi.create(t -> t == PoiTypes.HOME, MemoryModuleType.HOME)),
                p(3, new SleepInBed()),
                p(5, new RunOne<Villager>(Map.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT), List.of(
                        w(SetClosestHomeAsWalkTarget.create(speed), 1),
                        w(InsideBrownianWalk.create(speed), 4),
                        w(GoToClosestVillage.create(speed, 4), 2),
                        w(new DoNothing(20, 40), 2)))),
                getMinimalLookBehavior(),
                p(99, UpdateActivityFromSchedule.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getMeetPackage(String profession, float speed) {
        return list(
                p(2, TriggerGate.triggerOneShuffled(List.of(t(StrollAroundPoi.create(MemoryModuleType.MEETING_POINT, 0.4F, 40), 2), t(SocializeAtBell.create(), 2)))),
                p(10, new Unported<>("ShowTradesToPlayer")),
                p(10, SetLookAndInteract.create(Targetable::isPlayer, 4)),
                p(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, speed, 6, 100, 200)),
                p(3, new Unported<>("GiveGiftToHero")),
                p(3, ValidateNearbyPoi.create(t -> t == PoiTypes.MEETING, MemoryModuleType.MEETING_POINT)),
                p(3, new GateBehavior<Villager>(Map.of(), Set.of(MemoryModuleType.INTERACTION_TARGET), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.RUN_ONE,
                        List.of(w(new TradeWithVillager(), 1)))),
                getFullLookBehavior(),
                p(99, UpdateActivityFromSchedule.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getIdlePackage(String profession, float speed) {
        return list(
                p(2, new RunOne<Villager>(List.of(
                        w(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, speed, 2), 2),
                        w(InteractWith.<Villager, AgeableMob>of(EntityType.VILLAGER, 8, AgeableMob::canBreed, AgeableMob::canBreed, MemoryModuleType.BREED_TARGET, speed, 2), 1),
                        w(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speed, 2), 1),
                        w(VillageBoundRandomStroll.create(speed), 1),
                        w(SetWalkTargetFromLookTarget.create(speed, 2), 1),
                        w(new JumpOnBed(speed), 1),
                        w(new DoNothing(30, 60), 1)))),
                p(3, new Unported<>("GiveGiftToHero")),
                p(3, SetLookAndInteract.create(Targetable::isPlayer, 4)),
                p(3, new Unported<>("ShowTradesToPlayer")),
                p(3, new GateBehavior<Villager>(Map.of(), Set.of(MemoryModuleType.INTERACTION_TARGET), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.RUN_ONE,
                        List.of(w(new TradeWithVillager(), 1)))),
                p(3, new GateBehavior<Villager>(Map.of(), Set.of(MemoryModuleType.BREED_TARGET), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.RUN_ONE,
                        List.of(w(new VillagerMakeLove(), 1)))),
                getFullLookBehavior(),
                p(99, UpdateActivityFromSchedule.create()));
    }

    public static List<Map.Entry<Integer, ? extends BehaviorControl<? super Villager>>> getPanicPackage(String profession, float speed) {
        float f = speed * 1.5F;
        return list(
                p(0, VillagerCalmDown.create()),
                p(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                p(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                p(3, VillageBoundRandomStroll.create(f, 2, 2)),
                getMinimalLookBehavior());
    }

    private static Map.Entry<Integer, BehaviorControl<? super Villager>> getFullLookBehavior() {
        return p(5, new RunOne<Villager>(List.of(
                w(lookAt(EntityType.CAT, 8.0F), 8),
                w(lookAt(EntityType.VILLAGER, 8.0F), 2),
                w(lookAtPlayer(8.0F), 2),
                w(lookAt(MobCategory.CREATURE, 8.0F), 1),
                w(lookAt(MobCategory.WATER_CREATURE, 8.0F), 1),
                w(lookAt(MobCategory.AXOLOTLS, 8.0F), 1),
                w(lookAt(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0F), 1),
                w(lookAt(MobCategory.WATER_AMBIENT, 8.0F), 1),
                w(lookAt(MobCategory.MONSTER, 8.0F), 1),
                w(new DoNothing(30, 60), 2))));
    }

    private static Map.Entry<Integer, BehaviorControl<? super Villager>> getMinimalLookBehavior() {
        return p(5, new RunOne<Villager>(List.of(w(lookAt(EntityType.VILLAGER, 8.0F), 2), w(lookAtPlayer(8.0F), 2), w(new DoNothing(30, 60), 8))));
    }
}
