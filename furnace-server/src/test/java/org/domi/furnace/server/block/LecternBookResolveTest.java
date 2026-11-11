// 독서대에 올린(또는 NBT 로 읽은) 서명된 책은 바닐라 LecternBlockEntity.resolveBook 처럼 쪽의 선택자·점수 글이 풀린다.
// 청크를 읽을 때(load 만, 레벨 없음)는 풀리지 않는다. 늦게 끝나는 풀기는 그새 책이 바뀌었으면 버린다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class LecternBookResolveTest {
    static final class Lvl extends BlockPlacementTest.TestLevel {
        CompletableFuture<Text> pending;   // 설정하면 풀기가 끝나지 않는다(다른 region 의 엔티티를 찾는 선택자)
        Pos lastPos;
        @Override protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void resolveLecternBook(BlockLevel level, int x, int y, int z, ItemStack book, BiConsumer<BlockLevel, ItemStack> apply) {
                    lastPos = new Pos(x, y, z);
                    var f = ItemUse.resolveBookStack(book, t -> pending != null ? pending : CompletableFuture.completedFuture(
                            t.toJson().contains("selector") ? Text.text("Lectern") : t));
                    if (f.isDone()) apply.accept(level, f.join());
                    else f.whenComplete((out, e) -> apply.accept(level, out));
                }
            };
        }
    }

    private static ItemStack book(boolean resolved, Object... pages) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", "written_book"), 1);
        return ItemTools.with(s, "minecraft:written_book_content",
                Map.of("title", "T", "author", "A", "generation", 0, "pages", List.of(pages), "resolved", resolved));
    }

    private static Map<?, ?> content(ItemStack b) { return (Map<?, ?>) ItemTools.component(b, "minecraft:written_book_content"); }

    private static LecternBlockEntity lectern(Lvl level, Pos pos) {
        level.setBlock(pos, B.prop("facing").with(B.defaultState(B.id("minecraft:lectern")), "east"), 3);
        return (LecternBlockEntity) level.getBlockEntity(pos);
    }

    @Test
    void placedSignedBookIsResolvedWithLecternSource() {
        Lvl level = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        var be = lectern(level, pos);
        ItemUseTest.User u = new ItemUseTest.User();
        u.hands[0] = book(false, "{\"selector\":\"@p\"}");
        assertTrue(org.domi.furnace.server.block.types.LecternBlock.tryPlaceBook(u, level, pos, level.getBlockState(pos), 0, u.hands[0]));
        assertEquals(Boolean.TRUE, content(be.book()).get("resolved"));
        assertEquals(List.of(Map.of("raw", "\"Lectern\"")), content(be.book()).get("pages"));
        assertEquals(pos, level.lastPos);
    }

    @Test
    void plainBookIsLeftAlone() {
        Lvl level = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        var be = lectern(level, pos);
        ItemStack writable = new ItemStack(ItemContext.id("minecraft:item", "writable_book"), 1);
        be.setBook(writable, level);
        assertSame(writable, be.book());
    }

    @Test
    void loadedBookIsResolvedOnlyWhenAfterLoadRunsWithALevel() {
        Lvl level = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        var raw = new LecternBlockEntity(pos, level.getBlockState(pos));
        raw.setBook(book(false, "{\"selector\":\"@p\"}"), null);   // 레벨 없음: 풀리지 않는다
        assertNotEquals(Boolean.TRUE, content(raw.book()).get("resolved"));
        NbtCompound nbt = raw.saveWithoutMetadata();
        var chunkLoaded = new LecternBlockEntity(pos, level.getBlockState(pos));
        chunkLoaded.load(nbt);   // 청크 읽기: 레벨 없이 load 만
        assertNotEquals(Boolean.TRUE, content(chunkLoaded.book()).get("resolved"));
        var placed = lectern(level, pos);   // 레벨에 놓인 독서대
        placed.loadWithComponentsInLevel(level, nbt);   // /data·/setblock: 레벨이 있는 읽기
        assertEquals(Boolean.TRUE, content(placed.book()).get("resolved"));
        assertEquals(List.of(Map.of("raw", "\"Lectern\"")), content(placed.book()).get("pages"));
    }

    @Test
    void lateResolutionIsDroppedWhenBookChangedMeanwhile() {
        Lvl level = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        var be = lectern(level, pos);
        level.pending = new CompletableFuture<>();
        ItemStack first = book(false, "{\"selector\":\"@e\"}");
        be.setBook(first, level);
        assertSame(first, be.book(), "아직 풀리지 않았다");
        ItemStack other = book(true, "\"other\"");
        be.setBook(other, level);
        level.pending.complete(Text.text("Late"));
        assertSame(other, be.book(), "책이 바뀐 뒤 끝난 풀기는 버린다");
    }

    @Test
    void lateResolutionIsAppliedWhenBookIsStillThere() {
        Lvl level = new Lvl();
        Pos pos = new Pos(0, -60, 0);
        var be = lectern(level, pos);
        level.pending = new CompletableFuture<>();
        be.setBook(book(false, "{\"selector\":\"@e\"}"), level);
        level.pending.complete(Text.text("Late"));
        assertEquals(List.of(Map.of("raw", "\"Late\"")), content(be.book()).get("pages"));
        assertEquals(Boolean.TRUE, content(be.book()).get("resolved"));
    }
}
