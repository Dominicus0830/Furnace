// IdentityKit 자기 시험: 증명 장치가 같으면 통과시키고, 다르면 재현 가능한 메시지로 실패하는지.
package org.domi.furnace.server.identity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.net.io.CompressionEncoder;
import org.domi.furnace.server.net.io.FrameEncoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.world.anvil.RegionFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityKitTest {

    @Nested
    class Hash {
        @Test
        void sameFormulaAsExistingBenches() {
            // NoiseIdentityBench.fnv 와 같은 식: 기존 도구가 적은 해시와 이어져야 한다
            long h = IdentityHash.OFFSET;
            h = (h ^ 7) * 0x100000001B3L;
            assertEquals(h, new IdentityHash().add(7).value());
        }

        @Test
        void lengthSeparatesConcatenations() {
            assertNotEquals(new IdentityHash().add("ab").add("c").value(), new IdentityHash().add("a").add("bc").value());
        }

        @Test
        void negativeZeroDiffersFromZero() {
            assertNotEquals(new IdentityHash().add(0.0).value(), new IdentityHash().add(-0.0).value());
        }
    }

    @Nested
    class Diff {
        private Differential<int[], Integer> sumDiff() {
            return Differential.<int[], Integer>of("sum", r -> {
                int[] a = new int[1 + r.nextInt(20)];
                for (int i = 0; i < a.length; i++) a[i] = r.nextInt(100) - 50;
                return a;
            }).seed(42).iterations(300);
        }

        private static int sumLoop(int[] a) { int s = 0; for (int v : a) s += v; return s; }

        @Test
        void identicalImplementationsPass() {
            assertEquals(300, sumDiff().check(IdentityKitTest.Diff::sumLoop, a -> java.util.Arrays.stream(a).sum()));
        }

        @Test
        void differenceReportsSeedIterationAndInput() {
            AssertionError e = assertThrows(AssertionError.class,
                    () -> sumDiff().check(IdentityKitTest.Diff::sumLoop, a -> a.length > 10 ? sumLoop(a) + 1 : sumLoop(a)));
            String m = e.getMessage();
            assertTrue(m.contains("[sum]") && m.contains("seed=0x2a") && m.contains("iter=") && m.contains("입력: [")
                    && m.contains("-Dfurnace.identity.seed=42 -Dfurnace.identity.iter="), m);
        }

        @Test
        void inputsAreReproduciblePerIteration() {
            SplittableRandom a = Differential.randomFor(9, 17), b = Differential.randomFor(9, 17), c = Differential.randomFor(9, 18);
            long x = a.nextLong();
            assertEquals(x, b.nextLong());
            assertNotEquals(x, c.nextLong());
        }

        @Test
        void eachImplementationGetsItsOwnInput() {
            // 옛 구현이 입력을 고쳐도 새 구현은 원래 입력을 받는다
            sumDiff().check(a -> { int s = sumLoop(a); java.util.Arrays.fill(a, 0); return s; }, IdentityKitTest.Diff::sumLoop);
        }

        @Test
        void sameExceptionTypeIsSameResult_differentIsNot() {
            Differential<int[], Integer> d = sumDiff();
            d.check(a -> { throw new IllegalStateException("x"); }, a -> { throw new IllegalStateException("y"); });
            AssertionError e = assertThrows(AssertionError.class,
                    () -> d.check(a -> { throw new IllegalStateException(); }, IdentityKitTest.Diff::sumLoop));
            assertTrue(e.getMessage().contains("옛 구현만 예외"), e.getMessage());
        }

        @Test
        void arraysCompareByContent_andReportFirstIndex() {
            assertNull(Differential.deepDifference(new int[][]{{1, 2}}, new int[][]{{1, 2}}));
            assertTrue(Differential.deepDifference(new int[]{1, 2, 3}, new int[]{1, 9, 3}).contains("[1]"));
        }

        @Test
        void mutationCheckFindsBrokenCandidate() {
            int k = sumDiff().assertDetects(IdentityKitTest.Diff::sumLoop, a -> a.length > 5 ? sumLoop(a) - a[5] : sumLoop(a));
            assertTrue(k >= 0);
        }

        @Test
        void mutationCheckFailsWhenGeneratorCannotReachTheBug() {
            // 길이 21 이상 배열에서만 틀리는 변이 — 생성기는 길이 20 까지만 만든다
            AssertionError e = assertThrows(AssertionError.class,
                    () -> sumDiff().assertDetects(IdentityKitTest.Diff::sumLoop, a -> a.length > 20 ? 0 : sumLoop(a)));
            assertTrue(e.getMessage().contains("변이를"), e.getMessage());
        }
    }

    @Nested
    class GoldenFiles {
        @Test
        void matchingLinesPass_commentsIgnored() {
            Golden.assertMatches("identitykit-selftest", List.of("alpha 0000000000000001", "beta 0000000000000002"));
        }

        @Test
        void mismatchNamesFirstLineAndDumpsActual() {
            AssertionError e = assertThrows(AssertionError.class,
                    () -> Golden.assertMatches("identitykit-selftest", List.of("alpha 0000000000000001", "beta 00000000000000ff")));
            assertTrue(e.getMessage().contains("첫 차이 2번째") && e.getMessage().contains("00000000000000ff"), e.getMessage());
            assertTrue(Files.isRegularFile(Path.of(System.getProperty("user.dir"), "build/identity/actual/identitykit-selftest.txt")));
        }

        @Test
        void missingGoldenFailsWithBlessHint() {
            AssertionError e = assertThrows(AssertionError.class, () -> Golden.assertMatches("identitykit-no-such-scene", List.of("x 1")));
            assertTrue(e.getMessage().contains("-Dfurnace.identity.bless=identitykit-no-such-scene"), e.getMessage());
        }

        @Test
        void emptyResultNeverMatches() {
            assertThrows(AssertionError.class, () -> Golden.assertMatches("identitykit-selftest", List.of()));
        }

        @Test
        void blessNeedsExplicitNames() {
            assertTrue(Golden.blessRequested("a", "b, a"));
            assertFalse(Golden.blessRequested("a", "ab"));
            assertFalse(Golden.blessRequested("a", null));
            for (String bulk : new String[]{"*", "all", "true"}) {
                assertThrows(IllegalArgumentException.class, () -> Golden.blessRequested("a", bulk));
            }
        }

        @Test
        void namesCannotEscapeTheDirectory() {
            assertThrows(IllegalArgumentException.class, () -> Golden.checkName("../x"));
            assertThrows(IllegalArgumentException.class, () -> Golden.checkName("A"));
        }
    }

    @Nested
    class Scenes {
        @Test
        void deterministicSceneAddsTotal() {
            List<String> lines = SceneHash.deterministic("t", rec -> rec.line("a", 1L).text("count 3"));
            assertEquals(3, lines.size());
            assertTrue(lines.get(2).startsWith("TOTAL ") && lines.get(2).endsWith("lines=2"), lines.get(2));
        }

        @Test
        void nondeterministicSceneFailsBeforeGoldenComparison() {
            AtomicInteger runs = new AtomicInteger();
            AssertionError e = assertThrows(AssertionError.class,
                    () -> SceneHash.assertGolden("identitykit-selftest", rec -> rec.line("run", runs.incrementAndGet())));
            assertTrue(e.getMessage().contains("결정적이지 않다"), e.getMessage());
        }

        @Test
        void emptySceneAndEmptyHashFail() {
            assertThrows(AssertionError.class, () -> SceneHash.record(rec -> {}));
            assertThrows(AssertionError.class, () -> SceneHash.record(rec -> rec.line("x", new IdentityHash())));
            assertThrows(IllegalArgumentException.class, () -> SceneHash.record(rec -> rec.text("TOTAL 1")));
        }
    }

    @Nested
    class FreshJvm {
        @Test
        void entitySceneIsDeterministicOnlyInFreshSeededJvms() {
            // 같은 JVM: 엔티티 id 카운터가 이어지고 씨앗 이음매도 꺼져 있어 두 번째 실행이 다르다(PR #272 검토 R2)
            AssertionError e = assertThrows(AssertionError.class, () -> SceneHash.deterministic("entity-same-jvm", new EntitySelftestScene()));
            assertTrue(e.getMessage().contains("결정적이지 않다"), e.getMessage());
            // 새 JVM(씨앗 켜짐) 두 번: 같고, 금 파일과도 같다
            SceneHash.assertGoldenInFreshJvm("identitykit-entity-selftest", EntitySelftestScene.class);
        }

        @Test
        void freshJvmFailureCarriesChildLog() {
            AssertionError e = assertThrows(AssertionError.class, () -> SceneHash.recordInFreshJvm(BrokenScene.class));
            assertTrue(e.getMessage().contains("exit") && e.getMessage().contains("장면 고장"), e.getMessage());
        }
    }

    public static final class BrokenScene implements SceneHash.Scene {
        @Override
        public void run(SceneHash.Recorder rec) { throw new IllegalStateException("장면 고장"); }
    }

    @Nested
    class Packets {
        private static ByteBuf packet(int id, int len, int fill) {
            ByteBuf b = Unpooled.buffer();
            Types.writeVarInt(b, id);
            for (int i = 0; i < len; i++) b.writeByte((fill + i % 7) & 0xFF);
            return b;
        }

        private static PacketTape send(EmbeddedChannel ch, PacketTape.Wire wire) {
            ch.writeAndFlush(packet(0x27, 10, 1));
            ch.writeAndFlush(packet(0x0B, 5000, 3));   // 임계값 넘음 → 압축
            ch.writeAndFlush(packet(0x41, 0, 0));
            PacketTape t = new PacketTape();
            assertEquals(3, t.drain(ch, wire));
            ch.finishAndReleaseAll();
            return t;
        }

        @Test
        void framedCompressedTapeEqualsPlainTape() {
            PacketTape plain = send(new EmbeddedChannel(), PacketTape.Wire.PLAIN);
            // 실서버 순서: 압축 → 프레임(쓰기는 뒤 핸들러부터 거친다)
            PacketTape wire = send(new EmbeddedChannel(new FrameEncoder(), new CompressionEncoder(256)), PacketTape.Wire.FRAMED_COMPRESSED);
            PacketTape.assertSame(plain, wire);
            assertEquals(plain.hash(), wire.hash());
            assertArrayEquals(new int[]{0x27, 0x0B, 0x41}, wire.ids());
            assertTrue(wire.rawFrames().get(1).length < 5000, "큰 패킷은 압축돼 나갔어야 한다");
            PacketTape compressedOnly = send(new EmbeddedChannel(new CompressionEncoder(256)), PacketTape.Wire.COMPRESSED);
            PacketTape.assertSame(plain, compressedOnly);
        }

        @Test
        void differenceNamesPacketAndByte() {
            PacketTape a = send(new EmbeddedChannel(), PacketTape.Wire.PLAIN);
            EmbeddedChannel ch = new EmbeddedChannel();
            ch.writeAndFlush(packet(0x27, 10, 1));
            ch.writeAndFlush(packet(0x0B, 5000, 4));
            ch.writeAndFlush(packet(0x41, 0, 0));
            PacketTape b = new PacketTape();
            b.drain(ch, PacketTape.Wire.PLAIN);
            AssertionError e = assertThrows(AssertionError.class, () -> PacketTape.assertSame(a, b));
            assertTrue(e.getMessage().contains("패킷 1") && e.getMessage().contains("첫 다른 바이트 0"), e.getMessage());
            assertNotEquals(a.hash(), b.hash());
            assertEquals(2, a.only(id -> id != 0x0B).size());
            assertEquals("0 c 10 " + IdentityHash.hex(a.packets().get(0).hash()), a.lines(id -> id == 0x27 ? "c" : null).get(0));
        }
    }

    @Nested
    class RegionBytes {
        private static void write(Path dir, int clock, byte fill) throws Exception {
            Files.createDirectories(dir.resolve("region"));
            try (RegionFile rf = new RegionFile(dir.resolve("region/r.0.0.mca"))) {
                Mca.fixClock(rf, clock);
                byte[] nbt = new byte[3000];
                java.util.Arrays.fill(nbt, fill);
                rf.writeChunk(1, 2, nbt);
                rf.writeChunk(0, 0, nbt);
            }
        }

        @Test
        void sameWritesSameBytes_timestampsCanBeIgnored(@TempDir Path tmp) throws Exception {
            write(tmp.resolve("a"), 1000, (byte) 5);
            write(tmp.resolve("b"), 1000, (byte) 5);
            write(tmp.resolve("c"), 2000, (byte) 5);
            write(tmp.resolve("d"), 1000, (byte) 6);
            Mca.assertSameBytes(tmp.resolve("a"), tmp.resolve("b"));
            String ts = Mca.difference(tmp.resolve("a"), tmp.resolve("c"), false);
            assertNotNull(ts);
            assertTrue(ts.contains("시각 헤더"), ts);
            Mca.assertSameBytes(tmp.resolve("a"), tmp.resolve("c"), true);
            String body = Mca.difference(tmp.resolve("a"), tmp.resolve("d"), true);
            assertNotNull(body);
            assertTrue(body.contains("섹터"), body);
            assertNotNull(Mca.difference(tmp.resolve("a"), tmp.resolve("none"), false), "빈 폴더와 같다고 하면 안 된다");
        }
    }
}
