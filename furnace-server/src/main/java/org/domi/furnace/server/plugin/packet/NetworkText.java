// 네트워크 NBT 텍스트 ↔ Text. 쓰기는 Text.networkNbt() 그대로, 읽기는 NBT 트리를 바닐라 텍스트 JSON 으로 옮겨 JsonTextSerializer 로 읽는다
// (NBT 텍스트와 JSON 텍스트는 같은 구조다: 불리언은 byte, 섞인 목록의 원소는 {"": 값} 으로 감싸인다).
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class NetworkText {

    private NetworkText() {}

    static void write(ByteBuf buf, Text text) { buf.writeBytes(text.networkNbt()); }

    static Text read(ByteBuf buf) {
        Object tag;
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            tag = NbtIo.readUnnamed(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StringBuilder sb = new StringBuilder();
        json(tag, sb);
        return JsonTextSerializer.json().deserialize(sb.toString());
    }

    private static void json(Object v, StringBuilder sb) {
        if (v instanceof NbtCompound c) {
            Map<String, Object> tags = c.tags();
            if (tags.size() == 1 && tags.containsKey("")) {   // 섞인 목록의 감싼 원소
                json(tags.get(""), sb);
                return;
            }
            sb.append('{');
            boolean first = true;
            for (var e : tags.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                str(e.getKey(), sb);
                sb.append(':');
                json(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof NbtList l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                json(l.get(i), sb);
            }
            sb.append(']');
        } else if (v instanceof String s) {
            str(s, sb);
        } else if (v instanceof Byte b && (b == 0 || b == 1)) {
            sb.append(b == 1 ? "true" : "false");   // 텍스트 NBT 의 byte 는 불리언 필드뿐이다
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
            sb.append(']');
        } else if (v instanceof long[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
            sb.append(']');
        } else if (v instanceof byte[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
            sb.append(']');
        } else {
            sb.append("null");
        }
    }

    private static void str(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }
}
