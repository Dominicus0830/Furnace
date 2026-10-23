// 플레이스홀더 — "<name>" 태그를 값으로 바꾼다. parsed 는 값을 마크업으로 다시 해석, unparsed 는 글자 그대로, component 는 Text 를 그대로, styling 은 감싼 내용에 스타일을 입힌다.
package org.domi.furnace.api.text.markup;

import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;

public final class Placeholder {

    private Placeholder() {}

    /** 값을 원문에 그대로 끼워 마크업으로 해석한다(값 안의 태그가 동작한다 — 신뢰할 수 없는 입력에는 쓰지 말 것). 태그 인자 안에서도 동작한다. */
    public static TagResolver parsed(String name, String value) {
        return new Resolvers.Named(TagResolver.checkName(name), Tag.preProcessParsed(value));
    }

    /** 값을 해석하지 않고 글자 그대로 넣는다(사용자 입력에 안전). */
    public static TagResolver unparsed(String name, String value) {
        return new Resolvers.Named(TagResolver.checkName(name), Tag.selfClosingInserting(Text.text(value)));
    }

    /** 완성된 텍스트를 그대로 넣는다. */
    public static TagResolver component(String name, Text value) {
        return new Resolvers.Named(TagResolver.checkName(name), Tag.selfClosingInserting(value));
    }

    /** "<name>내용</name>" 으로 감싼 내용에 스타일을 입힌다. 여러 스타일은 뒤의 것이 앞의 것을 덮는다. */
    public static TagResolver styling(String name, Style... styles) {
        Style merged = Style.empty();
        for (Style s : styles) merged = merged.merge(s);
        return new Resolvers.Named(TagResolver.checkName(name), Tag.styling(merged));
    }

    /** 색과 꾸밈으로 간단히. */
    public static TagResolver styling(String name, TextColor color, TextDecoration... decorations) {
        return styling(name, Style.style(color, decorations));
    }
}
