// 텍스트 치환 설정 — 무엇을(정규식/글자) 무엇으로(텍스트/함수) 몇 번까지, 조건은 무엇인지. Text.replaceText 에 넘긴다. 불변.
package org.domi.furnace.api.text;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class TextReplacement {

    /** 조건 판정 결과. */
    public enum Result {
        /** 이 일치를 치환한다. */
        REPLACE,
        /** 이 일치만 건너뛴다(계속 찾는다). */
        SKIP,
        /** 여기서 치환을 끝낸다. */
        STOP
    }

    /** 일치마다 치환 여부를 정한다. matchCount 는 지금까지 찾은 일치 수(이번 것 포함), replaced 는 이미 치환한 수. */
    @FunctionalInterface
    public interface Condition {
        Result test(MatchResult match, int matchCount, int replaced);
    }

    final Pattern pattern;
    final String literal;
    final Function<MatchResult, Text> replacement;
    final int limit; // 음수면 제한 없음
    final Condition condition;

    private TextReplacement(Pattern pattern, String literal, Function<MatchResult, Text> replacement, int limit, Condition condition) {
        this.pattern = pattern;
        this.literal = literal;
        this.replacement = replacement;
        this.limit = limit;
        this.condition = condition;
    }

    public static Builder builder() { return new Builder(); }

    /** 글자 그대로 찾아 바꾸는 가장 흔한 형태(정규식을 쓰지 않아 빠르다). */
    public static TextReplacement literal(String match, Text replacement) {
        return builder().matchLiteral(match).replacement(replacement).build();
    }

    /** 빌더. match 와 replacement 는 꼭 정해야 한다. */
    public static final class Builder {
        private Pattern pattern;
        private String literal;
        private Function<MatchResult, Text> replacement;
        private int limit = -1;
        private Condition condition;

        Builder() {}

        public Builder match(Pattern p) { pattern = Objects.requireNonNull(p); literal = null; return this; }

        public Builder match(String regex) { return match(Pattern.compile(regex)); }

        /** 정규식 없이 글자 그대로(빈 문자열은 안 된다). */
        public Builder matchLiteral(String s) {
            if (s.isEmpty()) throw new IllegalArgumentException("빈 문자열은 찾을 수 없다");
            literal = s;
            pattern = null;
            return this;
        }

        public Builder replacement(Text t) { Objects.requireNonNull(t); replacement = m -> t; return this; }

        public Builder replacement(String s) { return replacement(Text.text(s)); }

        /** 일치마다 텍스트를 만든다. null 을 돌려주면 그 일치는 그대로 둔다. 인자 MatchResult 는 호출 동안만 유효하다. */
        public Builder replacement(Function<MatchResult, Text> f) { replacement = Objects.requireNonNull(f); return this; }

        /** 처음 한 번만 치환한다. */
        public Builder once() { limit = 1; return this; }

        /** 최대 n 번만 치환한다(0 이면 아무것도 안 바꾼다). */
        public Builder times(int n) {
            if (n < 0) throw new IllegalArgumentException("n < 0");
            limit = n;
            return this;
        }

        public Builder condition(Condition c) { condition = c; return this; }

        public TextReplacement build() {
            if (pattern == null && literal == null) throw new IllegalStateException("match 가 없다");
            if (replacement == null) throw new IllegalStateException("replacement 가 없다");
            return new TextReplacement(pattern, literal, replacement, limit, condition);
        }
    }
}
