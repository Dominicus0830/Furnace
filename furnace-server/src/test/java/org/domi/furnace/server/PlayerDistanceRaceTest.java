package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.junit.jupiter.api.Test;

/**
 * (PR #391 검토 시험을 옮김) Player.viewDistance(int)(client_information 의 네티 스레드·ServerCore.setViewDistance — 잠금 없음)와
 * sendCap(int)(ServerCore.applyPlayerDistances — 거리 잠금 안)이 동시에 돌면, 둘 다 끝난 뒤 viewDistance() 가
 * max(2, min(base, cap)) 이 아닌 값으로 남는가(갱신 유실). 매 판 같은 출발선에서 두 스레드를 한 번씩 돌린다.
 */
class PlayerDistanceRaceTest {

    /** @return 어긋난 판 수와 첫 예 */
    private static String run(int startCap, int newCap, int trials) throws Exception {
        Player p = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        AtomicInteger round = new AtomicInteger(-1), finished = new AtomicInteger();
        Runnable client = () -> {
            for (int i = 0; i < trials; i++) {
                while (round.get() < i) Thread.onSpinWait();
                p.viewDistance(12);                       // ConnectionHandler: 게임 중 렌더 거리 변경(같은 값이어도 다시 씀)
                finished.incrementAndGet();
            }
        };
        Runnable plugin = () -> {
            for (int i = 0; i < trials; i++) {
                while (round.get() < i) Thread.onSpinWait();
                synchronized (p) { p.sendCap(newCap); }   // ServerCore.applyPlayerDistances 와 같은 잠금
                finished.incrementAndGet();
            }
        };
        Thread a = new Thread(client), b = new Thread(plugin);
        a.start(); b.start();
        int bad = 0; String example = null;
        int want = Math.max(2, Math.min(12, newCap));
        for (int i = 0; i < trials; i++) {
            p.sendCap(startCap);
            p.viewDistance(12);
            round.set(i);
            while (finished.get() < 2 * (i + 1)) Thread.onSpinWait();
            int got = p.viewDistance();
            if (got != want || p.sendCap() != newCap) {
                bad++;
                if (example == null) example = "viewDistance()=" + got + " sendCap=" + p.sendCap() + " (기대 " + want + ")";
            }
        }
        a.join(); b.join();
        return bad + "/" + trials + (example == null ? "" : " 예: " + example);
    }

    @Test
    void capLostWhenClientInfoRaces() throws Exception {
        String r = run(Integer.MAX_VALUE, 4, 50_000);   // 플러그인이 4로 낮추는 순간 클라이언트가 렌더 거리를 다시 알림
        System.out.println("[R391-race lower] " + r);
        String r2 = run(4, Integer.MAX_VALUE, 50_000);  // 플러그인이 상한을 푸는 순간
        System.out.println("[R391-race release] " + r2);
        assertTrue(r.startsWith("0/") && r2.startsWith("0/"), r + " | " + r2);
    }
}
