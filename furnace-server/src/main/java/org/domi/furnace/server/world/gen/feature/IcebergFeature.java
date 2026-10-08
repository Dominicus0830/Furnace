// iceberg — 바닐라 IcebergFeature 포팅 (바다 수면 높이에서 빙산 본체 + 수중부 + 잘라내기)
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class IcebergFeature implements ConfiguredFeature {

    private static final int SEA_LEVEL = 63;      // ChunkGenerator.getSeaLevel() (overworld)

    private final BlockAttrs attrs = BlockAttrs.get();
    private final int state;
    private final int air;
    private final int snowBlock;
    private final int ice;
    private final int packedIce;
    private final int blueIce;
    private final int waterDefault;
    private final Set<Integer> water;
    private final Set<Integer> snowLayers;

    public IcebergFeature(int state, BlockRegistry registry) {
        this.state = state;
        this.air = 0;
        this.snowBlock = registry.defaultStateId("minecraft:snow_block");
        this.ice = registry.defaultStateId("minecraft:ice");
        this.packedIce = registry.defaultStateId("minecraft:packed_ice");
        this.blueIce = registry.defaultStateId("minecraft:blue_ice");
        this.waterDefault = registry.defaultStateId("minecraft:water");
        this.water = registry.stateIdsForBlock("minecraft:water");
        this.snowLayers = registry.stateIdsForBlock("minecraft:snow");
    }

    private static int ceil(float f) {
        int i = (int) f;
        return f > (float) i ? i + 1 : i;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom r, int ox, int oy, int oz) {
        oy = SEA_LEVEL;
        boolean snowOnTop = r.nextDouble() > 0.7;
        double angle = r.nextDouble() * 2.0 * Math.PI;
        int baseOval = 11 - r.nextInt(5);
        int ellipseB = 3 + r.nextInt(3);
        boolean oval = r.nextDouble() > 0.7;
        int h = oval ? r.nextInt(6) + 6 : r.nextInt(15) + 3;
        if (!oval && r.nextDouble() > 0.9) {
            h += r.nextInt(19) + 7;
        }
        int depth = Math.min(h + r.nextInt(11), 18);
        int width = Math.min(h + r.nextInt(7) - r.nextInt(5), 11);
        int radius = oval ? baseOval : 11;

        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                for (int y = 0; y < h; y++) {
                    int w = oval ? radiusEllipse(y, h, width) : radiusRound(r, y, h, width);
                    if (oval || x < w) {
                        placeColumn(view, r, ox, oy, oz, h, x, y, z, w, radius, oval, ellipseB, angle, snowOnTop);
                    }
                }
            }
        }

        smooth(view, ox, oy, oz, width, h, oval, baseOval);

        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                for (int y = -1; y > -depth; y--) {
                    int rr = oval ? ceil((float) radius * (1.0F - (float) Math.pow((double) y, 2.0) / ((float) depth * 8.0F))) : radius;
                    int w = radiusSteep(r, -y, depth, width);
                    if (x < w) {
                        placeColumn(view, r, ox, oy, oz, depth, x, y, z, w, rr, oval, ellipseB, angle, snowOnTop);
                    }
                }
            }
        }

        boolean cutOut = oval ? r.nextDouble() > 0.1 : r.nextDouble() > 0.7;
        if (cutOut) {
            cutOut(view, r, width, h, ox, oy, oz, oval, baseOval, angle, ellipseB);
        }
        return true;
    }

    private void cutOut(FeatureWorldView view, WorldgenRandom r, int width, int h, int ox, int oy, int oz,
                        boolean oval, int baseOval, double angle, int ellipseB) {
        int sx = r.nextBoolean() ? -1 : 1;
        int sz = r.nextBoolean() ? -1 : 1;
        int a = r.nextInt(Math.max(width / 2 - 2, 1));
        if (r.nextBoolean()) a = width / 2 + 1 - r.nextInt(Math.max(width - width / 2 - 1, 1));
        int b = r.nextInt(Math.max(width / 2 - 2, 1));
        if (r.nextBoolean()) b = width / 2 + 1 - r.nextInt(Math.max(width - width / 2 - 1, 1));
        if (oval) {
            a = b = r.nextInt(Math.max(baseOval - 5, 1));
        }
        int offX = sx * a;
        int offZ = sz * b;
        double ang = oval ? angle + (Math.PI / 2) : r.nextDouble() * 2.0 * Math.PI;

        for (int i = 0; i < h - 3; i++) {
            int rad = radiusRound(r, i, h, width);
            carve(view, rad, i, ox, oy, oz, false, ang, offX, offZ, baseOval, ellipseB);
        }
        for (int i = -1; i > -h + r.nextInt(5); i--) {
            int rad = radiusSteep(r, -i, h, width);
            carve(view, rad, i, ox, oy, oz, true, ang, offX, offZ, baseOval, ellipseB);
        }
    }

    private void carve(FeatureWorldView view, int rad, int y, int ox, int oy, int oz, boolean makeWater, double ang,
                       int offX, int offZ, int baseOval, int ellipseB) {
        int a = rad + 1 + baseOval / 3;
        int b = Math.min(rad - 3, 3) + ellipseB / 2 - 1;
        for (int x = -a; x < a; x++) {
            for (int z = -a; z < a; z++) {
                double d = signedDistanceEllipse(x, z, offX, offZ, a, b, ang);
                if (d < 0.0) {
                    int px = ox + x, py = oy + y, pz = oz + z;
                    int cur = view.getBlock(px, py, pz);
                    if (isIcebergBlock(cur) || cur == snowBlock) {
                        if (makeWater) {
                            view.setBlock(px, py, pz, waterDefault);
                        } else {
                            view.setBlock(px, py, pz, air);
                            if (snowLayers.contains(view.getBlock(px, py + 1, pz))) {
                                view.setBlock(px, py + 1, pz, air);
                            }
                        }
                    }
                }
            }
        }
    }

    private void placeColumn(FeatureWorldView view, WorldgenRandom r, int ox, int oy, int oz, int hh, int x, int y, int z,
                             int w, int radius, boolean oval, int ellipseB, double angle, boolean snowOnTop) {
        double d = oval
            ? signedDistanceEllipse(x, z, 0, 0, radius, ellipseC(y, hh, ellipseB), angle)
            : signedDistanceCircle(x, z, w, r);
        if (d < 0.0) {
            double e = oval ? -0.5 : (double) (-6 - r.nextInt(3));
            if (d > e && r.nextDouble() > 0.9) {
                return;
            }
            setIcebergBlock(view, ox + x, oy + y, oz + z, r, hh - y, hh, oval, snowOnTop);
        }
    }

    private void setIcebergBlock(FeatureWorldView view, int x, int y, int z, WorldgenRandom r, int heightDiff, int h,
                                 boolean oval, boolean snowOnTop) {
        int cur = view.getBlock(x, y, z);
        if (attrs.isAir(cur) || cur == snowBlock || cur == ice || water.contains(cur)) {
            boolean ok = !oval || r.nextDouble() > 0.05;
            int div = oval ? 3 : 2;
            if (snowOnTop && !water.contains(cur)
                && (double) heightDiff <= (double) r.nextInt(Math.max(1, h / div)) + (double) h * 0.6 && ok) {
                view.setBlock(x, y, z, snowBlock);
            } else {
                view.setBlock(x, y, z, state);
            }
        }
    }

    private void smooth(FeatureWorldView view, int ox, int oy, int oz, int width, int h, boolean oval, int baseOval) {
        int rr = oval ? baseOval : width / 2;
        for (int x = -rr; x <= rr; x++) {
            for (int z = -rr; z <= rr; z++) {
                for (int y = 0; y <= h; y++) {
                    int px = ox + x, py = oy + y, pz = oz + z;
                    int cur = view.getBlock(px, py, pz);
                    if (isIcebergBlock(cur) || snowLayers.contains(cur)) {
                        if (attrs.isAir(view.getBlock(px, py - 1, pz))) {
                            view.setBlock(px, py, pz, air);
                            view.setBlock(px, py + 1, pz, air);
                        } else if (isIcebergBlock(cur)) {
                            int count = 0;
                            if (!isIcebergBlock(view.getBlock(px - 1, py, pz))) count++;
                            if (!isIcebergBlock(view.getBlock(px + 1, py, pz))) count++;
                            if (!isIcebergBlock(view.getBlock(px, py, pz - 1))) count++;
                            if (!isIcebergBlock(view.getBlock(px, py, pz + 1))) count++;
                            if (count >= 3) {
                                view.setBlock(px, py, pz, air);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isIcebergBlock(int s) {
        return s == packedIce || s == snowBlock || s == blueIce;
    }

    private static int ellipseC(int y, int h, int b) {
        int c = b;
        if (y > 0 && h - y <= 3) {
            c = b - (4 - (h - y));
        }
        return c;
    }

    private static double signedDistanceCircle(int x, int z, int w, WorldgenRandom r) {
        float f = 10.0F * lerp(r.nextFloat(), 0.2F, 0.8F) / (float) w;
        return (double) f + Math.pow((double) x, 2.0) + Math.pow((double) z, 2.0) - Math.pow((double) w, 2.0);
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static double signedDistanceEllipse(int x, int z, int offX, int offZ, int a, int b, double angle) {
        return Math.pow(((double) (x - offX) * Math.cos(angle) - (double) (z - offZ) * Math.sin(angle)) / (double) a, 2.0)
            + Math.pow(((double) (x - offX) * Math.sin(angle) + (double) (z - offZ) * Math.cos(angle)) / (double) b, 2.0)
            - 1.0;
    }

    private static int radiusRound(WorldgenRandom r, int y, int h, int width) {
        float f = 3.5F - r.nextFloat();
        float g = (1.0F - (float) Math.pow((double) y, 2.0) / ((float) h * f)) * (float) width;
        if (h > 15 + r.nextInt(5)) {
            int i = y < 3 + r.nextInt(6) ? y / 2 : y;
            g = (1.0F - (float) i / ((float) h * f * 0.4F)) * (float) width;
        }
        return ceil(g / 2.0F);
    }

    private static int radiusEllipse(int y, int h, int width) {
        float g = (1.0F - (float) Math.pow((double) y, 2.0) / ((float) h * 1.0F)) * (float) width;
        return ceil(g / 2.0F);
    }

    private static int radiusSteep(WorldgenRandom r, int y, int h, int width) {
        float f = 1.0F + r.nextFloat() / 2.0F;
        float g = (1.0F - (float) y / ((float) h * f)) * (float) width;
        return ceil(g / 2.0F);
    }
}
