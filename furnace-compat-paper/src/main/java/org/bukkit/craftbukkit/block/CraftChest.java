package org.bukkit.craftbukkit.block;

import org.bukkit.inventory.Inventory;

/** Paper CraftChest: 상자·덫 상자. 큰 상자를 합친 인벤토리(CraftInventoryDoubleChest)는 아직 없어 그때 getInventory 는 UnsupportedFeature. */
public class CraftChest extends CraftLootable implements org.bukkit.block.Chest {

    public CraftChest(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
    }

    /** 이 칸(반쪽)의 인벤토리 — Paper getBlockInventory. */
    @Override
    public Inventory getBlockInventory() { return super.getInventory(); }

    @Override
    public Inventory getInventory() {
        if (isPlaced() && isDoubleHalf()) {
            // 반쪽만 주면 상점 플러그인이 재고를 틀리게 센다 — 조용히 틀리느니 알린다
            throw new org.domi.furnace.compat.paper.UnsupportedFeature("Chest#getInventory(큰 상자)", "두 칸을 합친 상자 인벤토리가 아직 없다 — getBlockInventory 는 쓸 수 있다");
        }
        return super.getInventory();
    }

    private boolean isDoubleHalf() {
        String t = ((org.domi.furnace.compat.paper.CompatBlockData) getBlockData()).furnace().properties().get("type");
        return "left".equals(t) || "right".equals(t);
    }
}
