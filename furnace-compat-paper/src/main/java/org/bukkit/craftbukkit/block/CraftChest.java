package org.bukkit.craftbukkit.block;

import org.bukkit.inventory.Inventory;

/** Paper CraftChest: 상자·덫 상자. 큰 상자를 합친 인벤토리(CraftInventoryDoubleChest)는 아직 없어 짝이 있는 반쪽의 getInventory 는 UnsupportedFeature. */
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

    /**
     * 바닐라 DoubleBlockCombiner: type 이 left/right 이고, 이은 쪽(left 는 facing 시계 방향, right 는 반시계) 칸이 같은 블록·같은 facing·반대 type 일 때만 큰 상자.
     * 짝 없는 반쪽(붙여넣기 등)은 Paper 처럼 단일 27칸. 짝 칸이 다른 region 이라 못 읽으면 큰 상자로 본다(조용히 틀리지 않게).
     */
    private boolean isDoubleHalf() {
        var me = ((org.domi.furnace.compat.paper.CompatBlockData) getBlockData()).furnace();
        String t = me.properties().get("type");
        if (!"left".equals(t) && !"right".equals(t)) return false;
        String facing = me.properties().get("facing");
        String dir = "left".equals(t) ? clockWise(facing) : clockWise(clockWise(clockWise(facing)));
        if (dir == null) return false;
        var p = position();
        var partner = switch (dir) {
            case "north" -> new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z() - 1);
            case "south" -> new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z() + 1);
            case "east" -> new org.domi.furnace.api.world.BlockPos(p.x() + 1, p.y(), p.z());
            default -> new org.domi.furnace.api.world.BlockPos(p.x() - 1, p.y(), p.z());
        };
        org.domi.furnace.api.world.BlockData other;
        try {
            other = org.domi.furnace.compat.paper.Compat.furnace().world(dimension()).block(partner).data();
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException otherRegion) {
            return true;
        }
        String ot = other.properties().get("type");
        return other.type().equals(me.type()) && facing.equals(other.properties().get("facing"))
                && ("left".equals(ot) || "right".equals(ot)) && !t.equals(ot);
    }

    private static String clockWise(String facing) {
        if (facing == null) return null;
        return switch (facing) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> null;
        };
    }
}
