// 스케줄러 진입점 파사드. global / region(추후 entity) owner로 분기.
package org.domi.furnace.api.sched;

public interface FurnaceScheduler {

    /** 전역 스케줄러. */
    GlobalScheduler global();

    /**
     * 주어진 region 좌표를 소유한 스레드의 스케줄러. 절대 null이 아니다.
     * 같은 RegionPos는 항상 같은 스레드로 라우팅되며(결정적 매핑), 여러 RegionPos가 한 스레드를 공유할 수 있다.
     */
    TaskScheduler region(RegionPos pos);
}
