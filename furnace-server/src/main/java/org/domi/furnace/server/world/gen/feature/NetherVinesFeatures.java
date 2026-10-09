// weeping_vines / twisting_vines — 바닐라 WeepingVinesFeature·TwistingVinesFeature 포팅(+ 거대 균류가 쓰는 placeWeepingVinesColumn)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class NetherVinesFeatures {

    private NetherVinesFeatures() {}

    /** 두 덩굴의 블록 state 표(age 0..25 머리 + 줄기). */
    public static final class Blocks {
        final BlockAttrs attrs = BlockAttrs.get();
        final int[] weepingHead = new int[26], twistingHead = new int[26];
        final int weepingPlant, twistingPlant, wartBlock;
        final int netherrackBlock, wartBlockBlock, warpedNyliumBlock, warpedWartBlockBlock;

        public Blocks(BlockRegistry r) {
            for (int age = 0; age <= 25; age++) {
                weepingHead[age] = r.stateIdOrAir("minecraft:weeping_vines", java.util.Map.of("age", Integer.toString(age)));
                twistingHead[age] = r.stateIdOrAir("minecraft:twisting_vines", java.util.Map.of("age", Integer.toString(age)));
            }
            weepingPlant = r.defaultStateId("minecraft:weeping_vines_plant");
            twistingPlant = r.defaultStateId("minecraft:twisting_vines_plant");
            wartBlock = r.defaultStateId("minecraft:nether_wart_block");
            netherrackBlock = attrs.blockIndex(r.defaultStateId("minecraft:netherrack"));
            wartBlockBlock = attrs.blockIndex(wartBlock);
            warpedNyliumBlock = attrs.blockIndex(r.defaultStateId("minecraft:warped_nylium"));
            warpedWartBlockBlock = attrs.blockIndex(r.defaultStateId("minecraft:warped_wart_block"));
        }

        boolean isAir(FeatureWorldView v, int x, int y, int z) { return attrs.isAir(v.getBlock(x, y, z)); }

        boolean isNetherrackOrWart(int state) {
            int b = attrs.blockIndex(state);
            return b == netherrackBlock || b == wartBlockBlock;
        }
    }

    /** WeepingVinesFeature.placeWeepingVinesColumn(level, random, pos, length, minAge, maxAge) — 아래로 자란다. */
    public static void placeWeepingColumn(Blocks b, FeatureWorldView v, WorldgenRandom random, int x, int y, int z, int length, int minAge, int maxAge) {
        for (int i = 0; i <= length; i++) {
            if (b.isAir(v, x, y, z)) {
                if (i == length || !b.isAir(v, x, y - 1, z)) {
                    v.setBlock(x, y, z, b.weepingHead[nextIntBetween(random, minAge, maxAge)]);
                    break;
                }
                v.setBlock(x, y, z, b.weepingPlant);
            }
            y--;
        }
    }

    /** TwistingVinesFeature.placeTwistingVinesColumn — 위로 자란다(i 는 1 부터). */
    public static void placeTwistingColumn(Blocks b, FeatureWorldView v, WorldgenRandom random, int x, int y, int z, int length, int minAge, int maxAge) {
        for (int i = 1; i <= length; i++) {
            if (b.isAir(v, x, y, z)) {
                if (i == length || !b.isAir(v, x, y + 1, z)) {
                    v.setBlock(x, y, z, b.twistingHead[nextIntBetween(random, minAge, maxAge)]);
                    break;
                }
                v.setBlock(x, y, z, b.twistingPlant);
            }
            y++;
        }
    }

    /** Mth.nextInt(random, min, max). */
    static int nextIntBetween(WorldgenRandom random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    // ------------------------------------------------------------------ weeping_vines

    public static final class Weeping implements ConfiguredFeature {
        private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};   // Direction.values()
        private final Blocks b;

        public Weeping(Blocks b) { this.b = b; }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
            if (!b.isAir(v, x, y, z)) return false;
            if (!b.isNetherrackOrWart(v.getBlock(x, y + 1, z))) return false;
            placeRoofNetherWart(v, random, x, y, z);
            placeRoofWeepingVines(v, random, x, y, z);
            return true;
        }

        private void placeRoofNetherWart(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
            v.setBlock(ox, oy, oz, b.wartBlock);
            for (int i = 0; i < 200; i++) {
                int px = ox + random.nextInt(6) - random.nextInt(6);
                int py = oy + random.nextInt(2) - random.nextInt(5);
                int pz = oz + random.nextInt(6) - random.nextInt(6);
                if (b.isAir(v, px, py, pz)) {
                    int j = 0;
                    for (int[] d : DIRS) {
                        if (b.isNetherrackOrWart(v.getBlock(px + d[0], py + d[1], pz + d[2]))) j++;
                        if (j > 1) break;
                    }
                    if (j == 1) v.setBlock(px, py, pz, b.wartBlock);
                }
            }
        }

        private void placeRoofWeepingVines(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
            for (int i = 0; i < 100; i++) {
                int px = ox + random.nextInt(8) - random.nextInt(8);
                int py = oy + random.nextInt(2) - random.nextInt(7);
                int pz = oz + random.nextInt(8) - random.nextInt(8);
                if (b.isAir(v, px, py, pz)) {
                    if (b.isNetherrackOrWart(v.getBlock(px, py + 1, pz))) {
                        int j = nextIntBetween(random, 1, 8);
                        if (random.nextInt(6) == 0) j *= 2;
                        if (random.nextInt(5) == 0) j = 1;
                        placeWeepingColumn(b, v, random, px, py, pz, j, 17, 25);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ twisting_vines

    public static final class Twisting implements ConfiguredFeature {
        private final Blocks b;
        private final int spreadWidth, spreadHeight, maxHeight;

        public Twisting(Blocks b, int spreadWidth, int spreadHeight, int maxHeight) {
            this.b = b;
            this.spreadWidth = spreadWidth;
            this.spreadHeight = spreadHeight;
            this.maxHeight = maxHeight;
        }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
            if (isInvalidPlacementLocation(v, ox, oy, oz)) return false;
            int w = spreadWidth, h = spreadHeight;
            for (int k = 0; k < w * w; k++) {
                int px = ox + nextIntBetween(random, -w, w);
                int py = oy + nextIntBetween(random, -h, h);
                int pz = oz + nextIntBetween(random, -w, w);
                // findFirstAirAboveGround(level, pos) — 아래로 내려가며 비공기를 찾고 한 칸 위로 복귀
                boolean outside = false;
                do {
                    py--;
                    if (py < v.minBuildHeight() || py >= v.maxBuildHeight()) { outside = true; break; }
                } while (b.attrs.isAir(v.getBlock(px, py, pz)));
                if (outside) continue;
                py++;
                if (!isInvalidPlacementLocation(v, px, py, pz)) {
                    int j = nextIntBetween(random, 1, maxHeight);
                    if (random.nextInt(6) == 0) j *= 2;
                    if (random.nextInt(5) == 0) j = 1;
                    placeTwistingColumn(b, v, random, px, py, pz, j, 17, 25);
                }
            }
            return true;
        }

        private boolean isInvalidPlacementLocation(FeatureWorldView v, int x, int y, int z) {
            if (!b.isAir(v, x, y, z)) return true;
            int below = b.attrs.blockIndex(v.getBlock(x, y - 1, z));
            return below != b.netherrackBlock && below != b.warpedNyliumBlock && below != b.warpedWartBlockBlock;
        }
    }
}
