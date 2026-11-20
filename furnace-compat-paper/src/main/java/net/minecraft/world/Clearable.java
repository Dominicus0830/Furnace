package net.minecraft.world;

/**
 * 바닐라 Clearable(Mojang 이름 shim, T-211 3단계): 담은 것을 비울 수 있는 것. 블록 엔티티 shim 가운데 바닐라에서 이것인 종류(담는 블록 엔티티 계열·모닥불·
 * 조각된 책장·장식 단지·주크박스·독서대)가 구현한다 — 월드의 블록 엔티티면 서버 블록 엔티티를 바닐라처럼 비운다.
 */
public interface Clearable {

    void clearContent();

    static void tryClear(Object o) {
        if (o instanceof Clearable c) c.clearContent();
    }
}
