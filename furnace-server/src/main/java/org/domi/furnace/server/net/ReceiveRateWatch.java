// server.properties rate-limit(바닐라 RateKickingConnection): 초당 평균 수신 패킷 수가 상한을 넘으면 끊는다.
// 평균은 바닐라 Connection.tickSecond 와 같다 — avg = lerp(0.75, 이번 1초의 수, avg). 이벤트루프 전용.
package org.domi.furnace.server.net;

final class ReceiveRateWatch {

    private final int limit;
    private int received;
    private float average;

    ReceiveRateWatch(int limitPerSecond) { this.limit = limitPerSecond; }

    void onPacket() { received++; }

    /** 1초마다 부른다. 평균이 상한을 넘었으면 true. */
    boolean tickSecond() {
        average = received + 0.75f * (average - received);   // Mth.lerp(0.75f, received, average)
        received = 0;
        return average > limit;
    }

    float average() { return average; }
}
