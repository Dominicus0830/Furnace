// 직렬화(풀)가 도는 동안 편집+autosave(clearDirty)가 끼어도 낡은 본문이 캐시에 남지 않는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkBodyCacheRaceTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private record Env(FurnaceSchedulerImpl sched, ChunkWorkerPool pool, World w, BlockRegistry reg) {}

    private static Env env() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));   // 블록 부수기가 아이템 레지스트리를 쓴다
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        World w = new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 2, 2, reg.directBits(), 7,
                Executors.newSingleThreadExecutor());
        return new Env(sched, pool, w, reg);
    }

    /** 소유 스레드서 현재 청크를 직접 직렬화한 "정답" 본문. */
    private static byte[] freshBody(Env e) throws Exception {
        return e.sched().region(R00).submit(() -> {
            ChunkColumn col = e.w().regionAt(R00).chunkAtBlocking(C00);
            ByteBuf b = Unpooled.buffer();
            ChunkSerializer.serialize(b, ChunkSnapshot.of(0, 0, col), e.reg().directBits(), 7);
            byte[] out = new byte[b.readableBytes()];
            b.getBytes(0, out);
            return out;
        }).get(10, TimeUnit.SECONDS);
    }

    /** 스냅샷이 떠져(캐시에 올라) 풀 직렬화가 큐에 들어갈 때까지 기다린다 — sleep 없이 소유 스레드 상태를 폴링. */
    private static void awaitSnapshotTaken(Env e) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (e.w().bodySnapshots.get() > 0) return;
            Thread.onSpinWait();
        }
        fail("스냅샷이 제한 시간 안에 떠지지 않음");
    }

    @Test
    void editAndAutosaveDuringSerialization_doesNotCacheStaleBody() throws Exception {
        Env e = env();
        CountDownLatch gate = new CountDownLatch(1);
        e.pool().submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });

        CompletableFuture<byte[]> first = e.w().serializedChunkBody(C00);   // 스냅샷(편집 전)을 뜨고 풀서 대기
        awaitSnapshotTaken(e);
        e.sched().region(R00).execute(() -> {
            Region r = e.w().regionAt(R00);
            r.breakBlock(0, -61, 0);          // 편집: dirty=true, 캐시 무효화
            r.saveDirtyChunks();              // autosave: dirty=false 로 되돌림(캐시는 안 건드림)
        }).get(10, TimeUnit.SECONDS);
        gate.countDown();

        byte[] stale = first.get(10, TimeUnit.SECONDS);
        byte[] second = e.w().serializedChunkBody(C00).get(10, TimeUnit.SECONDS);
        byte[] fresh = freshBody(e);

        assertFalse(Arrays.equals(stale, fresh), "사전: 첫 요청 본문은 편집 전 스냅샷이라 최신과 달라야 한다");
        assertArrayEquals(fresh, second, "편집 이후의 로더는 편집이 반영된 본문을 받아야 한다(낡은 본문이 캐시되면 안 됨)");
        e.sched().close();
    }

    @Test
    void untouchedChunk_bodyIsStillCached() throws Exception {
        Env e = env();
        byte[] a = e.w().serializedChunkBody(C00).get(10, TimeUnit.SECONDS);
        // 캐시 쓰기는 소유 스레드의 후속 태스크 — 배리어로 반영을 보장한 뒤 다시 요청
        e.sched().region(R00).execute(() -> { }).get(10, TimeUnit.SECONDS);
        byte[] b = e.w().serializedChunkBody(C00).get(10, TimeUnit.SECONDS);
        assertSame(a, b, "변경 없는 청크는 같은 본문 인스턴스를 재사용해야 한다(캐시 이득 유지)");
        e.sched().close();
    }

    /** 직렬화가 끝나기 전 같은 청크를 여럿이 요청하면 한 번만 직렬화하고 같은 본문(같은 압축 프레임 키)을 나눠 받는다. */
    @Test
    void concurrentRequests_shareOneSerialization() throws Exception {
        Env e = env();
        CountDownLatch gate = new CountDownLatch(1);
        e.pool().submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        CompletableFuture<byte[]> a = e.w().serializedChunkBody(C00);
        awaitSnapshotTaken(e);
        CompletableFuture<byte[]> b = e.w().serializedChunkBody(C00);
        CompletableFuture<byte[]> c = e.w().serializedChunkBody(C00);
        e.sched().region(R00).execute(() -> { }).get(10, TimeUnit.SECONDS);   // b·c 가 주인 스레드를 지나가게
        gate.countDown();
        byte[] ba = a.get(10, TimeUnit.SECONDS);
        assertSame(ba, b.get(10, TimeUnit.SECONDS));
        assertSame(ba, c.get(10, TimeUnit.SECONDS));
        assertEquals(1, e.w().bodySnapshots.get(), "동시 요청은 스냅샷·직렬화를 한 번만 해야 한다");
        assertArrayEquals(freshBody(e), ba, "공유 본문은 직접 직렬화한 것과 같아야 한다");
        e.sched().close();
    }

    /** 직렬화 중 편집이 끼면 그 뒤 요청은 진행 중인 낡은 본문에 붙지 않고 새로 직렬화한다. */
    @Test
    void editDuringInflight_laterRequestDoesNotJoinStale() throws Exception {
        Env e = env();
        CountDownLatch gate = new CountDownLatch(1);
        e.pool().submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        CompletableFuture<byte[]> first = e.w().serializedChunkBody(C00);
        awaitSnapshotTaken(e);
        e.sched().region(R00).execute(() -> e.w().regionAt(R00).breakBlock(0, -61, 0)).get(10, TimeUnit.SECONDS);
        CompletableFuture<byte[]> second = e.w().serializedChunkBody(C00);
        e.sched().region(R00).execute(() -> { }).get(10, TimeUnit.SECONDS);
        gate.countDown();
        byte[] stale = first.get(10, TimeUnit.SECONDS);
        byte[] fresh = freshBody(e);
        assertFalse(Arrays.equals(stale, fresh));
        assertArrayEquals(fresh, second.get(10, TimeUnit.SECONDS));
        assertEquals(2, e.w().bodySnapshots.get(), "편집 뒤 요청은 새로 직렬화해야 한다");
        e.sched().close();
    }

    /** 직렬화가 실패하면 기다리던 요청은 모두 예외로 끝나고, 그 뒤 요청은 실패를 물려받지 않고 새로 직렬화한다. */
    @Test
    void failedSerialization_waitersFail_nextRequestRetries() throws Exception {
        Env e = env();
        CountDownLatch gate = new CountDownLatch(1);
        e.pool().submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        e.w().serializeFaultHook = () -> { throw new IllegalStateException("주입된 실패"); };
        CompletableFuture<byte[]> a = e.w().serializedChunkBody(C00);
        awaitSnapshotTaken(e);
        CompletableFuture<byte[]> b = e.w().serializedChunkBody(C00);
        e.sched().region(R00).execute(() -> { }).get(10, TimeUnit.SECONDS);
        gate.countDown();
        assertThrows(java.util.concurrent.ExecutionException.class, () -> a.get(10, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class, () -> b.get(10, TimeUnit.SECONDS));
        assertEquals(1, e.w().bodySnapshots.get());
        e.w().serializeFaultHook = null;
        byte[] c = e.w().serializedChunkBody(C00).get(10, TimeUnit.SECONDS);
        assertEquals(2, e.w().bodySnapshots.get(), "실패 뒤 요청은 새로 직렬화해야 한다");
        assertArrayEquals(freshBody(e), c);
        e.sched().close();
    }
}
