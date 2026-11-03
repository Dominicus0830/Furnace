// 바닐라 GoAndGiveItemsToTarget: 가방에 아이템이 있으면 대상(좋아하는 플레이어·음표 블록) 쪽으로 가서 하나씩 던져 준다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Allay;
import org.domi.furnace.server.entity.InventoryCarrier;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.PositionTracker;

public class GoAndGiveItemsToTarget<E extends Mob & InventoryCarrier> extends Behavior<E> {
    private static final int CLOSE_ENOUGH_DISTANCE_TO_TARGET = 3;
    private static final int ITEM_PICKUP_COOLDOWN_AFTER_THROWING = 60;
    private final Function<E, Optional<PositionTracker>> targetPositionGetter;
    private final float speedModifier;

    public GoAndGiveItemsToTarget(Function<E, Optional<PositionTracker>> targetPositionGetter, float speedModifier, int duration) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
                MemoryStatus.REGISTERED), duration);
        this.targetPositionGetter = targetPositionGetter;
        this.speedModifier = speedModifier;
    }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, E mob) { return canThrowItemToTarget(mob); }

    @Override protected boolean canStillUse(ChunkLevel level, E mob, long gameTime) { return canThrowItemToTarget(mob); }

    @Override
    protected void start(ChunkLevel level, E mob, long gameTime) {
        targetPositionGetter.apply(mob).ifPresent(t -> BehaviorUtils.setWalkAndLookTargetMemories(mob, t, speedModifier, CLOSE_ENOUGH_DISTANCE_TO_TARGET));
    }

    @Override
    protected void tick(ChunkLevel level, E mob, long gameTime) {
        Optional<PositionTracker> t = targetPositionGetter.apply(mob);
        if (t.isEmpty()) return;
        double[] p = t.get().currentPosition();
        double dx = p[0] - mob.x(), dy = p[1] - mob.eyeY(), dz = p[2] - mob.z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) < CLOSE_ENOUGH_DISTANCE_TO_TARGET) {
            ItemStack stack = mob.getInventory().removeItem(0, 1);
            if (!stack.isEmpty()) {
                throwItem(mob, stack, new double[] {p[0], p[1] + 1.0, p[2]});
                if (mob instanceof Allay allay) {   // 바닐라: 좋아하는 플레이어에게 allay_drop_item_on_block(대상 칸, 던진 아이템)
                    org.domi.furnace.server.entity.AllayAi.getLikedPlayer(mob).ifPresent(liked -> {
                        int bx = (int) Math.floor(p[0]), by = (int) Math.floor(p[1]), bz = (int) Math.floor(p[2]);
                        var block = org.domi.furnace.server.ServerCore.lootBlockState(level.getBlockState(new org.domi.furnace.server.block.Pos(bx, by, bz)));
                        var item = org.domi.furnace.server.ServerCore.lootStackOf(stack);
                        liked.triggerAdvancement(a -> a.triggerLocation("minecraft:allay_drop_item_on_block", bx, by, bz, block, item));
                    });
                }
                mob.getBrain().setMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, ITEM_PICKUP_COOLDOWN_AFTER_THROWING);
            }
        }
    }

    private boolean canThrowItemToTarget(E mob) {
        if (mob.getInventory().isEmpty()) return false;
        return targetPositionGetter.apply(mob).isPresent();
    }

    public static void throwItem(Mob mob, ItemStack stack, double[] target) {
        BehaviorUtils.throwItem(mob, stack, target, new double[] {0.2F, 0.3F, 0.2F}, 0.2F);
        var level = mob.level();
        if (level.gameTime() % 7L == 0L && level.random().nextDouble() < 0.9) {
            float pitch = Allay.THROW_SOUND_PITCHES[level.random().nextInt(Allay.THROW_SOUND_PITCHES.length)];
            level.playSound(null, mob, "entity.allay.item_thrown", "neutral", 1.0F, pitch);
        }
    }
}
