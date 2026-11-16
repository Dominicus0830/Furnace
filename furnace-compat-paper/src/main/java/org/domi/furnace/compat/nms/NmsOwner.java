package org.domi.furnace.compat.nms;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.World;

/**
 * 값을 돌려줘야 하는 NMS 호출을 그 칸의 주인 region 에서 돌리는 규칙(T-198 블록 엔티티 저장과 같다, T-204 설계 3장).
 * 주인 스레드(전역 빌림 포함)면 바로, 틱 스레드가 아닌 스레드는 주인에게 부탁해 최대 10초 기다린다(기다리는 왕복 — 주인이 쉬고 있으면 다음 틱을 기다리지 않는다),
 * 다른 region·전역 스레드는 서로 기다리는 교착을 피해 {@link ThreadOwnershipException}.
 */
public final class NmsOwner {

    /** 기다리는 상한(초). */
    static final long WAIT_SECONDS = 10;

    private NmsOwner() {}

    /** 칸 (x, y, z) 의 주인 region 에서 task 를 돌린 값. what 은 오류 문구에 쓰는 NMS 이름. */
    public static <T> T call(World world, int x, int y, int z, String what, Supplier<T> task) {
        var b = NmsChunks.bridge();
        if (b.ownsChunk(world, x >> 4, z >> 4)) return task.get();
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
            throw new ThreadOwnershipException(what + ": the block at " + x + ", " + y + ", " + z + " belongs to another region - a region or global"
                    + " thread cannot wait for another region (deadlock); call it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
        var f = b.submitAwaited(world, x, y, z, task);
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new UnsupportedNmsCall(what, "interrupted while waiting for the owning region of " + x + ", " + y + ", " + z);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new UnsupportedNmsCall(what, "failed on the owning region thread (" + x + ", " + y + ", " + z + "): " + ex.getCause());
        } catch (TimeoutException ex) {
            throw new UnsupportedNmsCall(what, "the owning region did not answer within " + WAIT_SECONDS + " seconds (" + x + ", " + y + ", " + z + ")");
        }
    }
}
