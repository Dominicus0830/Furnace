// 바닐라 WorkAtComposter(농부): 밀 3개로 빵을 굽고(빵이 36개 이하일 때, 한 번에 최대 3개), 씨앗을 종류별 10개 남기고 최대 20개까지 퇴비통에 넣는다.
// 차이: 퇴비통 game_event 의 원인 엔티티는 넘기지 않는다(BlockActor 아님).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.ComposterBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.MobInventory;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.item.ItemTools;

public class WorkAtComposter extends WorkAtPoi {
    private static final List<String> COMPOSTABLE_ITEMS = List.of("minecraft:wheat_seeds", "minecraft:beetroot_seeds");

    @Override
    protected void useWorkstation(ChunkLevel level, Villager v) {
        var site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty()) return;
        GlobalPos g = site.get();
        int state = level.getBlockState(g.pos());
        if (B.is(state, "minecraft:composter")) {
            makeBread(v);
            compostItems(level, v, g, state);
        }
    }

    private void compostItems(ChunkLevel level, Villager v, GlobalPos g, int state) {
        Pos pos = g.pos();
        if (B.prop("level").intValue(state) == ComposterBlock.READY) state = ComposterBlock.extractProduce(null, state, level, pos);
        int budget = 20;
        int keep = 10;
        int[] counts = new int[COMPOSTABLE_ITEMS.size()];
        MobInventory inv = v.getInventory();
        int size = inv.size();
        int cur = state;
        for (int slot = size - 1; slot >= 0 && budget > 0; slot--) {
            ItemStack stack = inv.get(slot);
            int idx = COMPOSTABLE_ITEMS.indexOf(ItemTools.itemName(stack));
            if (idx == -1) continue;
            int count = stack.count();
            int total = counts[idx] + count;
            counts[idx] = total;
            int n = Math.min(Math.min(total - keep, budget), count);
            if (n <= 0) continue;
            budget -= n;
            for (int i = 0; i < n; i++) {
                boolean shrinks = B.prop("level").intValue(cur) < ComposterBlock.MAX_LEVEL && ComposterBlock.isCompostable(inv.get(slot));
                cur = ComposterBlock.insertItem(null, cur, level, inv.get(slot), pos);
                if (shrinks) inv.set(slot, inv.get(slot).withCount(inv.get(slot).count() - 1));   // insertItem 의 stack.shrink(1)
                if (B.prop("level").intValue(cur) == ComposterBlock.MAX_LEVEL) {
                    spawnComposterFillEffects(level, state, pos, cur);
                    return;
                }
            }
        }
        spawnComposterFillEffects(level, state, pos, cur);
    }

    private void spawnComposterFillEffects(ChunkLevel level, int oldState, Pos pos, int newState) {
        level.levelEvent(null, 1500, pos, newState != oldState ? 1 : 0);
    }

    private void makeBread(Villager v) {
        MobInventory inv = v.getInventory();
        if (inv.countItem("minecraft:bread") > 36) return;
        int wheat = inv.countItem("minecraft:wheat");
        int n = Math.min(3, wheat / 3);
        if (n == 0) return;
        inv.removeItemType("minecraft:wheat", n * 3);
        ItemStack rem = inv.addItem(B.stack("minecraft:bread", n));
        if (!rem.isEmpty()) v.spawnAtLocation(rem, 0.5F);
    }
}
