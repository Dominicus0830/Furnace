// 책과 깃펜(바닐라 ServerGamePacketListenerImpl.handleEditBook): edit_book(서버 수신 0x14) 이 오면 그 칸의 깃펜 책을 고친다.
//  - 제목이 없으면 저장: writable_book_content 의 쪽(최대 100)만 바꾼다.
//  - 제목이 있으면 서명: 책을 written_book 으로 바꾸고(개수·나머지 컴포넌트 유지, 깃펜 내용은 지움)
//    written_book_content{제목, 저자=플레이어 이름, 세대 0, 쪽=글자 그대로의 텍스트 컴포넌트, resolved=true} 를 단다.
// 순수 변환만 한다(아이템 칸 반영은 호출하는 쪽). 글자 걸러내기(text filtering)는 없으므로 raw 만 쓴다(filtered 없음).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public final class BookEditing {
    private BookEditing() { }

    /** edit_book 이 받아들이는 칸: 핫바 0~8 과 보조 손 40(Inventory.isHotbarSlot || 40). */
    public static boolean validSlot(int slot) { return slot >= 0 && slot <= 8 || slot == 40; }

    /** 패킷 칸 번호 → 창 칸 번호(핫바 36~44, 보조 손 45). */
    public static int windowSlot(int slot) { return slot == 40 ? 45 : 36 + slot; }

    private static boolean is(ItemStack s, String name) { return !s.isEmpty() && ItemTools.itemName(s).equals(name); }

    private static Map<String, Object> filterable(String raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        return m;
    }

    /** 깃펜 책의 쪽(글자 그대로). 깃펜 책이 아니거나 내용이 없으면 빈 목록. */
    public static List<String> pagesOf(ItemStack book) {
        List<String> out = new ArrayList<>();
        if (!is(book, "minecraft:writable_book") || !(ItemTools.component(book, "minecraft:writable_book_content") instanceof Map<?, ?> content)) return out;
        if (content.get("pages") instanceof List<?> pages) {
            for (Object page : pages) {
                if (page instanceof Map<?, ?> m && m.get("raw") != null) out.add(String.valueOf(m.get("raw")));
                else if (page instanceof String s) out.add(s);
            }
        }
        return out;
    }

    /** updateBookContents: 깃펜 책이면 쪽만 바꾼 새 스택, 아니면 null. 쪽은 앞의 100개만. */
    public static ItemStack write(ItemStack book, List<String> pages) {
        if (!is(book, "minecraft:writable_book")) return null;
        List<Object> list = new ArrayList<>();
        for (String page : pages.subList(0, Math.min(100, pages.size()))) list.add(filterable(page));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("pages", list);
        return ItemTools.with(book, "minecraft:writable_book_content", content);
    }

    /** signBook: 깃펜 책이면 서명한 책, 아니면 null. */
    public static ItemStack sign(ItemStack book, String title, List<String> pages, String author) {
        if (!is(book, "minecraft:writable_book")) return null;
        Map<String, Object> rest = new LinkedHashMap<>(book.patch().entries());
        rest.remove("minecraft:writable_book_content");
        ItemStack written = new ItemStack(ItemContext.id("minecraft:item", "written_book"), book.count(), ComponentPatch.ofNormalized(rest));
        List<Object> list = new ArrayList<>();
        for (String page : pages.subList(0, Math.min(100, pages.size()))) {
            list.add(filterable(org.domi.furnace.api.text.Text.text(page).toJson()));   // Component.literal(page)
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", filterable(title));
        content.put("author", author);
        content.put("generation", 0);
        content.put("pages", list);
        content.put("resolved", true);
        return ItemTools.with(written, "minecraft:written_book_content", content);
    }
}
