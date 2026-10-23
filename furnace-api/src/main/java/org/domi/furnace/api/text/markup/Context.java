// 태그를 해석하는 동안 쓰는 문맥 — 인자 안의 마크업 해석(호버 텍스트 등)과 오류 만들기.
package org.domi.furnace.api.text.markup;

import org.domi.furnace.api.text.Text;

public interface Context {

    /** 같은 태그 집합(플레이스홀더 포함)으로 마크업 조각을 해석한다. */
    Text deserialize(String markup);

    /** 추가 해석기를 얹어 해석한다. */
    Text deserialize(String markup, TagResolver... extra);

    /** 오류를 만든다(던지는 건 호출자 몫). 원문 위치가 붙는다. */
    MarkupParseException newException(String message);

    MarkupParseException newException(String message, ArgumentQueue args);

    /** 지금 해석 중인 원문. */
    String originalMessage();
}
