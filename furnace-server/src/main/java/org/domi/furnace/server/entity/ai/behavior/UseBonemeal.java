// 바닐라 UseBonemeal(농부): 10틱마다(지난 작업 뒤 160틱이 지났으면) 뼛가루가 있으면 주변 3×3×3 의 덜 자란 작물 하나를
// 레벨 난수 저수지 표집(nextInt(++n) == 0)으로 골라, 손에 뼛가루를 들고 가서 40틱마다 뿌린다. 최대 80번 일한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.ItemUse;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.MobInventory;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.item.ItemTools;

public class UseBonemeal extends Behavior<Villager> {
    private static final int BONEMEALING_DURATION = 80;
    private static final String BONE_MEAL = "minecraft:bone_meal";
    private long nextWorkCycleTime;
    private long lastBonemealingSession;
    private int timeWorkedSoFar;
    private Optional<Pos> cropPos = Optional.empty();

    public UseBonemeal() { super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT)); }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        if (v.tickCount % 10 == 0 && (lastBonemealingSession == 0L || lastBonemealingSession + 160L <= v.tickCount)) {
            if (v.getInventory().countItem(BONE_MEAL) <= 0) return false;
            cropPos = pickNextTarget(level, v);
            return cropPos.isPresent();
        }
        return false;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return timeWorkedSoFar < BONEMEALING_DURATION && cropPos.isPresent(); }

    private Optional<Pos> pickNextTarget(ChunkLevel level, Villager v) {
        Optional<Pos> out = Optional.empty();
        int n = 0;
        Pos base = v.blockPosition();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    Pos p = base.offset(i, j, k);
                    if (validPos(p, level) && level.random().nextInt(++n) == 0) out = Optional.of(p);
                }
            }
        }
        return out;
    }

    private static boolean validPos(Pos p, ChunkLevel level) {
        int st = level.getBlockState(p);
        return B.behavior(st) instanceof CropBlock c && !c.isMaxAge(st);
    }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        setCurrentCropAsTarget(v);
        v.setItemSlot(EquipmentSlot.MAINHAND, B.stack(BONE_MEAL, 1));
        nextWorkCycleTime = time;
        timeWorkedSoFar = 0;
    }

    private void setCurrentCropAsTarget(Villager v) {
        cropPos.ifPresent(p -> {
            BlockPosTracker t = new BlockPosTracker(p);
            v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, t);
            v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t, 0.5F, 1));
        });
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        lastBonemealingSession = v.tickCount;
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        Pos p = cropPos.get();
        if (time < nextWorkCycleTime || !ValidateNearbyPoi.closerToCenterThan(p, v, 1.0)) return;
        MobInventory inv = v.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.size(); i++) {
            if (ItemTools.itemName(inv.get(i)).equals(BONE_MEAL)) {
                slot = i;
                break;
            }
        }
        if (slot >= 0) {
            ItemStack s = inv.get(slot);
            int before = s.count();
            if (ItemUse.growCrop(level, null, 0, s, p)) {
                // growCrop 는 플레이어가 없으면 줄이지 않는다 — 바닐라 stack.shrink(1)
                inv.set(slot, before <= 1 ? ItemStack.EMPTY : s.withCount(before - 1));
                level.levelEvent(null, 1505, p, 15);
                cropPos = pickNextTarget(level, v);
                setCurrentCropAsTarget(v);
                nextWorkCycleTime = time + 40L;
            }
        }
        timeWorkedSoFar++;
    }
}
