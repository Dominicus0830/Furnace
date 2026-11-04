// 거래할 수 있는 엔티티(주민·떠돌이 상인). 거래 목록 읽기·바꾸기는 소유 region 스레드에서 실행된다.
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Merchant extends LivingEntity {

    /** 지금 거래 목록(사본). 엔티티가 사라졌거나 다른 region 으로 넘어갔으면 null 로 완료. */
    CompletableFuture<List<MerchantRecipe>> trades();

    /**
     * 거래 목록을 통째로 바꾼다. 거래 창을 연 플레이어가 있으면 그 창은 다음에 열 때 새 목록을 본다(바닐라도 열린 창을 갱신하지 않는다).
     * 엔티티가 없으면 false, 알 수 없는 아이템이면 IllegalArgumentException 으로 완료.
     */
    CompletableFuture<Boolean> setTrades(List<MerchantRecipe> recipes);

    /** 지금 거래 중인 플레이어가 있는가(핸들 시점). */
    boolean isTrading();
}
