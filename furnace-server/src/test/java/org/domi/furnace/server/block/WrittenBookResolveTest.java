// A12-10: 서명된 책을 펼칠 때 쪽의 선택자·점수 글을 풀어 책에 쓴다(WrittenBookItem.resolveBookComponents). 이미 resolved 면 그대로.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.Test;

class WrittenBookResolveTest {
    static final class Lvl extends BlockPlacementTest.TestLevel {
        boolean fail;
        @Override protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public CompletableFuture<Text> resolveBookText(Object player, Text text) {
                    if (fail) return CompletableFuture.failedFuture(new IllegalStateException("x"));
                    return CompletableFuture.completedFuture(text.toJson().contains("selector") ? Text.text("Steve") : text);
                }
            };
        }
    }

    private static ItemStack book(boolean resolved, Object... pages) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", "written_book"), 1);
        return ItemTools.with(s, "minecraft:written_book_content",
                Map.of("title", "T", "author", "A", "generation", 0, "pages", List.of(pages), "resolved", resolved));
    }

    @Test
    void selectorPagesAreResolvedAndBookMarkedResolved() {
        Lvl level = new Lvl();
        ItemUseTest.User u = new ItemUseTest.User();
        u.hands[0] = book(false, "{\"selector\":\"@p\"}", Map.of("raw", "{\"selector\":\"@p\"}", "filtered", "\"plain\""));
        ItemUse.useItem(level, u, 0);
        Map<?, ?> c = (Map<?, ?>) ItemTools.component(u.hands[0], "minecraft:written_book_content");
        assertEquals(Boolean.TRUE, c.get("resolved"));
        List<?> pages = (List<?>) c.get("pages");
        assertEquals(Map.of("raw", "\"Steve\""), pages.get(0));
        assertEquals(Map.of("raw", "\"Steve\"", "filtered", "\"plain\""), pages.get(1));
        assertTrue(u.stats.contains("minecraft:used:minecraft:written_book"));
    }

    @Test
    void alreadyResolvedBookIsUntouched() {
        Lvl level = new Lvl();
        ItemUseTest.User u = new ItemUseTest.User();
        ItemStack b = book(true, "{\"selector\":\"@p\"}");
        u.hands[0] = b;
        ItemUse.useItem(level, u, 0);
        assertSame(b, u.hands[0]);
    }

    @Test
    void failingPageKeepsOriginalTextButStillResolves() {
        Lvl level = new Lvl();
        level.fail = true;
        ItemUseTest.User u = new ItemUseTest.User();
        u.hands[0] = book(false, "{\"selector\":\"@p\"}");
        ItemUse.useItem(level, u, 0);
        Map<?, ?> c = (Map<?, ?>) ItemTools.component(u.hands[0], "minecraft:written_book_content");
        assertEquals(Boolean.TRUE, c.get("resolved"));
        assertTrue(((List<?>) c.get("pages")).get(0).toString().contains("selector"), "풀기 예외면 원래 글");
    }
}
