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

    /**
     * 엔티티 용기(말·상자 배·상자 수레)를 지금 문맥의 region 이 가졌는가 — 엔티티가 region 경계를 넘어 다른 스레드로 넘어간 뒤에는
     * 그 용기를 이 스레드에서 쓰지 않는다(창의 stillValid 가 거짓 → 닫힌다). 문맥이 없으면(시험) 참.
     */
    public static boolean ownsEntity(org.domi.furnace.server.entity.Entity e) {
        Region r = CURRENT.get();
        return r == null || r.level().entities().get(e.id()) == e;
    }

    /** 엔티티의 지금 블록 좌표 {x, y, z}. */
    public static int[] blockPos(org.domi.furnace.server.entity.Entity e) {
        return new int[]{(int) Math.floor(e.x()), (int) Math.floor(e.y()), (int) Math.floor(e.z())};
    }
}
