// 프로토콜 767(1.21.1) 패킷 종류·번호 표 — 스키마(packet/schema/protocol-767.schema)와 한 쌍이다.
// 스키마는 패킷의 "모양"(필드)이고 이것은 "어느 프로토콜(단계·방향)에서 몇 번인가". 호환 계층 shim 생성기(패킷 등록부 shim)와
// 시험이 쓴다. 표 파일은 tools/compat/protocol-ids-extract.py 가 바닐라 클래스에서 뽑은 번호·이름만 담는다(바닐라 코드 아님).
package org.domi.furnace.api.packet.schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.packet.PacketDirection;

public final class ProtocolIds {

    /** 한 PacketTypes 클래스(holder = common·configuration·cookie·game·handshake·login·ping·status)가 선언한 종류. */
    public record Type(String holder, PacketDirection direction, String wireName) {}

    /** 프로토콜 등록부의 번호 하나. holder·wireName 은 {@link Type} 과 같은 뜻. */
    public record Entry(int id, String holder, String wireName) {}

    private static volatile ProtocolIds protocol767;

    private final List<Type> types;
    private final Map<String, List<Entry>> entries;   // "<프로토콜>/<방향>" -> 번호 순서(0부터 빈틈 없음)

    private ProtocolIds(List<Type> types, Map<String, List<Entry>> entries) {
        this.types = List.copyOf(types);
        Map<String, List<Entry>> copy = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.entries = copy;
    }

    public static ProtocolIds protocol767() {
        ProtocolIds p = protocol767;
        if (p == null) {
            synchronized (ProtocolIds.class) {
                p = protocol767;
                if (p == null) protocol767 = p = load("protocol-767-ids.txt");
            }
        }
        return p;
    }

    private static ProtocolIds load(String resource) {
        try (InputStream in = ProtocolIds.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("번호 표 리소스가 없다: " + resource);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProtocolIds parse(String text) {
        List<Type> types = new ArrayList<>();
        Map<String, List<Entry>> entries = new LinkedHashMap<>();
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            try {
                switch (p[0]) {
                    case "type" -> types.add(new Type(p[1], direction(p[2]), p[3]));
                    case "id" -> {
                        String[] hw = p[4].split(":", 2);
                        List<Entry> list = entries.computeIfAbsent(p[1] + "/" + p[2], k -> new ArrayList<>());
                        int id = Integer.parseInt(p[3]);
                        if (id != list.size()) throw new IllegalArgumentException("번호가 이어지지 않는다: " + id + " (기대 " + list.size() + ")");
                        list.add(new Entry(id, hw[0], hw[1]));
                    }
                    default -> throw new IllegalArgumentException("모르는 줄 종류: " + p[0]);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("번호 표 " + lineNo + "째 줄: " + line + " — " + e.getMessage(), e);
            }
        }
        return new ProtocolIds(types, entries);
    }

    private static PacketDirection direction(String s) {
        return switch (s) {
            case "clientbound" -> PacketDirection.CLIENTBOUND;
            case "serverbound" -> PacketDirection.SERVERBOUND;
            default -> throw new IllegalArgumentException("모르는 방향: " + s);
        };
    }

    /** 모든 종류(holder 마다 선언 순서). */
    public List<Type> types() { return types; }

    /** holder 의 종류들(선언 순서). */
    public List<Type> types(String holder) { return types.stream().filter(t -> t.holder().equals(holder)).toList(); }

    /** 프로토콜(configuration·game·handshake·login·status)의 한 방향 등록부(번호 순서). 없으면 빈 목록. */
    public List<Entry> entries(String protocol, PacketDirection direction) {
        return entries.getOrDefault(protocol + "/" + direction.name().toLowerCase(java.util.Locale.ROOT), List.of());
    }

    /** 이 표가 아는 프로토콜 이름. */
    public List<String> protocols() {
        return entries.keySet().stream().map(k -> k.substring(0, k.indexOf('/'))).distinct().toList();
    }
}
