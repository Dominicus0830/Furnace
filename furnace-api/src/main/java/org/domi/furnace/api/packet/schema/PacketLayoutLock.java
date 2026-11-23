// 패킷 와이어 모양 잠금(protocol-767.layout)을 읽는다. 잠금은 바닐라 서버 jar 의 패킷 코덱 바이트코드에서 기계로 뽑은 것이다
// (tools/vanilla-extract -Pmode=packet-layout, 설명 docs/feature-parity/compat-packet-layout-2026-11-23.md).
// 손으로 쓴 스키마(protocol-767.schema)·서버 범용 코덱과 맞는지 대조하는 시험이 쓴다. Mojang 이름은 들어 있지 않다(안쪽 값 코덱은 이름 지문).
package org.domi.furnace.api.packet.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.api.packet.PacketDirection;

public final class PacketLayoutLock {

    /** 와이어 토큰 하나. field 는 값이 온 필드(또는 게터) 이름이며 없으면 null 이다. */
    public record Token(String wire, String field) {
        @Override
        public String toString() { return field == null ? wire : wire + "@" + field; }
    }

    /**
     * 패킷 하나의 모양.
     *
     * @param form       정의 방식: composite · codec · unit · expr · multi · none
     * @param customKind 손으로 남는 이유의 종류(branch·expr·multi·no-static-init…), 없으면 null
     * @param tokens     와이어 순서. customKind 가 있으면 분기 전까지의 일부일 수 있다
     */
    public record Entry(String state, PacketDirection direction, String wireName, String form, String customKind, List<Token> tokens) {
        public Entry {
            tokens = List.copyOf(tokens);
        }

        public boolean custom() { return customKind != null; }

        public String key() { return state + '/' + direction + '/' + wireName; }
    }

    private final int protocol;
    private final Map<String, Entry> entries;

    private PacketLayoutLock(int protocol, Map<String, Entry> entries) {
        this.protocol = protocol;
        this.entries = entries;
    }

    public int protocol() { return protocol; }

    public List<Entry> entries() { return List.copyOf(entries.values()); }

    public Optional<Entry> entry(String state, PacketDirection direction, String wireName) {
        return Optional.ofNullable(entries.get(state + '/' + direction + '/' + wireName));
    }

    /** 리소스 {@code protocol-<번호>.layout} 을 읽는다. */
    public static PacketLayoutLock load(int protocol) {
        String resource = "protocol-" + protocol + ".layout";
        try (InputStream in = PacketLayoutLock.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("layout lock resource is missing: " + resource);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PacketLayoutLock parse(String text) {
        int protocol = -1;
        Map<String, Entry> out = new LinkedHashMap<>();
        int lineNo = 0;
        for (String line : text.split("\\R")) {
            lineNo++;
            if (line.isBlank()) continue;
            if (line.startsWith("#furnace-packet-layout")) {
                String[] h = line.split(" ");
                if (h.length < 3 || !h[1].equals("1")) throw new IllegalArgumentException("unsupported layout lock header (line " + lineNo + "): " + line);
                protocol = Integer.parseInt(h[2]);
                continue;
            }
            if (line.startsWith("#")) continue;
            String[] c = line.split("\t", -1);
            if (c.length != 6) throw new IllegalArgumentException("layout lock line " + lineNo + " needs 6 columns: " + line);
            PacketDirection d = switch (c[1]) {
                case "C" -> PacketDirection.CLIENTBOUND;
                case "S" -> PacketDirection.SERVERBOUND;
                default -> throw new IllegalArgumentException("layout lock line " + lineNo + " has a bad direction: " + c[1]);
            };
            List<Token> tokens = new ArrayList<>();
            if (!c[5].equals("-")) {
                for (String t : splitTokens(c[5])) {
                    int at = t.lastIndexOf('@');
                    tokens.add(at < 0 ? new Token(t, null) : new Token(t.substring(0, at), t.substring(at + 1)));
                }
            }
            Entry e = new Entry(c[0], d, c[2], c[3], c[4].equals("-") ? null : c[4], tokens);
            if (out.put(e.key(), e) != null) throw new IllegalArgumentException("layout lock line " + lineNo + " repeats a packet: " + e.key());
        }
        if (protocol < 0) throw new IllegalArgumentException("layout lock header is missing");
        return new PacketLayoutLock(protocol, out);
    }

    /** 공백으로 나누되 꺾쇠·괄호 안의 공백은 건드리지 않는다(토큰 안에는 공백이 없지만 방어). */
    private static List<String> splitTokens(String s) {
        List<String> r = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '<' || ch == '(') depth++;
            else if (ch == '>' || ch == ')') depth--;
            else if (ch == ' ' && depth == 0) {
                if (i > start) r.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) r.add(s.substring(start));
        return r;
    }
}
