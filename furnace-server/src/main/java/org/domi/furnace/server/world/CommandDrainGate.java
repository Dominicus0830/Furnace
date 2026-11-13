// 명령 큐(전역 스레드)의 즉시 처리 문. 명령이 region 에 보낸 작업(명령 범위 안에서 넣은 것, Task.command)을 다음 틱을 기다리지 않고
// drain 차례(Regionizer.urgentArrived)로 곧바로 돌린다 — 리전 접근 한 줄이 한 틱(50ms)씩 걸려 #tick 함수가 초당 6.7 번만 돌던 문제(O11 P-O11-01)를 푼다.
//
// 범위: "명령 범위"는 CommandQueue.drive(전역 스레드)가 도는 동안과, 그 명령 작업이 region 스레드에서 도는 동안(이어서 넣는 두 번째 홉이 이어받는다)이다.
// 명령과 무관한 전역 작업(플러그인 전역 스케줄러·전역 틱 몸통)은 범위 밖이라 예전처럼 다음 틱에 돈다.
// 새 실행 순서를 만들지 않는다: 줄 순서는 ExecutionContext 가 지킨다. 다만 명령 작업은 같은 region 큐에 먼저 들어온 다른 출처 작업을 앞지를 수 있다
// (명령 블록이 기다릴 때 이미 그랬다 — 출처가 다른 작업끼리는 바닐라에도 순서가 없다).
//
// 예산: 한 전역 틱에 급한 작업 실행 시간(Region.runUrgentTasks)의 합이 명령 블록 예산(-Dfurnace.commandBlockWaitMicros, 기본 20ms)을 넘으면
// 문이 닫혀 예전처럼 region 의 다음 틱에 작업이 돈다(결과는 같고 시간만 늦어진다). drain 차례 하나도 남은 예산을 마감으로 받아 넘으면 멈춘다. 다음 전역 틱에 되살아난다.
// 끄기: furnace.properties commands.region-drain=false (명령 블록의 즉시 처리는 따로 항상 동작).
package org.domi.furnace.server.world;

import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.config.OperatorOptions;

public final class CommandDrainGate {

    private CommandDrainGate() {}

    /** 이번 전역 틱에 쓴 급한 작업 시간 합(ns). */
    private static final AtomicLong USED = new AtomicLong();
    /** 이 스레드가 명령 범위 안인가(깊이). */
    private static final ThreadLocal<int[]> SCOPE = ThreadLocal.withInitial(() -> new int[1]);

    /** 명령 범위에 들어간다(전역 스레드의 CommandQueue.drive, region 스레드의 명령 작업). 짝으로 {@link #exitScope}. */
    public static void enterScope() { SCOPE.get()[0]++; }

    public static void exitScope() {
        int[] d = SCOPE.get();
        if (d[0] > 0) d[0]--;
    }

    /** 지금 스레드가 명령 범위 안이면 참 — 여기서 region 에 넣는 작업은 문 대상이다. */
    public static boolean inScope() { return SCOPE.get()[0] > 0; }

    /** 시험·도구: r 을 명령 범위 안에서 돌린다. */
    public static void inCommandScope(Runnable r) {
        enterScope();
        try {
            r.run();
        } finally {
            exitScope();
        }
    }

    /** 새 전역 틱: 예산을 되살린다. */
    public static void newGlobalTick() { USED.set(0L); }

    /** 급한 작업에 쓴 시간을 올린다(Region.runUrgentTasks). 시험이 예산을 다 쓴 상태를 만들 때도 쓴다. */
    public static void charge(long nanos) {
        if (nanos > 0L) USED.addAndGet(nanos);
    }

    /** 지금 명령 작업을 곧바로 돌려도 되는가(옵션이 켜져 있고 이번 틱 예산이 남았다). */
    static boolean open() {
        return USED.get() < Region.URGENT_WAIT_BUDGET_NANOS && OperatorOptions.current().commandRegionDrain();
    }

    /** 이번 틱에 남은 예산(ns, 0 이상). */
    static long remainingNanos() { return Math.max(0L, Region.URGENT_WAIT_BUDGET_NANOS - USED.get()); }

    /** 시험·지표: 한 전역 틱의 문 예산(ns). */
    public static long budgetNanos() { return Region.URGENT_WAIT_BUDGET_NANOS; }

    /** 시험·지표: 이번 전역 틱에 쓴 시간(ns). */
    public static long usedNanos() { return USED.get(); }
}
