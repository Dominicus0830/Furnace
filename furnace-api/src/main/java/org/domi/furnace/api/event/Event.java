// 이벤트 바탕 클래스. 어느 스레드에서 불리는지는 각 이벤트 문서에 적는다(Furnace 는 region 스레딩이라 "메인 스레드"가 없다).
package org.domi.furnace.api.event;

public abstract class Event {

    /** 이벤트 이름(기본: 클래스 단순 이름). */
    public String eventName() { return getClass().getSimpleName(); }
}
