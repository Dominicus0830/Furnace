// basalt_columns / basalt_pillar — 바닐라 BasaltColumnsFeature·BasaltPillarFeature 포팅
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BasaltFeatures {

    private BasaltFeatures() {}

    // ------------------------------------------------------------------ basalt_columns

    public static final class Columns implements ConfiguredFeature {
        private final BlockAttrs attrs = BlockAttrs.get();
        private final TreeIntProvider reach, height;
        private final int basaltState, lavaBlock, basaltBlock;
        private final Set<Integer> cannotPlaceOn;   // 블록 인덱스

        public Columns(TreeIntProvider reach, TreeIntProvider height, BlockRegistry r) {
            this.reach = reach;
            this.height = height;
            this.basaltState = r.defaultStateId("minecraft:basalt");
            this.basaltBlock = attrs.blockIndex(basaltState);
            this.lavaBlock = attrs.blockIndex(r.defaultStateId("minecraft:lava"));
            java.util.HashSet<Integer> s = new java.util.HashSet<>();
            for (String n : new String[]{"lava", "bedrock", "magma_block", "soul_sand", "nether_bricks", "nether_brick_fence",
                "nether_brick_stairs", "nether_wart", "chest", "spawner"}) {
                s.add(attrs.blockIndex(r.defaultStateId("minecraft:" + n)));
            }
            this.cannotPlaceOn = Set.copyOf(s);
        }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
            int seaLevel = v.seaLevel();
            if (!canPlaceAt(v, seaLevel, ox, oy, oz)) return false;
            int h = height.sample(random);
            boolean flag = random.nextFloat() < 0.9F;
            int j = Math.min(h, flag ? 5 : 8);
            int k = flag ? 50 : 15;
            boolean any = false;
            // BlockPos.randomBetweenClosed(random, k, ox-j, oy, oz-j, ox+j, oy, oz+j): x,y,z 순으로 난수 소비
            int sx = 2 * j + 1, sy = 1, sz = 2 * j + 1;
            for (int n = 0; n < k; n++) {
                int px = ox - j + random.nextInt(sx);
                int py = oy + random.nextInt(sy);
                int pz = oz - j + random.nextInt(sz);
                int l = h - BlockPosIter.distManhattan(px, py, pz, ox, oy, oz);
                if (l >= 0) any |= placeColumn(v, seaLevel, px, py, pz, l, reach.sample(random));
            }
            return any;
        }

        private boolean placeColumn(FeatureWorldView v, int seaLevel, int cx, int cy, int cz, int columnHeight, int r) {
            boolean placed = false;
            // BlockPos.betweenClosed(cx-r, cy, cz-r, cx+r, cy, cz+r): x 가장 빠르게, 그다음 y, z
            for (int pz = cz - r; pz <= cz + r; pz++) {
                for (int px = cx - r; px <= cx + r; px++) {
                    int dist = BlockPosIter.distManhattan(px, cy, pz, cx, cy, cz);
                    int fy = isAirOrLavaOcean(v, seaLevel, px, cy, pz)
                        ? findSurface(v, seaLevel, px, cy, pz, dist)
                        : findAir(v, px, cy, pz, dist);
                    if (fy != Integer.MIN_VALUE) {
                        int j = columnHeight - dist / 2;
                        int my = fy;
                        for (; j >= 0; j--) {
                            if (isAirOrLavaOcean(v, seaLevel, px, my, pz)) {
                                v.setBlock(px, my, pz, basaltState);
                                my++;
                                placed = true;
                            } else {
                                if (attrs.blockIndex(v.getBlock(px, my, pz)) != basaltBlock) break;
                                my++;
                            }
                        }
                    }
                }
            }
            return placed;
        }

        /** 찾은 y 를 돌려주고, 없으면 Integer.MIN_VALUE(바닐라 null). */
        private int findSurface(FeatureWorldView v, int seaLevel, int x, int y, int z, int maxDist) {
            while (y > v.minBuildHeight() + 1 && maxDist > 0) {
                maxDist--;
                if (canPlaceAt(v, seaLevel, x, y, z)) return y;
                y--;
            }
            return Integer.MIN_VALUE;
        }

        private int findAir(FeatureWorldView v, int x, int y, int z, int maxDist) {
            while (y < v.maxBuildHeight() && maxDist > 0) {
                maxDist--;
                int s = v.getBlock(x, y, z);
                if (cannotPlaceOn.contains(attrs.blockIndex(s))) return Integer.MIN_VALUE;
                if (attrs.isAir(s)) return y;
                y++;
            }
            return Integer.MIN_VALUE;
        }

        private boolean canPlaceAt(FeatureWorldView v, int seaLevel, int x, int y, int z) {
            if (!isAirOrLavaOcean(v, seaLevel, x, y, z)) return false;
            int below = v.getBlock(x, y - 1, z);
            return !attrs.isAir(below) && !cannotPlaceOn.contains(attrs.blockIndex(below));
        }

        private boolean isAirOrLavaOcean(FeatureWorldView v, int seaLevel, int x, int y, int z) {
            int s = v.getBlock(x, y, z);
            return attrs.isAir(s) || attrs.blockIndex(s) == lavaBlock && y <= seaLevel;
        }
    }

    // ------------------------------------------------------------------ basalt_pillar

    public static final class Pillar implements ConfiguredFeature {
        private final BlockAttrs attrs = BlockAttrs.get();
        private final int basaltState;

        public Pillar(BlockRegistry r) {
            this.basaltState = r.defaultStateId("minecraft:basalt");
        }

        private boolean empty(FeatureWorldView v, int x, int y, int z) { return attrs.isAir(v.getBlock(x, y, z)); }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
            if (!(empty(v, ox, oy, oz) && !empty(v, ox, oy + 1, oz))) return false;
            int y = oy;
            boolean north = true, south = true, west = true, east = true;
            while (empty(v, ox, y, oz)) {
                if (y < v.minBuildHeight() || y >= v.maxBuildHeight()) return true;
                v.setBlock(ox, y, oz, basaltState);
                // && 단락 평가 — 앞 방향이 한 번 실패하면 이후 해당 방향은 난수를 소비하지 않는다
                north = north && placeHangOff(v, random, ox, y, oz - 1);
                south = south && placeHangOff(v, random, ox, y, oz + 1);
                west = west && placeHangOff(v, random, ox - 1, y, oz);
                east = east && placeHangOff(v, random, ox + 1, y, oz);
                y--;
            }
            y++;
            placeBaseHangOff(v, random, ox, y, oz - 1);
            placeBaseHangOff(v, random, ox, y, oz + 1);
            placeBaseHangOff(v, random, ox - 1, y, oz);
            placeBaseHangOff(v, random, ox + 1, y, oz);
            y--;
            for (int i = -3; i < 4; i++) {
                for (int j = -3; j < 4; j++) {
                    int k = Math.abs(i) * Math.abs(j);
                    if (random.nextInt(10) < 10 - k) {
                        int px = ox + i, py = y, pz = oz + j;
                        int l = 3;
                        while (empty(v, px, py - 1, pz)) {
                            py--;
                            if (--l <= 0) break;
                        }
                        if (!empty(v, px, py - 1, pz)) v.setBlock(px, py, pz, basaltState);
                    }
                }
            }
            return true;
        }

        private void placeBaseHangOff(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
            if (random.nextBoolean()) v.setBlock(x, y, z, basaltState);
        }

        private boolean placeHangOff(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
            if (random.nextInt(10) != 0) {
                v.setBlock(x, y, z, basaltState);
                return true;
            }
            return false;
        }
    }
}
