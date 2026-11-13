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

    /**
     * 지금 스레드가 서버의 틱 스레드(전역 스레드 또는 region 스레드)인가. 틱 스레드는 다른 region 의 일을 막고 기다리면 서로 기다리는 교착이 될 수
     * 있어 기다려야 하는 호환 호출(id 로 엔티티 찾기)이 가른다. 모르는 구현은 false(= 기다려도 되는 스레드로 본다).
     */
    default boolean isTickThread() { return false; }
}
