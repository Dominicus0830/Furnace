// 바닐라 CrafterBlockEntity: 3×3 재료 칸(전리품 표·이름·잠금은 무작위 용기 바탕 그대로), 잠근 칸(disabled_slots), 신호 받음(triggered),
// 제작 뒤 남은 "crafting" 표시 틱(crafting_ticks_remaining, 0 이 되면 crafting=false). 깔때기 넣기는 잠근 칸·가득 찬 칸을 피하고
// 뒤 칸에 더 적게 든 같은 아이템(또는 빈 칸)이 있으면 그쪽으로 미룬다(고르게 채우기). 비교기 = 찬 칸 + 잠근 칸 수.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.menu.Stacks;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.CraftingInput;

public class CrafterBlockEntity extends ContainerBlockEntity {

    public static final int CONTAINER_SIZE = 9;
    private static final Prop CRAFTING = B.prop("crafting");

    /** containerData: 칸 0..8 잠금(1 = 잠김), 9 = triggered. */
    private final int[] slotStates = new int[9];
    private int triggered;
    private int craftingTicksRemaining;

    public CrafterBlockEntity(Pos pos, int state) { super("minecraft:crafter", pos, state, CONTAINER_SIZE); }

    @Override public boolean isValid(int state) { return B.is(state, "crafter"); }

    @Override public String defaultTitleKey() { return "container.crafter"; }

    /** setSlotState(slot, enabled): 빈 칸만 잠그거나 풀 수 있다. */
    public void setSlotState(int slot, boolean enabled, BlockLevel level) {
        if (slotCanBeDisabled(slot)) {
            slotStates[slot] = enabled ? 0 : 1;
            if (level != null) setChanged(level);
        }
    }

    public boolean isSlotDisabled(int slot) { return slot >= 0 && slot < 9 && slotStates[slot] == 1; }

    private boolean slotCanBeDisabled(int slot) { return slot > -1 && slot < 9 && items[slot].isEmpty(); }

    /** canPlaceItem: 잠긴 칸 불가, 가득 찬 칸 불가, 빈 칸 가능, 아니면 뒤에 더 적은 칸이 없을 때만. */
    public boolean canPlaceItem(int slot, ItemStack s) {
        if (slotStates[slot] == 1) return false;
        ItemStack cur = items[slot];
        int n = cur.count();
        if (n >= ItemStackCodecs.maxStackSize(cur)) return false;
        return cur.isEmpty() || !smallerStackExist(n, cur, slot);
    }

    private boolean smallerStackExist(int count, ItemStack s, int slot) {
        for (int i = slot + 1; i < 9; i++) {
            if (!isSlotDisabled(i)) {
                ItemStack o = items[i];
                if (o.isEmpty() || o.count() < count && Stacks.isSameItemSameComponents(o, s)) return true;
            }
        }
        return false;
    }

    /** setItem 전에: 잠긴 칸에 넣으면 그 칸을 푼다(잠긴 칸은 늘 비어 있다). */
    public void beforeSetItem(int slot, BlockLevel level) {
        if (isSlotDisabled(slot)) setSlotState(slot, true, level);
    }

    /** containerData.get: 0..8 = 칸 잠금, 9 = triggered(메뉴 데이터 칸). */
    public int dataValue(int i) { return i == 9 ? triggered : slotStates[i]; }

    /** containerData.set(클라이언트가 바꾸지는 않는다 — 메뉴 setData 용). */
    public void setDataValue(int i, int v) {
        if (i == 9) triggered = v;
        else slotStates[i] = v;
    }

    public void setTriggered(boolean t) { triggered = t ? 1 : 0; }

    public boolean isTriggered() { return triggered == 1; }

    public void setCraftingTicksRemaining(int t) { craftingTicksRemaining = t; }

    public int craftingTicksRemaining() { return craftingTicksRemaining; }

    /** getRedstoneSignal: 찼거나 잠긴 칸 수. */
    public int redstoneSignal() {
        int n = 0;
        for (int i = 0; i < 9; i++) if (!items[i].isEmpty() || isSlotDisabled(i)) n++;
        return n;
    }

    /** asCraftInput: 3×3 을 잘라 낸 입력. */
    public CraftingInput asCraftInput() {
        List<ItemStack> l = new ArrayList<>(9);
        for (ItemStack s : items) l.add(s);
        return CraftingInput.of(3, 3, l);
    }

    @Override public boolean ticking() { return true; }

    /** serverTick: 남은 틱을 줄이고 0 이 되면 crafting=false(setBlock 3). */
    @Override
    public void tick(BlockLevel level) {
        int t = craftingTicksRemaining - 1;
        if (t >= 0) {
            craftingTicksRemaining = t;
            if (t == 0) level.setBlock(pos, CRAFTING.with(level.getBlockState(pos), false), 3);
        }
    }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        craftingTicksRemaining = tag.getIntOr0("crafting_ticks_remaining");
        java.util.Arrays.fill(slotStates, 0);
        if (tag.tags().get("disabled_slots") instanceof int[] a) {
            for (int s : a) if (slotCanBeDisabled(s)) slotStates[s] = 1;
        }
        triggered = tag.getIntOr0("triggered");
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putInt("crafting_ticks_remaining", craftingTicksRemaining);
        int n = 0;
        for (int v : slotStates) if (v == 1) n++;
        int[] d = new int[n];
        for (int i = 0, j = 0; i < 9; i++) if (slotStates[i] == 1) d[j++] = i;
        tag.putIntArray("disabled_slots", d);
        tag.putInt("triggered", triggered);
    }
}
