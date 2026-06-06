// 스케줄러 진입점 파사드. global / region(추후 entity) owner로 분기.
package org.domi.furnace.api.sched;

public interface FurnaceScheduler {

    /** 전역 스케줄러. */
    GlobalScheduler global();

    /**
     * 주어진 region 좌표를 소유한 스레드의 스케줄러. 절대 null이 아니다.
     * M0에서는 모든 RegionPos가 단일 region에 매핑되어 pos와 무관하게 같은 스케줄러를 반환한다.
     */
    TaskScheduler region(RegionPos pos);
}
