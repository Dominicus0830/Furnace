// 살아 있는 인벤토리 핸들. 읽기·쓰기 모두 CompletableFuture — 소유 스레드에서 실행된 결과를 돌려준다(아무 스레드에서 호출 가능).
// 이벤트 안에서 "그 순간의 내용"이 필요하면 이벤트가 가진 InventorySnapshot 을 쓴다.
// 슬롯 번호(플레이어 인벤토리): 0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손.
package org.domi.furnace.api.inventory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.item.ItemStack;

public interface Inventory {

    /** 종류 키(minecraft:player). */
    String type();

    int size();

    /** 범위 밖이면 IndexOutOfBoundsException 으로 완료. */
    CompletableFuture<ItemStack> get(int slot);

    /** item 이 EMPTY 면 비운다. 범위 밖이면 IndexOutOfBoundsException 으로 완료. */
    CompletableFuture<Void> set(int slot, ItemStack item);

    CompletableFuture<List<ItemStack>> contents();
}
