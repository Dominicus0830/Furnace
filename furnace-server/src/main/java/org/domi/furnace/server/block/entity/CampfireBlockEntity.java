// 바닐라 CampfireBlockEntity: 모닥불 위 음식 4칸, 칸마다 굽는 진행(CookingTimes)·목표(CookingTotalTimes). 켜져 있으면 굽고 꺼져 있으면 되감는다.
// 연기 입자(clientTick)는 클라이언트 몫이라 서버에는 없다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.RecipeManager;

public class CampfireBlockEntity extends ContainerBlockEntity {

    private static final Prop LIT = B.prop("lit");
    public static final String RECIPE_TYPE = "minecraft:campfire_cooking";

    public final int[] cookingProgress = new int[4];
    public final int[] cookingTime = new int[4];

    public CampfireBlockEntity(Pos pos, int state) { super("minecraft:campfire", pos, state, 4); }

    @Override public boolean isValid(int state) { return B.isTag(state, "minecraft:campfires"); }

    @Override public String defaultTitleKey() { return "container.campfire"; }

    @Override public boolean ticking() { return true; }

    /** 바닐라 getCookableRecipe: 빈 칸이 하나도 없으면 없다. */
    public RecipeManager.CookingEntry getCookableRecipe(ItemStack stack, BlockLevel level) {
        boolean anyEmpty = false;
        for (ItemStack s : items) if (s.isEmpty()) { anyEmpty = true; break; }
        if (!anyEmpty || !(level.recipes() instanceof RecipeManager rm)) return null;
        return rm.findCooking(RECIPE_TYPE, stack);
    }

    /** 바닐라 placeFood: 첫 빈 칸에 한 개(consumeAndReturn)를 올린다. 올렸으면 true. */
    public boolean placeFood(BlockActor entity, int hand, ItemStack stack, int cookTime, BlockLevel level) {
        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty()) continue;
            cookingTime[i] = cookTime;
            cookingProgress[i] = 0;
            items[i] = stack.withCount(1);
            if (entity == null || !entity.isCreative()) {
                if (entity != null) entity.setItemInHand(hand, stack.withCount(stack.count() - 1));
            }
            level.gameEvent("minecraft:block_change", pos, entity, level.getBlockState(pos));
            markUpdated(level);
            return true;
        }
        return false;
    }

    /** 바닐라 markUpdated: 저장 대상 + 클라이언트 갱신(같은 상태 setBlock 3). */
    public void markUpdated(BlockLevel level) {
        setChanged(level);
        int s = level.getBlockState(pos);
        level.setBlock(pos, s, 3);
    }

    @Override
    public void tick(BlockLevel level) {
        if (LIT.has(level.getBlockState(pos)) && LIT.is(level.getBlockState(pos))) cookTick(level);
        else cooldownTick(level);
    }

    /** 바닐라 cookTick: 올린 칸마다 +1, 목표에 닿으면 결과(없으면 그 아이템 그대로)를 모닥불 칸에서 떨군다. */
    private void cookTick(BlockLevel level) {
        boolean any = false;
        RecipeManager rm = level.recipes() instanceof RecipeManager r ? r : null;
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack.isEmpty()) continue;
            any = true;
            cookingProgress[i]++;
            if (cookingProgress[i] >= cookingTime[i]) {
                ItemStack result = stack;
                var entry = rm == null ? null : rm.findCooking(RECIPE_TYPE, stack);
                if (entry != null) result = entry.recipe().result().stack(rm.context());
                level.dropItemStack(pos.x(), pos.y(), pos.z(), result);
                items[i] = ItemStack.EMPTY;
                int s = level.getBlockState(pos);
                level.setBlock(pos, s, 3);
                level.gameEvent("minecraft:block_change", pos, null, s);
            }
        }
        if (any) setChanged(level);
    }

    /** 바닐라 cooldownTick: 꺼져 있으면 진행을 틱마다 2씩 되감는다. */
    private void cooldownTick(BlockLevel level) {
        boolean any = false;
        for (int i = 0; i < items.length; i++) {
            if (cookingProgress[i] > 0) {
                any = true;
                cookingProgress[i] = Math.clamp(cookingProgress[i] - 2, 0, cookingTime[i]);
            }
        }
        if (any) setChanged(level);
    }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        if (tag.tags().get("CookingTimes") instanceof int[] a) System.arraycopy(a, 0, cookingProgress, 0, Math.min(cookingTime.length, a.length));
        if (tag.tags().get("CookingTotalTimes") instanceof int[] a) System.arraycopy(a, 0, cookingTime, 0, Math.min(cookingTime.length, a.length));
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putIntArray("CookingTimes", cookingProgress.clone());
        tag.putIntArray("CookingTotalTimes", cookingTime.clone());
    }

    /** getUpdateTag: 클라이언트가 올린 음식을 그리도록 Items 만(바닐라 saveAllItems alwaysPut). */
    @Override
    public NbtCompound updateTag() {
        NbtCompound t = new NbtCompound();
        saveAdditional(t);
        t.tags().remove("CookingTimes");
        t.tags().remove("CookingTotalTimes");
        t.tags().remove("CustomName");
        t.tags().remove("Lock");
        return t;
    }
}
