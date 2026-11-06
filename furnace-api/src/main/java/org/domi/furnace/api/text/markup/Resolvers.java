// 해석기 구현 모음(패키지 내부): 이름 하나·함수·묶음·빈 해석기와 존재 확인용 문맥.
package org.domi.furnace.api.text.markup;

import java.util.Set;
import java.util.function.BiFunction;
import org.domi.furnace.api.text.Text;

final class Resolvers {

    private Resolvers() {}

    static final TagResolver EMPTY = new TagResolver() {
        @Override public Tag resolve(String name, ArgumentQueue args, Context ctx) { return null; }

        @Override public boolean has(String name) { return false; }
    };

    /** has() 시험용 문맥 — 인자 해석은 할 수 없다. */
    static final Context PROBE = new Context() {
        @Override public Text deserialize(String markup) { throw newException("probe"); }

        @Override public Text deserialize(String markup, TagResolver... extra) { throw newException("probe"); }

        @Override public MarkupParseException newException(String message) { return new MarkupParseException(message); }

        @Override public MarkupParseException newException(String message, ArgumentQueue args) { return new MarkupParseException(message); }

        @Override public String originalMessage() { return ""; }
    };

    /** 이름 하나 → 고정 태그. */
    static final class Named implements TagResolver {
        private final String name;
        private final Tag tag;

        Named(String name, Tag tag) {
            this.name = name;
            this.tag = tag;
        }

        @Override public Tag resolve(String n, ArgumentQueue args, Context ctx) { return name.equals(n) ? tag : null; }

        @Override public boolean has(String n) { return name.equals(n); }

        @Override
        public String preProcessValue(String n) {
            return name.equals(n) && tag instanceof Tag.PreProcess p ? p.value() : null;
        }

        @Override public boolean hasPreProcess() { return tag instanceof Tag.PreProcess; }
    }

    /** 이름들 → 함수. */
    static final class Func implements TagResolver {
        private final Set<String> names;
        private final BiFunction<ArgumentQueue, Context, Tag> handler;

        Func(Set<String> names, BiFunction<ArgumentQueue, Context, Tag> handler) {
            this.names = names;
            this.handler = handler;
        }

        @Override public Tag resolve(String n, ArgumentQueue args, Context ctx) { return names.contains(n) ? handler.apply(args, ctx) : null; }

        @Override public boolean has(String n) { return names.contains(n); }
    }

    /** 앞에서부터 처음 답하는 해석기. */
    static final class Composite implements TagResolver {
        private final TagResolver[] list;
        private final boolean pre;

        Composite(TagResolver[] list) {
            this.list = list;
            boolean p = false;
            for (TagResolver r : list) p |= r.hasPreProcess();
            this.pre = p;
        }

        @Override public boolean hasPreProcess() { return pre; }

        @Override
        public Tag resolve(String name, ArgumentQueue args, Context ctx) {
            for (TagResolver r : list) {
                Tag t = r.resolve(name, args, ctx);
                if (t != null) return t;
                args.reset();
            }
            return null;
        }

        @Override
        public boolean has(String name) {
            for (TagResolver r : list) if (r.has(name)) return true;
            return false;
        }

        @Override
        public String preProcessValue(String name) {
            if (!pre) return null;
            for (TagResolver r : list) if (r.hasPreProcess() && r.has(name)) return r.preProcessValue(name);
            return null;
        }
    }

    static TagResolver compose(TagResolver[] rs) {
        if (rs.length == 0) return EMPTY;
        if (rs.length == 1) return rs[0];
        return new Composite(rs.clone());
    }
}
