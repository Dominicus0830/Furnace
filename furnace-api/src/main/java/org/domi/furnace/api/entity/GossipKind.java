// 주민 소문 종류(바닐라 GossipType). 평판 = 종류별 값 × 가중치의 합. 키 문자열은 마인크래프트 버전과 상관없이 같다.
package org.domi.furnace.api.entity;

public enum GossipKind {
    MAJOR_NEGATIVE("major_negative", -5, 100),
    MINOR_NEGATIVE("minor_negative", -1, 200),
    MINOR_POSITIVE("minor_positive", 1, 25),
    MAJOR_POSITIVE("major_positive", 5, 20),
    TRADING("trading", 1, 25);

    private final String key;
    private final int weight, max;

    GossipKind(String key, int weight, int max) {
        this.key = key;
        this.weight = weight;
        this.max = max;
    }

    public String key() { return key; }

    /** 평판에 곱하는 가중치(음수면 평판을 깎는다). */
    public int weight() { return weight; }

    /** 한 대상에 쌓일 수 있는 최댓값(넘으면 잘린다). */
    public int max() { return max; }
}
