// 해석된 태그 — 무엇을 하는지만 담는다: 텍스트 끼우기·자식에 스타일 입히기·자식 변형·원문 치환·초기화.
package org.domi.furnace.api.text.markup;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;

public sealed interface Tag {

    /** 텍스트를 그 자리에 끼운다. allowsChildren 이면 닫는 태그 전까지의 내용이 끼운 텍스트의 자식이 된다. */
    record Inserting(Text value, boolean allowsChildren) implements Tag {
        public Inserting { Objects.requireNonNull(value, "value"); }
    }

    /** 자식들에 스타일(덮어 쓸 값만 정한 Style)을 입힌다. */
    record Styling(Style style) implements Tag {
        public Styling { Objects.requireNonNull(style, "style"); }
    }

    /** 원문 문자열 치환 — 값을 마크업으로 다시 해석한다(Placeholder.parsed). */
    record PreProcess(String value) implements Tag {
        public PreProcess { Objects.requireNonNull(value, "value"); }
    }

    /** 닫히는 순간 자식(이미 해석된 텍스트들)을 통째로 받아 바꾼다(그라디언트 등). */
    record Modifying(Modifier modifier) implements Tag {
        public Modifying { Objects.requireNonNull(modifier, "modifier"); }
    }

    /** 지금까지 열린 태그를 모두 닫고 스타일을 처음으로 되돌린다. */
    record Reset() implements Tag {}

    /** 자식 변형기. 입력 목록을 고치지 말고 새 목록을 돌려준다. */
    @FunctionalInterface
    interface Modifier {
        List<Text> modify(List<Text> children);
    }

    Reset RESET = new Reset();

    /** 닫는 태그가 필요 없는 텍스트 끼우기(<newline>, 플레이스홀더 등). */
    static Tag selfClosingInserting(Text value) { return new Inserting(value, false); }

    static Tag inserting(Text value) { return new Inserting(value, true); }

    static Tag inserting(boolean allowsChildren, Text value) { return new Inserting(value, allowsChildren); }

    static Tag styling(Style delta) { return new Styling(delta); }

    /** 빈 스타일에 빌더로 값을 채워 입힌다. */
    static Tag styling(Consumer<Style.Builder> config) {
        Style.Builder b = Style.builder();
        config.accept(b);
        return new Styling(b.build());
    }

    static Tag preProcessParsed(String value) { return new PreProcess(value); }

    static Tag modifying(Modifier modifier) { return new Modifying(modifier); }

    static Tag reset() { return RESET; }
}
