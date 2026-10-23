// 호버 이벤트(바닐라 HoverEvent): 텍스트·아이템·엔티티 보여주기.
package org.domi.furnace.api.text;

import java.util.Objects;
import java.util.UUID;

public sealed interface HoverEvent {

    /** JSON/NBT action 이름. */
    String action();

    record ShowText(Text text) implements HoverEvent {
        public ShowText { Objects.requireNonNull(text, "text"); }

        public String action() { return "show_text"; }
    }

    /**
     * @param item       아이템 레지스트리 키(minecraft:diamond)
     * @param count      개수(1 이면 직렬화에서 생략 — 바닐라와 같다)
     * @param components 아이템 컴포넌트를 SNBT 문자열로(없으면 null) — 아이템 시스템이 생기면 구조화한다
     */
    record ShowItem(String item, int count, String components) implements HoverEvent {
        public ShowItem { Objects.requireNonNull(item, "item"); }

        public String action() { return "show_item"; }
    }

    /** @param name 보여줄 이름(없으면 null) */
    record ShowEntity(String type, UUID id, Text name) implements HoverEvent {
        public ShowEntity {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }

        public String action() { return "show_entity"; }
    }

    static HoverEvent showText(Text text) { return new ShowText(text); }

    static HoverEvent showItem(String item, int count) { return new ShowItem(item, count, null); }

    static HoverEvent showEntity(String type, UUID id, Text name) { return new ShowEntity(type, id, name); }
}
