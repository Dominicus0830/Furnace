// 바닐라 GrindstoneMenu(숫돌, grindstone) — 칸: 0·1 재료, 2 결과, 3..29 주 인벤토리, 30..38 핫바.
package org.domi.furnace.server.menu;

import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public class GrindstoneMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0, ADDITIONAL_SLOT = 1, RESULT_SLOT = 2, INV_SLOT_START = 3, INV_SLOT_END = 30,
            USE_ROW_SLOT_START = 30, USE_ROW_SLOT_END = 39;

    private final Container resultSlots = new ResultContainer(new SimpleContainer(1));
    private final Container repairSlots;
    private final int[] blockPos;
    private final BlockAccess access;
    private final MenuPlayer owner;

    public GrindstoneMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BlockAccess access) {
        super("minecraft:grindstone", containerId);
        this.owner = owner;
        this.blockPos = blockPos;
        this.access = access;
        this.repairSlots = new SimpleContainer(2) {
            @Override public void setChanged() {
                super.setChanged();
                slotsChanged(this);
            }
        };
        addSlot(new InputSlot(repairSlots, 0));
        addSlot(new InputSlot(repairSlots, 1));
        addSlot(new Slot(resultSlots, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public void onTake(MenuPlayer player, ItemStack stack) {
                access.awardExperience(experienceAmount());
                access.levelEvent(1042);
                repairSlots.set(0, ItemStack.EMPTY);
                repairSlots.set(1, ItemStack.EMPTY);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    /** 손상됐거나 인챈트가 있어야 놓을 수 있다. */
    private static final class InputSlot extends Slot {
        InputSlot(Container c, int i) { super(c, i); }

        @Override public boolean mayPlace(ItemStack stack) { return ItemTools.isDamageable(stack) || EnchantTools.hasAny(stack); }
    }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == repairSlots) createResult();
    }

    private void createResult() {
        resultSlots.set(0, computeResult(repairSlots.get(0), repairSlots.get(1)));
        broadcastChanges();
    }

    private ItemStack computeResult(ItemStack a, ItemStack b) {
        boolean any = !a.isEmpty() || !b.isEmpty();
        if (!any) return ItemStack.EMPTY;
        if (a.count() > 1 || b.count() > 1) return ItemStack.EMPTY;
        if (!a.isEmpty() && !b.isEmpty()) return mergeItems(a, b);
        ItemStack one = !a.isEmpty() ? a : b;
        return EnchantTools.hasAny(one) ? removeNonCurses(one) : ItemStack.EMPTY;
    }

    private ItemStack mergeItems(ItemStack a, ItemStack b) {
        if (a.itemId() != b.itemId()) return ItemStack.EMPTY;
        int maxDamage = Math.max(ItemTools.maxDamage(a), ItemTools.maxDamage(b));
        int remA = ItemTools.maxDamage(a) - ItemTools.damage(a);
        int remB = ItemTools.maxDamage(b) - ItemTools.damage(b);
        int extra = remA + remB + maxDamage * 5 / 100;
        int count = 1;
        if (!ItemTools.isDamageable(a)) {
            if (Stacks.maxStack(a) < 2 || !Stacks.isSameItemSameComponents(a, b)) return ItemStack.EMPTY;
            count = 2;
        }
        ItemStack result = a.withCount(count);
        if (ItemTools.isDamageable(result)) {
            result = ItemTools.with(result, "minecraft:max_damage", maxDamage);
            result = ItemTools.withDamage(result, Math.max(maxDamage - extra, 0));
        }
        result = mergeEnchantsFrom(result, b);
        return removeNonCurses(result);
    }

    private static ItemStack mergeEnchantsFrom(ItemStack target, ItemStack source) {
        Map<String, Integer> src = EnchantTools.forCrafting(source);
        return EnchantTools.update(target, m -> {
            for (var e : src.entrySet()) {
                if (!EnchantTools.isCurse(e.getKey()) || m.getOrDefault(e.getKey(), 0) == 0) m.put(e.getKey(), e.getValue());
            }
        });
    }

    private ItemStack removeNonCurses(ItemStack stack) {
        ItemStack s = EnchantTools.update(stack, m -> m.keySet().removeIf(k -> !EnchantTools.isCurse(k)));
        int remaining = EnchantTools.forCrafting(s).size();
        if ("minecraft:enchanted_book".equals(ItemTools.itemName(s)) && remaining == 0) {   // 인챈트 책 → 책
            Integer book = ItemContext.id("minecraft:item", "minecraft:book");
            if (book != null) s = new ItemStack(book, s.count(), s.patch().normalizedFor("minecraft:book"));
        }
        int cost = 0;
        for (int i = 0; i < remaining; i++) cost = cost * 2 + 1;   // AnvilMenu.calculateIncreasedRepairCost
        return ItemTools.with(s, "minecraft:repair_cost", cost);
    }

    /** 결과를 꺼낼 때 나오는 경험치: 저주가 아닌 인챈트의 최소 비용 합의 절반(올림) + 난수. */
    private int experienceAmount() {
        int total = cost(repairSlots.get(0)) + cost(repairSlots.get(1));
        if (total > 0) {
            int half = (int) Math.ceil(total / 2.0);
            return half + access.nextInt(half);
        }
        return 0;
    }

    private int cost(ItemStack s) {
        int total = 0;
        for (var e : EnchantTools.forCrafting(s).entrySet()) {
            if (!EnchantTools.isCurse(e.getKey())) total += owner.enchantmentMinCost(e.getKey(), e.getValue());
        }
        return total;
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        ItemStack in0 = repairSlots.get(0), in1 = repairSlots.get(1);
        Moved moved;
        if (index == RESULT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index != INPUT_SLOT && index != ADDITIONAL_SLOT) {
            if (!in0.isEmpty() && !in1.isEmpty()) {
                if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                    moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
                    if (!moved.changed()) return ItemStack.EMPTY;
                } else {
                    moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
                    if (!moved.changed()) return ItemStack.EMPTY;
                }
            } else {
                moved = moveItemStackTo(stack, INPUT_SLOT, RESULT_SLOT, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            }
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        clearContainer(player, repairSlots);
    }
}
