// 이식 다리 소유 검사(설계 docs/roadmap/port-bridge-2026-11-27.md 6장) — 생성 구현(server.port.gen)의 모든 공개 메서드가 처음 부르는 곳.
// 호환 계층 NmsOwner 의 칸 주인 규칙을 바꾸지 않고 서버 쪽으로 옮긴 것이다.
package org.domi.furnace.server.port;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.internal.port.PortRefused;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;
import org.domi.furnace.server.plugin.WorldHandle;
import org.domi.furnace.server.sched.GlobalThread;
import org.domi.furnace.server.sched.RegionThread;
import org.domi.furnace.server.world.CommandDrainGate;
import org.domi.furnace.server.world.RegionBorrow;
import org.domi.furnace.server.world.Regionizer;

/**
 * 검사 종류별 규칙.
 * <ul>
 *   <li>{@code pure} — 검사 없음({@link #pure()} 는 빈 호출: 생성 구현의 "첫 호출은 PortGuards" 규칙을 바이트코드로 보게 한다).</li>
 *   <li>{@code pos} — 그 칸의 주인 region 스레드(전역 빌림 포함)면 바로. 틱 스레드(전역·region)는 다른 region 을 기다리면 교착이라
 *       {@link ThreadOwnershipException}. 틱 스레드가 아니면 주인에게 넘겨 최대 {@value #WAIT_SECONDS}초 기다리고, 받은 쪽에서 주인이
 *       바뀌었으면(나뉨·합침) 한 번 더 보낸다. 그래도 아니면 {@link PortRefused}.</li>
 * </ul>
 */
public final class PortGuards {

    /** 기다리는 상한(초) — 호환 NmsOwner 와 같다. */
    public static final long WAIT_SECONDS = 10;

    /** 넘긴 작업을 받은 스레드가 주인이 아니었다. */
    private static final Object MOVED = new Object();

    private PortGuards() {}

    /** check=pure: 검사 없음. */
    public static void pure() {}

    /** 지금 스레드가 칸 (x, z) 의 주인 region 스레드인가(빌림 구간이면 빌려서라도). 로드 안 된 곳이면 false. */
    public static boolean ownsPos(World world, int x, int z) {
        return RegionBorrow.claimChunk(handle(world, "port").handle(), x >> 4, z >> 4);
    }

    /** check=pos 의 느린 길: 주인이 아닌 스레드에서 task 를 주인 region 에서 돌린 값. task 는 주인 스레드에서만 불린다. */
    public static <T> T atPos(World world, int x, int y, int z, String what, Supplier<T> task) {
        WorldHandle h = handle(world, what);
        if (isTickThread()) {
            throw new ThreadOwnershipException(what + ": the block at " + x + ", " + y + ", " + z + " belongs to another region - a region or global"
                    + " thread cannot wait for another region (deadlock); call it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            Supplier<Object> wrapped = () -> ownsPos(h, x, z) ? task.get() : MOVED;
            Object r = await(submit(h, x, y, z, wrapped), what, x, y, z);
            if (r != MOVED) {
                @SuppressWarnings("unchecked") T t = (T) r;
                return t;
            }
        }
        throw new PortRefused(what, "the owning region of the block at " + x + ", " + y + ", " + z + " changed twice while waiting");
    }

    /** 전역·region 틱 스레드인가(서로 기다리면 교착인 스레드). */
    static boolean isTickThread() {
        return GlobalThread.isGlobalThread() || RegionThread.isRegionThread() || Regionizer.isRegionWorkerThread();
    }

    private static CompletableFuture<Object> submit(WorldHandle h, int x, int y, int z, Supplier<Object> task) {
        CommandDrainGate.enterScope();   // ChunkInternalsImpl.submitAwaited 와 같다 — 기다리는 왕복이 명령 배수 예산을 쓴다
        try {
            return h.scheduler(new BlockPos(x, y, z)).submit(task);
        } finally {
            CommandDrainGate.exitScope();
        }
    }

    private static Object await(CompletableFuture<Object> f, String what, int x, int y, int z) {
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PortRefused(what, "interrupted while waiting for the owning region of the block at " + x + ", " + y + ", " + z);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            if (ex.getCause() instanceof Error e) throw e;
            throw new PortRefused(what, "failed on the owning region thread (the block at " + x + ", " + y + ", " + z + "): " + ex.getCause());
        } catch (TimeoutException ex) {
            throw new PortRefused(what, "the owning region did not answer within " + WAIT_SECONDS + " seconds (the block at " + x + ", " + y + ", " + z + ")");
        }
    }

    /** 서버가 만든 월드 손잡이. 아니면 PortRefused. */
    static WorldHandle handle(World w, String what) {
        if (w instanceof WorldHandle wh) return wh;
        throw new PortRefused(what, "not a world of this Furnace server: " + w);
    }
}
