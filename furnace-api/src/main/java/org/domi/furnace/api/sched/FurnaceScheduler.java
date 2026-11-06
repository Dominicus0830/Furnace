// 스케줄러 진입점 파사드. global / region(추후 entity) owner로 분기.
package org.domi.furnace.api.sched;

public interface FurnaceScheduler {

    /** 전역 스케줄러. */
    GlobalScheduler global();

    /**
     * 그 청크(RegionPos = 청크 좌표)를 가진 region 의 스케줄러. 절대 null 이 아니다.
     * region 은 동적이라(가까운 청크끼리 합쳐지고 멀어지면 나뉨) 작업은 실행 시점의 주인 region 에서, 그 region 의 다른 작업과
     * 같은 스레드 순서로 돈다. 지연 작업은 남은 틱을 지키며 합치기·나누기를 따라간다.
     */
    TaskScheduler region(RegionPos pos);
}
