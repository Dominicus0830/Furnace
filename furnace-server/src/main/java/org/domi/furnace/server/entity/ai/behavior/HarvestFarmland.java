// 바닐라 HarvestFarmland(농부): 주변 3×3×3 에서 다 자란 작물·빈 경작지 위 칸을 모아(레벨 난수로 하나 고름) 걸어가
// 다 자랐으면 부수고, 비었으면 가방의 첫 심을 씨앗(villager_plantable_seeds)을 심는다. 최대 200틱 일한다.
// 차이: 부수기·심기 game_event 의 원인 엔티티는 넘기지 않는다(BlockActor 아님).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.server.block.types.FarmBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.MobInventory;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.item.ItemTools;

public class HarvestFarmland extends Behavior<Villager> {
    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    private Pos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<Pos> validFarmlandAroundVillager = new ArrayList<>();

    public HarvestFarmland() {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        if (!level.gameRule("mobGriefing")) return false;
        if (!v.profession().equals("farmer")) return false;
        validFarmlandAroundVillager.clear();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    Pos p = new Pos((int) Math.floor(v.x() + i), (int) Math.floor(v.y() + j), (int) Math.floor(v.z() + k));
                    if (validPos(p, level)) validFarmlandAroundVillager.add(p);
                }
            }
        }
        aboveFarmlandPos = getValidFarmland(level);
        return aboveFarmlandPos != null;
    }

    private Pos getValidFarmland(ChunkLevel level) {
        return validFarmlandAroundVillager.isEmpty() ? null : validFarmlandAroundVillager.get(level.random().nextInt(validFarmlandAroundVillager.size()));
    }

    /** validPos: 다 자란 작물(CropBlock)이거나, 빈 칸이고 아래가 경작지. */
    static boolean validPos(Pos p, ChunkLevel level) {
        int st = level.getBlockState(p);
        int below = level.getBlockState(p.below());
        return B.behavior(st) instanceof CropBlock c && c.isMaxAge(st) || B.isAir(st) && B.behavior(below) instanceof FarmBlock;
    }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        if (time > nextOkStartTime && aboveFarmlandPos != null) {
            v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(aboveFarmlandPos));
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(aboveFarmlandPos), SPEED_MODIFIER, 1));
        }
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        timeWorkedSoFar = 0;
        nextOkStartTime = time + 40L;
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        if (aboveFarmlandPos != null && !closerToCenterThan(aboveFarmlandPos, v, 1.0)) return;
        if (aboveFarmlandPos != null && time > nextOkStartTime) {
            int st = level.getBlockState(aboveFarmlandPos);
            var block = B.behavior(st);
            var below = B.behavior(level.getBlockState(aboveFarmlandPos.below()));
            if (block instanceof CropBlock c && c.isMaxAge(st)) level.destroyBlock(aboveFarmlandPos, true);
            if (B.isAir(st) && below instanceof FarmBlock && v.hasFarmSeeds()) {
                MobInventory inv = v.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack s = inv.get(i);
                    boolean planted = false;
                    // 바닐라: villager_plantable_seeds 태그에 들고 BlockItem 이면 그 블록의 기본 상태를 심는다(데이터 팩이 씨앗을 더할 수 있다)
                    var blockItem = Villager.isPlantableSeed(s) ? org.domi.furnace.server.block.BlockItems.entry(ItemTools.itemName(s)) : null;
                    if (blockItem != null && blockItem.block() >= 0) {
                        int state = B.defaultState(blockItem.block());
                        level.setBlock(aboveFarmlandPos, state, 3);
                        level.gameEventFrom("minecraft:block_place", aboveFarmlandPos, v, state);
                        planted = true;
                    }
                    if (planted) {
                        level.playSound(null, aboveFarmlandPos.x(), aboveFarmlandPos.y(), aboveFarmlandPos.z(), "minecraft:item.crop.plant", "blocks", 1.0F, 1.0F);
                        inv.set(i, s.count() <= 1 ? ItemStack.EMPTY : s.withCount(s.count() - 1));
                        break;
                    }
                }
            }
            if (block instanceof CropBlock c && !c.isMaxAge(st)) {
                validFarmlandAroundVillager.remove(aboveFarmlandPos);
                aboveFarmlandPos = getValidFarmland(level);
                if (aboveFarmlandPos != null) {
                    nextOkStartTime = time + 20L;
                    v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(aboveFarmlandPos), SPEED_MODIFIER, 1));
                    v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(aboveFarmlandPos));
                }
            }
        }
        timeWorkedSoFar++;
    }

    /** Vec3i.closerToCenterThan(몹 위치, d). */
    private static boolean closerToCenterThan(Pos p, Villager v, double d) { return ValidateNearbyPoi.closerToCenterThan(p, v, d); }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return timeWorkedSoFar < HARVEST_DURATION; }
}
