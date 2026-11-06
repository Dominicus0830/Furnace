// 측정 구간 하나(플러그인·종류·이름). 스레드 안전 — 여러 region 스레드가 같은 구간을 동시에 재도 된다.
// 쓰는 법: long t = timing.start(); try { ... } finally { timing.stop(t); }
package org.domi.furnace.api.profiler;

public interface Timing {

    /** 재기 시작. 프로파일러가 꺼져 있으면 음수를 돌려준다(그대로 stop 에 넘기면 아무것도 하지 않는다). */
    long start();

    /** start 가 돌려준 값을 넘겨 재기를 끝낸다. 같은 스레드에서 불러야 겹친 구간의 자기 시간이 맞게 나뉜다. */
    void stop(long token);

    /** 아무것도 재지 않는 구간. */
    Timing NONE = new Timing() {
        @Override
        public long start() { return -1; }

        @Override
        public void stop(long token) {}
    };
}
