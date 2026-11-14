package org.domi.furnace.server.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.sched.GlobalThread;
import org.junit.jupiter.api.Test;

/** T-035 연결별 채팅 사슬: 도착 순서, 전역 단계 기다리는 동안 실행기 스레드를 붙잡지 않음, 상한, 실패 격리. */
class ChatChainTest {

    @Test
    void chatAndGlobalStepsRunOneAtATimeInArrivalOrder() throws Exception {
        ExecutorService exec = Executors.newFixedThreadPool(4);
        try (GlobalThread global = new GlobalThread("chain-test-global")) {
            ChatChain chain = new ChatChain(exec);
            List<String> seen = new CopyOnWriteArrayList<>();
            int[] running = {0};
            List<String> overlaps = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 60; i++) {
                int n = i;
                Runnable body = () -> {
                    synchronized (running) { if (running[0]++ != 0) overlaps.add("겹침 " + n); }
                    seen.add(n + (global.isOwnerThread() ? "g" : "c"));
                    if (n % 7 == 0) Thread.onSpinWait();
                    synchronized (running) { running[0]--; }
                };
                assertTrue(i % 3 == 1 ? chain.appendGlobal(global, body) : chain.append(body));
            }
            chain.barrier().get(10, TimeUnit.SECONDS);
            List<String> want = new ArrayList<>();
            for (int i = 0; i < 60; i++) want.add(i + (i % 3 == 1 ? "g" : "c"));
            assertEquals(want, seen, "채팅 단계는 채팅 실행기에서, 전역 단계는 전역 스레드에서, 넣은 순서대로");
            assertEquals(List.of(), overlaps, "한 사슬의 단계는 겹치지 않는다");
            assertEquals(0, chain.pending());
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void waitingForGlobalDoesNotHoldAChatThread() throws Exception {
        ExecutorService one = Executors.newSingleThreadExecutor();   // 채팅 실행기 스레드가 하나뿐이어도
        CountDownLatch release = new CountDownLatch(1);
        try (GlobalThread global = new GlobalThread("chain-test-global")) {
            ChatChain a = new ChatChain(one), b = new ChatChain(one);
            List<String> seen = new CopyOnWriteArrayList<>();
            a.appendGlobal(global, () -> {
                try {
                    release.await(10, TimeUnit.SECONDS);   // 전역에서 오래 걸리는 명령
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                seen.add("a-global");
            });
            a.append(() -> seen.add("a-after"));
            CompletableFuture<Void> aDone = a.barrier();
            b.append(() -> seen.add("b-chat"));
            b.barrier().get(5, TimeUnit.SECONDS);   // a 가 전역을 기다리는 동안 b 는 같은 스레드에서 돈다
            assertEquals(List.of("b-chat"), seen);
            assertFalse(aDone.isDone(), "a 의 다음 단계는 전역 단계가 끝난 뒤에");
            release.countDown();
            aDone.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("b-chat", "a-global", "a-after"), seen);
        } finally {
            release.countDown();
            one.shutdownNow();
        }
    }

    @Test
    void overflowIsRefusedButBarrierStillQueues() {
        List<Runnable> parked = new ArrayList<>();
        ChatChain chain = new ChatChain(parked::add);   // 실행기가 아직 돌지 않는다(처리기가 막힌 것과 같다)
        for (int i = 0; i < ChatChain.MAX_PENDING; i++) assertTrue(chain.append(() -> { }), "칸 " + i);
        assertFalse(chain.append(() -> { }), "상한을 넘으면 넣지 않는다");
        assertFalse(chain.appendGlobal(null, () -> { }));
        CompletableFuture<Void> barrier = chain.barrier();
        assertEquals(ChatChain.MAX_PENDING + 1, chain.pending());
        assertEquals(1, parked.size(), "큐를 비울 주인은 하나만 예약된다");
        parked.get(0).run();
        assertTrue(barrier.isDone());
        assertEquals(0, chain.pending());
        assertTrue(chain.append(() -> { }));
    }

    @Test
    void failingStepDoesNotBreakTheChain() throws Exception {
        ExecutorService exec = Executors.newCachedThreadPool();
        try (GlobalThread global = new GlobalThread("chain-test-global")) {
            ChatChain chain = new ChatChain(exec);
            List<String> seen = new CopyOnWriteArrayList<>();
            chain.append(() -> { throw new IllegalStateException("처리기 오류(시험)"); });
            chain.appendGlobal(global, () -> { throw new IllegalStateException("명령 오류(시험)"); });
            chain.append(() -> seen.add("next"));
            chain.appendGlobal(global, () -> seen.add("next-global"));
            chain.barrier().get(5, TimeUnit.SECONDS);
            assertEquals(List.of("next", "next-global"), seen);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void closedGlobalDropsTheStepAndContinues() throws Exception {
        ExecutorService exec = Executors.newCachedThreadPool();
        try {
            GlobalThread global = new GlobalThread("chain-test-global");
            global.close();   // 서버가 닫히는 중
            ChatChain chain = new ChatChain(exec);
            List<String> seen = new CopyOnWriteArrayList<>();
            chain.appendGlobal(global, () -> seen.add("never"));
            chain.append(() -> seen.add("after"));
            chain.barrier().get(5, TimeUnit.SECONDS);
            assertEquals(List.of("after"), seen);
        } finally {
            exec.shutdownNow();
        }
    }
}
