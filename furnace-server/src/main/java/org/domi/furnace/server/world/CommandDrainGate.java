// 명령 큐(전역 스레드)의 즉시 처리 문. 명령 컨텍스트가 도는 동안 열려, 전역이 region 에 보낸 급한 작업을 다음 틱을 기다리지 않고
// drain 차례(Regionizer.urgentArrived)로 곧바로 돌린다 — 리전 접근 한 줄이 한 틱(50ms)씩 걸려 #tick 함수가 초당 6.7 번만 돌던 문제(O11 P-O11-01)를 푼다.
// 새 실행 순서를 만들지 않는다: 어차피 그 region 이 다음 틱 머리에서 돌릴 급한 작업을 앞당길 뿐이고, 전역 쪽 순서는 큐가 그대로 지킨다.
//
// 예산: 한 전역 틱에 급한 작업(drain 차례 실행 시간 + 명령 블록이 기다린 시간)을 합쳐 명령 블록 예산(-Dfurnace.commandBlockWaitMicros, 기본 20ms)까지만
// 문이 열려 있다. 넘으면 문이 닫혀 예전처럼 region 의 다음 틱에 작업이 돈다(결과는 같고 시간만 늦어진다). 다음 전역 틱에 다시 열린다.
// 끄기: furnace.properties commands.region-drain=false (명령 블록의 즉시 처리는 따로 항상 동작).
package org.domi.furnace.server.world;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.config.OperatorOptions;

public final class CommandDrainGate {

    private CommandDrainGate() {}

    /** 지금 도는(문을 연) 명령 컨텍스트 수(서버 전체 — 큐마다 하나 이하). */
    private static final AtomicInteger CONTEXTS = new AtomicInteger();
    /** 이번 전역 틱에 쓴 급한 작업 시간 합(ns). */
    private static final AtomicLong USED = new AtomicLong();

    /** 명령 컨텍스트가 시작했다(전역 스레드). 끝나면 {@link #exit} 를 한 번 부른다. */
    public static void enter() { CONTEXTS.incrementAndGet(); }

    public static void exit() {
        if (CONTEXTS.decrementAndGet() < 0) CONTEXTS.set(0);   // 짝이 어긋나도 음수로 닫히지 않게
    }

    /** 새 전역 틱: 예산을 되살린다. */
    public static void newGlobalTick() { USED.set(0L); }

    /** 급한 작업에 쓴 시간을 올린다(drain 차례, 명령 블록 기다림). 시험이 예산을 다 쓴 상태를 만들 때도 쓴다. */
    public static void charge(long nanos) {
        if (nanos > 0L) USED.addAndGet(nanos);
    }

    /** 지금 새 급한 작업을 곧바로 돌려도 되는가(명령 컨텍스트가 돌고, 옵션이 켜져 있고, 이번 틱 예산이 남았다). */
    static boolean open() {
        return CONTEXTS.get() > 0 && USED.get() < Region.URGENT_WAIT_BUDGET_NANOS && OperatorOptions.current().commandRegionDrain();
    }

    /** 시험·지표: 이번 전역 틱에 쓴 시간(ns). */
    public static long usedNanos() { return USED.get(); }

    /** 시험·지표: 문을 연 명령 컨텍스트 수. */
    public static int contexts() { return CONTEXTS.get(); }
}
