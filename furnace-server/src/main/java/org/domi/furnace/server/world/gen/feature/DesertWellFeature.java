// desert_well — 바닐라 DesertWellFeature 포팅. 우물 안쪽 수정(suspicious_sand) 두 곳을 고르는 RNG(nextInt(5)×2)까지 1:1.
// suspicious_sand의 고고학 loot table은 블록 엔티티 값이라 블록만 놓는다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.feature.DesertWellFeature")
public final class DesertWellFeature implements ConfiguredFeature {

    // Direction.Plane.HORIZONTAL 순서: NORTH, EAST, SOUTH, WEST
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final BlockAttrs attrs = BlockAttrs.get();
    private final int sand;
    private final int slab;
    private final int sandstone;
    private final int water;
    private final int suspiciousSand;
    private final int sandBlock;

    public DesertWellFeature(BlockRegistry registry) {
        this.sand = registry.defaultStateId("minecraft:sand");
        this.slab = registry.stateIdOrAir("minecraft:sandstone_slab", java.util.Map.of("type", "bottom", "waterlogged", "false"));
        this.sandstone = registry.defaultStateId("minecraft:sandstone");
        this.water = registry.defaultStateId("minecraft:water");
        this.suspiciousSand = registry.defaultStateId("minecraft:suspicious_sand");
        this.sandBlock = attrs.blockIndex(sand);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        y++;
        while (attrs.isAir(view.getBlock(x, y, z)) && y > view.minY() + 2) {
            y--;
        }
        if (attrs.blockIndex(view.getBlock(x, y, z)) != sandBlock) return false;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (attrs.isAir(view.getBlock(x + dx, y - 1, z + dz)) && attrs.isAir(view.getBlock(x + dx, y - 2, z + dz))) {
                    return false;
                }
            }
        }

        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    view.setBlock(x + dx, y + dy, z + dz, sandstone);
                }
            }
        }

        view.setBlock(x, y, z, water);
        for (int[] d : HORIZONTAL) view.setBlock(x + d[0], y, z + d[1], water);

        view.setBlock(x, y - 1, z, sand);
        for (int[] d : HORIZONTAL) view.setBlock(x + d[0], y - 1, z + d[1], sand);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == -2 || dx == 2 || dz == -2 || dz == 2) {
                    view.setBlock(x + dx, y + 1, z + dz, sandstone);
                }
            }
        }

        view.setBlock(x + 2, y + 1, z, slab);
        view.setBlock(x - 2, y + 1, z, slab);
        view.setBlock(x, y + 1, z + 2, slab);
        view.setBlock(x, y + 1, z - 2, slab);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                view.setBlock(x + dx, y + 4, z + dz, dx == 0 && dz == 0 ? sandstone : slab);
            }
        }

        for (int dy = 1; dy <= 3; dy++) {
            view.setBlock(x - 1, y + dy, z - 1, sandstone);
            view.setBlock(x - 1, y + dy, z + 1, sandstone);
            view.setBlock(x + 1, y + dy, z - 1, sandstone);
            view.setBlock(x + 1, y + dy, z + 1, sandstone);
        }

        // List.of(pos, east, south, west, north) 에서 무작위로 둘을 골라 한 칸 아래에 suspicious_sand
        int[][] candidates = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int[] first = candidates[random.nextInt(5)];
        view.setBlock(x + first[0], y - 1, z + first[1], suspiciousSand);
        brushable(view, x + first[0], y - 1, z + first[1]);
        int[] second = candidates[random.nextInt(5)];
        view.setBlock(x + second[0], y - 2, z + second[1], suspiciousSand);
        brushable(view, x + second[0], y - 2, z + second[1]);
        return true;
    }

    private static final java.util.Set<String> BRUSHABLE = java.util.Set.of("minecraft:brushable_block");

    /**
     * 바닐라 placeSusSand 의 {@code getBlockEntity(pos, BRUSHABLE_BLOCK).ifPresent(setLootTable)} 자리(난수 없음). 전리품 표는 블록 엔티티 값이라 아직 놓지
     * 않는다(머리 주석) — 여기서는 플러그인 레벨 통로(T-211)에서 그 레벨의 블록 엔티티를 고쳐야 하는 경우를 알리기만 한다.
     */
    private static void brushable(FeatureWorldView view, int x, int y, int z) {
        view.blockEntityPresent(x, y, z, BRUSHABLE);
    }
}
