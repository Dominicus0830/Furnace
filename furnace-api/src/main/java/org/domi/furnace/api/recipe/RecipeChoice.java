// 레시피 재료 하나가 받아들이는 아이템 — 아이템 키 목록 또는 아이템 태그. 개수·컴포넌트는 보지 않는다(바닐라 1.21.1 Ingredient 와 같다).
package org.domi.furnace.api.recipe;

import java.util.List;
import java.util.Objects;

public sealed interface RecipeChoice {

    /** 목록 안의 아이템 중 하나. */
    record Items(List<String> items) implements RecipeChoice {
        public Items {
            Objects.requireNonNull(items, "items");
            if (items.isEmpty()) throw new IllegalArgumentException("재료 아이템 목록이 비었다");
            items = items.stream().map(RecipeChoice::normalize).toList();
        }
    }

    /** 아이템 태그(minecraft:planks, #는 붙이지 않는다). */
    record Tag(String tag) implements RecipeChoice {
        public Tag {
            Objects.requireNonNull(tag, "tag");
            tag = normalize(tag.startsWith("#") ? tag.substring(1) : tag);
        }
    }

    /**
     * 여러 갈래 중 하나(아이템과 태그를 섞은 재료). 비어 있으면 "아무것도 없음" — 대장장이 작업대의 비워 둘 수 있는 칸에만 쓴다.
     */
    record Any(List<RecipeChoice> choices) implements RecipeChoice {
        public Any {
            Objects.requireNonNull(choices, "choices");
            choices = List.copyOf(choices);
        }
    }

    /** 비어 있는 재료(그 칸에는 아무것도 놓지 않는다). 대장장이 작업대 칸에만 쓴다. */
    RecipeChoice NONE = new Any(List.of());

    static RecipeChoice item(String... items) { return new Items(List.of(items)); }

    static RecipeChoice tag(String tag) { return new Tag(tag); }

    private static String normalize(String id) {
        Objects.requireNonNull(id, "id");
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
