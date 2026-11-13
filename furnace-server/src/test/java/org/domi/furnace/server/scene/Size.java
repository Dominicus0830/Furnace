// 장면 크기. PROOF = 평소 시험(수 초, 금 파일과 비교), FULL = bench 작업(실서버에 가까운 크기, 시간 측정·해시를 JSON 에 남김).
package org.domi.furnace.server.scene;

public enum Size {
    PROOF,
    FULL;

    public boolean full() { return this == FULL; }

    /** 크기별 값 고르기. */
    public int pick(int proof, int full) { return this == FULL ? full : proof; }
}
