// 바닐라 TradeWithVillager: 상호작용 대상 주민과 마주 보며 다가가(5 이내) 소문을 나누고(골렘 소환 확인), 남는 음식·농부에게 밀·상대 직업이 원하는 아이템을 반 묶음 던져 준다.
// 소문은 Villager.gossip 이 옮긴다(소문 10개를 엔티티 난수로 고르고, 시각 기록과 골렘 확인도 한다).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.MobInventory;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.item.ItemStackCodecs;

public class TradeWithVillager extends Behavior<Villager> {
    private Set<String> trades = Set.of();

    public TradeWithVillager() {
        super(Map.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        return BehaviorUtils.targetIsValid(v.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return checkExtraStartConditions(level, v); }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        Villager t = (Villager) v.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(v, t, 0.5F, 2);
        trades = figureOutWhatIAmWillingToTrade(v, t);
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        Villager t = (Villager) v.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (BehaviorUtils.distSqr(v, t) > 5.0) return;
        BehaviorUtils.lockGazeAndWalkToEachOther(v, t, 0.5F, 2);
        v.gossip(level, t, time);
        if (v.hasExcessFood() && (v.profession().equals("farmer") || t.wantsMoreFood())) throwHalfStack(v, Villager.FOOD_POINTS.keySet(), t);
        if (t.profession().equals("farmer") && v.getInventory().countItem("minecraft:wheat") > ItemStackCodecs.maxStackSize(B.stack("minecraft:wheat", 1)) / 2) {
            throwHalfStack(v, Set.of("minecraft:wheat"), t);
        }
        if (!trades.isEmpty() && v.getInventory().hasAnyOf(trades)) throwHalfStack(v, trades, t);
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) { v.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET); }

    private static Set<String> figureOutWhatIAmWillingToTrade(Villager me, Villager target) {
        Set<String> mine = Villager.requestedItems(me.profession());
        Set<String> out = new HashSet<>();
        for (String s : Villager.requestedItems(target.profession())) if (!mine.contains(s)) out.add(s);
        return out;
    }

    private static void throwHalfStack(Villager v, Set<String> items, Targetable target) {
        MobInventory inv = v.getInventory();
        ItemStack toThrow = ItemStack.EMPTY;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty() || !items.contains(ItemTools.itemName(s))) continue;
            int n;
            if (s.count() > ItemStackCodecs.maxStackSize(s) / 2) n = s.count() / 2;
            else if (s.count() > 24) n = s.count() - 24;
            else continue;
            inv.set(i, s.withCount(s.count() - n));
            toThrow = s.withCount(n);
            break;
        }
        if (!toThrow.isEmpty()) BehaviorUtils.throwItem(v, toThrow, new double[] {target.x(), target.y(), target.z()});
    }
}
