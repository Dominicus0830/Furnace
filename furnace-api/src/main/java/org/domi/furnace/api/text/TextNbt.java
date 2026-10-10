// 플레이 단계 텍스트 NBT 인코딩 — 바닐라 ComponentSerialization + NbtOps 와 같은 바이트.
// 규칙: 스타일·자식 없는 글자는 String 태그로 접힘, CompoundTag 는 HashMap 순서(같은 삽입 순서의 HashMap 으로 재현),
// 리스트는 원소 종류가 같으면 그 종류의 리스트(byte·int·long 만으로 된 리스트는 배열 태그), 섞이면 compound 가 아닌 원소를 {"": 값} 으로 감싼다(NbtOps 이종 리스트).
package org.domi.furnace.api.text;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TextNbt {

    private static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9,
            COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private TextNbt() {}

    /** NBT 리스트 값(원소 태그 종류 + 원소들). */
    private record ListTag(byte type, List<Object> items) {}

    /** {"": 값} 감싸기(NbtOps 이종 리스트). */
    private static final class Wrapper extends HashMap<String, Object> {}

    static byte[] encode(Text t) {
        Object tag = toTag(t);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeByte(typeOf(tag));
            writePayload(out, tag);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    // ------------------------------------------------------------------ Text → 태그 트리

    private static Object toTag(Text t) {
        String collapsed = t.collapseToString();
        if (collapsed != null) return collapsed;
        Map<String, Object> m = new HashMap<>();
        switch (t.content()) {
            case Content.Literal l -> m.put("text", l.text());
            case Content.Translatable tr -> {
                m.put("translate", tr.key());
                if (tr.fallback() != null) m.put("fallback", tr.fallback());
                if (!tr.args().isEmpty()) {
                    List<Object> args = new ArrayList<>(tr.args().size());
                    for (Object a : tr.args()) args.add(argTag(a));
                    m.put("with", list(args));
                }
            }
            case Content.Keybind k -> m.put("keybind", k.keybind());
            case Content.Score s -> {
                Map<String, Object> sc = new HashMap<>();
                sc.put("name", s.name());
                sc.put("objective", s.objective());
                m.put("score", sc);
            }
            case Content.Selector s -> {
                m.put("selector", s.pattern());
                if (s.separator() != null) m.put("separator", toTag(s.separator()));
            }
            case Content.Nbt n -> {
                m.put("nbt", n.path());
                if (n.interpret()) m.put("interpret", (byte) 1);
                if (n.separator() != null) m.put("separator", toTag(n.separator()));
                switch (n.source()) {
                    case Content.NbtSource.Block b -> m.put("block", b.pos());
                    case Content.NbtSource.Entity e -> m.put("entity", e.selector());
                    case Content.NbtSource.Storage st -> m.put("storage", st.id());
                }
            }
        }
        putStyle(t.style(), m);
        if (!t.children().isEmpty()) {
            List<Object> extra = new ArrayList<>(t.children().size());
            for (Text c : t.children()) extra.add(toTag(c));
            m.put("extra", list(extra));
        }
        return m;
    }

    private static Object argTag(Object a) {
        if (a instanceof Text t) return toTag(t);
        if (a instanceof Boolean b) return (byte) (b ? 1 : 0);
        return a;   // String·Integer·Long·Float·Double·Short·Byte
    }

    private static void putStyle(Style s, Map<String, Object> m) {
        if (s.isEmpty()) return;
        if (s.color() != null) m.put("color", s.color().serialize());
        bool("bold", s.decoration(TextDecoration.BOLD), m);
        bool("italic", s.decoration(TextDecoration.ITALIC), m);
        bool("underlined", s.decoration(TextDecoration.UNDERLINED), m);
        bool("strikethrough", s.decoration(TextDecoration.STRIKETHROUGH), m);
        bool("obfuscated", s.decoration(TextDecoration.OBFUSCATED), m);
        if (s.clickEvent() != null) {
            Map<String, Object> c = new HashMap<>();
            c.put("action", s.clickEvent().action().key());
            c.put("value", s.clickEvent().value());
            m.put("clickEvent", c);
        }
        if (s.hoverEvent() != null) {
            Map<String, Object> h = new HashMap<>();
            h.put("action", s.hoverEvent().action());
            switch (s.hoverEvent()) {
                case HoverEvent.ShowText st -> h.put("contents", toTag(st.text()));
                case HoverEvent.ShowItem si -> {
                    Map<String, Object> it = new HashMap<>();
                    it.put("id", si.item());
                    if (si.count() != 1) it.put("count", si.count());
                    h.put("contents", it);
                }
                case HoverEvent.ShowEntity se -> {
                    Map<String, Object> e = new HashMap<>();
                    e.put("type", se.type());
                    e.put("id", TextJson.uuidInts(se.id()));
                    if (se.name() != null) e.put("name", toTag(se.name()));
                    h.put("contents", e);
                }
            }
            m.put("hoverEvent", h);
        }
        if (s.insertion() != null) m.put("insertion", s.insertion());
        if (s.font() != null) m.put("font", s.font());
    }

    private static void bool(String name, TriState v, Map<String, Object> m) {
        if (v != TriState.NOT_SET) m.put(name, (byte) (v == TriState.TRUE ? 1 : 0));
    }

    /** NbtOps 리스트 수집: 모두 같은 종류면 그 종류, 섞이면 compound 가 아닌 원소(와 감싸개처럼 보이는 compound)를 감싼다. */
    private static ListTag list(List<Object> items) {
        if (items.isEmpty()) return new ListTag(END, items);
        byte type = typeOf(items.get(0));
        boolean same = true;
        for (Object o : items) if (typeOf(o) != type) { same = false; break; }
        if (same) {
            // NbtOps: 같은 종류의 byte·int·long 리스트는 배열 태그가 된다.
            if (type == BYTE) {
                byte[] a = new byte[items.size()];
                for (int i = 0; i < a.length; i++) a[i] = (Byte) items.get(i);
                return new ListTag(BYTE_ARRAY, List.of(a));
            }
            if (type == INT) return new ListTag(INT_ARRAY, List.of(items.stream().mapToInt(o -> (Integer) o).toArray()));
            if (type == LONG) return new ListTag(LONG_ARRAY, List.of(items.stream().mapToLong(o -> (Long) o).toArray()));
            return new ListTag(type, items);
        }
        List<Object> wrapped = new ArrayList<>(items.size());
        for (Object o : items) {
            if (o instanceof Map<?, ?> c && !isWrapper(c)) {
                wrapped.add(c);
            } else {
                Wrapper w = new Wrapper();
                w.put("", o);
                wrapped.add(w);
            }
        }
        return new ListTag(COMPOUND, wrapped);
    }

    private static boolean isWrapper(Map<?, ?> c) { return c.size() == 1 && c.containsKey(""); }

    // ------------------------------------------------------------------ 태그 트리 → 바이트

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof String) return STRING;
        if (v instanceof ListTag l) return l.type() == BYTE_ARRAY || l.type() == INT_ARRAY || l.type() == LONG_ARRAY ? l.type() : LIST;
        if (v instanceof Map) return COMPOUND;
        if (v instanceof int[]) return INT_ARRAY;
        throw new IllegalArgumentException("NBT 로 쓸 수 없는 값: " + v);
    }

    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (v) {
            case Byte b -> out.writeByte(b);
            case Short s -> out.writeShort(s);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case Float f -> out.writeFloat(f);
            case Double d -> out.writeDouble(d);
            case String s -> out.writeUTF(s);
            case int[] a -> {
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
            }
            case ListTag l when l.type() == BYTE_ARRAY -> {
                byte[] a = (byte[]) l.items().get(0);
                out.writeInt(a.length);
                out.write(a);
            }
            case ListTag l when l.type() == INT_ARRAY -> writePayload(out, l.items().get(0));
            case ListTag l when l.type() == LONG_ARRAY -> {
                long[] a = (long[]) l.items().get(0);
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
            }
            case ListTag l -> {
                out.writeByte(l.type());
                out.writeInt(l.items().size());
                for (Object o : l.items()) writePayload(out, o);
            }
            case Map<?, ?> m -> {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF((String) e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(END);
            }
            default -> throw new IllegalArgumentException("NBT 로 쓸 수 없는 값: " + v);
        }
    }
}
