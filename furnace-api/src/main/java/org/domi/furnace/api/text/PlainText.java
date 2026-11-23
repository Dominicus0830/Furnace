// 평문 렌더링(바닐라 Component.getString): 번역은 Translators, 점수·선택자·NBT 는 해석 전이라 빈 문자열.
package org.domi.furnace.api.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PlainText {

    private PlainText() {}

    static void append(Text t, StringBuilder sb, Locale locale) {
        switch (t.content()) {
            case Content.Literal l -> sb.append(l.text());
            case Content.Translatable tr -> {
                List<String> args = new ArrayList<>(tr.args().size());
                for (Object a : tr.args()) {
                    if (a instanceof Text at) {
                        StringBuilder s = new StringBuilder();
                        append(at, s, locale);
                        args.add(s.toString());
                    } else {
                        args.add(String.valueOf(a));
                    }
                }
                sb.append(Translators.format(tr.key(), tr.fallback(), args, locale));
            }
            // 서버의 KeybindResolver 기본값은 키 글자 그대로다(바닐라 1.21.1 getString: key.jump → "key.jump" — en_us 에 "Jump" 가 있어도.
            // 키를 실제 키로 바꾸는 건 클라이언트다). T-235 바닐라 대조로 확인(11-23).
            case Content.Keybind k -> sb.append(k.keybind());
            case Content.Score s -> { }
            case Content.Selector s -> { }
            case Content.Nbt n -> { }
        }
        for (Text c : t.children()) append(c, sb, locale);
    }
}
