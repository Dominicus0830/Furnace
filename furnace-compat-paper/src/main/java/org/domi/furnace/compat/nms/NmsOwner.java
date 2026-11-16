package org.domi.furnace.compat.nms;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.World;

/**
 * 값을 돌려줘야 하는 NMS 호출을 그 칸의 주인 region 에서 돌리는 규칙(T-198 블록 엔티티 저장, T-204 설계 3장).
 * 주인 스레드(전역 빌림 포함)면 바로, 틱 스레드가 아닌 스레드는 주인에게 부탁해 최대 10초 기다린다(기다리는 왕복 — 주인이 쉬고 있으면 다음 틱을 기다리지 않는다),
 * 다른 region·전역 스레드는 서로 기다리는 교착을 피해 {@link ThreadOwnershipException}.
 * <p>보낸 사이 그 칸의 주인이 바뀌면(region 나뉨·합침) 받은 스레드는 주인이 아니다 — 그때는 한 번 다시 보낸다(검토 #468). 그래도 주인이 아니면 UnsupportedNmsCall.
 */
public final class NmsOwner {

    /** 기다리는 상한(초). */
    public static final long WAIT_SECONDS = 10;

    /** 왕복 task 를 받은 스레드가 주인이 아니었다. */
    private static final Object MOVED = new Object();

    private NmsOwner() {}

    /** 칸 (x, y, z) 의 주인 region 에서 task 를 돌린 값. what 은 오류 문구에 쓰는 NMS 이름. */
    public static <T> T call(World world, int x, int y, int z, String what, Supplier<T> task) { return call(world, x, y, z, what, task, true); }

    /**
     * {@link #call(World, int, int, int, String, Supplier)} — recheckOwner 가 거짓이면 왕복 task 가 받은 스레드에서 주인이 아니게 된 경우를 스스로 처리한다
     * (T-198 블록 엔티티 저장: 그 사이 청크가 내려가면 묶인 객체의 마지막 내용).
     */
    public static <T> T call(World world, int x, int y, int z, String what, Supplier<T> task, boolean recheckOwner) {
        var b = NmsChunks.bridge();
        if (b.ownsChunk(world, x >> 4, z >> 4)) return task.get();
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
            throw new ThreadOwnershipException(what + ": " + where(x, y, z) + " belongs to another region - a region or global"
                    + " thread cannot wait for another region (deadlock); call it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            Supplier<Object> wrapped = recheckOwner ? () -> b.ownsChunk(world, x >> 4, z >> 4) ? task.get() : MOVED : task::get;
            Object r = await(b.submitAwaited(world, x, y == CHUNK_LEVEL ? 0 : y, z, wrapped), what, x, y, z);
            if (r != MOVED) {
                @SuppressWarnings("unchecked") T t = (T) r;
                return t;
            }
        }
        throw new UnsupportedNmsCall(what, "the owning region of " + where(x, y, z) + " changed twice while waiting");
    }

    private static Object await(java.util.concurrent.CompletableFuture<Object> f, String what, int x, int y, int z) {
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new UnsupportedNmsCall(what, "interrupted while waiting for the owning region of " + where(x, y, z));
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new UnsupportedNmsCall(what, "failed on the owning region thread (" + where(x, y, z) + "): " + ex.getCause());
        } catch (TimeoutException ex) {
            throw new UnsupportedNmsCall(what, "the owning region did not answer within " + WAIT_SECONDS + " seconds (" + where(x, y, z) + ")");
        }
    }

    /** 오류 문구의 자리 — y 가 {@link Integer#MIN_VALUE} 면 청크 단위 호출. */
    private static String where(int x, int y, int z) {
        return y == Integer.MIN_VALUE ? "chunk " + (x >> 4) + ", " + (z >> 4) : "the block at " + x + ", " + y + ", " + z;
    }

    /** 청크 단위 호출의 y 자리(오류 문구가 칸 대신 청크를 말한다). */
    public static final int CHUNK_LEVEL = Integer.MIN_VALUE;
}
