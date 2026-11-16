// 텍스트 내용 종류(바닐라 ComponentContents): 글자·번역·키 바인딩·점수·선택자·NBT.
package org.domi.furnace.api.text;

import java.util.List;
import java.util.Objects;

public sealed interface Content {

    /** 바닐라 PlainTextContents. */
    record Literal(String text) implements Content {
        public Literal { Objects.requireNonNull(text, "text"); }
    }

    /**
     * 바닐라 TranslatableContents. args 원소는 {@link Text} 또는 원시값(String·Number·Boolean)이며, 바닐라처럼 원시값 그대로 직렬화된다.
     *
     * @param fallback 클라이언트가 키를 모를 때 쓸 문장(없으면 null)
     */
    record Translatable(String key, String fallback, List<Object> args) implements Content {
        public Translatable {
            Objects.requireNonNull(key, "key");
            for (Object a : args) {
                if (!(a instanceof Text || a instanceof String || a instanceof Number || a instanceof Boolean)) {
                    throw new IllegalArgumentException("translation arguments must be Text, String, Number or Boolean: " + a);
                }
            }
            args = List.copyOf(args);
        }
    }

    /** 바닐라 KeybindContents(key.jump 등). 클라이언트 키 설정에 따라 보인다. */
    record Keybind(String keybind) implements Content {
        public Keybind { Objects.requireNonNull(keybind, "keybind"); }
    }

    /** 바닐라 ScoreContents. */
    record Score(String name, String objective) implements Content {
        public Score {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(objective, "objective");
        }
    }

    /** 바닐라 SelectorContents(@a 등). separator 없으면 null. */
    record Selector(String pattern, Text separator) implements Content {
        public Selector { Objects.requireNonNull(pattern, "pattern"); }
    }

    /** NBT 출처 — 블록 좌표·엔티티 선택자·저장소 키. */
    sealed interface NbtSource {
        record Block(String pos) implements NbtSource {}

        record Entity(String selector) implements NbtSource {}

        record Storage(String id) implements NbtSource {}
    }

    /** 바닐라 NbtContents. */
    record Nbt(String path, boolean interpret, Text separator, NbtSource source) implements Content {
        public Nbt {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(source, "source");
        }
    }
}
