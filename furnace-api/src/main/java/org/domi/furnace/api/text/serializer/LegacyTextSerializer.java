// 레거시 서식 코드(§ 또는 &) ↔ Text 변환기 — Adventure LegacyComponentSerializer 와 같은 동작.
// 색 코드는 꾸밈을 초기화하고(바닐라와 같다), r 은 전부 초기화한다. 16진 색은 &#RRGGBB 와 &x&R&R&G&G&B&B 두 형식을 모두 읽는다.
package org.domi.furnace.api.text.serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.Texts;
import org.domi.furnace.api.text.Translators;

public final class LegacyTextSerializer {

    /** 주소 찾기 기본 패턴(Adventure 와 같다): 스킴은 선택, 없으면 https:// 를 붙인다. */
    public static final Pattern DEFAULT_URL_PATTERN = Pattern.compile("(?:(https?)://)?([-\\w_.]{2,}\\.[a-z]{2,4})(/\\S*)?");

    private static final LegacyTextSerializer SECTION = builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyTextSerializer AMPERSAND = builder().character('&').hexColors().build();

    private final char character;
    private final char hexCharacter;
    private final boolean hexColors;
    private final boolean unusualX;
    private final Pattern urlPattern;   // null 이면 주소 추출 안 함
    private final Style urlStyle;
    private final String urlScheme;   // 스킴 없는 주소 앞에 붙일 것(기본 "https://")
    private final Locale locale;

    private LegacyTextSerializer(Builder b) {
        this.character = b.character;
        this.hexCharacter = b.hexCharacter;
        this.hexColors = b.hexColors;
        this.unusualX = b.unusualX;
        this.urlPattern = b.urlPattern;
        this.urlStyle = b.urlStyle;
        this.urlScheme = b.urlScheme;
        this.locale = b.locale;
    }

    /** § 코드, 16진은 §x§R§R§G§G§B§B 로 쓴다(바닐라 방식). */
    public static LegacyTextSerializer section() { return SECTION; }

    /** & 코드, 16진은 &#RRGGBB 로 쓴다. */
    public static LegacyTextSerializer ampersand() { return AMPERSAND; }

    public static Builder builder() { return new Builder(); }

    // ------------------------------------------------------------------ 읽기

    /**
     * 레거시 문자열을 Text 로. 서식 코드가 없으면 글자 하나짜리 Text, 있으면 서식 구간마다 자식을 가진 빈 그릇이다.
     * 알 수 없는 코드(예: "&z")는 글자 그대로 남는다.
     */
    public Text deserialize(String input) {
        int n = input.length();
        if (urlPattern == null && input.indexOf(character) < 0) return Text.text(input);
        List<Text> parts = new ArrayList<>();
        StringBuilder seg = new StringBuilder();
        Style cur = Style.empty();
        int i = 0;
        while (i < n) {
            char c = input.charAt(i);
            if (c == character && i + 1 < n) {
                char d = input.charAt(i + 1);
                TextColor color = null;
                TextDecoration deco = null;
                boolean reset = false;
                int len = 0;
                if (hexColors && d == hexCharacter && i + 8 <= n && (color = hex6(input, i + 2)) != null) {
                    len = 8;
                } else if (hexColors && (d == 'x' || d == 'X') && i + 14 <= n && (color = xHex(input, i + 2)) != null) {
                    len = 14;
                } else if ((color = NamedTextColor.byLegacyCode(d)) != null) {
                    len = 2;
                } else if ((deco = decoration(d)) != null) {
                    len = 2;
                } else if (d == 'r' || d == 'R') {
                    reset = true;
                    len = 2;
                }
                if (len > 0) {
                    flush(seg, cur, parts);
                    if (color != null) cur = Style.style(color); // 색 코드는 꾸밈을 초기화한다
                    else if (deco != null) cur = cur.decoration(deco, true);
                    else if (reset) cur = Style.empty();
                    i += len;
                    continue;
                }
            }
            seg.append(c);
            i++;
        }
        flush(seg, cur, parts);
        return switch (parts.size()) {
            case 0 -> Text.empty();
            case 1 -> parts.get(0);
            default -> Text.of(new Content.Literal(""), Style.empty(), parts);
        };
    }

    private void flush(StringBuilder seg, Style style, List<Text> parts) {
        if (seg.length() == 0) return;
        String s = seg.toString();
        seg.setLength(0);
        if (urlPattern == null) {
            parts.add(Text.text(s, style));
            return;
        }
        Matcher m = urlPattern.matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) parts.add(Text.text(s.substring(last, m.start()), style));
            String link = m.group();
            String url = m.groupCount() >= 1 && m.group(1) == null ? urlScheme + link : link;
            Style linkStyle = style.merge(urlStyle).clickEvent(ClickEvent.openUrl(url));
            parts.add(Text.text(link, linkStyle));
            last = m.end();
        }
        if (last == 0) parts.add(Text.text(s, style));
        else if (last < s.length()) parts.add(Text.text(s.substring(last), style));
    }

    private static TextDecoration decoration(char d) {
        return switch (Character.toLowerCase(d)) {
            case 'k' -> TextDecoration.OBFUSCATED;
            case 'l' -> TextDecoration.BOLD;
            case 'm' -> TextDecoration.STRIKETHROUGH;
            case 'n' -> TextDecoration.UNDERLINED;
            case 'o' -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    private static TextColor hex6(String s, int at) {
        int v = 0;
        for (int k = 0; k < 6; k++) {
            int d = Character.digit(s.charAt(at + k), 16);
            if (d < 0) return null;
            v = v << 4 | d;
        }
        return TextColor.color(v);
    }

    /** "§x§R§R§G§G§B§B" 의 R..B 부분(앞의 x 는 이미 읽음). */
    private TextColor xHex(String s, int at) {
        int v = 0;
        for (int k = 0; k < 6; k++) {
            if (s.charAt(at + k * 2) != character) return null;
            int d = Character.digit(s.charAt(at + k * 2 + 1), 16);
            if (d < 0) return null;
            v = v << 4 | d;
        }
        return TextColor.color(v);
    }

    // ------------------------------------------------------------------ 쓰기

    /**
     * Text 를 레거시 문자열로. 꾸밈이 사라지거나 색이 없어지면 r 로 초기화한 뒤 필요한 코드를 다시 쓴다.
     * 이벤트·글꼴 등 레거시로 표현할 수 없는 스타일은 버린다. 번역 텍스트는 서버 언어로 번역해 쓴다.
     */
    public String serialize(Text text) {
        StringBuilder sb = new StringBuilder();
        TextColor[] color = new TextColor[1];
        boolean[] on = new boolean[TextDecoration.values().length];
        Texts.forEachSegment(text, (node, eff) -> {
            String content = Texts.plainContent(node, locale);
            if (content.isEmpty()) return;
            TextColor col = eff.color();
            if (col != null && !hexColors) col = NamedTextColor.nearestTo(col);
            boolean needReset = color[0] != null && col == null;
            for (TextDecoration d : TextDecoration.values()) if (on[d.ordinal()] && !eff.hasDecoration(d)) needReset = true;
            if (needReset) {
                sb.append(character).append('r');
                color[0] = null;
                java.util.Arrays.fill(on, false);
            }
            if (col != null && !col.equals(color[0])) {
                appendColor(sb, col);
                color[0] = col;
                java.util.Arrays.fill(on, false); // 색 코드는 꾸밈을 초기화한다
            }
            for (TextDecoration d : TextDecoration.values()) {
                if (eff.hasDecoration(d) && !on[d.ordinal()]) {
                    sb.append(character).append(d.legacyCode());
                    on[d.ordinal()] = true;
                }
            }
            sb.append(content);
        });
        return sb.toString();
    }

    private void appendColor(StringBuilder sb, TextColor c) {
        if (c.isNamed()) {
            sb.append(character).append(NamedTextColor.VALUES.indexOf(c) < 0 ? 'f' : Character.forDigit(NamedTextColor.VALUES.indexOf(c), 16));
            return;
        }
        String hex = String.format("%06x", c.value());
        if (unusualX) {
            sb.append(character).append('x');
            for (int i = 0; i < 6; i++) sb.append(character).append(hex.charAt(i));
        } else {
            sb.append(character).append(hexCharacter).append(hex);
        }
    }

    // ------------------------------------------------------------------ 빌더

    /** 옵션 빌더. */
    public static final class Builder {
        private char character = '§';
        private char hexCharacter = '#';
        private boolean hexColors;
        private boolean unusualX;
        private Pattern urlPattern;
        private Style urlStyle = Style.empty();
        private String urlScheme = "https://";
        private Locale locale = Translators.SERVER_LOCALE;

        Builder() {}

        /** 서식 문자(기본 §). */
        public Builder character(char c) { character = c; return this; }

        /** 16진 색 표시 문자(기본 '#'): &#RRGGBB. */
        public Builder hexCharacter(char c) { hexCharacter = c; return this; }

        /** 16진 색을 읽고 쓴다. 끄면 쓸 때 가장 가까운 16색으로 줄이고, 읽을 때 16진 코드는 무시한다. */
        public Builder hexColors() { hexColors = true; return this; }

        public Builder hexColors(boolean v) { hexColors = v; return this; }

        /** 16진 색을 §x§R§R§G§G§B§B 형식으로 쓴다(읽기는 두 형식 모두 된다). */
        public Builder useUnusualXRepeatedCharacterHexFormat() { unusualX = true; return this; }

        public Builder useUnusualXRepeatedCharacterHexFormat(boolean v) { unusualX = v; return this; }

        /** 읽을 때 글 속 주소를 찾아 클릭하면 열리게 한다(기본 패턴, 추가 스타일 없음). */
        public Builder extractUrls() { return extractUrls(DEFAULT_URL_PATTERN, Style.empty()); }

        /** 주소 부분에 style 을 더해 입힌다. */
        public Builder extractUrls(Style style) { return extractUrls(DEFAULT_URL_PATTERN, style); }

        /** 패턴의 그룹 1 이 비어 있으면 앞에 https:// 를 붙인다. */
        public Builder extractUrls(Pattern pattern, Style style) {
            urlPattern = pattern;
            urlStyle = style == null ? Style.empty() : style;
            return this;
        }

        /** 스킴 없는 주소의 클릭 값 앞에 붙일 것(기본 "https://", CraftBukkit 은 "http://"). */
        public Builder urlScheme(String scheme) {
            urlScheme = java.util.Objects.requireNonNull(scheme, "scheme");
            return this;
        }

        /** 번역 텍스트를 쓸 때 쓸 언어. */
        public Builder locale(Locale l) { locale = l; return this; }

        public LegacyTextSerializer build() { return new LegacyTextSerializer(this); }
    }
}
