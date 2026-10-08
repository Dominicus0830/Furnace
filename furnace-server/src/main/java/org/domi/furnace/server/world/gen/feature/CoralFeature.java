// coral_tree / coral_claw / coral_mushroom — 바닐라 CoralFeature 계열 포팅(warm_ocean_vegetation의 simple_random_selector 안에서 쓰인다).
// 산호 블록·산호·벽 산호 고르기는 태그 순서대로 nextInt(size) 하나로 뽑는 Registry.getRandomElementOf(tag, random)과 같다.
package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public abstract class CoralFeature implements ConfiguredFeature {

    public enum Shape { TREE, CLAW, MUSHROOM }

    // Direction.Plane.HORIZONTAL 순서: NORTH, EAST, SOUTH, WEST
    protected static final int[][] H = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] H_NAME = {"north", "east", "south", "west"};
    private static final int UP = 4; // 방향 코드: 0..3 수평, 4 위

    protected final BlockAttrs attrs = BlockAttrs.get();
    private final int[] coralBlocks;
    private final int[] corals;
    private final int[][] wallCorals; // [태그 순번][수평 방향]
    private final Set<Integer> coralBlockIndexSet;
    private final int waterBlock;
    private final int[] seaPickleByCount = new int[4];

    protected CoralFeature(BlockRegistry registry) {
        this.coralBlocks = defaults(registry, "minecraft:coral_blocks");
        this.corals = defaults(registry, "minecraft:corals");
        List<String> walls = BlockTags.get().blocksOf("minecraft:wall_corals");
        this.wallCorals = new int[walls.size()][4];
        for (int i = 0; i < walls.size(); i++) {
            for (int d = 0; d < 4; d++) {
                wallCorals[i][d] = registry.stateIdOrAir(walls.get(i), Map.of("facing", H_NAME[d], "waterlogged", "true"));
            }
        }
        Set<Integer> idx = new HashSet<>();
        for (int s : corals) idx.add(attrs.blockIndex(s));
        this.coralBlockIndexSet = Set.copyOf(idx);
        this.waterBlock = attrs.blockIndex(registry.defaultStateId("minecraft:water"));
        for (int i = 0; i < 4; i++) {
            seaPickleByCount[i] = registry.stateIdOrAir("minecraft:sea_pickle",
                Map.of("pickles", Integer.toString(i + 1), "waterlogged", "true"));
        }
    }

    private static int[] defaults(BlockRegistry registry, String tag) {
        List<Integer> out = new ArrayList<>();
        for (String b : BlockTags.get().blocksOf(tag)) out.add(registry.defaultStateId(b));
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    public static CoralFeature of(Shape shape, BlockRegistry registry) {
        return switch (shape) {
            case TREE -> new Tree(registry);
            case CLAW -> new Claw(registry);
            case MUSHROOM -> new Mushroom(registry);
        };
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int block = coralBlocks[random.nextInt(coralBlocks.length)];
        return placeCoral(view, random, x, y, z, block);
    }

    protected abstract boolean placeCoral(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int coralBlock);

    /** 바닐라 CoralFeature.placeCoralBlock. */
    protected boolean placeCoralBlock(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int coralBlock) {
        int current = view.getBlock(x, y, z);
        int cb = attrs.blockIndex(current);
        if ((cb == waterBlock || coralBlockIndexSet.contains(cb))
            && attrs.blockIndex(view.getBlock(x, y + 1, z)) == waterBlock) {
            view.setBlock(x, y, z, coralBlock);
            if (random.nextFloat() < 0.25F) {
                view.setBlock(x, y + 1, z, corals[random.nextInt(corals.length)]);
            } else if (random.nextFloat() < 0.05F) {
                view.setBlock(x, y + 1, z, seaPickleByCount[random.nextInt(4)]);
            }
            for (int d = 0; d < 4; d++) {
                if (random.nextFloat() < 0.2F) {
                    int nx = x + H[d][0], nz = z + H[d][1];
                    if (attrs.blockIndex(view.getBlock(nx, y, nz)) == waterBlock) {
                        view.setBlock(nx, y, nz, wallCorals[random.nextInt(wallCorals.length)][d]);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /** Util.shuffle — 뒤에서부터 Fisher-Yates(바닐라와 같은 호출 순서). */
    protected static <T> void shuffle(List<T> list, WorldgenRandom random) {
        for (int i = list.size(); i > 1; i--) {
            int j = random.nextInt(i);
            T tmp = list.get(i - 1);
            list.set(i - 1, list.get(j));
            list.set(j, tmp);
        }
    }

    static final class Tree extends CoralFeature {
        Tree(BlockRegistry registry) { super(registry); }

        @Override
        protected boolean placeCoral(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int coral) {
            int cx = x, cy = y, cz = z;
            int trunk = random.nextInt(3) + 1;
            for (int i = 0; i < trunk; i++) {
                if (!placeCoralBlock(view, random, cx, cy, cz, coral)) return true;
                cy++;
            }
            int bx = cx, by = cy, bz = cz;
            int branches = random.nextInt(3) + 2;
            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            shuffle(dirs, random);
            for (int dir : dirs.subList(0, branches)) {
                cx = bx + H[dir][0];
                cy = by;
                cz = bz + H[dir][1];
                int length = random.nextInt(5) + 2;
                int run = 0;
                for (int i = 0; i < length && placeCoralBlock(view, random, cx, cy, cz, coral); i++) {
                    run++;
                    cy++;
                    if (i == 0 || run >= 2 && random.nextFloat() < 0.25F) {
                        cx += H[dir][0];
                        cz += H[dir][1];
                        run = 0;
                    }
                }
            }
            return true;
        }
    }

    static final class Mushroom extends CoralFeature {
        Mushroom(BlockRegistry registry) { super(registry); }

        @Override
        protected boolean placeCoral(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int coral) {
            int sx = random.nextInt(3) + 3;
            int sy = random.nextInt(3) + 3;
            int sz = random.nextInt(3) + 3;
            int down = random.nextInt(3) + 1;
            for (int dx = 0; dx <= sy; dx++) {
                for (int dy = 0; dy <= sx; dy++) {
                    for (int dz = 0; dz <= sz; dz++) {
                        int px = dx + x, py = dy + y - down, pz = dz + z;
                        if ((dx != 0 && dx != sy || dy != 0 && dy != sx)
                            && (dz != 0 && dz != sz || dy != 0 && dy != sx)
                            && (dx != 0 && dx != sy || dz != 0 && dz != sz)
                            && (dx == 0 || dx == sy || dy == 0 || dy == sx || dz == 0 || dz == sz)
                            && !(random.nextFloat() < 0.1F)) {
                            placeCoralBlock(view, random, px, py, pz, coral);
                        }
                    }
                }
            }
            return true;
        }
    }

    static final class Claw extends CoralFeature {
        Claw(BlockRegistry registry) { super(registry); }

        @Override
        protected boolean placeCoral(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int coral) {
            if (!placeCoralBlock(view, random, x, y, z, coral)) return false;
            int primary = random.nextInt(4);
            int count = random.nextInt(2) + 2;
            List<Integer> dirs = new ArrayList<>(List.of(primary, (primary + 1) & 3, (primary + 3) & 3));
            shuffle(dirs, random);
            for (int dir : dirs.subList(0, count)) {
                int cx = x, cy = y, cz = z;
                int arm = random.nextInt(2) + 1;
                cx += H[dir][0];
                cz += H[dir][1];
                int moveDir;
                int height;
                if (dir == primary) {
                    moveDir = primary;
                    height = random.nextInt(3) + 2;
                } else {
                    cy++;
                    moveDir = random.nextInt(2) == 0 ? dir : UP; // Util.getRandom({dir, UP})
                    height = random.nextInt(3) + 3;
                }
                for (int i = 0; i < arm && placeCoralBlock(view, random, cx, cy, cz, coral); i++) {
                    if (moveDir == UP) cy++; else { cx += H[moveDir][0]; cz += H[moveDir][1]; }
                }
                // $$9.c($$11.g()) — 이동 방향의 반대로 한 칸, 그다음 위로 한 칸
                if (moveDir == UP) cy--; else { cx -= H[moveDir][0]; cz -= H[moveDir][1]; }
                cy++;
                for (int i = 0; i < height; i++) {
                    cx += H[primary][0];
                    cz += H[primary][1];
                    if (!placeCoralBlock(view, random, cx, cy, cz, coral)) break;
                    if (random.nextFloat() < 0.25F) cy++;
                }
            }
            return true;
        }
    }
}
