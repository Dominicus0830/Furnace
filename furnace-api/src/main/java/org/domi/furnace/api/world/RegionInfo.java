// region 한 개의 관측 사본(읽기 전용). 청크 구역 묶음을 가진 틱 단위이며, region 은 합쳐지거나 나뉘면 바뀐다 —
// 값은 잰 순간의 것이라 곧 낡을 수 있다. 아무 스레드에서 읽는다.
package org.domi.furnace.api.world;

/**
 * @param id            region 번호(같은 차원 안에서 유일, region 이 합쳐지면 사라질 수 있다)
 * @param dimension     차원 키(minecraft:overworld)
 * @param chunkCount    이 region 이 가진 청크 수(로드·로드 중·시야)
 * @param minChunkX     가진 구역의 청크 좌표 바깥 상자(구역 단위로 넓혀 있어 실제 청크보다 클 수 있다)
 * @param ownerThread   지금 이 region 을 틱 중인 워커 스레드 이름, 쉬는 중이면 null
 * @param tps           최근 5초 틱 속도(초당), 기록이 1초 미만이면 NaN
 * @param lastTickNanos 직전 틱이 걸린 시간(ns)
 * @param maxTickNanos  가장 긴 틱이 걸린 시간(ns)
 */
public record RegionInfo(int id, String dimension, int chunkCount, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                         String ownerThread, double tps, long lastTickNanos, long maxTickNanos) {

    /** 청크 좌표가 이 region 의 바깥 상자 안인가(상자 안이라고 꼭 이 region 것은 아니다 — 모양이 L 자일 수 있다). */
    public boolean boundsContain(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}
