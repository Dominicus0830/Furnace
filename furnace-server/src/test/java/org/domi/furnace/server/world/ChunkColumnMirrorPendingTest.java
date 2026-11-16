package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.internal.ChunkInternals;
import org.junit.jupiter.api.Test;

/**
 * N2b-4 거울 대기 표시(ChunkColumn.mirrorPending·mirrorHeld)와 월드 대기 청크 수 — 처음 세운 쓰기만 주인을 깨우고, 맞추면 0 으로 돌아오며,
 * 재생성은 대기를 새 컬럼으로 넘기고, 표시가 서 있는 동안은 늘 거울을 쥔다(거울이 GC 되면 들여올 쓰기를 잃는다 — PR #396 검토 R4).
 */
class ChunkColumnMirrorPendingTest {

    static final class FakeMirror implements ChunkInternals.ChunkMirror {
        int syncs;

        @Override
        public boolean sync() {
            syncs++;
            return false;
        }

        @Override
        public void published(ChunkInternals.ChunkView view) {}

        @Override
        public void unloaded() {}
    }

    /** 세운 호출이 대기 수를 올리는 부르는 쪽(World.requestMirrorSync)과 같게. */
    static void mark(ChunkColumn col, ChunkInternals.ChunkMirror m, AtomicInteger pend, int[] wakes) {
        boolean[] woke = new boolean[1];
        assertTrue(col.markMirrorPending(m, woke), "이 로드에 붙은 거울");
        if (woke[0]) {
            pend.incrementAndGet();
            wakes[0]++;
        }
    }

    @Test
    void onlyFirstMarkWakesAndSyncBringsCountBackToZero() {
        ChunkColumn col = new ChunkColumn(0);
        Object shim = new Object();
        FakeMirror m = new FakeMirror();
        col.attachViewHolder(shim, m);
        AtomicInteger pend = new AtomicInteger();
        int[] wakes = {0};
        for (int i = 0; i < 5; i++) mark(col, m, pend, wakes);
        assertEquals(1, wakes[0], "대기 표시를 처음 세운 쓰기만 주인을 깨운다");
        assertEquals(1, pend.get());
        assertTrue(col.mirrorPending());
        col.syncMirror(false, pend);
        assertEquals(1, m.syncs);
        assertEquals(0, pend.get(), "맞추면 대기 청크 수가 0 으로 돌아온다(Bukkit 진입 맞춤 지점이 다시 volatile 읽기 하나로)");
        assertFalse(col.mirrorPending());
        col.syncMirror(false, pend);
        assertEquals(1, m.syncs, "대기가 없으면 맞추지 않는다");
        assertFalse(col.markMirrorPending(new FakeMirror(), new boolean[1]), "다른 거울(옛 로드의 shim)의 쓰기는 받지 않는다");
        assertEquals(0, pend.get());
        Reference.reachabilityFence(shim);
    }

    @Test
    void regenerationCarriesPendingMirrorToNewColumn() {
        ChunkColumn old = new ChunkColumn(0);
        Object shim = new Object();
        FakeMirror m = new FakeMirror();
        old.attachViewHolder(shim, m);
        AtomicInteger pend = new AtomicInteger();
        mark(old, m, pend, new int[1]);
        ChunkColumn fresh = new ChunkColumn(0);
        fresh.inheritLoadToken(old);
        assertSame(old.loadToken(), fresh.loadToken());
        assertSame(m, fresh.mirror(), "새 컬럼이 거울을 이어 쥔다");
        assertTrue(fresh.mirrorPending(), "들여오지 않은 쓰기는 새 컬럼에 맞춘다");
        assertFalse(old.mirrorPending(), "대기 수는 새 컬럼이 이어 센다");
        assertFalse(old.dropMirrorPending(), "옛 컬럼을 거둬도 대기 수를 두 번 줄이지 않는다");
        fresh.syncMirror(false, pend);
        assertEquals(1, m.syncs);
        assertEquals(0, pend.get());
        assertFalse(fresh.mirrorPending());
        Reference.reachabilityFence(shim);
    }

    /** 쓰는 스레드의 표시 세우기와 주인의 맞추기가 겹쳐도 "표시는 섰는데 거울을 쥐지 않은" 판이 남지 않는다(검토 R4 재현 — 고치기 전 약 3%). */
    @Test
    void pendingMarkAlwaysHoldsMirror() throws Exception {
        ChunkColumn col = new ChunkColumn(0);
        Object shim = new Object();
        FakeMirror m = new FakeMirror();
        col.attachViewHolder(shim, m);
        VarHandle held = MethodHandles.privateLookupIn(ChunkColumn.class, MethodHandles.lookup())
                .findVarHandle(ChunkColumn.class, "mirrorHeld", ChunkInternals.ChunkMirror.class);
        AtomicInteger pend = new AtomicInteger();
        int iters = 200_000;
        AtomicInteger go = new AtomicInteger(), doneW = new AtomicInteger(), doneO = new AtomicInteger();
        int[] delays = new int[iters];
        SplittableRandom rnd = new SplittableRandom(7);
        for (int i = 0; i < iters; i++) delays[i] = rnd.nextInt(-40, 40);
        Thread writer = new Thread(() -> {
            boolean[] woke = new boolean[1];
            for (int i = 1; i <= iters; i++) {
                while (go.get() < i) Thread.onSpinWait();
                for (int s = 0; s < Math.max(0, delays[i - 1]); s++) Thread.onSpinWait();
                if (col.markMirrorPending(m, woke) && woke[0]) pend.incrementAndGet();
                doneW.set(i);
            }
        }, "mirror-pending-writer");
        Thread owner = new Thread(() -> {
            for (int i = 1; i <= iters; i++) {
                while (go.get() < i) Thread.onSpinWait();
                for (int s = 0; s < Math.max(0, -delays[i - 1]); s++) Thread.onSpinWait();
                col.syncMirror(false, pend);
                doneO.set(i);
            }
        }, "mirror-pending-owner");
        writer.setDaemon(true);
        owner.setDaemon(true);
        writer.start();
        owner.start();
        int violations = 0;
        for (int i = 1; i <= iters; i++) {
            go.set(i);
            while (doneW.get() < i || doneO.get() < i) Thread.onSpinWait();
            if (col.mirrorPending() && held.getVolatile(col) == null) violations++;
            col.syncMirror(false, pend);   // 다음 판은 대기 0·쥠 없음에서
        }
        writer.join();
        owner.join();
        assertEquals(0, pend.get(), "대기 청크 수가 맞는다");
        assertEquals(0, violations, "대기 표시가 섰는데 거울을 쥐지 않은 판(거울이 GC 되면 들여올 쓰기를 잃는다)");
        Reference.reachabilityFence(shim);
    }
}
