// 바닐라 PlayerChunkSender 의 속도 조절 — 클라이언트가 chunk_batch_received 로 알려 주는 "틱당 받을 수 있는 청크 수"에 맞춘다.
// 차이 1: unacknowledgedBatches 는 0 아래로 내려가지 않게 막는다(바닐라는 거짓 응답으로 음수가 된다).
// 차이 2: 첫 chunk_batch_received 가 오기 전에는 제한하지 않는다(바닐라는 첫 배치 하나만 보내고 응답을 기다린다) — 기존 고정 전송 예산을 그대로 쓴다.
package org.domi.furnace.server.world;

public final class ChunkSendRate {

    public static final float MIN_CHUNKS_PER_TICK = 0.01F;
    public static final float MAX_CHUNKS_PER_TICK = 64.0F;
    private static final float START_CHUNK_RATE = 9.0F;
    private static final int MAX_UNACKNOWLEDGED_AFTER_FIRST = 10;

    private float desiredChunksPerTick = START_CHUNK_RATE;
    private float batchQuota;
    private int unacknowledgedBatches;
    private int maxUnacknowledgedBatches = 1;
    private boolean clientReported;

    /**
     * 한 틱의 전송 허용량(sendNextChunks 앞부분): 미확인 배치가 한도 아래일 때만 quota 를 채우고, 1 이상이면 그 내림 값을 돌려준다.
     * 이 틱에 실제로 배치를 보내면 batchSent 로 알린다.
     */
    public synchronized int nextBudget() {
        if (!clientReported) return Integer.MAX_VALUE;   // 클라이언트가 속도를 알려 오기 전: 제한 없음(호출부의 고정 예산만)
        if (unacknowledgedBatches >= maxUnacknowledgedBatches) return 0;
        float cap = Math.max(1.0F, desiredChunksPerTick);
        batchQuota = Math.min(batchQuota + desiredChunksPerTick, cap);
        return batchQuota < 1.0F ? 0 : (int) Math.floor(batchQuota);
    }

    /** 배치를 count 개 청크로 보냈다(스폰 배치 포함). */
    public synchronized void batchSent(int count) {
        unacknowledgedBatches++;
        batchQuota -= count;
    }

    /** onChunkBatchReceivedByClient: 클라이언트가 한 배치를 받았다고 알리며 원하는 속도를 준다. */
    public synchronized void onBatchReceived(float desired) {
        clientReported = true;
        if (unacknowledgedBatches > 0) unacknowledgedBatches--;
        desiredChunksPerTick = Float.isNaN(desired) ? MIN_CHUNKS_PER_TICK : Math.clamp(desired, MIN_CHUNKS_PER_TICK, MAX_CHUNKS_PER_TICK);
        if (unacknowledgedBatches == 0) batchQuota = 1.0F;
        maxUnacknowledgedBatches = MAX_UNACKNOWLEDGED_AFTER_FIRST;
    }

    public synchronized float desiredChunksPerTick() { return desiredChunksPerTick; }

    public synchronized int unacknowledgedBatches() { return unacknowledgedBatches; }
}
