// 틱 장면의 뼈대: 준비(setup) → 틱 N번(tick, Probe 로 재기) → 점검 시점마다 해시 줄(checkpoint).
// 증명 모드와 벤치 모드가 같은 코드로 돈다 — 크기(Size)만 다르다. 장면은 매번 새로 setup 한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.identity.SceneHash;

public abstract class TickScene implements LibraryScene {

    /** 같은 장면을 시드만 바꿔 되풀이하는 횟수(기본 1). round 는 0부터. */
    protected int rounds(Size size) { return 1; }

    /** 준비: 레벨·땅·엔티티를 만든다(틱 시간에 안 든다). */
    protected abstract void setup(Size size, int round);

    /** 틱 수. */
    protected abstract int ticks(Size size);

    /** 점검 간격(틱). 마지막 틱에서는 항상 점검한다. */
    protected abstract int checkEvery(Size size);

    /** 장면 틱 하나(Probe 로 재는 구간). */
    protected abstract void tick(int t);

    /** 점검: 이 시점의 상태를 줄로 적는다. label 은 "tick N" 꼴. */
    protected abstract void checkpoint(SceneHash.Recorder rec, String label);

    @Override
    public final void play(Size size, SceneHash.Recorder rec, Probe probe) {
        SceneKit.install();
        int n = ticks(size), every = checkEvery(size), rounds = rounds(size);
        for (int round = 0; round < rounds; round++) {
            String prefix = rounds > 1 ? "round " + round + " " : "";
            setup(size, round);
            checkpoint(rec, prefix + "tick 0");
            for (int t = 0; t < n; t++) {
                probe.before(t);
                tick(t);
                probe.after(t);
                if ((t + 1) % every == 0 || t == n - 1) checkpoint(rec, prefix + "tick " + (t + 1));
            }
        }
    }
}
