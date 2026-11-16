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
}
