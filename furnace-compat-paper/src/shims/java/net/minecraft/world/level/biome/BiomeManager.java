package net.minecraft.world.level.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

/**
 * 바닐라 BiomeManager(Mojang 이름 shim, T-211 3단계) — 바닐라 1.21.1 그대로: 블록 좌표의 바이옴은 둘레 quart 8개 가운데 시드 지터 거리가 가장 가까운 것.
 * 레벨의 것은 {@code Level#getBiomeManager()} — 원천은 그 레벨의 {@code getNoiseBiome}(로드된 청크는 저장된 바이옴, 아니면 생성기).
 */
class BiomeManager {

    public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
    private static final int ZOOM_BITS = 2;
    private static final int ZOOM = 4;
    private static final int ZOOM_MASK = 3;
    private final NoiseBiomeSource noiseBiomeSource;
    private final long biomeZoomSeed;

    public BiomeManager(NoiseBiomeSource storage, long seed) {
        this.noiseBiomeSource = storage;
        this.biomeZoomSeed = seed;
    }

    /** 바닐라: 구아바 {@code Hashing.sha256().hashLong(seed).asLong()} — 8바이트 리틀 엔디언의 SHA-256 앞 8바이트를 리틀 엔디언 long 으로. */
    public static long obfuscateSeed(long seed) {
        try {
            byte[] in = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(in);
            return java.nio.ByteBuffer.wrap(h, 0, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public BiomeManager withDifferentSource(NoiseBiomeSource storage) { return new BiomeManager(storage, biomeZoomSeed); }

    public Holder<Biome> getBiome(BlockPos pos) {
        int i = pos.getX() - 2;
        int j = pos.getY() - 2;
        int k = pos.getZ() - 2;
        int l = i >> 2;
        int m = j >> 2;
        int n = k >> 2;
        double d = (double) (i & 3) / 4.0;
        double e = (double) (j & 3) / 4.0;
        double f = (double) (k & 3) / 4.0;
        int o = 0;
        double g = Double.POSITIVE_INFINITY;
        for (int p = 0; p < 8; p++) {
            boolean bl = (p & 4) == 0;
            boolean bl2 = (p & 2) == 0;
            boolean bl3 = (p & 1) == 0;
            int q = bl ? l : l + 1;
            int r = bl2 ? m : m + 1;
            int s = bl3 ? n : n + 1;
            double h = bl ? d : d - 1.0;
            double t = bl2 ? e : e - 1.0;
            double u = bl3 ? f : f - 1.0;
            double v = getFiddledDistance(biomeZoomSeed, q, r, s, h, t, u);
            if (g > v) {
                o = p;
                g = v;
            }
        }
        int w = (o & 4) == 0 ? l : l + 1;
        int x = (o & 2) == 0 ? m : m + 1;
        int y = (o & 1) == 0 ? n : n + 1;
        return noiseBiomeSource.getNoiseBiome(w, x, y);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(double x, double y, double z) {
        return getNoiseBiomeAtQuart(QuartPos.fromBlock(Mth.floor(x)), QuartPos.fromBlock(Mth.floor(y)), QuartPos.fromBlock(Mth.floor(z)));
    }

    public Holder<Biome> getNoiseBiomeAtPosition(BlockPos pos) {
        return getNoiseBiomeAtQuart(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
    }

    public Holder<Biome> getNoiseBiomeAtQuart(int biomeX, int biomeY, int biomeZ) { return noiseBiomeSource.getNoiseBiome(biomeX, biomeY, biomeZ); }

    private static double getFiddledDistance(long l, int i, int j, int k, double d, double e, double f) {
        long m = LinearCongruentialGenerator.next(l, i);
        m = LinearCongruentialGenerator.next(m, j);
        m = LinearCongruentialGenerator.next(m, k);
        m = LinearCongruentialGenerator.next(m, i);
        m = LinearCongruentialGenerator.next(m, j);
        m = LinearCongruentialGenerator.next(m, k);
        double g = getFiddle(m);
        m = LinearCongruentialGenerator.next(m, l);
        double h = getFiddle(m);
        m = LinearCongruentialGenerator.next(m, l);
        double n = getFiddle(m);
        return Mth.square(f + n) + Mth.square(e + h) + Mth.square(d + g);
    }

    private static double getFiddle(long l) {
        double d = (double) Math.floorMod(l >> 24, 1024) / 1024.0;
        return (d - 0.5) * 0.9;
    }

    /** 바닐라 BiomeManager.NoiseBiomeSource: quart 좌표의 바이옴. */
    public interface NoiseBiomeSource {
        Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ);
    }
}
