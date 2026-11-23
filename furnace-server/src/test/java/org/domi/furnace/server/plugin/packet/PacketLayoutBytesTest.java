// 바닐라에서 기계로 뽑은 패킷 와이어 모양(잠금 protocol-767.layout)으로 무작위 본문을 쓰고(잠금 기준 인코더),
// 서버 범용 코덱(스키마 기준)이 그 본문을 끝까지 읽고 같은 바이트로 되쓰는지 본다 — 두 쪽이 서로 어긋나면 어느 한쪽이 바닐라와 다른 것이다.
// 레지스트리·안쪽 값 코덱이 필요한 토큰이 든 패킷은 건너뛴다(개수는 아래에서 지킨다). 바닐라 jar 는 필요 없다.
package org.domi.furnace.server.plugin.packet;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.schema.PacketLayoutLock;
import org.domi.furnace.api.packet.schema.PacketLayoutLock.Token;
import org.junit.jupiter.api.Test;

class PacketLayoutBytesTest {

    private static final List<String> PLAY_STATES = List.of("game", "common", "cookie", "ping");
    private static final int ROUNDS = 25;

    /** 잠금 토큰 하나를 무작위 값으로 쓰는 인코더. 쓸 수 없는 토큰이면 UnsupportedToken. */
    static final class Writer {
        final Random rnd;

        /** 이 패킷의 "열거형·비트 묶음" 칸 이름 → 값 상한(미포함). 이 칸만 0..상한-1 을 쓰고, 나머지 정수 칸은 여러 바이트 값까지 쓴다. */
        final java.util.Map<String, Integer> narrow;

        /** 시드는 섞어서 쓴다(단순 곱셈 시드는 첫 값이 패킷마다 거의 같다). */
        Writer(long seed, java.util.Map<String, Integer> narrow) {
            rnd = new Random(new java.util.SplittableRandom(seed).nextLong());
            this.narrow = narrow;
        }

        private static final int[] WIDE_VARINTS = {0, 1, 127, 128, 300, 16384, 2_097_152, 268_435_456, -1};

        static final class UnsupportedToken extends RuntimeException {
            UnsupportedToken(String t) { super(t, null, false, false); }
        }

        /** 값 종류를 잠금이 모르는 문자열 칸(열거형을 이름으로 보내는 칸)의 고정 값. 칸 이름 → 값. */
        static final java.util.Map<String, String> STRING_CHOICES = java.util.Map.of("joint", "aligned");

        void write(Token t, ByteBuf b) {
            String choice = t.field() == null ? null : STRING_CHOICES.get(t.field());
            Integer bound = t.field() == null ? null : narrow.get(t.field());
            if (t.wire().equals("string") && choice != null) string(b, 0, choice);
            else if (bound != null && t.wire().equals("varint")) varint(b, rnd.nextInt(bound));
            else if (bound != null && (t.wire().equals("i8") || t.wire().equals("u8"))) b.writeByte(rnd.nextInt(bound));
            else write(t.wire(), b);
        }

        void write(String tok, ByteBuf b) {
            switch (tok) {
                case "bool" -> b.writeBoolean(rnd.nextBoolean());
                case "i8", "u8" -> b.writeByte(rnd.nextInt(256));
                case "i16", "u16" -> b.writeShort(rnd.nextInt(2001) - 1000);   // 속도 칸(×8000 짧은 정수)은 범위를 넘으면 값이 잘린다
                case "i32" -> b.writeInt(rnd.nextInt());
                case "i64", "chunk_pos", "section_pos", "block_pos" -> b.writeLong(rnd.nextLong());
                case "f32" -> b.writeFloat((float) (rnd.nextDouble() * 1000 - 500));
                case "f64" -> b.writeDouble(rnd.nextDouble() * 1000 - 500);
                case "varint" -> varint(b, WIDE_VARINTS[rnd.nextInt(WIDE_VARINTS.length)]);   // 한 바이트·여러 바이트를 섞어 가변 길이를 i8 로 바꾼 것도 잡는다
                case "varlong" -> varlong(b, rnd.nextLong());
                case "string" -> string(b, 12);
                case "identifier" -> string(b, 0, "minecraft:" + "abcdefghij".substring(0, 1 + rnd.nextInt(8)));
                case "uuid" -> { b.writeLong(rnd.nextLong()); b.writeLong(rnd.nextLong()); }
                case "bytes" -> { int n = rnd.nextInt(20); varint(b, n); for (int i = 0; i < n; i++) b.writeByte(rnd.nextInt(256)); }
                case "longs" -> { int n = rnd.nextInt(5); varint(b, n); for (int i = 0; i < n; i++) b.writeLong(rnd.nextLong()); }
                case "varints" -> { int n = rnd.nextInt(5); varint(b, n); for (int i = 0; i < n; i++) varint(b, rnd.nextInt(1000)); }
                case "nbt" -> { b.writeByte(10); b.writeByte(0); }   // 빈 복합 태그(이름 없는 루트)
                case "nbt_opt" -> { if (rnd.nextBoolean()) b.writeByte(0); else { b.writeByte(10); b.writeByte(0); } }
                default -> {
                    if (tok.startsWith("list<")) {
                        String in = inner(tok, "list");
                        int n = rnd.nextInt(4);
                        varint(b, n);
                        for (int i = 0; i < n; i++) write(in, b);
                    } else if (tok.startsWith("optional<")) {
                        String in = inner(tok, "optional");
                        boolean present = rnd.nextBoolean();
                        b.writeBoolean(present);
                        if (present) write(in, b);
                    } else if (tok.startsWith("map<")) {
                        List<String> kv = split(inner(tok, "map"));
                        int n = rnd.nextInt(3);
                        varint(b, n);
                        for (int i = 0; i < n; i++) { write(kv.get(0), b); write(kv.get(1), b); }
                    } else if (tok.startsWith("struct(")) {
                        for (String t : split(tok.substring(7, tok.length() - 1))) write(t, b);
                    } else {
                        throw new UnsupportedToken(tok);
                    }
                }
            }
        }

        void string(ByteBuf b, int max) {
            int n = rnd.nextInt(max + 1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append((char) ('a' + rnd.nextInt(26)));
            string(b, 0, sb.toString());
        }

        static void string(ByteBuf b, int unused, String s) {
            byte[] d = s.getBytes(StandardCharsets.UTF_8);
            varint(b, d.length);
            b.writeBytes(d);
        }

        static void varint(ByteBuf b, int v) {
            while ((v & ~0x7F) != 0) { b.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
            b.writeByte(v);
        }

        static void varlong(ByteBuf b, long v) {
            while ((v & ~0x7FL) != 0) { b.writeByte((int) ((v & 0x7F) | 0x80)); v >>>= 7; }
            b.writeByte((int) v);
        }

        static String inner(String t, String head) { return t.substring(head.length() + 1, t.length() - 1); }

        static List<String> split(String s) {
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
    }

    /**
     * 열거형 번호·비트 묶음·레지스트리 번호 칸(범위를 넘으면 서버 코덱이 거부하거나 잘라 쓴다) — 패킷 와이어 이름 → (칸 이름 → 값 상한).
     * 잠금은 칸의 의미를 모르므로 이 목록은 시험이 안다. 여기에 없는 칸은 한 바이트·여러 바이트 값을 모두 쓴다.
     */
    static final java.util.Map<String, java.util.Map<String, Integer>> NARROW = java.util.Map.ofEntries(
            java.util.Map.entry("animate", java.util.Map.of("action", 1)),
            java.util.Map.entry("block_update", java.util.Map.of("getBlockState", 1)),
            java.util.Map.entry("change_difficulty", java.util.Map.of("difficulty", 4)),
            java.util.Map.entry("custom_chat_completions", java.util.Map.of("action", 3)),
            java.util.Map.entry("debug_sample", java.util.Map.of("debugSampleType", 1)),
            java.util.Map.entry("open_book", java.util.Map.of("hand", 2)),
            java.util.Map.entry("player_position", java.util.Map.of("relativeArguments", 32)),
            java.util.Map.entry("set_display_objective", java.util.Map.of("slot", 3)),
            java.util.Map.entry("client_command", java.util.Map.of("action", 2)),
            java.util.Map.entry("debug_sample_subscription", java.util.Map.of("sampleType", 1)),
            java.util.Map.entry("player_action", java.util.Map.of("action", 7, "direction", 6)),
            java.util.Map.entry("player_command", java.util.Map.of("action", 9)),
            java.util.Map.entry("recipe_book_change_settings", java.util.Map.of("bookType", 4)),
            java.util.Map.entry("resource_pack", java.util.Map.of("action", 2)),
            java.util.Map.entry("swing", java.util.Map.of("hand", 2)),
            java.util.Map.entry("use_item", java.util.Map.of("hand", 2)));

    static java.util.Map<String, Integer> narrowFields(String wire) {
        return NARROW.getOrDefault(wire, java.util.Map.of());
    }

    /** 시험 결과: 같은 바이트로 확인한 종류의 와이어 이름들, 건너뛴 수, 어긋난 곳. */
    record Run(List<String> checked, int skippedToken, int notReadable, List<String> failures) {}

    static Run run(PacketLayoutLock lock) {
        ProtocolTable codec = new ProtocolTable(() -> null);
        List<String> checked = new ArrayList<>();
        int skippedToken = 0;
        int notReadable = 0;
        List<String> failures = new ArrayList<>();
        for (PacketDirection dir : PacketDirection.values()) {
            for (PacketType<?> t : PacketTypes.all(dir)) {
                String wire = t.name().substring(t.name().indexOf(':') + 1);
                PacketLayoutLock.Entry e = null;
                for (String st : PLAY_STATES) {
                    var x = lock.entry(st, dir, wire);
                    if (x.isPresent()) { e = x.get(); break; }
                }
                if (e == null || e.custom() || !codec.readable(t) || codec.ids(t).length != 1) { notReadable++; continue; }
                int id = codec.ids(t)[0];
                boolean ok = true;
                for (int round = 0; round < ROUNDS; round++) {
                    ByteBuf in = Unpooled.buffer();
                    try {
                        Writer w = new Writer(31L * (dir + ":" + wire).hashCode() + round, narrowFields(wire));
                        for (var tok : e.tokens()) w.write(tok, in);
                    } catch (Writer.UnsupportedToken u) {
                        skippedToken++;
                        ok = false;
                        break;
                    }
                    byte[] want = ByteBufUtil.getBytes(in);
                    try {
                        Packet p = codec.decode(t, id, Unpooled.wrappedBuffer(want));
                        ByteBuf out = Unpooled.buffer();
                        codec.encode(p, out);
                        byte[] got = ByteBufUtil.getBytes(out);
                        if (!java.util.Arrays.equals(want, got)) {
                            failures.add(dir + " " + wire + " bytes differ: lock-writer=" + ByteBufUtil.hexDump(want) + " codec=" + ByteBufUtil.hexDump(got));
                            ok = false;
                            break;
                        }
                    } catch (RuntimeException ex) {
                        failures.add(dir + " " + wire + " " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + " bytes=" + ByteBufUtil.hexDump(want));
                        ok = false;
                        break;
                    }
                }
                if (ok) checked.add(dir + ":" + wire);
            }
        }
        return new Run(checked, skippedToken, notReadable, failures);
    }

    @Test
    void genericCodecReadsAndRewritesWhatTheLayoutLockWrites() {
        Run r = run(PacketLayoutLock.load(767));
        assertTrue(r.failures().isEmpty(), "generic codec ≠ layout lock:\n" + String.join("\n", r.failures()));
        System.out.println("[패킷 모양 바이트] 같은 바이트 " + r.checked().size() + "종, 토큰 때문에 건너뜀 " + r.skippedToken() + "종, 코덱이 못 읽는 종류·잠금 손 필요 " + r.notReadable() + "종");
        assertTrue(r.checked().size() >= 80, "checked packets: " + r.checked().size());
    }

    @Test
    void aChangedLockTokenIsCaughtAtTheByteLevel() {
        // 시험이 정말 잡는지: 검증된 패킷 하나의 잠금 토큰 폭을 바꾼다(i16 → i32 또는 i64 → i32).
        String text = readLock();
        Run base = run(PacketLayoutLock.parse(text));
        assertTrue(base.failures().isEmpty());
        String victim = base.checked().stream().filter(s -> s.startsWith("CLIENTBOUND:container_set_data")).findFirst().orElseThrow();
        String broken = text.replaceFirst("(game\tC\tcontainer_set_data\tcodec\t-\t)i8@containerId i16@id i16@value", "$1i8@containerId i32@id i16@value");
        assertNotEquals(text, broken, "lock line for " + victim + " was not found (the test sample is out of date)");
        Run r = run(PacketLayoutLock.parse(broken));
        assertTrue(r.failures().stream().anyMatch(s -> s.contains("container_set_data")), r.failures().toString());
    }

    private static String readLock() {
        try (var in = PacketLayoutLock.class.getResourceAsStream("protocol-767.layout")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void aVarintChangedToAFixedByteIsCaught() {
        // 가변 정수(varint)를 한 바이트 정수(i8)로 바꾼 잠금도 잡는다 — 열거형이 아닌 칸에서 여러 바이트 값을 쓰기 때문.
        String text = readLock();
        String[][] cases = {
                {"game\tC\tchunk_batch_finished\tcodec\t-\tvarint@batchSize", "game\tC\tchunk_batch_finished\tcodec\t-\ti8@batchSize"},
                {"game\tC\tset_chunk_cache_radius\tcodec\t-\tvarint@radius", "game\tC\tset_chunk_cache_radius\tcodec\t-\ti8@radius"},
        };
        for (String[] c : cases) {
            String broken = text.replace(c[0], c[1]);
            assertNotEquals(text, broken, "lock line not found: " + c[0]);
            Run r = run(PacketLayoutLock.parse(broken));
            String wire = c[0].split("\t")[2];
            assertTrue(r.failures().stream().anyMatch(s -> s.contains(wire)), wire + ": " + r.failures());
        }
    }

    @Test
    void anOptionalPresenceByteIsCaught() {
        // 선택 값의 있음/없음 바이트를 지운(optional<x> → x) 잠금도 잡는다.
        String text = readLock();
        String[][] cases = {
                {"game\tC\treset_score\tcodec\t-\tstring@owner optional<string>@objectiveName", "game\tC\treset_score\tcodec\t-\tstring@owner string@objectiveName"},
                {"game\tC\tselect_advancements_tab\tcodec\t-\toptional<identifier>@tab", "game\tC\tselect_advancements_tab\tcodec\t-\tidentifier@tab"},
        };
        Run base = run(PacketLayoutLock.parse(text));
        for (String[] c : cases) {
            String wire = c[0].split("\t")[2];
            assertTrue(base.checked().stream().anyMatch(s -> s.endsWith(":" + wire)), wire + " is not byte-checked at all");
            String broken = text.replace(c[0], c[1]);
            assertNotEquals(text, broken, "lock line not found: " + c[0]);
            Run r = run(PacketLayoutLock.parse(broken));
            assertTrue(r.failures().stream().anyMatch(s -> s.contains(wire)), wire + ": " + r.failures());
        }
    }
}
