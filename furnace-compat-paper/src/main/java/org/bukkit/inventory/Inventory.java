package org.bukkit.inventory;

import java.util.HashMap;
import java.util.ListIterator;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;

/**
 * 칸이 줄지어 있는 보관함. 빈 칸은 null 이다. getItem·getContents 가 주는 ItemStack 은 칸을 그대로 비추는 "거울"이라
 * 바꾸면 칸에 곧바로 반영된다(Bukkit 과 같다).
 * 스레드: 플레이어를 가진 region 스레드에서는 모두 곧바로 실행된다. 다른 스레드(접속·퇴장 처리기, 전역 스레드의 명령 등)에서는
 * 읽기는 곧바로 되고(서버가 플레이어 인벤토리를 스레드 안전하게 둔다), 쓰기는 소유 스레드로 **예약**된다. 예약된 쓰기는 이 Inventory
 * 객체가 들고 있다가 읽을 때 겹쳐 보여 주므로 같은 스레드에서 쓰고 바로 읽거나 addItem 을 연달아 불러도 Bukkit 처럼 서로 덮어쓰지 않는다.
 * 다만 다른 스레드가 그 사이 같은 칸을 고치면 예약 쓰기가 그 칸을 덮어쓰거나 결과(addItem 이 돌려준 못 넣은 목록 등)가 실제와 다를 수 있다.
 */
public interface Inventory extends Iterable<ItemStack> {

    int getSize();

    int getMaxStackSize();

    ItemStack getItem(int index);

    void setItem(int index, ItemStack item);

    /** 앞에서부터 합치고 빈 칸에 넣는다. 못 넣은 것은 넘긴 순서 번호 → 남은 스택으로 돌려준다(넘긴 스택의 개수도 줄어든다). */
    HashMap<Integer, ItemStack> addItem(ItemStack... items);

    /** 같은 종류(isSimilar)를 앞에서부터 뺀다. 못 뺀 것은 순서 번호 → 남은 스택. */
    HashMap<Integer, ItemStack> removeItem(ItemStack... items);

    ItemStack[] getContents();

    void setContents(ItemStack[] items);

    ItemStack[] getStorageContents();

    void setStorageContents(ItemStack[] items);

    boolean contains(Material material);

    boolean contains(ItemStack item);

    boolean contains(Material material, int amount);

    boolean contains(ItemStack item, int amount);

    boolean containsAtLeast(ItemStack item, int amount);

    HashMap<Integer, ? extends ItemStack> all(Material material);

    HashMap<Integer, ? extends ItemStack> all(ItemStack item);

    int first(Material material);

    int first(ItemStack item);

    int firstEmpty();

    boolean isEmpty();

    void remove(Material material);

    void remove(ItemStack item);

    void clear(int index);

    void clear();

    InventoryType getType();

    InventoryHolder getHolder();

    @Override
    ListIterator<ItemStack> iterator();
}
