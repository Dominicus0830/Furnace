package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 이식한 블록 종류의 설치·갱신 결과(기대값은 바닐라 코드를 손으로 따라가 정했다). */
class BlockTypesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static int item(String name) { return ItemContext.id("minecraft:item", name); }

    /** (bx,-61,bz) 윗면을 플레이어가 yRot 방향으로 서서 클릭해 놓는다. */
    private static void placeOnGround(BlockPlacementTest.TestLevel level, String item, int bx, int bz, float yRot) {
        BlockPlacementTest.Actor a = new BlockPlacementTest.Actor();
        a.creative = true;
        a.yRot = yRot;
        a.xRot = 30;
        a.x = bx + 0.5 - 2 * Dir.DX[Dir.fromYRot(yRot)];
        a.z = bz + 0.5 - 2 * Dir.DZ[Dir.fromYRot(yRot)];
        a.hands[0] = new ItemStack(item(item), 1);
        BlockPlacement.useItemOn(level, a, 0, new Hit(new Pos(bx, -61, bz), Dir.UP, bx + 0.5, -60, bz + 0.5, false));
    }

    private static String at(BlockPlacementTest.TestLevel level, int x, int y, int z) { return B.describe(level.getBlockState(new Pos(x, y, z))); }

    @Test
    void door_placesBothHalves_facingPlayer() {
        var level = new BlockPlacementTest.TestLevel();
        placeOnGround(level, "minecraft:oak_door", 1, 1, 0);   // 남쪽을 봄
        assertEquals("minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]", at(level, 1, -60, 1));
        assertEquals("minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]", at(level, 1, -59, 1));
        // 아래 반쪽을 지우면 위 반쪽도 사라진다(updateShape)
        level.setBlock(new Pos(1, -60, 1), B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:air", at(level, 1, -59, 1));
    }

    @Test
    void stairs_formOuterCorner() {
        var level = new BlockPlacementTest.TestLevel();
        placeOnGround(level, "minecraft:oak_stairs", 0, 0, 180);   // 북쪽
        placeOnGround(level, "minecraft:oak_stairs", 0, -1, 270);  // 동쪽 — 앞(북) 칸 계단 옆
        // 앞(북) 칸 계단이 동쪽을 보고, 동쪽은 북쪽의 반시계 방향이 아니므로 outer_right
        assertTrue(at(level, 0, -60, 0).contains("shape=outer_right"), at(level, 0, -60, 0));
    }

    @Test
    void wallTorch_breaksWhenWallRemoved() {
        var level = new BlockPlacementTest.TestLevel();
        Pos wall = new Pos(0, -60, 0);
        level.setBlock(wall, B.defaultState("minecraft:stone"), 3);
        Pos torch = new Pos(1, -60, 0);
        int s = B.prop("facing").with(B.defaultState("minecraft:wall_torch"), "east");
        level.setBlock(torch, s, 3);
        level.setBlock(wall, B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:air", at(level, 1, -60, 0));
    }

    @Test
    void concretePowder_nextToWater_becomesConcrete() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(3, -60, 3), B.defaultState("minecraft:water"), 3);
        placeOnGround(level, "minecraft:white_concrete_powder", 2, 3, 0);
        assertEquals("minecraft:white_concrete", at(level, 2, -60, 3));
    }

    @Test
    void sponge_absorbsWater() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:water"), 3);
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:sponge"), 3);
        assertEquals("minecraft:wet_sponge", at(level, 0, -60, 0));
        assertEquals("minecraft:air", at(level, 1, -60, 0));
    }

    @Test
    void lever_pullTogglesPowered() {
        var level = new BlockPlacementTest.TestLevel();
        Pos p = new Pos(0, -60, 0);
        level.setBlock(p, B.defaultState("minecraft:lever"), 3);   // 벽(북쪽 면) 기본 — 붙을 곳이 없으면 깨지니 바닥에 둔다
        int floor = B.prop("face").with(B.defaultState("minecraft:lever"), "floor");
        level.setBlock(p, floor, 3);
        var a = new BlockPlacementTest.Actor();
        BlockBehaviors.of(floor).useWithoutItem(floor, level, p, a, new Hit(p, Dir.UP, 0.5, -59.9, 0.5, false));
        assertTrue(B.prop("powered").is(level.getBlockState(p)));
    }

    @Test
    void everyDefinitionType_hasPortedBehavior() {
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < B.blockCount(); i++) {
            String type = B.type(i);
            if (!type.equals("minecraft:block") && BlockBehaviors.ofBlock(i).getClass() == BlockBehavior.class && missing.indexOf(type) < 0) {
                missing.append(type).append(' ');
            }
        }
        assertEquals("", missing.toString());
    }

    @Test
    void wall_sideBecomesTallUnderFullBlock() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        placeOnGround(level, "minecraft:cobblestone_wall", 0, 0, 0);
        assertTrue(at(level, 0, -60, 0).contains("east=low"), at(level, 0, -60, 0));
        level.setBlock(new Pos(0, -59, 0), B.defaultState("minecraft:stone"), 3);
        String w = at(level, 0, -60, 0);
        assertTrue(w.contains("east=tall") && w.contains("up=true") && w.contains("west=none"), w);
    }

    /** 레드스톤 가루 갱신 순서 = java.util.HashSet 에 같은 hashCode 의 위치를 넣고 순회한 순서. */
    @Test
    void redstoneWire_updateOrderMatchesHashSet() {
        record V(int x, int y, int z) {
            @Override public int hashCode() { return (y + z * 31) * 31 + x; }
        }
        int[][] samples = {{0, -60, 0}, {13, 64, -7}, {-1000, 5, 333}, {29999, 319, -29999}, {7, -64, 15}};
        for (int[] c : samples) {
            Pos pos = new Pos(c[0], c[1], c[2]);
            java.util.Set<V> set = new java.util.HashSet<>();
            set.add(new V(pos.x(), pos.y(), pos.z()));
            for (int d : Dir.ALL) {
                Pos n = pos.relative(d);
                set.add(new V(n.x(), n.y(), n.z()));
            }
            java.util.List<Pos> expected = new java.util.ArrayList<>();
            for (V v : set) expected.add(new Pos(v.x(), v.y(), v.z()));
            assertEquals(expected, java.util.List.of(org.domi.furnace.server.block.types.RedStoneWireBlock.hashSetOrder(pos)));
        }
    }

    @Test
    void chest_pairsWithNeighbour() {
        var level = new BlockPlacementTest.TestLevel();
        placeOnGround(level, "minecraft:chest", 0, 0, 180);   // 북쪽을 봄 → 상자는 남쪽을 향함
        placeOnGround(level, "minecraft:chest", 1, 0, 180);
        assertTrue(at(level, 1, -60, 0).contains("type=left"), at(level, 1, -60, 0));
        assertTrue(at(level, 0, -60, 0).contains("type=right"), at(level, 0, -60, 0));
    }

    @Test
    void noteBlock_instrumentFromBlockBelow() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:stone"), 3);
        placeOnGroundAt(level, "minecraft:note_block", new Pos(0, -60, 0));
        assertTrue(at(level, 0, -59, 0).contains("instrument=basedrum"), at(level, 0, -59, 0));
    }

    /** 그 칸 윗면을 클릭해 놓는다. */
    private static void placeOnGroundAt(BlockPlacementTest.TestLevel level, String item, Pos on) {
        BlockPlacementTest.Actor a = new BlockPlacementTest.Actor();
        a.creative = true;
        a.xRot = 30;
        a.x = on.x() + 0.5;
        a.z = on.z() - 1.5;
        a.hands[0] = new ItemStack(item(item), 1);
        BlockPlacement.useItemOn(level, a, 0, new Hit(on, Dir.UP, on.x() + 0.5, on.y() + 1, on.z() + 0.5, false));
    }
}
