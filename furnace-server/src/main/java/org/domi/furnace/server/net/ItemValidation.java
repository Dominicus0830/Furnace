// 클라이언트가 보낸 아이템 안의 글자 수 상한 검사(Paper item-validation 대응). 켜진 상한이 없으면(기본 = 바닐라) 아무것도 하지 않는다.
// 이름·설명·책 쪽·저자의 길이가 상한을 넘으면 CodecException — 디코드 실패와 같이 연결을 끊는다.
// 상자·묶음 같은 안에 든 아이템도 같은 상한으로 검사한다(안에 숨겨 우회하지 못하게).
package org.domi.furnace.server.net;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.item.CodecException;
import org.domi.furnace.server.entity.ItemStack;

final class ItemValidation {

    private static final int MAX_DEPTH = 16;

    private ItemValidation() {}

    static void check(ItemStack s, ConnectionLimits l) {
        if (s == null || s.isEmpty() || !l.itemValidationEnabled()) return;
        components(s.patch().entries(), l, 0);
    }

    private static void components(Map<String, Object> comps, ConnectionLimits l, int depth) {
        if (depth > MAX_DEPTH) throw new CodecException("item-validation: 아이템이 너무 깊게 중첩됨");
        for (var e : comps.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            switch (e.getKey()) {
                case "minecraft:custom_name" -> text(v, l.displayNameLength(), "custom_name");
                case "minecraft:item_name" -> text(v, l.itemNameLength(), "item_name");
                case "minecraft:lore" -> {
                    if (v instanceof List<?> lines) for (Object line : lines) text(line, l.loreLineLength(), "lore");
                }
                case "minecraft:writable_book_content", "minecraft:written_book_content" -> book(v, l);
                default -> { }
            }
            nested(v, l, depth);
        }
    }

    private static void book(Object v, ConnectionLimits l) {
        if (!(v instanceof Map<?, ?> m)) return;
        text(m.get("author"), l.bookAuthorLength(), "book author");
        if (m.get("pages") instanceof List<?> pages) {
            for (Object page : pages) {
                if (page instanceof Map<?, ?> pm) {   // Filterable: {raw, filtered?}
                    text(pm.get("raw"), l.bookPageLength(), "book page");
                    text(pm.get("filtered"), l.bookPageLength(), "book page");
                } else {
                    text(page, l.bookPageLength(), "book page");
                }
            }
        }
    }

    /** 안에 든 아이템({id, count, components})을 찾아 같은 검사를 한다. */
    private static void nested(Object v, ConnectionLimits l, int depth) {
        if (v instanceof Map<?, ?> m) {
            if (m.get("components") instanceof Map<?, ?> inner) {
                @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>) inner;
                components(c, l, depth + 1);
            }
            for (Object child : m.values()) {
                if (child instanceof Map<?, ?> || child instanceof List<?>) nested(child, l, depth + 1);
            }
        } else if (v instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> || child instanceof List<?>) nested(child, l, depth + 1);
            }
        }
        if (depth > MAX_DEPTH) throw new CodecException("item-validation: 아이템이 너무 깊게 중첩됨");
    }

    private static void text(Object v, int max, String what) {
        if (max <= 0 || !(v instanceof String s)) return;
        if (s.length() > max) {
            throw new CodecException("item-validation: " + what + " 길이 " + s.length() + " 가 상한 " + max + " 를 넘음");
        }
    }
}
