package org.domi.furnace.api.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

/** T-141 E11c-1: Menu#update — 칸 하나를 읽고-바꾸기를 한 번에(읽기와 쓰기 사이에 다른 쓰기가 끼지 않는다). */
class MenuUpdateTest {

    static ItemStack stone(int n) { return new ItemStack("minecraft:stone", n); }

    @Test
    void replacesOnlyWhenTheFunctionReturnsADifferentStack() {
        Menu menu = Menu.chest(Text.text("t"), 1);
        menu.set(2, stone(3));
        assertTrue(menu.update(2, cur -> cur.withAmount(cur.amount() + 1)));
        assertEquals(4, menu.item(2).amount());

        ItemStack now = menu.item(2);
        assertFalse(menu.update(2, cur -> cur), "받은 것 그대로면 바꾸지 않는다");
        assertFalse(menu.update(2, cur -> null), "null 이면 바꾸지 않는다");
        assertFalse(menu.update(2, cur -> new ItemStack("minecraft:stone", 4)), "같은 값이면 바꾼 것이 아니다");
        assertSame(now, menu.item(2));
        assertThrows(IndexOutOfBoundsException.class, () -> menu.update(99, cur -> cur));
    }

    @Test
    void emptyResultClearsTheSlotAndListenersHearOnlyRealChanges() {
        Menu menu = Menu.chest(Text.text("t"), 1);
        List<Integer> heard = new ArrayList<>();
        menu.set(0, stone(1));
        menu.addListener((m, slot, item) -> heard.add(slot));   // 칸이 바뀔 때 부른다
        assertTrue(menu.update(0, cur -> ItemStack.EMPTY));
        assertTrue(menu.item(0).isEmpty());
        assertFalse(menu.update(0, cur -> cur));
        assertEquals(List.of(0), heard);
    }

    @Test
    void concurrentUpdatesDoNotLoseWrites() throws Exception {
        Menu menu = Menu.chest(Text.text("t"), 1);
        menu.set(0, stone(1));
        int threads = 8, each = 2000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int k = 0; k < each; k++) menu.update(0, cur -> new ItemStack("minecraft:stone", cur.amount() + 1));
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        assertEquals(1 + threads * each, menu.item(0).amount(), "읽고-쓰기가 한 번에 돌아 하나도 잃지 않는다");
    }

    @Test
    void theFunctionRunsOutsideEveryLockSoAnotherThreadCanUseTheMenuMeanwhile() {
        for (boolean storage : new boolean[] {false, true}) {
            Menu menu = Menu.chest(Text.text("t"), 1);
            if (storage) menu.storage(true);   // 보관함 메뉴: 쓰기는 전역 클릭 잠금과 한 줄
            menu.set(0, stone(1));
            java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch otherDone = new java.util.concurrent.CountDownLatch(1);
            Thread other = new Thread(() -> {
                try {
                    inside.await();
                    menu.item(0);                 // fn 이 도는 동안 읽기·쓰기가 막히지 않아야 한다
                    menu.set(1, stone(9));
                    otherDone.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            other.start();
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
                assertTrue(menu.update(0, cur -> {
                    inside.countDown();
                    try {
                        assertTrue(otherDone.await(5, java.util.concurrent.TimeUnit.SECONDS), "fn 이 잠금을 쥐고 있으면 다른 스레드가 막힌다");
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    return stone(2);
                }));
            });
            assertEquals(2, menu.item(0).amount());
            assertEquals(9, menu.item(1).amount());
        }
    }

    @Test
    void aWriteThatLandsDuringTheFunctionMakesItRunAgainOnTheNewValue() {
        Menu menu = Menu.chest(Text.text("t"), 1);
        menu.set(0, stone(1));
        List<Integer> seen = new ArrayList<>();
        boolean[] interfered = {false};
        assertTrue(menu.update(0, cur -> {
            seen.add(cur.amount());
            if (!interfered[0]) {
                interfered[0] = true;
                menu.set(0, stone(10));   // fn 안에서 이 메뉴를 다시 건드려도 막히지 않는다
            }
            return stone(cur.amount() + 1);
        }));
        assertEquals(List.of(1, 10), seen, "읽은 값이 그새 바뀌었으면 버리고 새 값으로 fn 을 다시 부른다");
        assertEquals(11, menu.item(0).amount());
    }

    @Test
    void aFunctionThatKeepsWritingTheSameSlotEndsWithAnErrorInsteadOfSpinning() {
        Menu menu = Menu.chest(Text.text("t"), 1);
        menu.set(0, stone(1));
        assertThrows(IllegalStateException.class, () -> menu.update(0, cur -> {
            menu.set(0, stone(cur.amount() + 1));   // 읽은 칸을 매번 바꿔 계속 진다
            return stone(cur.amount() + 100);
        }));
    }
}
