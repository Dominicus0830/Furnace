// 번역 키 → 문장 패턴 공급자. 서버는 en_us 를 등록하고, 플러그인은 자기 키(사용자 정의 메시지)를 등록할 수 있다.
package org.domi.furnace.api.text;

import java.util.Locale;

@FunctionalInterface
public interface Translator {

    /** 이 번역기가 아는 키면 패턴(%s, %1$s, %% 형식), 모르면 null. */
    String translate(String key, Locale locale);
}
