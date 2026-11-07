// 원인 사슬을 가진 이벤트. 사슬은 이벤트를 만들 때(구독자가 있을 때만) 정해지고 바뀌지 않는다.
// 서버는 원인을 담는 이벤트를 하나라도 구독한 처리기가 있을 때만 엔티티에 원인을 적어 둔다(구독이 없으면 비용 0) —
// 그래서 구독하기 전에 이미 켜진 TNT 는 바닐라가 아는 만큼(주인)만 사슬에 나온다.
package org.domi.furnace.api.event.cause;

public interface Caused {

    /** 이 변경의 원인 사슬. 모르면 {@link CauseChain#NONE}(null 아님). */
    CauseChain causeChain();
}
