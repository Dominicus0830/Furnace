// 손으로 쓴 스키마(protocol-767.schema)와 바닐라에서 기계로 뽑은 잠금(protocol-767.layout)이 같은 와이어 모양을 말하는지 대조한다.
// 바닐라 jar 없이 돈다(잠금은 커밋돼 있다). 잠금을 새로 뽑는 방법: tools/vanilla-extract/README.md 의 packet-layout.
package org.domi.furnace.api.packet.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.api.packet.PacketDirection;
import org.junit.jupiter.api.Test;

class PacketLayoutLockTest {

    /** 와이어 바이트가 같은 이름들(부호·묶음 이름만 다르다)을 한쪽으로 모은다. */
    private static final Map<String, String> CANON = Map.ofEntries(
            Map.entry("u8", "i8"), Map.entry("u16", "i16"),
            Map.entry("chunk_pos", "i64"), Map.entry("section_pos", "i64"),
            Map.entry("json_status", "json"),
            Map.entry("block_state", "varint"),
            Map.entry("vec3", "struct(f64,f64,f64)"));

    /** 잠금 쪽 nbt_any(어떤 태그든)는 스키마의 nbt 와 같은 와이어다. 선택 값 nbt 는 {@link #unify} 가 따로 본다. */
    private static String canon(String w) {
        if (w.equals("nbt_any")) return "nbt";
        return CANON.getOrDefault(w, w);
    }

    /** 스키마 와이어 칸이 {@code custom}(스키마가 모양을 못 적은 값)인 칸은 대조할 수 없다. 늘리지 않도록 이름을 못 박는다. */
    private static final Set<String> SCHEMA_OPAQUE_FIELDS = Set.of("game/CLIENTBOUND/remove_entities#0");

    /** 대조 결과. */
    record Result(List<String> problems, int compared, int lockCustom, int schemaCustom, Map<String, Set<String>> refs) {}

    static Result compare(PacketSchema schema, PacketLayoutLock lock) {
        List<String> problems = new ArrayList<>();
        Map<String, Set<String>> refs = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        int compared = 0;
        int lockCustom = 0;
        int schemaCustom = 0;
        for (PacketDef p : schema.packets()) {
            var e = lock.entry(p.state(), p.direction(), p.wireName());
            String key = p.state() + "/" + p.direction() + "/" + p.wireName();
            seen.add(key);
            if (e.isEmpty()) {
                problems.add("lock has no packet: " + key);
                continue;
            }
            if (e.get().custom()) { lockCustom++; continue; }
            if (p.custom()) { schemaCustom++; continue; }
            List<String> sch = new ArrayList<>();
            for (FieldDef f : p.fields()) {
                String w = schema.wireOf(f, p.javaPackage());
                sch.add(f.nullable() ? "optional<" + w + ">" : w);
            }
            List<String> lk = new ArrayList<>();
            for (PacketLayoutLock.Token t : e.get().tokens()) lk.add(t.wire());
            compared++;
            boolean ok = sch.size() == lk.size();
            for (int i = 0; ok && i < sch.size(); i++) {
                if (sch.get(i).equals("custom") && SCHEMA_OPAQUE_FIELDS.contains(key + "#" + i)) continue;
                ok = unify(sch.get(i), lk.get(i), refs);
            }
            if (!ok) problems.add("wire differs: " + key + "  schema=" + sch + "  lock=" + lk);
        }
        for (PacketLayoutLock.Entry e : lock.entries()) if (!seen.contains(e.key())) problems.add("schema has no packet: " + e.key());
        refs.forEach((h, ws) -> { if (ws.size() > 1) problems.add("one inner value codec maps to several schema wires: " + h + " -> " + ws); });
        return new Result(problems, compared, lockCustom, schemaCustom, refs);
    }

    /** 스키마 와이어 {@code sch} 와 잠금 토큰 {@code lock} 이 같은 모양인가. 불투명 참조(ref#해시)는 스키마 와이어에 한 번 묶고(refs) 다른 곳과 어긋나면 문제로 본다. */
    static boolean unify(String sch, String lock, Map<String, Set<String>> refs) {
        if (lock.startsWith("ref#")) {
            refs.computeIfAbsent(lock, k -> new TreeSet<>()).add(sch);
            return true;
        }
        // 스키마의 선택 값 nbt 는 바닐라 writeNbt(null → 끝 태그 하나)이고 잠금에서는 nbt_opt 다. 잠금 쪽 optional<…>(있음 바이트가 붙는다)는 이것과 다르다.
        if (sch.equals("optional<nbt>")) return lock.equals("nbt_opt");
        if (lock.equals("nbt_opt")) return false;
        for (String h : new String[] {"list", "optional"}) {
            if (sch.startsWith(h + "<") && lock.startsWith(h + "<")) return unify(inner(sch, h), inner(lock, h), refs);
        }
        if (sch.startsWith("map<") && lock.startsWith("map<")) {
            List<String> a = args(inner(sch, "map"));
            List<String> b = args(inner(lock, "map"));
            return a.size() == 2 && b.size() == 2 && unify(a.get(0), b.get(0), refs) && unify(a.get(1), b.get(1), refs);
        }
        return canon(sch).equals(canon(lock));
    }

    private static String inner(String t, String head) { return t.substring(head.length() + 1, t.length() - 1); }

    private static List<String> args(String s) {
        List<String> r = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(') depth++;
            else if (c == '>' || c == ')') depth--;
            else if (c == ',' && depth == 0) { r.add(s.substring(start, i)); start = i + 1; }
        }
        r.add(s.substring(start));
        return r;
    }

    @Test
    void handWrittenSchemaMatchesTheLayoutExtractedFromVanilla() {
        Result r = compare(PacketSchema.current(), PacketLayoutLock.load(767));
        assertTrue(r.problems().isEmpty(), "schema ≠ extracted layout:\n" + String.join("\n", r.problems()));
        // 대조한 패킷이 줄지 않게 지킨다 — 추출기가 못 푸는 것이 늘면 알아챈다.
        assertEquals(202, PacketSchema.current().packets().size());
        assertTrue(r.compared() >= 150, "compared packets: " + r.compared());
        assertTrue(r.lockCustom() <= 23, "lock custom: " + r.lockCustom());
    }

    @Test
    void anOptionalNbtIsNotTheSameWireAsAPresenceByteOptional() {
        Map<String, Set<String>> refs = new TreeMap<>();
        assertTrue(unify("optional<nbt>", "nbt_opt", refs));
        assertFalse(unify("optional<nbt>", "optional<nbt>", refs));
        assertFalse(unify("optional<nbt>", "nbt", refs));
        assertFalse(unify("nbt", "nbt_opt", refs));
        assertFalse(unify("nbt", "optional<nbt>", refs));
        assertTrue(unify("nbt", "nbt", refs));
    }

    @Test
    void lockHasEveryPacketOnce() {
        PacketLayoutLock lock = PacketLayoutLock.load(767);
        assertEquals(767, lock.protocol());
        assertEquals(202, lock.entries().size());
        assertTrue(lock.entry("game", PacketDirection.CLIENTBOUND, "set_time").isPresent());
        var set = lock.entry("game", PacketDirection.CLIENTBOUND, "set_time").orElseThrow();
        assertEquals("i64 i64", String.join(" ", set.tokens().stream().map(PacketLayoutLock.Token::wire).toList()));
    }

    @Test
    void aChangedSchemaFieldIsCaught() {
        // 시험이 정말 잡는지: 스키마 필드 와이어 하나를 일부러 틀리게 한다(set_time 의 둘째 i64 → i32).
        String text = readSchemaText();
        String broken = text.replace("packet clientbound set_time ClientboundSetTimePacket class\n  long gameTime get=getGameTime\n  long dayTime get=getDayTime",
                "packet clientbound set_time ClientboundSetTimePacket class\n  long gameTime get=getGameTime\n  int dayTime i32 get=getDayTime");
        assertNotEquals(text, broken, "schema text for set_time was not found (the test sample is out of date)");
        Result r = compare(PacketSchema.parse(767, broken), PacketLayoutLock.load(767));
        assertTrue(r.problems().stream().anyMatch(s -> s.contains("set_time")), r.problems().toString());
    }

    @Test
    void aChangedLockTokenIsCaught() {
        String lock = readResource("protocol-767.layout");
        String broken = lock.replaceFirst("(game\tC\tset_time\tcodec\t-\t)i64@gameTime i64@dayTime", "$1i64@gameTime i32@dayTime");
        assertNotEquals(lock, broken, "lock line for set_time was not found (the test sample is out of date)");
        Result r = compare(PacketSchema.current(), PacketLayoutLock.parse(broken));
        assertTrue(r.problems().stream().anyMatch(s -> s.contains("set_time")), r.problems().toString());
    }

    private static String readSchemaText() { return readResource("protocol-767.schema"); }

    private static String readResource(String name) {
        try (var in = PacketLayoutLockTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
