// 블록 상태(구조물 팔레트의 {Name, Properties}) 고침: 바닐라 BlockRenameFix 이름 바꾸기와 속성 고침 4개
// (WallPropertyFix·JigsawRotationFix·RedstoneWireConnectionsFix·CauldronRenameFix).
package org.domi.furnace.server.datafix;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

final class BlockStateFixes {
    private BlockStateFixes() {}

    /** BlockRenameFix(BLOCK_STATE 쪽): Name 만 바꾼다. */
    static UnaryOperator<NbtCompound> rename(UnaryOperator<String> f) {
        return st -> {
            String name = FixUtil.str(st, "Name");
            if (name != null) {
                String n = f.apply(name);
                if (!n.equals(name)) st.putString("Name", n);
            }
            return st;
        };
    }

    // ---- 2503 WallPropertyFix

    private static final Set<String> WALLS = Set.of(
        "minecraft:andesite_wall", "minecraft:brick_wall", "minecraft:cobblestone_wall", "minecraft:diorite_wall",
        "minecraft:end_stone_brick_wall", "minecraft:granite_wall", "minecraft:mossy_cobblestone_wall", "minecraft:mossy_stone_brick_wall",
        "minecraft:nether_brick_wall", "minecraft:prismarine_wall", "minecraft:red_nether_brick_wall", "minecraft:red_sandstone_wall",
        "minecraft:sandstone_wall", "minecraft:stone_brick_wall");

    static NbtCompound wallProperty(NbtCompound st) {
        // 바닐라는 Name 을 이름공간 붙이지 않고 그대로 비교한다
        if (!WALLS.contains(FixUtil.str(st, "Name"))) return st;
        if (!(st.tags().get("Properties") instanceof NbtCompound p)) return st;
        for (String side : new String[] {"east", "west", "north", "south"}) {
            FixUtil.update(p, side, v -> v instanceof String s ? ("true".equals(s) ? "low" : "none") : v);
        }
        return st;
    }

    // ---- 2518 JigsawRotationFix

    private static final Map<String, String> JIGSAW_ORIENTATION = Map.of(
        "down", "down_south", "up", "up_north", "north", "north_up", "south", "south_up", "west", "west_up", "east", "east_up");

    static NbtCompound jigsawRotation(NbtCompound st) {
        if (!"minecraft:jigsaw".equals(FixUtil.str(st, "Name"))) return st;
        if (!(st.tags().get("Properties") instanceof NbtCompound p)) return st;
        String facing = p.tags().get("facing") instanceof String s ? s : "north";
        p.tags().remove("facing");
        p.putString("orientation", JIGSAW_ORIENTATION.getOrDefault(facing, facing));
        return st;
    }

    // ---- 2531 RedstoneWireConnectionsFix

    static NbtCompound redstoneConnections(NbtCompound st) {
        if (!"minecraft:redstone_wire".equals(FixUtil.str(st, "Name"))) return st;
        if (!(st.tags().get("Properties") instanceof NbtCompound p)) return st;
        String east = sideOf(p, "east"), west = sideOf(p, "west"), north = sideOf(p, "north"), south = sideOf(p, "south");
        boolean x = on(east) || on(west);
        boolean z = on(north) || on(south);
        String e = !on(east) && !z ? "side" : east;
        String w = !on(west) && !z ? "side" : west;
        String n = !on(north) && !x ? "side" : north;
        String s = !on(south) && !x ? "side" : south;
        // Dynamic.update: 키가 있을 때만 바꾼다
        FixUtil.update(p, "east", v -> e);
        FixUtil.update(p, "west", v -> w);
        FixUtil.update(p, "north", v -> n);
        FixUtil.update(p, "south", v -> s);
        return st;
    }

    private static String sideOf(NbtCompound p, String k) { return p.tags().get(k) instanceof String s ? s : "none"; }

    private static boolean on(String s) { return !"none".equals(s); }

    // ---- 2679 CauldronRenameFix

    static NbtCompound cauldron(NbtCompound st) {
        if (!"minecraft:cauldron".equals(FixUtil.str(st, "Name"))) return st;
        NbtCompound p = st.tags().get("Properties") instanceof NbtCompound c ? c : new NbtCompound();
        String level = p.tags().get("level") instanceof String s ? s : "0";
        if (level.equals("0")) st.tags().remove("Properties");
        else st.putString("Name", "minecraft:water_cauldron");
        return st;
    }
}
