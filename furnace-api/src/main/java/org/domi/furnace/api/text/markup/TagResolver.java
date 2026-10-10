// 태그 해석기 — 태그 이름과 인자를 받아 Tag 를 돌려준다(모르는 이름이면 null → 글자 그대로 남는다). 표준 태그·플레이스홀더·사용자 태그를 모두 이걸로 만든다.
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

@FunctionalInterface
public interface TagResolver {

    /**
     * @param name 소문자로 바뀐 태그 이름("red", "#ff0000", "!bold" …)
     * @return 해석한 태그, 이 해석기가 모르는 이름이면 null
     * @throws MarkupParseException 인자가 잘못됐을 때
     */
    Tag resolve(String name, ArgumentQueue args, Context ctx);

    /** 이 이름을 아는 해석기인가(닫는 태그·escape/strip 판단용). 람다 해석기는 빈 인자로 한 번 시험해 본다. */
    default boolean has(String name) {
        try {
            return resolve(name, ArgumentQueue.empty(), Resolvers.PROBE) != null;
        } catch (RuntimeException e) {
            return true; // 인자가 모자라 실패했다면 태그는 존재한다
        }
    }

    /**
     * 이 이름이 "원문 치환" 플레이스홀더(Placeholder.parsed)면 치환할 값, 아니면 null. 태그 인자 안의 플레이스홀더까지 풀기 위해
     * 해석 전에 한 번 훑는 데 쓴다. 보통은 구현할 필요가 없다.
     */
    default String preProcessValue(String name) { return null; }

    /** preProcessValue 로 답할 수 있는 이름이 하나라도 있나(없으면 해석 전 훑기를 건너뛴다). */
    default boolean hasPreProcess() { return false; }

    // ------------------------------------------------------------------ 만들기

    /** MiniMessage 4.17 표준 태그 전부. */
    static TagResolver standard() { return StandardTags.INSTANCE; }

    /** 아무것도 모르는 해석기. */
    static TagResolver empty() { return Resolvers.EMPTY; }

    /** 이름 하나에 고정 태그. */
    static TagResolver resolver(String name, Tag tag) { return new Resolvers.Named(checkName(name), tag); }

    /** 이름 하나에 인자를 받아 태그를 만드는 함수. */
    static TagResolver resolver(String name, BiFunction<ArgumentQueue, Context, Tag> handler) {
        return new Resolvers.Func(Set.of(checkName(name)), handler);
    }

    /** 여러 이름(별칭)에 같은 함수. */
    static TagResolver resolver(Set<String> names, BiFunction<ArgumentQueue, Context, Tag> handler) {
        List<String> checked = new ArrayList<>(names.size());
        for (String n : names) checked.add(checkName(n));
        return new Resolvers.Func(Set.copyOf(checked), handler);
    }

    /** 앞에 있는 것이 우선하는 묶음. */
    static TagResolver resolver(TagResolver... resolvers) { return Resolvers.compose(resolvers); }

    static TagResolver resolver(Iterable<? extends TagResolver> resolvers) {
        List<TagResolver> l = new ArrayList<>();
        for (TagResolver r : resolvers) l.add(r);
        return Resolvers.compose(l.toArray(new TagResolver[0]));
    }

    static Builder builder() { return new Builder(); }

    /** 태그 이름 규칙 검사 + 소문자화: 영문 소문자·숫자·_ ! ? - 만 허용. */
    static String checkName(String name) {
        if (name.isEmpty()) throw new IllegalArgumentException("태그 이름이 비었다");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '!' || c == '?' || c == '-';
            if (!ok) throw new IllegalArgumentException("태그 이름은 소문자·숫자·_!?- 만 쓸 수 있다: " + name);
        }
        return name;
    }

    /** 해석기를 모으는 빌더. */
    final class Builder {
        private final List<TagResolver> list = new ArrayList<>();

        Builder() {}

        public Builder resolver(TagResolver r) { list.add(r); return this; }

        public Builder resolvers(TagResolver... rs) { for (TagResolver r : rs) list.add(r); return this; }

        public Builder tag(String name, Tag tag) { list.add(TagResolver.resolver(name, tag)); return this; }

        public Builder tag(String name, BiFunction<ArgumentQueue, Context, Tag> handler) {
            list.add(TagResolver.resolver(name, handler));
            return this;
        }

        public Builder tag(Set<String> names, BiFunction<ArgumentQueue, Context, Tag> handler) {
            list.add(TagResolver.resolver(names, handler));
            return this;
        }

        public TagResolver build() { return Resolvers.compose(list.toArray(new TagResolver[0])); }
    }
}
