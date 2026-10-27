// 바닐라 NbtUtils.toPrettyComponent(TextComponentTagVisitor, 들여쓰기 없음) — /data get 이 보여 주는 색칠한 SNBT.
// 키 aqua, 문자열 green, 수 gold, 수 종류 접미사 red.
// 차이: 바닐라 CompoundTag 는 HashMap 이라 키 순서가 해시 순이다. 여기서는 키들로 새 HashMap 을 만들어 그 순서를 쓴다
// (지우기 이력에 따라 바닐라 표 크기가 더 클 수 있어 드물게 순서가 다를 수 있다 — 가정).
package org.domi.furnace.server.command;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.nbt.NbtArray;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class NbtText {
    private NbtText() {}

    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final List<Byte> INLINE = List.of(NbtIo.TAG_BYTE, NbtIo.TAG_SHORT, NbtIo.TAG_INT, NbtIo.TAG_LONG, NbtIo.TAG_FLOAT, NbtIo.TAG_DOUBLE);

    public static Text pretty(Object tag) {
        if (tag instanceof NbtArray a) tag = a.toArray();
        return switch (tag) {
            case String s -> quoted(s, NamedTextColor.GREEN);
            case Byte b -> number(String.valueOf(b), "b");
            case Short s -> number(String.valueOf(s), "s");
            case Integer i -> Text.text(String.valueOf(i)).color(NamedTextColor.GOLD);
            case Long l -> number(String.valueOf(l), "L");
            case Float f -> number(String.valueOf(f), "f");
            case Double d -> number(String.valueOf(d), "d");
            case byte[] a -> {
                Text type = Text.text("B").color(NamedTextColor.RED);
                Text m = Text.text("[").append(type).append(";");
                for (int i = 0; i < a.length; i++) {
                    m = m.append(" ").append(Text.text(String.valueOf(a[i])).color(NamedTextColor.GOLD)).append(type);
                    if (i != a.length - 1) m = m.append(",");
                }
                yield m.append("]");
            }
            case int[] a -> {
                Text type = Text.text("I").color(NamedTextColor.RED);
                Text m = Text.text("[").append(type).append(";");
                for (int i = 0; i < a.length; i++) {
                    m = m.append(" ").append(Text.text(String.valueOf(a[i])).color(NamedTextColor.GOLD));
                    if (i != a.length - 1) m = m.append(",");
                }
                yield m.append("]");
            }
            case long[] a -> {
                Text type = Text.text("L").color(NamedTextColor.RED);
                Text m = Text.text("[").append(type).append(";");
                for (int i = 0; i < a.length; i++) {
                    m = m.append(" ").append(Text.text(String.valueOf(a[i])).color(NamedTextColor.GOLD)).append(type);
                    if (i != a.length - 1) m = m.append(",");
                }
                yield m.append("]");
            }
            case NbtList l -> list(l);
            case NbtCompound c -> compound(c);
            default -> throw new IllegalArgumentException("NBT 값이 아님: " + tag);
        };
    }

    private static Text number(String v, String suffix) {
        return Text.text(v).append(Text.text(suffix).color(NamedTextColor.RED)).color(NamedTextColor.GOLD);
    }

    private static Text list(NbtList l) {
        if (l.size() == 0) return Text.text("[]");
        Text m = Text.text("[");
        if (INLINE.contains(l.elementType()) && l.size() <= 8) {
            for (int i = 0; i < l.size(); i++) {
                if (i != 0) m = m.append(", ");
                m = m.append(pretty(l.get(i)));
            }
        } else {
            for (int i = 0; i < l.size(); i++) {
                Text n = Text.text("").append(pretty(l.get(i)));
                if (i != l.size() - 1) n = n.append(",").append(" ");
                m = m.append(n);
            }
        }
        return m.append("]");
    }

    private static Text compound(NbtCompound c) {
        if (c.tags().isEmpty()) return Text.text("{}");
        Text m = Text.text("{");
        var keys = new HashMap<>(c.tags()).keySet();
        int i = 0, size = keys.size();
        for (String k : keys) {
            Text n = Text.text("").append(key(k)).append(":").append(" ").append(pretty(c.tags().get(k)));
            if (++i != size) n = n.append(",").append(" ");
            m = m.append(n);
        }
        return m.append("}");
    }

    private static Text key(String k) {
        if (SIMPLE_VALUE.matcher(k).matches()) return Text.text(k).color(NamedTextColor.AQUA);
        return quoted(k, NamedTextColor.AQUA);
    }

    private static Text quoted(String s, org.domi.furnace.api.text.TextColor color) {
        StringBuilder b = new StringBuilder();
        org.domi.furnace.server.item.Snbt.quoteString(s, b);
        String q = b.substring(0, 1);
        return Text.text(q).append(Text.text(b.substring(1, b.length() - 1)).color(color)).append(q);
    }
}
