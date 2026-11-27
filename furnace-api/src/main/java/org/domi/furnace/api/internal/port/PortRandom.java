// 이식 다리 random kind(V4 콜백, 설계 4.4) — 플러그인이 만든 NMS 난수를 서버 코드가 그대로 돌린다.
package org.domi.furnace.api.internal.port;

/**
 * 플러그인 쪽 난수 객체로 돌아가는 콜백. 서버 쪽 어댑터가 부를 때마다 이 메서드를 부르고, 호환 쪽 구현이 그 NMS 난수의
 * 같은 이름 메서드를 부른다 — 바닐라처럼 넘긴 난수 객체를 그대로 쓰므로 호출 순서·수열이 같다(복사하지 않는다).
 * <p>주인 스레드로 넘겨 기다리는 길에서는 주인 스레드가 부르고 부른 스레드는 기다린다(넘기기·완료가 happens-before).
 */
public interface PortRandom {
    int nextInt();

    int nextInt(int bound);

    long nextLong();

    float nextFloat();

    double nextDouble();

    boolean nextBoolean();

    double nextGaussian();
}
