// 바닐라 LecternBlockEntity: 독서대에 올린 책 한 권과 펼친 쪽. 저장은 Book(아이템 NBT)·Page.
package org.domi.furnace.server.block.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.LecternBlock;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemNbt;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class LecternBlockEntity extends BlockEntity {

    private ItemStack book = ItemStack.EMPTY;
    private int page;
    private int pageCount;

    public LecternBlockEntity(Pos pos, int state) { super("minecraft:lectern", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "lectern"); }

    public ItemStack book() { return book; }

    public boolean hasBook() { return !book.isEmpty(); }

    public int page() { return page; }

    /** WrittenBookItem/WritableBookItem 의 쪽 수. */
    private static int pageCountOf(ItemStack b) {
        for (String comp : List.of("minecraft:written_book_content", "minecraft:writable_book_content")) {
            if (ItemTools.component(b, comp) instanceof Map<?, ?> m && m.get("pages") instanceof List<?> l) return l.size();
        }
        return 0;
    }

    /** 책을 올리거나(비어 있지 않으면) 치운다. 쪽은 0 으로. */
    public void setBook(ItemStack b, BlockLevel level) {
        book = b == null ? ItemStack.EMPTY : b;
        page = 0;
        pageCount = pageCountOf(book);
        resolveBook(level);
        if (level != null) setChanged(level);
    }

    /**
     * 바닐라 resolveBook: 서명된 책이면 쪽의 선택자·점수·NBT 글을 값으로 풀어 책에 쓴다(독서대 소스, 플레이어 없음). 레벨이 있을 때만 —
     * 청크를 읽을 때는 레벨이 없어 풀리지 않는다. 늦게 끝나면(다른 region 의 엔티티) 그새 책이 바뀌지 않았을 때만 바꾼다.
     */
    private void resolveBook(BlockLevel level) {
        // 바닐라 drk.b(cuq,cmx): stack.is(Items.WRITTEN_BOOK) 일 때만(컴포넌트만 있는 다른 아이템은 풀지 않는다)
        if (level == null || !"minecraft:written_book".equals(ItemTools.itemName(book))) return;
        ItemStack original = book;
        boolean[] sync = {true};
        level.resolveLecternBook(pos, original, (lv, out) -> {
            if (lv.getBlockEntity(pos) != this || book != original) return;
            book = out;
            if (!sync[0]) setChanged(lv);
        });
        sync[0] = false;
    }

    @Override public void afterLoad(BlockLevel level) { resolveBook(level); }

    /** 바닐라 setPage: 범위로 잘라 넣고, 바뀌었으면 저장 표시 + 비교기/레드스톤에 쪽 넘김 신호. */
    public void setPage(int newPage, BlockLevel level) {
        int clamped = Math.clamp(newPage, 0, Math.max(pageCount - 1, 0));
        if (clamped != page) {
            page = clamped;
            if (level != null) {
                setChanged(level);
                LecternBlock.signalPageChange(level, pos, level.getBlockState(pos));
            }
        }
    }

    /** 바닐라 getRedstoneSignal: 책이 있으면 쪽 위치 비례 0..14 에 1 을 더한다. */
    public int redstoneSignal() {
        float f = pageCount > 1 ? page / (pageCount - 1.0F) : 1.0F;
        return (int) Math.floor(f * 14.0F) + (hasBook() ? 1 : 0);
    }

    @Override
    public void load(NbtCompound tag) {
        book = ItemStack.EMPTY;
        if (tag.contains("Book") && ItemContext.installed()) {
            try {
                NbtCompound ie = tag.getCompound("Book");
                String name = ie.getString("id");
                Integer id = ItemContext.id("minecraft:item", name);
                int count = ie.contains("count") ? ie.getInt("count") : 1;
                var patch = id == null ? null : ItemNbt.read(ie, name, ItemNbt.LEGACY_KEY);
                if (id != null && id > 0 && count > 0 && patch != null) book = new ItemStack(id, count, patch);
            } catch (RuntimeException e) {
                // 이상한 책은 없는 것으로
            }
        }
        pageCount = pageCountOf(book);
        page = Math.clamp(tag.getIntOr0("Page"), 0, Math.max(pageCount - 1, 0));
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (book.isEmpty() || !ItemContext.installed()) return;
        NbtCompound e = new NbtCompound();
        e.putString("id", ItemContext.name("minecraft:item", book.itemId()));
        e.putInt("count", book.count());
        NbtCompound comps = ItemNbt.write(book.patch());
        if (comps != null) e.putCompound(ItemNbt.KEY, comps);
        tag.putCompound("Book", e);
        tag.putInt("Page", page);
    }
}
