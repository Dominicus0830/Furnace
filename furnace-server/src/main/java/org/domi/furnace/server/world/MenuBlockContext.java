// 블록에 매인 메뉴(상자·제작대)의 클릭을 처리하는 동안, 그 블록을 가진 region 이 어디인지 알려 주는 스레드 문맥.
// 바닐라의 "단일 스레드에서 Level 을 직접 만진다"는 가정을 region 모델로 옮긴 것 — 컨테이너·유효성 검사는 이 문맥 안에서만 쓰기/블록 조회를 한다.
package org.domi.furnace.server.world;

public final class MenuBlockContext {

    private static final ThreadLocal<Region> CURRENT = new ThreadLocal<>();

    private MenuBlockContext() { }

    /** 그 region 스레드에서 body 를 돌린다 — 그동안 current() 가 그 region 이다. */
    public static void runIn(Region region, Runnable body) {
        Region prev = CURRENT.get();
        CURRENT.set(region);
        try {
            body.run();
        } finally {
            if (prev == null) CURRENT.remove();
            else CURRENT.set(prev);
        }
    }

    /** 지금 스레드가 메뉴 문맥으로 돌고 있는 region(없으면 null). */
    public static Region current() { return CURRENT.get(); }
}
