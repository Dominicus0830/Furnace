package org.domi.furnace.compat.nms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;

/**
 * 빛 엔진 shim 의 값(T-205) — Paper 1.21.1 Moonrise(Starlight) {@code StarLightInterface} 의 읽개·{@code getRawBrightness}·{@code checkBlock} 규칙을
 * 서버 빛 위에 그대로 옮긴다(docs/feature-parity/compat-nms-t205-light-surface-2026-11-16.md).
 *
 * <p>읽는 곳: 지금 스레드가 그 청크의 주인(전역 빌림 구간 포함)이면 살아 있는 서버 빛, 아니면 주인이 틱 끝에 낸 공개 스냅샷(최대 한 틱 옛 값 —
 * Paper 의 다른 스레드 읽기도 경쟁 중의 값이다). 공개본이 아직 없는 청크는 region 이 아닌 스레드면 주인이 낼 때까지 기다리고(다른 청크 읽기와 같은 간선),
 * 다른 region·전역 스레드면 기다리지 않고 IllegalStateException(교착 방지 — {@link ChunkInternals#awaitChunkView}).
 * <p>Starlight 와 다른 점: Starlight 는 빛이 닿지 않는 섹션의 니블을 비워 둘(null) 수 있지만 Furnace 는 모든 빛 섹션에 값을 가진다 — 빈 니블 자리에서
 * Paper 는 null(또는 위에서 끌어내린 값)을, Furnace 는 그 칸의 실제 빛을 준다(값은 같거나 Furnace 쪽이 저장된 실제 값).
 */
public final class NmsLight {

    private NmsLight() {}

    private static World world(Level level) { return level.getWorld().furnaceHandle(); }

    public static boolean hasSkyLight(Level level) { return NmsChunks.bridge().hasSkyLight(world(level)); }

    /** 가장 낮은 빛 섹션 = 가장 낮은 블록 섹션 − 1(Starlight WorldUtil.getMinLightSection). */
    public static int minLightSection(Level level) { return (NmsChunks.bridge().minY(world(level)) >> 4) - 1; }

    /** 가장 높은 빛 섹션 = 가장 높은 블록 섹션 + 1(포함). */
    public static int maxLightSection(Level level) {
        var b = NmsChunks.bridge();
        World w = world(level);
        return ((b.minY(w) + b.height(w) - 1) >> 4) + 1;
    }

    /** 다른 스레드의 읽기 출처(로드돼 있지 않으면 null). */
    private static ChunkInternals.ChunkView foreignView(World w, int cx, int cz) {
        var b = NmsChunks.bridge();
        var v = b.chunkView(w, cx, cz);
        return v != null ? v : b.awaitChunkView(w, cx, cz);
    }

    /** 그 청크 shim 의 isLightCorrect(붙은 shim 이 없으면 처음 값 true — Furnace 빛은 늘 계산돼 있다). Starlight 하늘빛 읽개가 본다. */
    private static boolean lightCorrect(World w, int cx, int cz) {
        return !(NmsChunks.bridge().chunkShim(w, cx, cz) instanceof net.minecraft.world.level.chunk.LevelChunk c) || c.isLightCorrect();
    }

    /**
     * Starlight 층 읽개의 getDataLayerData: 그 빛 섹션의 새 사본. 하늘빛: 청크가 없거나 빛이 맞지 않으면(isLightCorrect·빛 켜짐) null. 둘 다 빛 섹션 범위 밖이면 null.
     */
    public static DataLayer dataLayer(Level level, boolean sky, SectionPos pos) {
        World w = world(level);
        var b = NmsChunks.bridge();
        int sy = pos.getY(), cx = pos.getX(), cz = pos.getZ();
        if (sy < minLightSection(level) || sy > maxLightSection(level)) return null;
        if (!b.isLoaded(w, cx, cz)) return null;
        byte[] data;
        if (b.ownsChunk(w, cx, cz)) {
            if (sky && !(b.lightReadyNow(w, cx, cz) && lightCorrect(w, cx, cz))) return null;
            data = b.lightLayerNow(w, cx, sy, cz, sky);
        } else {
            var v = foreignView(w, cx, cz);
            if (v == null || sky && !(v.lightReady() && lightCorrect(w, cx, cz))) return null;
            data = v.lightLayer(sky, sy);
        }
        return data == null ? null : new DataLayer(data);
    }

    /** 칸 값 하나(주인은 살아 있는 값, 아니면 공개본). 청크가 없거나 빛 자료가 없으면 -1, 빛이 켜지지 않았으면(하늘빛 판정용) -2. */
    private static int stored(World w, boolean sky, int x, int y, int z, boolean needReady) {
        var b = NmsChunks.bridge();
        int cx = x >> 4, cz = z >> 4;
        if (!b.isLoaded(w, cx, cz)) return -1;
        if (b.ownsChunk(w, cx, cz)) {
            if (needReady && !b.lightReadyNow(w, cx, cz)) return -2;
            return b.lightNow(w, x, y, z, sky);
        }
        var v = foreignView(w, cx, cz);
        if (v == null) return -1;
        if (needReady && !v.lightReady()) return -2;
        return v.light(sky, x, y, z);
    }

    /**
     * Starlight getSkyLightValue: 하늘빛이 없는 차원이면 0. 청크가 없거나 빛이 맞지 않으면 15, 가장 높은 빛 섹션 위는 15, 가장 낮은 빛 섹션 아래는
     * 그 섹션 맨 아래 칸의 값.
     */
    public static int skyLight(Level level, BlockPos pos) {
        if (!hasSkyLight(level)) return 0;
        World w = world(level);
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int sy = y >> 4;
        if (sy > maxLightSection(level)) {
            return 15;
        }
        int min = minLightSection(level);
        if (sy < min) y = min << 4;
        int v = stored(w, true, x, y, z, true);
        if (v < 0 || !lightCorrect(w, x >> 4, z >> 4)) return 15;
        return v;
    }

    /** Starlight getBlockLightValue: 빛 섹션 범위 밖이거나 청크가 없으면 0. */
    public static int blockLight(Level level, BlockPos pos) {
        int sy = pos.getY() >> 4;
        if (sy < minLightSection(level) || sy > maxLightSection(level)) return 0;
        int v = stored(world(level), false, pos.getX(), pos.getY(), pos.getZ(), false);
        return Math.max(v, 0);
    }

    /** Starlight getRawBrightness: 하늘빛 − 어둠이 15 면 15, 아니면 그것과 블록빛 중 큰 값. */
    public static int rawBrightness(Level level, BlockPos pos, int ambientDarkness) {
        int sky = skyLight(level, pos) - ambientDarkness;
        if (sky == 15) return 15;
        return Math.max(sky, blockLight(level, pos));
    }

    /** Starlight checkBlock(ThreadedLevelLightEngine 은 청크 작업 대기열로): 아무 스레드, 기다리지 않음 — 서버가 그 청크 주인에게 보낸다. */
    public static void checkBlock(Level level, BlockPos pos) {
        NmsChunks.bridge().checkLight(world(level), pos.getX(), pos.getY(), pos.getZ());
    }
}
