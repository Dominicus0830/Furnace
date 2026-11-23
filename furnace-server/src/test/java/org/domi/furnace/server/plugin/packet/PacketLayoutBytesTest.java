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

        Writer(long seed) { rnd = new Random(seed); }

        static final class UnsupportedToken extends RuntimeException {
            UnsupportedToken(String t) { super(t, null, false, false); }
        }

        /** 값 종류를 잠금이 모르는 문자열 칸(열거형을 이름으로 보내는 칸)의 고정 값. 칸 이름 → 값. */
        static final java.util.Map<String, String> STRING_CHOICES = java.util.Map.of("joint", "aligned");

        void write(Token t, ByteBuf b) {
            String choice = t.field() == null ? null : STRING_CHOICES.get(t.field());
            if (t.wire().equals("string") && choice != null) string(b, 0, choice);
            else write(t.wire(), b);
        }

        void write(String tok, ByteBuf b) {
            switch (tok) {
                case "bool" -> b.writeBoolean(rnd.nextBoolean());
                case "i8", "u8" -> b.writeByte(0);   // 열거형 번호·비트 묶음 칸일 수 있어 0 만(잠금은 칸의 의미를 모른다). 폭은 길이가 본다
                case "i16", "u16" -> b.writeShort(rnd.nextInt(2001) - 1000);   // 속도 칸(×8000 짧은 정수)은 범위를 넘으면 값이 잘린다
                case "i32" -> b.writeInt(rnd.nextInt());
                case "i64", "chunk_pos", "section_pos", "block_pos" -> b.writeLong(rnd.nextLong());
                case "f32" -> b.writeFloat((float) (rnd.nextDouble() * 1000 - 500));
                case "f64" -> b.writeDouble(rnd.nextDouble() * 1000 - 500);
                case "varint" -> varint(b, 0);   // 열거형 번호일 수 있어(상수가 하나뿐인 것도 있다) 0 만. 가변 길이 자체는 varlong·목록 길이·varints 가 본다
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
                        Writer w = new Writer(31L * wire.hashCode() + round);
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
}
