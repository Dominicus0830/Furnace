// 바닐라 Beardifier 이식 — terrain_adaptation 구조물(마을·전초기지·고대 도시 등) 주변 지형 밀도를 보정한다(땅 메우기/깎기).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;

public final class Beardifier {

    public static final int NONE = 0, BURY = 1, BEARD_THIN = 2, BEARD_BOX = 3, ENCAPSULATE = 4;

    private static final float[] KERNEL = new float[24 * 24 * 24];

    static {
        for (int i = 0; i < 24; i++)
            for (int j = 0; j < 24; j++)
                for (int k = 0; k < 24; k++)
                    KERNEL[i * 24 * 24 + j * 24 + k] = (float) computeBeardContribution(j - 12, k - 12, i - 12);
    }

    public static int adaptation(String name) {
        return switch (name) {
            case "bury" -> BURY;
            case "beard_thin" -> BEARD_THIN;
            case "beard_box" -> BEARD_BOX;
            case "encapsulate" -> ENCAPSULATE;
            default -> NONE;
        };
    }

    /** Beardifier.Rigid(box, adaptation, groundLevelDelta) */
    public record Rigid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int adaptation, int groundLevelDelta) {}

    /** 조인트: (sourceX, sourceGroundY, sourceZ) */
    public record Joint(int x, int groundY, int z) {}

    private final Rigid[] rigids;
    private final Joint[] joints;

    public Beardifier(List<Rigid> rigids, List<Joint> joints) {
        this.rigids = rigids.toArray(new Rigid[0]);
        this.joints = joints.toArray(new Joint[0]);
    }

    public boolean isEmpty() { return rigids.length == 0 && joints.length == 0; }

    /**
     * Beardifier.forStructuresInChunk — 이 청크를 참조하는 terrain_adaptation 시작점들의 조각 중 청크 ±12 에 닿는 것.
     * starts 는 이미 "start.boundingBox() 가 청크와 겹치는" 것으로 걸러진 목록이어야 한다.
     */
    public static Beardifier forChunk(List<StructureStart> starts, int cx, int cz) {
        int minX = cx * 16, minZ = cz * 16;
        List<Rigid> rigids = new ArrayList<>();
        List<Joint> joints = new ArrayList<>();
        for (StructureStart s : starts) {
            int ad = adaptation(s.terrainAdaptation());
            if (ad == NONE) continue;
            for (StructurePiece p : s.pieces()) {
                BoundingBox b = p.boundingBox();
                // isCloseToChunk(chunkPos, 12): 2D 교차
                if (!(b.maxX() >= minX - 12 && b.minX() <= minX + 15 + 12 && b.maxZ() >= minZ - 12 && b.minZ() <= minZ + 15 + 12)) continue;
                if (p instanceof JigsawPiece jp) {
                    if (jp.rigid()) rigids.add(new Rigid(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), ad, jp.groundLevelDelta()));
                    for (JigsawPiece.Junction j : jp.junctions()) {
                        int x = j.sourceX(), z = j.sourceZ();
                        if (x > minX - 12 && z > minZ - 12 && x < minX + 15 + 12 && z < minZ + 15 + 12) joints.add(new Joint(x, j.sourceGroundY(), z));
                    }
                } else {
                    rigids.add(new Rigid(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), ad, 0));
                }
            }
        }
        return new Beardifier(rigids, joints);
    }

    /** 셀 [x0..x1]×[y0..y1]×[z0..z1] 에 0 이 아닌 기여가 있을 수 있는가(보수적). */
    public boolean mayAffect(int x0, int y0, int z0, int x1, int y1, int z1) {
        final int r = 25;
        for (Rigid g : rigids) {
            if (x1 >= g.minX - r && x0 <= g.maxX + r && z1 >= g.minZ - r && z0 <= g.maxZ + r
                && y1 >= g.minY - r - Math.abs(g.groundLevelDelta) && y0 <= g.maxY + r + Math.abs(g.groundLevelDelta)) return true;
        }
        for (Joint j : joints) {
            if (x1 >= j.x - 13 && x0 <= j.x + 13 && z1 >= j.z - 13 && z0 <= j.z + 13 && y1 >= j.groundY - 13 && y0 <= j.groundY + 13) return true;
        }
        return false;
    }

    public double compute(int x, int y, int z) {
        double d = 0.0;
        for (Rigid g : rigids) {
            int dx = Math.max(0, Math.max(g.minX - x, x - g.maxX));
            int dz = Math.max(0, Math.max(g.minZ - z, z - g.maxZ));
            int y0 = g.minY + g.groundLevelDelta;
            int dy = y - y0;
            int yDist = switch (g.adaptation) {
                case NONE -> 0;
                case BURY, BEARD_THIN -> dy;
                case BEARD_BOX -> Math.max(0, Math.max(y0 - y, y - g.maxY));
                default -> Math.max(0, Math.max(g.minY - y, y - g.maxY));   // ENCAPSULATE
            };
            d += switch (g.adaptation) {
                case NONE -> 0.0;
                case BURY -> getBuryContribution(dx, yDist / 2.0, dz);
                case BEARD_THIN, BEARD_BOX -> getBeardContribution(dx, yDist, dz, dy) * 0.8;
                default -> getBuryContribution(dx / 2.0, yDist / 2.0, dz / 2.0) * 0.8;
            };
        }
        for (Joint j : joints) {
            int dx = x - j.x, dy = y - j.groundY, dz = z - j.z;
            d += getBeardContribution(dx, dy, dz, dy) * 0.4;
        }
        return d;
    }

    private static double getBuryContribution(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);   // Mth.length
        return clampedMap(len, 0.0, 6.0, 1.0, 0.0);
    }

    private static double getBeardContribution(int x, int y, int z, int yy) {
        int i = x + 12, j = y + 12, k = z + 12;
        if (isInKernelRange(i) && isInKernelRange(j) && isInKernelRange(k)) {
            double d = yy + 0.5;
            double lenSq = x * (double) x + d * d + z * (double) z;   // Mth.lengthSquared
            double v = -d * fastInvSqrt(lenSq / 2.0) / 2.0;
            return v * KERNEL[k * 24 * 24 + i * 24 + j];
        }
        return 0.0;
    }

    private static boolean isInKernelRange(int i) { return i >= 0 && i < 24; }

    private static double computeBeardContribution(int x, int y, int z) {
        double yy = y + 0.5;
        double lenSq = x * (double) x + yy * yy + z * (double) z;
        return Math.pow(Math.E, -lenSq / 16.0);
    }

    /** Mth.fastInvSqrt(double) */
    static double fastInvSqrt(double x) {
        double half = 0.5 * x;
        long l = Double.doubleToRawLongBits(x);
        l = 6910469410427058090L - (l >> 1);
        x = Double.longBitsToDouble(l);
        return x * (1.5 - half * x * x);
    }

    /** Mth.clampedMap(v, a, b, c, d) = clampedLerp(c, d, inverseLerp(v, a, b)) */
    private static double clampedMap(double v, double a, double b, double c, double d) {
        double t = (v - a) / (b - a);
        if (t < 0.0) return c;
        if (t > 1.0) return d;
        return c + t * (d - c);
    }
}
