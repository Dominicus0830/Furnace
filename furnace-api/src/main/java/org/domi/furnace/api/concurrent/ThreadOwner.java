// 어떤 객체/작업을 소유한 스레드를 나타내는 추상. 소유 스레드에서만 접근 허용.
package org.domi.furnace.api.concurrent;

public interface ThreadOwner {

    /** 현재 스레드가 지금 이 owner 를 실행 중인 스레드인가(region 은 틱마다 다른 워커가 돌릴 수 있어 고정 스레드가 아니다). */
    boolean isOwnerThread();

    /** 진단/로그용 owner 이름. */
    String ownerName();
}
