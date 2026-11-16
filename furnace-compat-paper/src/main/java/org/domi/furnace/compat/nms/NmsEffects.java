package org.domi.furnace.compat.nms;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.i18n.CompatLang;
import org.slf4j.LoggerFactory;

/**
 * 쪼갠 {@code Level#setBlock} 조각(T-204 설계 2·3장) — FAWE NativeAccess 등이 부르는 부수 효과 NMS 메서드가 서버의 같은 코드({@code ChunkLevel.setBlock} 의
 * 조각)를 부르는 길. 원점 칸의 주인 region 스레드(전역 빌림 포함)면 원점·이웃 청크 거울을 먼저 맞추고 바로 한다.
 * <ul>
 *   <li>값이 없는 조각(이웃 갱신·변경 방송·POI·놓기 반응·모양 갱신): 다른 스레드면 주인 region 작업으로 넘긴다(기다리지 않음). 주인은 원점 청크의 거울을 맞춘 뒤
 *       한다 — 그 스레드가 앞서 그 청크와 닿는 이웃 청크에 쓴 것(일지·섹션 교체)이 먼저 들어간다. 바닐라·Paper 보다 최대 한 틱 늦다(Paper 에서는 경쟁 호출이다 — 설계 9장 가정).</li>
 *   <li>값이 있는 조각(triggerEvent·updateFromNeighbourShapes): {@link NmsOwner} 규칙(틱 스레드가 아니면 기다림, region·전역 스레드는 ThreadOwnershipException).</li>
 * </ul>
 * 레벨이 서버 월드가 아니면(플러그인이 만든 가짜 레벨 등) UnsupportedNmsCall.
 */
public final class NmsEffects {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NmsEffects() {}

    /** 그 레벨의 Furnace 월드. 서버 월드가 아니면 UnsupportedNmsCall. */
    public static World world(Object level, String what) {
        if (level instanceof net.minecraft.world.level.Level l && l.getWorld() != null) return l.getWorld().furnaceHandle();
        throw new UnsupportedNmsCall(what, "only the server's own levels are supported here (got " + (level == null ? "null" : level.getClass().getName()) + ")");
    }

    /** 값이 없는 조각. 원점 청크가 로드돼 있지 않으면 아무것도 하지 않는다(바닐라: 그 칸을 볼 청크가 없다). */
    public static void run(Object level, BlockPos pos, int kind, int a, int b, int c, long other, String what) {
        World w = world(level, what);
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        var br = NmsChunks.bridge();
        if (!br.isLoaded(w, x >> 4, z >> 4)) return;
        if (br.ownsChunk(w, x >> 4, z >> 4)) {
            syncOrigin(w, kind, x, y, z);
            br.blockEffectNow(w, kind, x, y, z, a, b, c, other);
            return;
        }
        String who = NmsCaller.pluginName();
        br.submitAwaited(w, x, y, z, () -> {
            syncOrigin(w, kind, x, y, z);
            return br.blockEffectNow(w, kind, x, y, z, a, b, c, other);
        }).whenComplete((v, e) -> {
            if (e != null && WARNED.add(what + "|" + who)) {
                Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                LoggerFactory.getLogger("Furnace").warn(CompatLang.t("compat.nms.effect-failed", who == null ? "?" : who, what, cause.toString()));
            }
        });
    }

    /** 값이 있는 조각. 원점 청크가 로드돼 있지 않으면 IllegalStateException(Level#setBlock 과 같다 — 동기로 불러오지 않는다). */
    public static int call(Object level, BlockPos pos, int kind, int a, int b, int c, String what) {
        World w = world(level, what);
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        var br = NmsChunks.bridge();
        if (!br.isLoaded(w, x >> 4, z >> 4)) throw NmsChunks.notLoaded(w, x >> 4, z >> 4, what);   // 부르는 순간 부른 스레드에서
        int r = NmsOwner.call(w, x, y, z, what, () -> {
            syncOrigin(w, kind, x, y, z);
            return br.blockEffectNow(w, kind, x, y, z, a, b, c, 0L);
        });
        if (r < 0) throw NmsChunks.notLoaded(w, x >> 4, z >> 4, what);
        return r;
    }

    /**
     * 주인: 부수 효과 전 맞춤 지점 — 원점 청크와, 원점이 청크 가장자리 2칸 안이면 그쪽 이웃 청크(둘레 3×3 중 효과가 바로 닿는 것)의 거울을 그 높이 자리부터
     * 맞춘다. 일지나 섹션 교체로 들어온 앞선 쓰기가 효과보다 먼저 서버에 든다(검토 #468). 이 스레드가 가진 청크만(다른 region 의 청크는 그 주인이 맞춘다).
     * 변경 방송·길찾기·POI 는 원점 칸만 본다 — 원점 청크만 맞춘다.
     */
    private static void syncOrigin(World w, int kind, int x, int y, int z) {
        int cx = x >> 4, cz = z >> 4;
        boolean wide = kind != SEND_BLOCK_UPDATED && kind != BLOCK_CHANGED && kind != POI;
        int lx = x & 15, lz = z & 15;
        int x0 = wide && lx < 2 ? -1 : 0, x1 = wide && lx > 13 ? 1 : 0, z0 = wide && lz < 2 ? -1 : 0, z1 = wide && lz > 13 ? 1 : 0;
        var b = NmsChunks.bridge();
        for (int dz = z0; dz <= z1; dz++) {
            for (int dx = x0; dx <= x1; dx++) {
                if (b.chunkShim(w, cx + dx, cz + dz) instanceof net.minecraft.world.level.chunk.ChunkAccess c) c.furnaceOwnerSyncAt(y);
            }
        }
    }

    /** 시험에서 한 번 경고 기록을 비운다. */
    static void resetWarnings() { WARNED.clear(); }

    /** 종류 상수(시험·shim 이 같은 이름을 쓰게). */
    public static final int SEND_BLOCK_UPDATED = ChunkInternals.EFFECT_SEND_BLOCK_UPDATED, BLOCK_CHANGED = ChunkInternals.EFFECT_BLOCK_CHANGED,
            UPDATE_NEIGHBORS = ChunkInternals.EFFECT_UPDATE_NEIGHBORS, OUTPUT_SIGNAL = ChunkInternals.EFFECT_OUTPUT_SIGNAL, POI = ChunkInternals.EFFECT_POI,
            ON_PLACE = ChunkInternals.EFFECT_ON_PLACE, NEIGHBOR_CHANGED = ChunkInternals.EFFECT_NEIGHBOR_CHANGED,
            NEIGHBOUR_SHAPES = ChunkInternals.EFFECT_NEIGHBOUR_SHAPES, INDIRECT_SHAPES = ChunkInternals.EFFECT_INDIRECT_SHAPES,
            TRIGGER_EVENT = ChunkInternals.EFFECT_TRIGGER_EVENT, FROM_NEIGHBOUR_SHAPES = ChunkInternals.EFFECT_FROM_NEIGHBOUR_SHAPES;
}
