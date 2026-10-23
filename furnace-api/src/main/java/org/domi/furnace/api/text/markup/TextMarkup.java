// Furnace 텍스트 마크업 — MiniMessage 4.17 문법과 호환되는 해석기/직렬화기. TextMarkup.markup().deserialize("<red>안녕 <bold>세상") 처럼 쓴다.
// 한 번 훑는 수제 파서(정규식 없음)이며, 인스턴스는 불변이라 스레드 안전하다.
package org.domi.furnace.api.text.markup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.domi.furnace.api.text.Text;

public final class TextMarkup {

    private static final TextMarkup DEFAULT = builder().build();

    private final TagResolver tags;
    private final boolean strict;
    private final UnaryOperator<String> preProcessor;
    private final UnaryOperator<Text> postProcessor;
    private final boolean closeAllTags;
    private final Cache cache;

    private TextMarkup(Builder b) {
        this.tags = b.tags;
        this.strict = b.strict;
        this.preProcessor = b.preProcessor;
        this.postProcessor = b.postProcessor;
        this.closeAllTags = b.closeAllTags;
        this.cache = b.cacheSize > 0 ? new Cache(b.cacheSize) : null;
    }

    /** 기본 인스턴스: 표준 태그, 관대한 모드, 캐시 없음. */
    public static TextMarkup markup() { return DEFAULT; }

    public static Builder builder() { return new Builder(); }

    public boolean strict() { return strict; }

    public TagResolver tags() { return tags; }

    // ------------------------------------------------------------------ 해석

    /**
     * 마크업을 Text 로 바꾼다. resolvers(플레이스홀더·사용자 태그)는 기본 태그보다 먼저 쓰인다.
     * 플레이스홀더 없이 부르면 캐시(켜 둔 경우)를 쓴다.
     *
     * @throws MarkupParseException 태그 인자가 잘못됐거나 strict 모드에서 태그가 어긋났을 때
     */
    public Text deserialize(String input, TagResolver... resolvers) {
        Objects.requireNonNull(input, "input");
        if (cache != null && resolvers.length == 0) {
            Text hit = cache.get(input);
            if (hit != null) return hit;
            Text t = parse(input, tags);
            cache.put(input, t);
            return t;
        }
        return parse(input, resolvers.length == 0 ? tags : combine(resolvers));
    }

    private TagResolver combine(TagResolver[] extra) {
        TagResolver[] all = java.util.Arrays.copyOf(extra, extra.length + 1);
        all[extra.length] = tags;
        return TagResolver.resolver(all);
    }

    private Text parse(String input, TagResolver resolver) {
        String in = preProcessor != null ? preProcessor.apply(input) : input;
        Text t = MarkupParser.parseFull(in, resolver, strict, 0);
        return postProcessor != null ? postProcessor.apply(t) : t;
    }

    // ------------------------------------------------------------------ 직렬화·도구

    /** Text 를 마크업으로. 다시 해석하면 글자와 글자별 스타일이 같다. 닫는 태그는 중간에서만 쓰고 맨 끝은 생략한다(옵션으로 전부 닫기). */
    public String serialize(Text text) { return MarkupWriter.write(text, closeAllTags); }

    /** 입력 안의 모든 알려진 태그를 글자로 보이게 이스케이프한다: deserialize(escapeTags(s)).plainText() == s. */
    public String escapeTags(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 8);
        MarkupWriter.escape(sb, input);
        return sb.toString();
    }

    /** 이스케이프하되 사용자 해석기의 태그도 포함하는 호환용 오버로드(현재는 모든 '<' 를 이스케이프하므로 결과가 같다). */
    public String escapeTags(String input, TagResolver... resolvers) { return escapeTags(input); }

    /** 알려진 태그를 모두 지우고 글자만 남긴다(이스케이프는 풀린다). <pre> 안은 그대로 남는다. */
    public String stripTags(String input, TagResolver... resolvers) {
        TagResolver r = resolvers.length == 0 ? tags : combine(resolvers);
        TagScanner sc = new TagScanner();
        StringBuilder sb = new StringBuilder(input.length());
        int n = input.length(), i = 0;
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\\') {
                if (i + 1 < n && (input.charAt(i + 1) == '<' || input.charAt(i + 1) == '\\')) {
                    sb.append(input.charAt(i + 1));
                    i += 2;
                } else {
                    sb.append(c);
                    i++;
                }
                continue;
            }
            if (c == '<' && sc.scan(input, i)) {
                String name = sc.name(input);
                if (sc.kind != TagScanner.CLOSE && name.equals("pre")) {
                    int close = MarkupParser.indexOfIgnoreCase(input, "</pre>", sc.end);
                    if (close < 0) {
                        sb.append(input, sc.end, n);
                        i = n;
                    } else {
                        sb.append(input, sc.end, close);
                        i = close + 6;
                    }
                    continue;
                }
                if (r.has(name) || name.equals("pre")) {
                    i = sc.end;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 태그를 지운 글자만, 기본 태그로. */
    public String stripTags(String input) { return stripTags(input, new TagResolver[0]); }

    // ------------------------------------------------------------------ 템플릿

    /** 슬롯 이름들을 정해 템플릿으로 컴파일한다: 이후 fill 은 다시 해석하지 않는다. */
    public TextTemplate template(String markup, String... slots) {
        TextTemplate.Builder b = TextTemplate.builder(this);
        for (String s : slots) b.slot(s);
        return b.compile(markup);
    }

    // ------------------------------------------------------------------ 캐시

    /** 접근 순서 LRU(동기화). */
    private static final class Cache {
        private final Map<String, Text> map;

        Cache(int max) {
            this.map = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Text> e) { return size() > max; }
            };
        }

        synchronized Text get(String k) { return map.get(k); }

        synchronized void put(String k, Text v) { map.put(k, v); }
    }

    // ------------------------------------------------------------------ 빌더

    /** 옵션 빌더. */
    public static final class Builder {
        private TagResolver tags = TagResolver.standard();
        private boolean strict;
        private UnaryOperator<String> preProcessor;
        private UnaryOperator<Text> postProcessor;
        private boolean closeAllTags;
        private int cacheSize;

        Builder() {}

        /** 쓸 태그 집합을 통째로 바꾼다(기본: 표준 태그). */
        public Builder tags(TagResolver r) { tags = Objects.requireNonNull(r); return this; }

        /** 표준 태그에 더한다(사용자 태그가 우선). */
        public Builder editTags(Consumer<TagResolver.Builder> edit) {
            TagResolver.Builder b = TagResolver.builder();
            edit.accept(b);
            tags = TagResolver.resolver(b.build(), tags);
            return this;
        }

        /** true 면 닫히지 않은 태그·짝 없는 닫는 태그·순서가 어긋난 닫기가 예외다(기본 false: 자동으로 닫는다). */
        public Builder strict(boolean strict) { this.strict = strict; return this; }

        /** 해석 전에 입력 문자열을 가공한다(최상위 입력에만 적용). */
        public Builder preProcessor(UnaryOperator<String> f) { preProcessor = f; return this; }

        /** 해석 뒤 결과 Text 를 가공한다(최상위에만 적용). */
        public Builder postProcessor(UnaryOperator<Text> f) { postProcessor = f; return this; }

        /** serialize 가 맨 끝의 닫는 태그까지 모두 쓴다(엄격 모드로 다시 해석할 문자열이 필요할 때). */
        public Builder closeAllTags(boolean v) { closeAllTags = v; return this; }

        /** 플레이스홀더 없는 입력의 해석 결과를 최대 n 개까지 LRU 로 캐시한다(0 이면 끔). */
        public Builder cache(int maxEntries) { cacheSize = Math.max(0, maxEntries); return this; }

        public TextMarkup build() { return new TextMarkup(this); }
    }
}
