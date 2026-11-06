// 바닐라 PlayerChunkSender 속도 조절의 결정적 시나리오.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChunkSendRateTest {

    @Test
    void firstTickAllowsStartRateAndWaitsForAck() {
        ChunkSendRate r = new ChunkSendRate();
        assertEquals(Integer.MAX_VALUE, r.nextBudget());   // 첫 응답 전: 제한 없음
        r.batchSent(9);
        assertEquals(Integer.MAX_VALUE, r.nextBudget());
        r.onBatchReceived(2.5F);
        assertEquals(2.5F, r.desiredChunksPerTick());
        // 모두 확인 → quota 1 로 재설정, 이어서 +2.5 하되 상한 max(1, 2.5)=2.5 → 2
        assertEquals(2, r.nextBudget());
    }

    @Test
    void lowRateAccumulatesAcrossTicks() {
        ChunkSendRate r = new ChunkSendRate();
        r.batchSent(9);
        r.onBatchReceived(0.5F);    // quota = 1
        assertEquals(1, r.nextBudget());   // min(1+0.5, max(1,0.5)=1) = 1
        r.batchSent(1);             // quota 0, 미확인 1 (한도 10)
        assertEquals(0, r.nextBudget());   // 0.5
        assertEquals(1, r.nextBudget());   // 1.0
    }

    @Test
    void clampsDesiredAndHandlesNaN() {
        ChunkSendRate r = new ChunkSendRate();
        r.onBatchReceived(1000F);
        assertEquals(64F, r.desiredChunksPerTick());
        r.onBatchReceived(-3F);
        assertEquals(0.01F, r.desiredChunksPerTick());
        r.onBatchReceived(Float.NaN);
        assertEquals(0.01F, r.desiredChunksPerTick());
    }

    @Test
    void unacknowledgedNeverNegativeAndLimitRisesToTen() {
        ChunkSendRate r = new ChunkSendRate();
        r.onBatchReceived(64F);   // 거짓 응답
        assertEquals(0, r.unacknowledgedBatches());
        assertEquals(64, r.nextBudget());   // quota 1 → min(1+64, 64)
        for (int i = 0; i < 10; i++) r.batchSent(1);
        assertEquals(10, r.unacknowledgedBatches());
        assertEquals(0, r.nextBudget());
    }
}
