package org.bukkit.craftbukkit.block;

import org.bukkit.inventory.Inventory;

/** Paper CraftChest: 상자·덫 상자. 큰 상자의 getInventory 는 두 칸을 합친 DoubleChestInventory(CraftInventoryDoubleChest). */
public class CraftChest extends CraftLootable implements org.bukkit.block.Chest {

    public CraftChest(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
    }

    /** 이 칸(반쪽)의 인벤토리 — Paper getBlockInventory(놓이지 않았으면 사본). */
    @Override
    public Inventory getBlockInventory() { return super.getInventory(); }

    /**
     * Paper getInventory: 큰 상자면 ChestBlock.getMenuProvider(…, ignoreBlocked=true) 처럼 두 칸을 합친 54칸 — 왼쪽(칸 0..26)은 type=right 인 상자
     * (바닐라 DoubleBlockCombiner 의 첫째), 오른쪽은 type=left. 짝 칸은 같은 region 스레드에서 읽혀야 한다(아니면 무엇을 할지 적은 ThreadOwnershipException).
     */
    @Override
    public Inventory getInventory() {
        Inventory mine = getBlockInventory();
        if (!isPlaced()) return mine;
        org.domi.furnace.api.world.BlockPos partner;
        Inventory other;
        try {
            partner = partner();
            if (partner == null) return mine;
            other = org.domi.furnace.compat.paper.BlockInventories.live(dimension(), partner);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {
            // Paper 에는 없는 경우(Furnace region 경계) — 서버 내부 문구 대신 이 API 로 무엇을 하면 되는지 알려 준다
            var p = position();
            var ex = new org.domi.furnace.api.concurrent.ThreadOwnershipException("Chest#getInventory: 큰 상자 (" + p.x() + "," + p.y() + "," + p.z()
                    + ")의 짝 칸이 다른 region 이라 이 스레드에서 읽을 수 없다 — getBlockInventory() 로 이 반쪽만 쓰거나, 짝 칸 region 스케줄러에서 부른다");
            ex.initCause(e);
            throw ex;
        }
        if (other == null) return mine;   // 짝 칸에 상자 블록 엔티티가 없다 — Paper 도 그때는 반쪽(CompoundContainer 를 못 만든다)
        boolean selfFirst = "right".equals(((org.domi.furnace.compat.paper.CompatBlockData) getBlockData()).furnace().properties().get("type"));
        return selfFirst ? org.domi.furnace.compat.paper.CompatDoubleChestInventory.of(mine, other)
                : org.domi.furnace.compat.paper.CompatDoubleChestInventory.of(other, mine);
    }

    /**
     * 바닐라 DoubleBlockCombiner: type 이 left/right 이고, 이은 쪽(left 는 facing 시계 방향, right 는 반시계) 칸이 같은 블록·같은 facing·반대 type 이면 그 칸.
     * 짝 없는 반쪽(붙여넣기 등)은 null(Paper 처럼 단일 27칸).
     */
    private org.domi.furnace.api.world.BlockPos partner() {
        var me = ((org.domi.furnace.compat.paper.CompatBlockData) getBlockData()).furnace();
        String t = me.properties().get("type");
        if (!"left".equals(t) && !"right".equals(t)) return null;
        String facing = me.properties().get("facing");
        String dir = "left".equals(t) ? clockWise(facing) : clockWise(clockWise(clockWise(facing)));
        if (dir == null) return null;
        var p = position();
        var partner = switch (dir) {
            case "north" -> new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z() - 1);
            case "south" -> new org.domi.furnace.api.world.BlockPos(p.x(), p.y(), p.z() + 1);
            case "east" -> new org.domi.furnace.api.world.BlockPos(p.x() + 1, p.y(), p.z());
            default -> new org.domi.furnace.api.world.BlockPos(p.x() - 1, p.y(), p.z());
        };
        org.domi.furnace.api.world.BlockData other = org.domi.furnace.compat.paper.Compat.furnace().world(dimension()).block(partner).data();
        String ot = other.properties().get("type");
        boolean pair = other.type().equals(me.type()) && facing.equals(other.properties().get("facing"))
                && ("left".equals(ot) || "right".equals(ot)) && !t.equals(ot);
        return pair ? partner : null;
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
