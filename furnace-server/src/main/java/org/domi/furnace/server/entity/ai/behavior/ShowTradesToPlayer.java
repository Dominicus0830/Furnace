// 바닐라 ShowTradesToPlayer(400, 1600): 상호작용 대상 플레이어가 17(제곱) 안이면 그를 바라보며, 플레이어 손 아이템을 값으로 받는
// 품절 아닌 거래의 결과물을 손에 들어 보여 준다(여러 개면 40틱마다 돌아가며). 보여 줄 게 생기면 900틱, 없으면 40틱까지만 바라본다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.trade.MerchantOffer;

public class ShowTradesToPlayer extends Behavior<Villager> {
    private static final int MAX_LOOK_TIME = 900, STARTING_LOOK_TIME = 40, TIME_BETWEEN_ITEMS = 40;
    private ItemStack playerItemStack;
    private final List<ItemStack> displayItems = new ArrayList<>();
    private int cycleCounter, displayIndex, lookTime;

    public ShowTradesToPlayer(int minDuration, int maxDuration) {
        super(Map.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    @Override
    public boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        var t = v.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET);
        if (t.isEmpty()) return false;
        Targetable e = t.get();
        return e.isPlayer() && v.isAlive() && e.isAlive() && !v.isBaby() && BehaviorUtils.distSqr(v, e) <= 17.0;
    }

    @Override
    public boolean canStillUse(ChunkLevel level, Villager v, long time) {
        return checkExtraStartConditions(level, v) && lookTime > 0 && v.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    @Override
    public void start(ChunkLevel level, Villager v, long time) {
        lookAtTarget(v);
        cycleCounter = 0;
        displayIndex = 0;
        lookTime = STARTING_LOOK_TIME;
    }

    @Override
    public void tick(ChunkLevel level, Villager v, long time) {
        Targetable t = lookAtTarget(v);
        findItemsToDisplay(t, v);
        if (!displayItems.isEmpty()) displayCyclingItems(v);
        else {
            clearHeldItem(v);
            lookTime = Math.min(lookTime, STARTING_LOOK_TIME);
        }
        lookTime--;
    }

    @Override
    public void stop(ChunkLevel level, Villager v, long time) {
        v.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        clearHeldItem(v);
        playerItemStack = null;
    }

    private void findItemsToDisplay(Targetable t, Villager v) {
        boolean changed = false;
        ItemStack held = t instanceof Player p ? p.inventory().get(p.inventory().heldWindowSlot()) : ItemStack.EMPTY;
        if (playerItemStack == null || !isSameItem(playerItemStack, held)) {
            playerItemStack = held;
            changed = true;
            displayItems.clear();
        }
        if (changed && !playerItemStack.isEmpty()) {
            updateDisplayItems(v);
            if (!displayItems.isEmpty()) {
                lookTime = MAX_LOOK_TIME;
                displayFirstItem(v);
            }
        }
    }

    private void displayFirstItem(Villager v) { displayAsHeldItem(v, displayItems.get(0)); }

    private void updateDisplayItems(Villager v) {
        for (MerchantOffer o : v.offers()) if (!o.isOutOfStock() && playerItemStackMatchesCostOfOffer(o)) displayItems.add(o.assemble());
    }

    private boolean playerItemStackMatchesCostOfOffer(MerchantOffer o) { return isSameItem(playerItemStack, o.costA()) || isSameItem(playerItemStack, o.costBStack()); }

    /** ItemStack.isSameItem: 같은 아이템 종류(빈 칸끼리도 같다). */
    private static boolean isSameItem(ItemStack a, ItemStack b) { return ItemTools.itemName(a).equals(ItemTools.itemName(b)); }

    private static void clearHeldItem(Villager v) {
        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        v.setDropChance(EquipmentSlot.MAINHAND, 0.085F);
    }

    private static void displayAsHeldItem(Villager v, ItemStack s) {
        v.setItemSlot(EquipmentSlot.MAINHAND, s);
        v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static Targetable lookAtTarget(Villager v) {
        Targetable t = v.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(t, true));
        return t;
    }

    private void displayCyclingItems(Villager v) {
        if (displayItems.size() >= 2 && ++cycleCounter >= TIME_BETWEEN_ITEMS) {
            displayIndex++;
            cycleCounter = 0;
            if (displayIndex > displayItems.size() - 1) displayIndex = 0;
            displayAsHeldItem(v, displayItems.get(displayIndex));
        }
    }
}
