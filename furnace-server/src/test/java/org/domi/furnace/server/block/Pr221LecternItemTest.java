// PR #221 검토 재현: 바닐라 LecternBlockEntity.resolveBook(drk.b(cuq,cmx))은 stack.is(Items.WRITTEN_BOOK)(cut.ua) 일 때만 푼다.
// Furnace 는 written_book_content 컴포넌트만 보고 풀어서, 다른 아이템(/data·block_entity_data 로 넣은 것)도 풀린다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.Test;

class Pr221LecternItemTest {
    @Test
    void nonWrittenBookWithContentIsNotResolved() {
        var level = new LecternBookResolveTest.Lvl();
        Pos pos = new Pos(0, -60, 0);
        level.setBlock(pos, B.prop("facing").with(B.defaultState(B.id("minecraft:lectern")), "east"), 3);
        var be = (LecternBlockEntity) level.getBlockEntity(pos);
        ItemStack writable = ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "writable_book"), 1), "minecraft:written_book_content",
                Map.of("title", "T", "author", "A", "generation", 0, "pages", List.of("{\"selector\":\"@p\"}"), "resolved", false));
        be.setBook(writable, level);
        // 바닐라: writable_book 이라 resolveBookComponents 를 부르지 않는다 → 같은 스택, resolved=false
        assertSame(writable, be.book(), "바닐라는 written_book 이 아닌 아이템을 풀지 않는다");
    }
}
