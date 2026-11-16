// 결과 동일 증명용 64비트 롤링 해시. 기존 동일성 도구(NoiseIdentityBench·O04 SharedPipelineBench·O07 유체 지문)와 같은 식
// h = (h ^ v) * FNV_PRIME 이라, 그 도구들이 적어 둔 해시 값과 이어진다(값 하나를 통째로 섞는다 — 바이트 단위 FNV-1a 가 아니다).
// 해시에 넣으면 안 되는 것: 정체 해시(System.identityHashCode·기본 hashCode), ConcurrentHashMap·HashSet<객체> 순회 순서, 시각.
// 넣어도 되는 것: 정수·double 비트(Java 17+ 는 항상 strictfp), 문자열 내용(UTF-8 바이트), 순서가 정해진 목록.
package org.domi.furnace.server.identity;

import java.nio.charset.StandardCharsets;

public final class IdentityHash {

    public static final long OFFSET = 0xCBF29CE484222325L;
    public static final long PRIME = 0x100000001B3L;

    private long h = OFFSET;
    private long count;

    /** 기존 도구와 같은 한 걸음. */
    public static long fnv(long h, long v) { return (h ^ v) * PRIME; }

    public IdentityHash add(long v) { h = fnv(h, v); count++; return this; }

    public IdentityHash add(int v) { return add((long) v); }

    public IdentityHash add(boolean v) { return add(v ? 1L : 0L); }

    /** double 은 비트 그대로(-0.0 과 0.0, NaN 비트 차이까지 구분한다). */
    public IdentityHash add(double v) { return add(Double.doubleToRawLongBits(v)); }

    public IdentityHash add(float v) { return add((long) Float.floatToRawIntBits(v)); }

    /** 길이를 먼저 넣어 ["ab","c"] 와 ["a","bc"] 를 구분한다. */
    public IdentityHash add(byte[] bytes) {
        add(bytes.length);
        for (byte b : bytes) h = fnv(h, b);
        count += bytes.length;
        return this;
    }

    public IdentityHash add(int[] values) {
        add(values.length);
        for (int v : values) add(v);
        return this;
    }

    public IdentityHash add(long[] values) {
        add(values.length);
        for (long v : values) add(v);
        return this;
    }

    public IdentityHash add(String s) { return add(s.getBytes(StandardCharsets.UTF_8)); }

    public long value() { return h; }

    /** 섞은 값 개수(바이트 배열은 바이트 수만큼). 빈 장면을 "같다"로 통과시키지 않으려고 본다. */
    public long count() { return count; }

    public String hex() { return hex(h); }

    public static String hex(long v) { return String.format("%016x", v); }

    @Override
    public String toString() { return hex() + " (n=" + count + ")"; }
}
