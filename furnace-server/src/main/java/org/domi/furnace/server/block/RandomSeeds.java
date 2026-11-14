// RandomSource 씨앗 이음매. 난수를 만드는 자리마다 "주인 열쇠"(엔티티 id·월드/region·습격 id 등)와 용도(Purpose)를 넘긴다.
// 꺼짐(운영): 바닐라 RandomSupport.generateUniqueSeed 와 같은 식 — 원자 uniquifier × 상수 ^ nanoTime(예측 불가, 같은 시각에 만든 둘도 다르다).
// 켜짐(-Dfurnace.test.seeded=true, 시험·벤치 JVM 전용): (열쇠, 용도)에서만 파생한다. 호출 순서에 기대지 않으므로
// 난수 객체를 하나 덜 만드는 최적화가 다른 객체의 씨앗을 밀지 않는다 — 같은 커밋·같은 장면이면 난수열도 UUID 도 같다.
package org.domi.furnace.server.block;

import java.util.concurrent.atomic.AtomicLong;

public final class RandomSeeds {
    private RandomSeeds() {}

    /** 시험 전용 결정적 씨앗. static final 이라 꺼져 있으면 JIT 가 분기를 접는다. Gradle 은 furnace.* 속성만 시험·벤치 JVM 에 넘긴다. */
    public static final boolean SEEDED = Boolean.getBoolean("furnace.test.seeded");

    /** 난수 용도. 값은 씨앗에 섞이므로 한 번 정하면 바꾸지 않는다(금 파일 해시가 달라진다). 새 용도는 끝에 번호를 이어 붙인다. */
    public static final class Purpose {
        private Purpose() {}

        public static final int ENTITY = 1;
        public static final int PLAYER = 2;
        public static final int LEVEL = 3;
        public static final int LEVEL_SOUND = 4;
        public static final int LEVEL_RAND_VALUE = 5;
        public static final int RAID = 6;
        public static final int DIMENSION_SPAWNERS = 7;
        public static final int WANDERING_TRADER = 8;
        public static final int WARDEN_ANGER = 9;
        public static final int SENSOR_SCAN = 10;
        public static final int SHUFFLING_LIST = 11;
        public static final int COMMAND_RANDOM = 12;
        public static final int SPREAD_PLAYERS = 13;
        public static final int END_CRYSTAL_YAW = 14;
        /** 폭발 자신의 난수(바닐라 Explosion.random — 불 붙이기). 열쇠 = 폭발 중심 좌표. */
        public static final int EXPLOSION = 15;
    }

    private static final AtomicLong UNIQUIFIER = new AtomicLong(8682522807148012L);   // 바닐라 RandomSupport.SEED_UNIQUIFIER

    /** 예측 불가 씨앗(바닐라 RandomSupport.generateUniqueSeed). */
    public static long uniqueSeed() { return UNIQUIFIER.updateAndGet(l -> l * 1181783497276652981L) ^ System.nanoTime(); }

    /** 문자열 열쇠(월드 이름·지점 이름) — 64비트 FNV-1a. String.hashCode 와 달리 32비트로 줄지 않는다. */
    public static long key(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) h = (h ^ s.charAt(i)) * 0x100000001B3L;
        return h;
    }

    /** 열쇠 둘을 섞는다(순서가 다르면 다른 값). */
    public static long combine(long a, long b) { return mix(a * 0x9E3779B97F4A7C15L + b); }

    /**
     * 월드의 주인 열쇠 — 차원과 추가 월드 이름. region 번호는 열쇠에 넣지 않는다: 번호는 region 생성·합치기·나누기 순번(Regionizer.nextId)이라
     * 구역 크기·합치기 규칙을 바꾸는 최적화가 레벨 난수 씨앗을 밀어 가짜 불일치를 낸다. 한 월드에 region 이 여럿이면 레벨 난수열이 같아진다(꺼짐은 영향 없음).
     */
    public static long world(String dimension, String levelName) { return key(levelName == null ? dimension : dimension + "|" + levelName); }

    /** 켜짐일 때의 씨앗: (열쇠, 용도)의 순수 함수. */
    public static long derive(long ownerKey, int purpose) { return mix(mix(ownerKey ^ 0x5DEECE66DL) + purpose * 0x9E3779B97F4A7C15L); }

    /** 이 자리에서 쓸 씨앗. */
    public static long seed(long ownerKey, int purpose) { return SEEDED ? derive(ownerKey, purpose) : uniqueSeed(); }

    /** Stafford variant 13 믹스(splitmix64 마무리). */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
