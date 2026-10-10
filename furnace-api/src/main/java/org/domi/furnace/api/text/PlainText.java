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
            // 서버의 KeybindResolver 기본값은 키를 번역 키로 본다(en_us 의 key.jump → "Jump").
            case Content.Keybind k -> sb.append(Translators.format(k.keybind(), null, List.of(), locale));
            case Content.Score s -> { }
            case Content.Selector s -> { }
            case Content.Nbt n -> { }
        }
        for (Text c : t.children()) append(c, sb, locale);
    }
}
