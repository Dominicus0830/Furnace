// 바닐라 AbstractFurnaceBlockEntity(화로·용광로·훈연기): 재료·연료·결과 3칸, 태우는 시간/굽는 시간, 쓴 레시피 수 → 꺼낼 때 경험치.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.CraftRemainders;
import org.domi.furnace.server.item.FuelRegistry;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.Stacks;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.RecipeManager;

public class FurnaceBlockEntity extends ContainerBlockEntity {

    private static final Prop LIT = B.prop("lit");

    /** 태우는 시간(남은 틱) · 이번 연료의 총 틱 · 굽는 진행 · 굽는 데 걸리는 틱 (바닐라 data 0..3). */
    public int litTime, litDuration, cookingProgress, cookingTotalTime;
    private final Map<ResourceId, Integer> recipesUsed = new LinkedHashMap<>();
    private final String recipeType;
    private final String blockName;
    private final String titleKey;

    private FurnaceBlockEntity(String type, String recipeType, String blockName, String titleKey, Pos pos, int state) {
        super(type, pos, state, 3);
        this.recipeType = recipeType;
        this.blockName = blockName;
        this.titleKey = titleKey;
    }

    public static FurnaceBlockEntity furnace(Pos pos, int state) {
        return new FurnaceBlockEntity("minecraft:furnace", "minecraft:smelting", "furnace", "container.furnace", pos, state);
    }

    public static FurnaceBlockEntity blastFurnace(Pos pos, int state) {
        return new FurnaceBlockEntity("minecraft:blast_furnace", "minecraft:blasting", "blast_furnace", "container.blast_furnace", pos, state);
    }

    public static FurnaceBlockEntity smoker(Pos pos, int state) {
        return new FurnaceBlockEntity("minecraft:smoker", "minecraft:smoking", "smoker", "container.smoker", pos, state);
    }

    public String recipeType() { return recipeType; }

    @Override public boolean isValid(int state) { return B.is(state, blockName); }

    @Override public String defaultTitleKey() { return titleKey; }

    @Override public boolean ticking() { return true; }

    public boolean isLit() { return litTime > 0; }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        litTime = tag.getIntOr0("BurnTime");
        cookingProgress = tag.getIntOr0("CookTime");
        cookingTotalTime = tag.getIntOr0("CookTimeTotal");
        litDuration = burnDuration(items[1]);
        recipesUsed.clear();
        if (tag.contains("RecipesUsed")) {
            NbtCompound used = tag.getCompound("RecipesUsed");
            for (String k : used.tags().keySet()) recipesUsed.put(ResourceId.parse(k), used.getIntOr0(k));
        }
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putShort("BurnTime", (short) litTime);
        tag.putShort("CookTime", (short) cookingProgress);
        tag.putShort("CookTimeTotal", (short) cookingTotalTime);
        NbtCompound used = new NbtCompound();
        for (var e : recipesUsed.entrySet()) used.putInt(e.getKey().toString(), e.getValue());
        tag.putCompound("RecipesUsed", used);
    }

    public static int burnDuration(ItemStack fuel) { return FuelRegistry.burnTime(fuel); }

    private RecipeManager recipes(BlockLevel level) { return level.recipes() instanceof RecipeManager rm ? rm : null; }

    private RecipeManager.CookingEntry find(RecipeManager rm, ItemStack input) { return rm == null ? null : rm.findCooking(recipeType, input); }

    private static boolean canBurn(RecipeManager.CookingEntry recipe, ItemStack[] items, int maxStack, RecipeManager rm) {
        if (items[0].isEmpty() || recipe == null) return false;
        ItemStack result = recipe.recipe().result().stack(rm.context());
        if (result.isEmpty()) return false;
        ItemStack out = items[2];
        if (out.isEmpty()) return true;
        if (!Stacks.isSameItemSameComponents(out, result)) return false;
        return out.count() < maxStack && out.count() < Stacks.maxStack(out) ? true : out.count() < Stacks.maxStack(result);
    }

    private boolean burn(BlockLevel level, RecipeManager.CookingEntry recipe, ItemStack[] items, int maxStack, RecipeManager rm) {
        if (recipe == null || !canBurn(recipe, items, maxStack, rm)) return false;
        ItemStack input = items[0], result = recipe.recipe().result().stack(rm.context()), out = items[2];
        // 플러그인 FurnaceSmeltEvent: 결과를 바꾸거나 취소(CraftBukkit 처럼 바꾼 결과가 결과 칸과 합쳐지지 않으면 굽지 않는다)
        ItemStack made = level.gameEvents().furnaceSmelt(level.dimension(), pos, level.getBlockState(pos), input, result);
        if (made == null) return false;
        if (made.isEmpty()) {
            // 아무것도 나오지 않는다
        } else if (out.isEmpty()) items[2] = made;
        else if (Stacks.isSameItemSameComponents(out, made)) items[2] = out.withCount(out.count() + (made == result ? 1 : made.count()));   // 바닐라는 grow(1)
        else return false;
        if ("minecraft:wet_sponge".equals(ItemTools.itemName(input)) && !items[1].isEmpty() && "minecraft:bucket".equals(ItemTools.itemName(items[1]))) {
            Integer water = ItemContext.id("minecraft:item", "minecraft:water_bucket");
            if (water != null) items[1] = new ItemStack(water, 1);
        }
        items[0] = input.withCount(input.count() - 1);
        return true;
    }

    private int cookTime(RecipeManager rm) {
        var e = find(rm, items[0]);
        return e == null ? 200 : e.recipe().cookingTime();
    }

    @Override
    public void tick(BlockLevel level) {
        RecipeManager rm = recipes(level);
        boolean wasLit = isLit();
        boolean changed = false;
        if (isLit()) litTime--;
        ItemStack fuel = items[1], input = items[0];
        boolean hasInput = !input.isEmpty(), hasFuel = !fuel.isEmpty();
        if (isLit() || hasFuel && hasInput) {
            var recipe = hasInput ? find(rm, input) : null;
            int maxStack = 99;
            boolean burnable = rm != null && canBurn(recipe, items, maxStack, rm);
            if (!isLit() && burnable) {
                // 플러그인 FurnaceBurnEvent: 취소면 불을 켜지 않는다(연료도 그대로)
                var burn = level.gameEvents().furnaceBurn(level.dimension(), pos, level.getBlockState(pos), fuel, burnDuration(fuel));
                litTime = burn == null ? 0 : burn.burnTime();
                litDuration = litTime;
                if (isLit()) {
                    changed = true;
                    if (hasFuel && burn.consumeFuel()) {
                        ItemStack remaining = CraftRemainders.of(fuel);
                        items[1] = fuel.withCount(fuel.count() - 1);
                        if (items[1].isEmpty()) items[1] = remaining == null ? ItemStack.EMPTY : remaining;
                    }
                }
            }
            if (isLit() && burnable) {
                cookingProgress++;
                if (cookingProgress == cookingTotalTime) {
                    cookingProgress = 0;
                    cookingTotalTime = cookTime(rm);
                    if (burn(level, recipe, items, maxStack, rm)) recipesUsed.merge(recipe.id(), 1, Integer::sum);
                    changed = true;
                }
            } else {
                cookingProgress = 0;
            }
        } else if (!isLit() && cookingProgress > 0) {
            cookingProgress = Math.clamp(cookingProgress - 2, 0, cookingTotalTime);
        }
        if (wasLit != isLit()) {
            changed = true;
            blockState = LIT.with(level.getBlockState(pos), isLit());
            level.setBlock(pos, blockState, 3);
        }
        if (changed) setChanged(level);
    }

    /** 바닐라 setItem 부수 효과: 재료 칸이 다른 아이템으로 바뀌면 굽는 시간을 새로 정하고 진행을 0 으로. */
    @Override
    public void onItemSet(int index, ItemStack old, ItemStack now, BlockLevel level) {
        boolean same = !now.isEmpty() && Stacks.isSameItemSameComponents(old, now);
        if (index == 0 && !same) {
            cookingTotalTime = cookTime(recipes(level));
            cookingProgress = 0;
        }
    }

    /**
     * 바닐라 getRecipesToAwardAndPopExperience: 쌓인 레시피마다 (쓴 횟수 × 경험치) 의 정수부 + 소수부 확률로 1 을 구슬 하나 분량으로 돌려준다.
     * usedOut 에는 해금할 레시피를 채우고, 쌓인 기록은 비운다.
     */
    public List<Integer> takeExperience(RecipeManager rm, List<ResourceId> usedOut) {
        List<Integer> amounts = new ArrayList<>();
        for (var e : recipesUsed.entrySet()) {
            var r = rm == null ? null : rm.recipe(e.getKey());
            if (!(r instanceof org.domi.furnace.server.recipe.Recipe.Cooking c)) continue;
            usedOut.add(e.getKey());
            float v = e.getValue() * c.experience();
            int whole = (int) Math.floor(v);
            float frac = v - whole;
            if (frac != 0.0F && ThreadLocalRandom.current().nextDouble() < frac) whole++;
            amounts.add(whole);
        }
        recipesUsed.clear();
        return amounts;
    }
}
