package org.domi.furnace.compat.nms;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.world.World;

/**
 * 값을 돌려줘야 하는 NMS 엔티티 호출을 그 엔티티를 가진 스레드에서 돌리는 규칙(T-212). {@link NmsOwner} 의 엔티티판이다.
 * <ul>
 * <li>엔티티를 가진 스레드(전역 스레드의 빌림 포함)면 바로 돈다. 사라진 엔티티도 바로 돈다(작업이 "없음"을 스스로 처리한다).</li>
 * <li>틱 스레드가 아닌 스레드(FAWE 작업 스레드)는 핸들이 아는 자리의 주인 region 에 보내 최대 {@link NmsOwner#WAIT_SECONDS}초 기다린다.
 *     받은 스레드가 그 엔티티의 주인이 아니면(그 사이 다른 region 으로 건너감) 엔티티를 따라가는 대기열로 한 번 더 보낸다.</li>
 * <li>다른 region·전역 스레드는 교착을 피해 {@link ThreadOwnershipException}(NmsOwner 와 같은 규칙).</li>
 * </ul>
 */
public final class NmsEntities {

    private static final Object MOVED = new Object();

    private NmsEntities() {}

    public static <T> T call(World world, Entity handle, String what, Supplier<T> task) {
        if (handle.isOwnedByCurrentThread()) return task.get();
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
            throw new ThreadOwnershipException(what + ": entity " + handle.uniqueId() + " belongs to another region - a region or global thread cannot wait"
                    + " for another region (deadlock); call it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
        var at = handle.location();
        int x = (int) Math.floor(at.x()), y = (int) Math.floor(at.y()), z = (int) Math.floor(at.z());
        Object r = await(NmsChunks.bridge().submitAwaited(world, x, y, z, () -> handle.isOwnedByCurrentThread() ? task.get() : MOVED), what, handle);
        if (r == MOVED) {
            // 엔티티를 따라간다(다음 틱 머리). 받은 스레드는 그 엔티티의 주인이다. 그 사이 사라졌으면 주인 없이 돈다(작업이 "없음"을 처리한다)
            try {
                r = await(org.domi.furnace.api.internal.Internals.get().withEntity(handle, Object.class, ignored -> (Object) task.get()), what, handle);
            } catch (IllegalStateException gone) {
                if (handle.isOwnedByCurrentThread()) return task.get();
                throw gone;
            }
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    private static Object await(java.util.concurrent.CompletableFuture<?> f, String what, Entity handle) {
        try {
            return f.get(NmsOwner.WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new UnsupportedNmsCall(what, "interrupted while waiting for the owning region of entity " + handle.uniqueId());
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new UnsupportedNmsCall(what, "failed on the owning region thread of entity " + handle.uniqueId() + ": " + ex.getCause());
        } catch (TimeoutException ex) {
            throw new UnsupportedNmsCall(what, "the owning region of entity " + handle.uniqueId() + " did not answer within " + NmsOwner.WAIT_SECONDS + " seconds");
        }
    }
}
