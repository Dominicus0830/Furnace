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
 * 조각)를 부르는 길. 원점 칸의 주인 region 스레드(전역 빌림 포함)면 원점과 둘레 청크 거울을 먼저 맞추고 바로 한다.
 * <ul>
 *   <li>값이 없는 조각(이웃 갱신·변경 방송·POI·놓기 반응·모양 갱신): 다른 스레드면 주인 region 작업으로 넘긴다(기다리지 않음 — 받은 스레드가 주인이 아니면
 *       한 번 다시 보낸다). 주인은 원점과 둘레 청크의 거울을 맞춘 뒤 한다 — 앞서 그 청크들에 쓴 것(일지·섹션 교체)이 먼저 들어간다. 바닐라·Paper 보다 최대 한 틱 늦다(Paper 에서는 경쟁 호출이다 — 설계 9장 가정).</li>
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
            syncAround(w, kind, x, y, z);
            br.blockEffectNow(w, kind, x, y, z, a, b, c, other);
            return;
        }
        submit(w, kind, x, y, z, a, b, c, other, what, NmsCaller.pluginName(), true);
    }

    /** 받은 스레드가 주인이 아니었다(보낸 사이 region 이 나뉘거나 합쳐졌다). */
    private static final Object MOVED = new Object();

    /**
     * 주인 작업으로 넘긴다. 받은 스레드가 주인이 아니면 한 번 다시 보낸다({@link NmsOwner} 와 같은 규칙, 검토 #472). 그 사이 청크가 내려갔으면 아무것도 하지 않는다
     * (로드 안 된 원점과 같다). 실패는 플러그인·조각마다 한 번 경고한다(기다리는 이가 없다).
     */
    private static void submit(World w, int kind, int x, int y, int z, int a, int b, int c, long other, String what, String who, boolean retry) {
        var br = NmsChunks.bridge();
        br.submitAwaited(w, x, y, z, () -> {
            if (!br.isLoaded(w, x >> 4, z >> 4)) return null;
            if (!br.ownsChunk(w, x >> 4, z >> 4)) return MOVED;
            syncAround(w, kind, x, y, z);
            br.blockEffectNow(w, kind, x, y, z, a, b, c, other);
            return null;
        }).whenComplete((v, e) -> {
            if (e == null && v == MOVED) {
                if (retry) {
                    submit(w, kind, x, y, z, a, b, c, other, what, who, false);
                    return;
                }
                e = new UnsupportedNmsCall(what, "the owning region of the block at " + x + ", " + y + ", " + z + " changed twice while waiting");
            }
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
            syncAround(w, kind, x, y, z);
            return br.blockEffectNow(w, kind, x, y, z, a, b, c, 0L);
        });
        if (r < 0) throw NmsChunks.notLoaded(w, x >> 4, z >> 4, what);
        return r;
    }

    /**
     * 주인: 부수 효과 전 맞춤 지점 — 원점 청크와 둘레 8청크 가운데 이 스레드가 가진 것의 거울을 맞춘다(다른 region 의 청크는 그 주인이 맞춘다). 다른 스레드가
     * 앞서 거울 일지로 쓴 것이 효과보다 먼저 서버에 든다(검토 #468). 그 쓰기의 맞춤 작업은 주인 대기열에서 효과 작업보다 앞서지만, 주인 스레드가 그 사이에
     * 직접 효과를 부르면(주인 작업 안에서 다른 스레드의 쓰기를 기다린 뒤 등) 아직 돌지 않았다 — 이웃 청크를 읽는 효과(이웃 갱신·모양 갱신)가 그 쓰기를 보게 한다.
     * 맞춤은 바뀐 것이 없으면 비교 몇 개다(검토 #472 — 가장자리 2칸만 보던 것을 둘레 전부로). 효과가 더 먼 청크까지 번지면(레드스톤 선 전파, 깊이 511 모양 갱신)
     * 그 청크는 맞추지 않는다(설계 3장 한계). 변경 방송·POI·예약 틱은 원점 칸만 본다 — 원점 청크만 맞춘다.
     */
    private static void syncAround(World w, int kind, int x, int y, int z) {
        int cx = x >> 4, cz = z >> 4;
        int r = kind == SEND_BLOCK_UPDATED || kind == BLOCK_CHANGED || kind == POI || kind == SCHEDULE_BLOCK_TICK || kind == SCHEDULE_FLUID_TICK ? 0 : 1;
        var b = NmsChunks.bridge();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (b.chunkShim(w, cx + dx, cz + dz) instanceof net.minecraft.world.level.chunk.ChunkAccess c) c.furnaceOwnerSyncAt(y);
            }
        }
    }

    /** 시험용 — 그 조각의 실패 경고를 이미 냈는가. */
    static boolean warned(String what) {
        for (String k : WARNED) if (k.startsWith(what + "|")) return true;
        return false;
    }

    /** 시험에서 한 번 경고 기록을 비운다. */
    static void resetWarnings() { WARNED.clear(); }

    /** 종류 상수(시험·shim 이 같은 이름을 쓰게). */
    public static final int SEND_BLOCK_UPDATED = ChunkInternals.EFFECT_SEND_BLOCK_UPDATED, BLOCK_CHANGED = ChunkInternals.EFFECT_BLOCK_CHANGED,
            UPDATE_NEIGHBORS = ChunkInternals.EFFECT_UPDATE_NEIGHBORS, OUTPUT_SIGNAL = ChunkInternals.EFFECT_OUTPUT_SIGNAL, POI = ChunkInternals.EFFECT_POI,
            ON_PLACE = ChunkInternals.EFFECT_ON_PLACE, NEIGHBOR_CHANGED = ChunkInternals.EFFECT_NEIGHBOR_CHANGED,
            NEIGHBOUR_SHAPES = ChunkInternals.EFFECT_NEIGHBOUR_SHAPES, INDIRECT_SHAPES = ChunkInternals.EFFECT_INDIRECT_SHAPES,
            TRIGGER_EVENT = ChunkInternals.EFFECT_TRIGGER_EVENT, FROM_NEIGHBOUR_SHAPES = ChunkInternals.EFFECT_FROM_NEIGHBOUR_SHAPES,
            SCHEDULE_BLOCK_TICK = ChunkInternals.EFFECT_SCHEDULE_BLOCK_TICK, SCHEDULE_FLUID_TICK = ChunkInternals.EFFECT_SCHEDULE_FLUID_TICK;
}
