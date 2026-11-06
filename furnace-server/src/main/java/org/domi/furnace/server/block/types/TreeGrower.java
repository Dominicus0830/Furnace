// 바닐라 TreeGrower(1.21.1): 묘목 종류별 나무 feature 고르기와 growTree(2×2 큰 나무 먼저, 실패하면 되돌림).
// 미구현(Noop) feature 를 고르면 묘목을 건드리지 않고 실패한다 — 다른 나무로 바꿔 키우지 않는다(AGENTS.md 규칙 3).
package org.domi.furnace.server.block.types;

import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.LevelFeature;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public final class TreeGrower {

    /** 이름, 보조 확률, 큰 나무, 보조 큰 나무, 나무, 보조 나무, 꽃 근처 나무, 꽃 근처 보조 나무(없으면 null). */
    public record Spec(String name, float secondaryChance, String megaTree, String secondaryMegaTree,
                       String tree, String secondaryTree, String flowers, String secondaryFlowers) {}

    private static Spec simple(String name, String mega, String tree, String flowers) {
        return new Spec(name, 0.0F, mega, null, tree, null, flowers, null);
    }

    static final Map<String, Spec> GROWERS = Map.of(
            "oak", new Spec("oak", 0.1F, null, null, "minecraft:oak", "minecraft:fancy_oak", "minecraft:oak_bees_005", "minecraft:fancy_oak_bees_005"),
            "spruce", new Spec("spruce", 0.5F, "minecraft:mega_spruce", "minecraft:mega_pine", "minecraft:spruce", null, null, null),
            "mangrove", new Spec("mangrove", 0.85F, null, null, "minecraft:mangrove", "minecraft:tall_mangrove", null, null),
            "azalea", simple("azalea", null, "minecraft:azalea_tree", null),
            "birch", simple("birch", null, "minecraft:birch", "minecraft:birch_bees_005"),
            "jungle", simple("jungle", "minecraft:mega_jungle_tree", "minecraft:jungle_tree_no_vine", null),
            "acacia", simple("acacia", null, "minecraft:acacia", null),
            "cherry", simple("cherry", null, "minecraft:cherry", "minecraft:cherry_bees_005"),
            "dark_oak", simple("dark_oak", "minecraft:dark_oak", null, null));

    private TreeGrower() {}

    public static Spec of(String name) {
        Spec s = GROWERS.get(name);
        if (s == null) throw new IllegalArgumentException("모르는 TreeGrower: " + name);
        return s;
    }

    /** getConfiguredFeature(random, flowers): 보조 확률 난수는 항상 한 번 뽑는다. */
    static String configuredFeature(Spec s, RandomSource random, boolean flowers) {
        if (random.nextFloat() < s.secondaryChance()) {
            if (flowers && s.secondaryFlowers() != null) return s.secondaryFlowers();
            if (s.secondaryTree() != null) return s.secondaryTree();
        }
        return flowers && s.flowers() != null ? s.flowers() : s.tree();
    }

    /** getConfiguredMegaFeature(random): 보조 큰 나무가 있을 때만 난수를 뽑는다. */
    static String configuredMegaFeature(Spec s, RandomSource random) {
        return s.secondaryMegaTree() != null && random.nextFloat() < s.secondaryChance() ? s.secondaryMegaTree() : s.megaTree();
    }

    /** growTree(level, generator, pos, state, random). */
    public static boolean growTree(Spec s, BlockLevel level, Pos pos, int state, RandomSource random) {
        String megaId = configuredMegaFeature(s, random);
        if (megaId != null) {
            LevelFeature mega = level.configuredFeature(megaId);
            if (mega != null) {
                for (int dx = 0; dx >= -1; dx--) {
                    for (int dz = 0; dz >= -1; dz--) {
                        if (isTwoByTwoSapling(state, level, pos, dx, dz)) {
                            if (!mega.supported()) return false;   // 미구현: 묘목 4개를 그대로 둔다
                            int air = B.defaultState("minecraft:air");
                            level.setBlock(pos.offset(dx, 0, dz), air, 4);
                            level.setBlock(pos.offset(dx + 1, 0, dz), air, 4);
                            level.setBlock(pos.offset(dx, 0, dz + 1), air, 4);
                            level.setBlock(pos.offset(dx + 1, 0, dz + 1), air, 4);
                            if (level.placeFeature(mega, pos.offset(dx, 0, dz), random)) return true;
                            level.setBlock(pos.offset(dx, 0, dz), state, 4);
                            level.setBlock(pos.offset(dx + 1, 0, dz), state, 4);
                            level.setBlock(pos.offset(dx, 0, dz + 1), state, 4);
                            level.setBlock(pos.offset(dx + 1, 0, dz + 1), state, 4);
                            return false;
                        }
                    }
                }
            }
        }
        String id = configuredFeature(s, random, hasFlowers(level, pos));
        if (id == null) return false;
        LevelFeature f = level.configuredFeature(id);
        if (f == null || !f.supported()) return false;
        int legacy = Fluids.createLegacyBlock(level.getFluidState(pos));
        level.setBlock(pos, legacy, 4);
        if (level.placeFeature(f, pos, random)) {
            if (level.getBlockState(pos) == legacy) level.sendBlockUpdated(pos);
            return true;
        }
        level.setBlock(pos, state, 4);
        return false;
    }

    private static boolean isTwoByTwoSapling(int state, BlockLevel level, Pos pos, int dx, int dz) {
        int block = B.block(state);
        return B.is(level.getBlockState(pos.offset(dx, 0, dz)), block)
                && B.is(level.getBlockState(pos.offset(dx + 1, 0, dz)), block)
                && B.is(level.getBlockState(pos.offset(dx, 0, dz + 1)), block)
                && B.is(level.getBlockState(pos.offset(dx + 1, 0, dz + 1)), block);
    }

    /** hasFlowers: (x-2,y-1,z-2)..(x+2,y+1,z+2) 에 #flowers 가 있는가. */
    private static boolean hasFlowers(BlockLevel level, Pos pos) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -2; z <= 2; z++) {
                for (int x = -2; x <= 2; x++) {
                    if (B.isTag(level.getBlockState(pos.offset(x, y, z)), "minecraft:flowers")) return true;
                }
            }
        }
        return false;
    }
}
