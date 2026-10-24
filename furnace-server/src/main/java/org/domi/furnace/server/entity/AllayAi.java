// 바닐라 AllayAi: 알레이 브레인 구성 — core/idle 활동. 아이템을 줍고, 좋아하는 플레이어에게 가져다주며, 따라다닌다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.PositionTracker;
import org.domi.furnace.server.world.GameMode;

public final class AllayAi {
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_DEPOSIT_TARGET = 2.25F;
    private static final float SPEED_MULTIPLIER_WHEN_RETRIEVING_ITEM = 1.75F;
    private static final int CLOSE_ENOUGH_TO_TARGET = 4;
    private static final int TOO_FAR_FROM_TARGET = 16;
    private static final int MAX_LOOK_DISTANCE = 6;
    private static final int MIN_WAIT_DURATION = 30;
    private static final int MAX_WAIT_DURATION = 60;
    private static final int TIME_TO_FORGET_NOTEBLOCK = 600;
    private static final int DISTANCE_TO_WANTED_ITEM = 32;
    private static final int GIVE_ITEM_TIMEOUT_DURATION = 20;

    private AllayAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Allay> makeBrain(Brain<Allay> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Allay> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new AnimalPanic(2.5F), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                new CountDownCooldownTicks(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS), new CountDownCooldownTicks(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS)));
    }

    private static Map.Entry<Integer, BehaviorControl<? super Allay>> p(int prio, BehaviorControl<? super Allay> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Allay>, Integer> w(BehaviorControl<? super Allay> b, int weight) { return Map.entry(b, weight); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Allay> brain) {
        brain.addActivityWithConditions(Activity.IDLE, List.of(
                p(0, GoToWantedItem.<Allay>create(a -> true, SPEED_MULTIPLIER_WHEN_RETRIEVING_ITEM, true, DISTANCE_TO_WANTED_ITEM)),
                p(1, new GoAndGiveItemsToTarget<Allay>(AllayAi::getItemDepositPosition, SPEED_MULTIPLIER_WHEN_FOLLOWING_DEPOSIT_TARGET, GIVE_ITEM_TIMEOUT_DURATION)),
                p(2, StayCloseToTarget.<Allay>create(AllayAi::getItemDepositPosition, a -> !a.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM), CLOSE_ENOUGH_TO_TARGET,
                        TOO_FAR_FROM_TARGET, SPEED_MULTIPLIER_WHEN_FOLLOWING_DEPOSIT_TARGET)),
                p(3, SetEntityLookTargetSometimes.create(MAX_LOOK_DISTANCE, UniformInt.of(MIN_WAIT_DURATION, MAX_WAIT_DURATION))),
                p(4, new RunOne<Allay>(List.<Map.Entry<? extends BehaviorControl<? super Allay>, Integer>>of(w(RandomStroll.fly(SPEED_MULTIPLIER_WHEN_IDLING), 2),
                        w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 2), w(new DoNothing(30, 60), 1))))),
                Set.of());
    }

    public static void updateActivity(Allay allay) { allay.allayBrain().setActiveActivityToFirstValid(List.of(Activity.IDLE)); }

    /** 음표 블록 소리를 들었다: 좋아하는 음표 블록으로 기억한다(TODO: 게임 이벤트가 생기면 연결). */
    public static void hearNoteblock(Mob mob, Pos pos) {
        var brain = mob.getBrain();
        GlobalPos g = new GlobalPos(mob.level().dimension(), pos);
        Optional<GlobalPos> cur = brain.getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
        if (cur.isEmpty()) {
            brain.setMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION, g);
            brain.setMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS, TIME_TO_FORGET_NOTEBLOCK);
        } else if (cur.get().equals(g)) {
            brain.setMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS, TIME_TO_FORGET_NOTEBLOCK);
        }
    }

    private static Optional<PositionTracker> getItemDepositPosition(Allay allay) {
        var brain = allay.allayBrain();
        Optional<GlobalPos> noteblock = brain.getMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
        if (noteblock.isPresent()) {
            GlobalPos g = noteblock.get();
            if (shouldDepositItemsAtLikedNoteblockPosition(allay, brain, g)) return Optional.of(new BlockPosTracker(g.pos().above()));
            brain.eraseMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
        }
        return getLikedPlayerPositionTracker(allay);
    }

    private static boolean hasNearestVisibleWantedItem(Allay allay) { return allay.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM); }

    private static boolean shouldDepositItemsAtLikedNoteblockPosition(Allay allay, Brain<Allay> brain, GlobalPos pos) {
        Optional<Integer> cd = brain.getMemory(MemoryModuleType.LIKED_NOTEBLOCK_COOLDOWN_TICKS);
        return allay.level().dimension().equals(pos.dimension()) && org.domi.furnace.server.block.B.is(allay.level().getBlockState(pos.pos()), "minecraft:note_block") && cd.isPresent();
    }

    public static Optional<Player> getLikedPlayer(Mob mob) {
        Optional<UUID> id = mob.getBrain().getMemory(MemoryModuleType.LIKED_PLAYER);
        if (id.isEmpty()) return Optional.empty();
        Player[] found = new Player[1];
        mob.level().forEachPlayer(p -> {
            if (p.uuid().equals(id.get())) found[0] = p;
        });
        Player p = found[0];
        if (p != null && (p.gameMode() == GameMode.SURVIVAL || p.gameMode() == GameMode.CREATIVE)) {
            double dx = p.x() - mob.x(), dy = p.y() - mob.y(), dz = p.z() - mob.z();
            if (dx * dx + dy * dy + dz * dz < 64.0 * 64.0) return Optional.of(p);
        }
        return Optional.empty();
    }

    private static Optional<PositionTracker> getLikedPlayerPositionTracker(Mob mob) { return getLikedPlayer(mob).map(p -> new EntityTracker(p, true)); }
}
