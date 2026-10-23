// 평문 직렬화기 — 색·스타일 없이 글자만. 번역 텍스트를 번역할지 키로 둘지, 키 바인딩·점수·선택자를 어떻게 보일지 옵션으로 고른다.
// 기본 설정은 Text.plainText() 와 똑같이 동작한다.
package org.domi.furnace.api.text.serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.Translators;

public final class PlainTextSerializer {

    /** 번역 텍스트를 어떻게 쓸까. */
    public enum TranslatableMode {
        /** 등록된 번역기로 번역해 인자를 채운다(기본). */
        TRANSLATE,
        /** 번역 키만. */
        KEY,
        /** 키와 인자: key[a, b]. */
        KEY_WITH_ARGS
    }

    /** 키 바인딩을 어떻게 쓸까. */
    public enum KeybindMode {
        /** key.jump → "Jump" 처럼 번역(기본). */
        TRANSLATE,
        /** key.jump 그대로. */
        KEY,
        /** [key.jump]. */
        BRACKETED
    }

    /** 해석 전이라 값을 모르는 내용(점수·선택자·NBT)을 어떻게 쓸까. */
    public enum DynamicMode {
        /** 빈 문자열(기본). */
        EMPTY,
        /** 점수 name:objective, 선택자 패턴, NBT 경로. */
        DESCRIBE
    }

    private static final PlainTextSerializer DEFAULT = builder().build();

    private final Locale locale;
    private final TranslatableMode translatables;
    private final KeybindMode keybinds;
    private final DynamicMode dynamic;

    private PlainTextSerializer(Builder b) {
        this.locale = b.locale;
        this.translatables = b.translatables;
        this.keybinds = b.keybinds;
        this.dynamic = b.dynamic;
    }

    /** 기본 설정(서버 언어로 번역, Text.plainText 와 같다). */
    public static PlainTextSerializer plain() { return DEFAULT; }

    public static Builder builder() { return new Builder(); }

    /** 자식까지 이어 붙인 평문. */
    public String serialize(Text text) {
        StringBuilder sb = new StringBuilder();
        append(text, sb);
        return sb.toString();
    }

    private void append(Text t, StringBuilder sb) {
        switch (t.content()) {
            case Content.Literal l -> sb.append(l.text());
            case Content.Translatable tr -> {
                List<String> args = new ArrayList<>(tr.args().size());
                for (Object a : tr.args()) args.add(a instanceof Text at ? serialize(at) : String.valueOf(a));
                switch (translatables) {
                    case TRANSLATE -> sb.append(Translators.format(tr.key(), tr.fallback(), args, locale));
                    case KEY -> sb.append(tr.key());
                    case KEY_WITH_ARGS -> {
                        sb.append(tr.key());
                        if (!args.isEmpty()) sb.append('[').append(String.join(", ", args)).append(']');
                    }
                }
            }
            case Content.Keybind k -> {
                switch (keybinds) {
                    case TRANSLATE -> sb.append(Translators.format(k.keybind(), null, List.of(), locale));
                    case KEY -> sb.append(k.keybind());
                    case BRACKETED -> sb.append('[').append(k.keybind()).append(']');
                }
            }
            case Content.Score s -> {
                if (dynamic == DynamicMode.DESCRIBE) sb.append(s.name()).append(':').append(s.objective());
            }
            case Content.Selector s -> {
                if (dynamic == DynamicMode.DESCRIBE) sb.append(s.pattern());
            }
            case Content.Nbt n -> {
                if (dynamic == DynamicMode.DESCRIBE) sb.append(n.path());
            }
        }
        for (Text c : t.children()) append(c, sb);
    }

    /** 옵션 빌더. */
    public static final class Builder {
        private Locale locale = Translators.SERVER_LOCALE;
        private TranslatableMode translatables = TranslatableMode.TRANSLATE;
        private KeybindMode keybinds = KeybindMode.TRANSLATE;
        private DynamicMode dynamic = DynamicMode.EMPTY;

        Builder() {}

        public Builder locale(Locale l) { locale = l; return this; }

        public Builder translatables(TranslatableMode m) { translatables = m; return this; }

        /** translatables(TRANSLATE) 또는 (KEY) 의 줄임. */
        public Builder translate(boolean translate) { translatables = translate ? TranslatableMode.TRANSLATE : TranslatableMode.KEY; return this; }

        public Builder keybinds(KeybindMode m) { keybinds = m; return this; }

        public Builder dynamicContent(DynamicMode m) { dynamic = m; return this; }

        public PlainTextSerializer build() { return new PlainTextSerializer(this); }
    }
}
