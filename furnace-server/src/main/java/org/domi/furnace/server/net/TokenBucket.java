// 단일 스레드(이벤트 루프)용 토큰 버킷. 패킷 속도/이동 거리 제한에 쓴다.
package org.domi.furnace.server.net;

final class TokenBucket {

    private final double capacity;
    private final double perNano;
    private double tokens;
    private long last = System.nanoTime();

    TokenBucket(double capacity, double perSecond) {
        this.capacity = capacity;
        this.perNano = perSecond / 1_000_000_000.0;
        this.tokens = capacity;
    }

    /** cost만큼 소모를 시도한다. 부족하면 false(소모 없음). */
    boolean tryAcquire(double cost) {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - last) * perNano);
        last = now;
        if (tokens < cost) return false;
        tokens -= cost;
        return true;
    }
}
