// monster_room — 바닐라 MonsterRoomFeature(던전) 포팅. RNG 소비 순서까지 1:1.
// 방 모양·벽(이끼 조약돌/조약돌)·상자 2개·스포너를 놓는다. 상자 loot와 스포너 몹은 월드젠 시점에 블록 엔티티로만 남는 값이라
// 블록은 상자/스포너 자리만 만들고, 바닐라가 그 값을 뽑느라 쓰는 RNG(loot seed nextLong, 몹 nextInt(4))는 그대로 소비한다.
package org.domi.furnace.server.world.gen.feature;

import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class MonsterRoomFeature implements ConfiguredFeature {

    // Direction.Plane.HORIZONTAL 순서(NORTH, EAST, SOUTH, WEST)와 이름
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] HORIZONTAL_NAME = {"north", "east", "south", "west"};
    private static final int NORTH = 0;

    private final BlockAttrs attrs = BlockAttrs.get();
    private final BlockRegistry registry;
    private final int caveAir;
    private final int cobblestone;
    private final int mossyCobblestone;
    private final int spawner;
    private final int[] chestByFacing = new int[4];
    private final Set<Integer> cannotReplaceBlocks;
    private final int chestBlock;
    private final int spawnerBlock;

    public MonsterRoomFeature(BlockRegistry registry) {
        this.registry = registry;
        this.caveAir = registry.defaultStateId("minecraft:cave_air");
        this.cobblestone = registry.defaultStateId("minecraft:cobblestone");
        this.mossyCobblestone = registry.defaultStateId("minecraft:mossy_cobblestone");
        this.spawner = registry.defaultStateId("minecraft:spawner");
        for (int i = 0; i < 4; i++) {
            chestByFacing[i] = registry.stateIdOrAir("minecraft:chest",
                Map.of("facing", HORIZONTAL_NAME[i], "type", "single", "waterlogged", "false"));
        }
        java.util.HashSet<Integer> blocks = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf("minecraft:features_cannot_replace")) {
            for (int s : registry.stateIdsForBlock(b)) blocks.add(attrs.blockIndex(s));
        }
        this.cannotReplaceBlocks = Set.copyOf(blocks);
        this.chestBlock = attrs.blockIndex(chestByFacing[NORTH]);
        this.spawnerBlock = attrs.blockIndex(spawner);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int xr = random.nextInt(2) + 2;
        int xMin = -xr - 1;
        int xMax = xr + 1;
        int zr = random.nextInt(2) + 2;
        int zMin = -zr - 1;
        int zMax = zr + 1;
        int openings = 0;

        for (int dx = xMin; dx <= xMax; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = zMin; dz <= zMax; dz++) {
                    boolean solid = attrs.isSolid(view.getBlock(x + dx, y + dy, z + dz));
                    if (dy == -1 && !solid) return false;
                    if (dy == 4 && !solid) return false;
                    if ((dx == xMin || dx == xMax || dz == zMin || dz == zMax) && dy == 0
                        && attrs.isAir(view.getBlock(x + dx, y + dy, z + dz))
                        && attrs.isAir(view.getBlock(x + dx, y + dy + 1, z + dz))) {
                        openings++;
                    }
                }
            }
        }
        if (openings < 1 || openings > 5) return false;

        for (int dx = xMin; dx <= xMax; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = zMin; dz <= zMax; dz++) {
                    int px = x + dx, py = y + dy, pz = z + dz;
                    int state = view.getBlock(px, py, pz);
                    if (dx == xMin || dy == -1 || dz == zMin || dx == xMax || dy == 4 || dz == zMax) {
                        if (py >= view.minY() && !attrs.isSolid(view.getBlock(px, py - 1, pz))) {
                            view.setBlock(px, py, pz, caveAir);
                        } else if (attrs.isSolid(state) && attrs.blockIndex(state) != chestBlock) {
                            if (dy == -1 && random.nextInt(4) != 0) {
                                safeSet(view, px, py, pz, mossyCobblestone);
                            } else {
                                safeSet(view, px, py, pz, cobblestone);
                            }
                        }
                    } else if (attrs.blockIndex(state) != chestBlock && attrs.blockIndex(state) != spawnerBlock) {
                        safeSet(view, px, py, pz, caveAir);
                    }
                }
            }
        }

        for (int chest = 0; chest < 2; chest++) {
            for (int attempt = 0; attempt < 3; attempt++) {
                int cx = x + random.nextInt(xr * 2 + 1) - xr;
                int cy = y;
                int cz = z + random.nextInt(zr * 2 + 1) - zr;
                if (!attrs.isAir(view.getBlock(cx, cy, cz))) continue;
                int solidNeighbors = 0;
                for (int[] d : HORIZONTAL) {
                    if (attrs.isSolid(view.getBlock(cx + d[0], cy, cz + d[1]))) solidNeighbors++;
                }
                if (solidNeighbors == 1) {
                    safeSet(view, cx, cy, cz, reorientChest(view, cx, cy, cz));
                    random.nextLong(); // 상자 loot table seed
                    break;
                }
            }
        }

        safeSet(view, x, y, z, spawner);
        random.nextInt(4); // 스포너 몹 종류(Util.getRandom(MOBS))
        return true;
    }

    private void safeSet(FeatureWorldView view, int x, int y, int z, int state) {
        if (!cannotReplaceBlocks.contains(attrs.blockIndex(view.getBlock(x, y, z)))) {
            view.setBlock(x, y, z, state);
        }
    }

    /** 바닐라 StructurePiece.reorient(level, pos, chest 기본 상태). */
    private int reorientChest(FeatureWorldView view, int x, int y, int z) {
        int direction = -1;
        for (int i = 0; i < 4; i++) {
            int nx = x + HORIZONTAL[i][0], nz = z + HORIZONTAL[i][1];
            int ns = view.getBlock(nx, y, nz);
            if (attrs.blockIndex(ns) == chestBlock) return chestByFacing[NORTH];
            if (attrs.isSolidRender(ns)) {
                if (direction != -1) {
                    direction = -1;
                    break;
                }
                direction = i;
            }
        }
        if (direction != -1) return chestByFacing[opposite(direction)];
        int d = NORTH; // 기본 상태의 FACING
        if (solidRender(view, x, y, z, d)) d = opposite(d);
        if (solidRender(view, x, y, z, d)) d = clockwise(d);
        if (solidRender(view, x, y, z, d)) d = opposite(d);
        return chestByFacing[d];
    }

    private boolean solidRender(FeatureWorldView view, int x, int y, int z, int dir) {
        return attrs.isSolidRender(view.getBlock(x + HORIZONTAL[dir][0], y, z + HORIZONTAL[dir][1]));
    }

    private static int opposite(int d) {
        return (d + 2) & 3;
    }

    private static int clockwise(int d) {
        return (d + 1) & 3;
    }
}
