// 틱 장면의 뼈대: 준비(setup) → 틱 N번(tick, Probe 로 재기) → 점검 시점마다 해시 줄(checkpoint).
// 증명 모드와 벤치 모드가 같은 코드로 돈다 — 크기(Size)만 다르다. 장면은 매번 새로 setup 한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.identity.SceneHash;

public abstract class TickScene implements LibraryScene {

    /**
     * 엔티티 활성 범위(D11) 설정 — 장면 정의의 일부. 기본은 꺼짐(바닐라): 기존 장면의 금 파일은 "활성 범위를 끄면 결과가 바이트까지 같다"는 증명이다.
     * 켜는 장면(activation)만 덮는다.
     */
    protected org.domi.furnace.server.config.EntityActivationSettings entityActivation() {
        return org.domi.furnace.server.config.EntityActivationSettings.OFF;
    }

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

    /**
     * 틱 하나가 끝난 직후(Probe 구간 밖). 틱마다 봐야 하는 짧은 상태(예: 이동 중 블록)를 누적 해시에 섞는 자리 —
     * 점검 간격이 길어도 중간 상태를 놓치지 않는다. 기본은 아무것도 안 한다.
     */
    protected void afterTick(int t) {}

    /**
     * 점검: 이 시점의 상태를 줄로 적는다. label 은 "tick N" 꼴.
     * 주의: 점검이 레벨 난수를 쓰는 장면(예: fluid 의 난수 줄)은 점검이 시뮬레이션의 일부가 되므로 checkEvery 도 장면 정의에 든다 —
     * 바꾸면 이후 줄이 모두 달라져 금 파일을 다시 만들어야 한다.
     */
    protected abstract void checkpoint(SceneHash.Recorder rec, String label);

    @Override
    public final void play(Size size, SceneHash.Recorder rec, Probe probe) {
        SceneKit.install();
        var earBefore = org.domi.furnace.server.config.EntityActivationSettings.current();
        org.domi.furnace.server.config.EntityActivationSettings.install(entityActivation());
        try {
            playRounds(size, rec, probe);
        } finally {
            org.domi.furnace.server.config.EntityActivationSettings.install(earBefore);
        }
    }

    private void playRounds(Size size, SceneHash.Recorder rec, Probe probe) {
        int n = ticks(size), every = checkEvery(size), rounds = rounds(size);
        for (int round = 0; round < rounds; round++) {
            String prefix = rounds > 1 ? "round " + round + " " : "";
            setup(size, round);
            checkpoint(rec, prefix + "tick 0");
            for (int t = 0; t < n; t++) {
                probe.before(t);
                tick(t);
                probe.after(t);
                afterTick(t);
                if ((t + 1) % every == 0 || t == n - 1) checkpoint(rec, prefix + "tick " + (t + 1));
            }
        }
    }
}
