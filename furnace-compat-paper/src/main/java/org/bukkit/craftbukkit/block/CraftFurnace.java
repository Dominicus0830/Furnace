package org.bukkit.craftbukkit.block;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.FurnaceInventory;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.compat.paper.CompatBlockData;

/**
 * Paper CraftFurnace: 화로류 블록 상태. 값은 사본 NBT(바닐라 AbstractFurnaceBlockEntity 저장 꼴: BurnTime·CookTime·CookTimeTotal 은 short,
 * RecipesUsed 는 레시피 키 → 수)에서 int 로 읽어 들고 update 때 적는다. setBurnTime 은 Paper(SPIGOT-844)처럼 블록 상태 lit 도 맞춘다.
 */
public abstract class CraftFurnace extends CraftContainer implements org.bukkit.block.Furnace {

    private int litTime;
    private int cookingProgress;
    private int cookingTotalTime;
    private double cookSpeedMultiplier;

    protected CraftFurnace(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = snapshotNbt();
        litTime = n.getShort("BurnTime");
        cookingProgress = n.getShort("CookTime");
        cookingTotalTime = n.getShort("CookTimeTotal");
        cookSpeedMultiplier = n.contains("Paper.CookSpeedMultiplier") ? n.getDouble("Paper.CookSpeedMultiplier") : 1.0;
    }

    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        NbtCompound n = snapshotNbt();
        n.putShort("BurnTime", (short) litTime);
        n.putShort("CookTime", (short) cookingProgress);
        n.putShort("CookTimeTotal", (short) cookingTotalTime);
        if (cookSpeedMultiplier != 1.0) n.putDouble("Paper.CookSpeedMultiplier", cookSpeedMultiplier);
        else n.remove("Paper.CookSpeedMultiplier");
    }

    @Override
    public short getBurnTime() { return (short) litTime; }

    @Override
    public void setBurnTime(short burnTime) {
        litTime = burnTime;
        var d = ((CompatBlockData) getBlockData()).furnace();
        if (d.properties().containsKey("lit")) {
            Map<String, String> props = new HashMap<>(d.properties());
            props.put("lit", Boolean.toString(burnTime > 0));
            setBlockData(CompatBlockData.of(new org.domi.furnace.api.world.BlockData(d.type(), props)));
        }
    }

    @Override
    public short getCookTime() { return (short) cookingProgress; }

    @Override
    public void setCookTime(short cookTime) { cookingProgress = cookTime; }

    @Override
    public int getCookTimeTotal() { return cookingTotalTime; }

    @Override
    public void setCookTimeTotal(int cookTimeTotal) { cookingTotalTime = cookTimeTotal; }

    /** Paper 굽기 속도 배율(서버 화로가 NBT Paper.CookSpeedMultiplier 로 읽는다). */
    @Override
    public double getCookSpeedMultiplier() { return cookSpeedMultiplier; }

    /**
     * Paper: 검사 뒤 배율을 바꾸고 사본의 굽는 총 시간을 새 배율로 다시 잰다 — ceil(재료 칸 레시피 시간(없으면 200) / 배율).
     * 재료는 사본 NBT Items 의 0 번 칸(아이템 종류만 본다 — 바닐라 Ingredient 도 종류만 본다).
     */
    @Override
    public void setCookSpeedMultiplier(double multiplier) {
        if (!(multiplier >= 0)) throw new IllegalArgumentException("Furnace speed multiplier cannot be negative");
        if (multiplier > 200) throw new IllegalArgumentException("Furnace speed multiplier cannot more than 200");
        cookSpeedMultiplier = multiplier;
        int base = 200;
        String input = smeltingId();
        var kind = recipeKind();
        if (input != null && kind != null) {
            try {
                base = org.domi.furnace.compat.paper.Compat.furnace().recipes()
                        .cookingTime(kind, org.domi.furnace.api.item.ItemStack.of(input)).orElse(200);
            } catch (IllegalStateException noRecipes) {
                base = 200;
            }
        }
        cookingTotalTime = (int) Math.ceil(base / multiplier);
    }

    private String smeltingId() {
        var items = snapshotNbt().getList("Items");
        for (int i = 0; i < items.size(); i++) {
            NbtCompound it = items.getCompound(i);
            if (it.getByte("Slot") == 0 && it.contains("id")) return it.getString("id");
        }
        return null;
    }

    private org.domi.furnace.api.recipe.Recipe.CookingKind recipeKind() {
        return switch (((CompatBlockData) getBlockData()).furnace().type()) {
            case "minecraft:furnace" -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMELTING;
            case "minecraft:blast_furnace" -> org.domi.furnace.api.recipe.Recipe.CookingKind.BLASTING;
            case "minecraft:smoker" -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMOKING;
            default -> null;
        };
    }

    @Override
    public int getRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe) {
        return snapshotNbt().getCompound("RecipesUsed").getInt(furnaceRecipe.toString());
    }

    @Override
    public boolean hasRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe) {
        return snapshotNbt().getCompound("RecipesUsed").contains(furnaceRecipe.toString());
    }

    @Override
    public FurnaceInventory getInventory() { return (FurnaceInventory) super.getInventory(); }

    @Override
    public FurnaceInventory getSnapshotInventory() { return (FurnaceInventory) super.getSnapshotInventory(); }
}
