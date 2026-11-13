// 이벤트 바탕 클래스. 어느 스레드에서 불리는지는 각 이벤트 문서에 적는다(Furnace 는 region 스레딩이라 "메인 스레드"가 없다).
//
// 발화 스레드 계약:
//  - 동기 이벤트(isAsync()==false, 기본): 그 이벤트가 일어난 칸·엔티티를 가진 region 스레드(또는 전역 스레드)에서 불린다.
//    처리기는 그 스레드가 소유한 것만 바로 만질 수 있고, 다른 region 것은 스케줄러로 넘겨야 한다(어기면 ThreadOwnershipException).
//  - 비동기 이벤트(isAsync()==true): 어느 스레드에서든 불릴 수 있다. 처리기는 월드·엔티티 상태를 바로 만지지 말고 스케줄러로 넘긴다.
//    이름이 Async 로 시작하는 이벤트(AsyncPlayerPreLoginEvent·AsyncPlayerConfigureEvent·AsyncChatEvent)가 이것이다(Paper 와 같다).
//  - 연결 스레드 이벤트(isAsync()==false 지만 region/전역 스레드가 아니다): 그 플레이어 연결의 네트워크 스레드에서 불린다 —
//    PlayerHandshakeEvent·PlayerLoginEvent·PlayerCreateEvent·PlayerSpawnLocationEvent·PlayerJoinEvent·PlayerQuitEvent·PlayerRespawnEvent·
//    채팅 명령의 PlayerCommandPreprocessEvent·PlayerChannelEvent·PluginMessageEvent·ServerListPingEvent. Paper 에서 동기인 이벤트라
//    isAsync() 는 false 로 두지만(옮겨 온 코드가 놀라지 않게), 월드·엔티티 상태는 비동기 이벤트처럼 스케줄러(player.scheduler() 등)로 넘긴다.
//    각 이벤트 문서가 발화 스레드를 적는다.
package org.domi.furnace.api.event;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public abstract class Event {

    private final boolean async;

    /**
     * 붙임 칸({@link EventSlot}): (열쇠, 값, 열쇠, 값, …) 배열. 처리기들이 나눠 쓰는 파생 값을 전역 맵·잠금 없이 이벤트 객체에 직접 붙인다.
     * 바꿀 때는 배열을 새로 만들어 CAS 로 끼운다 — 읽기는 volatile 읽기 하나와 짧은 훑기다.
     */
    @SuppressWarnings("unused")   // SLOTS 로만 만진다
    private volatile Object[] slots;

    static final VarHandle SLOTS;

    static {
        try {
            SLOTS = MethodHandles.lookup().findVarHandle(Event.class, "slots", Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 동기 이벤트. */
    protected Event() { this(false); }

    /** @param async 서버의 region/전역 스레드가 아닌 곳에서도 불릴 수 있으면 true */
    protected Event(boolean async) { this.async = async; }

    /** 이벤트 이름(기본: 클래스 단순 이름). */
    public String eventName() { return getClass().getSimpleName(); }

    /** 비동기 이벤트인가 — true 면 처리기가 어느 스레드에서든 불릴 수 있다(위 발화 스레드 계약 참고). */
    public final boolean isAsync() { return async; }
}
