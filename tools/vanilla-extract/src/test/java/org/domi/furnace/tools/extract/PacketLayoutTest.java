package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 패킷 코덱 추출기: 바닐라 없이, 이름만 같은 아주 작은 뼈대 클래스(우리가 쓴 것)를 컴파일해 jar 로 묶어 돌린다.
 * 바닐라 코드는 들어 있지 않다. 실제 바닐라 jar 에 대한 결과는 잠금 파일(protocol-767.layout)과 대조 시험이 지킨다.
 */
class PacketLayoutTest {

    private static final Map<String, String> STUBS = new TreeMap<>();

    static {
        STUBS.put("net.minecraft.network.FriendlyByteBuf", """
                package net.minecraft.network;
                import java.util.*;
                public class FriendlyByteBuf {
                    public FriendlyByteBuf writeVarInt(int v) { return this; }
                    public FriendlyByteBuf writeLong(long v) { return this; }
                    public FriendlyByteBuf writeBoolean(boolean v) { return this; }
                    public FriendlyByteBuf writeShort(int v) { return this; }
                    public FriendlyByteBuf writeUtf(String s) { return this; }
                    public FriendlyByteBuf writeUUID(UUID s) { return this; }
                    public FriendlyByteBuf writeEnum(Enum<?> e) { return this; }
                    public <T> void writeCollection(Collection<T> c, net.minecraft.network.codec.StreamEncoder<FriendlyByteBuf, T> w) { }
                    public <T> void writeOptional(Optional<T> o, net.minecraft.network.codec.StreamEncoder<FriendlyByteBuf, T> w) { }
                }
                """);
        STUBS.put("net.minecraft.network.codec.StreamEncoder", """
                package net.minecraft.network.codec;
                public interface StreamEncoder<B, T> { void encode(B b, T t); }
                """);
        STUBS.put("net.minecraft.network.codec.StreamDecoder", """
                package net.minecraft.network.codec;
                public interface StreamDecoder<B, T> { T decode(B b); }
                """);
        STUBS.put("net.minecraft.network.codec.StreamMemberEncoder", """
                package net.minecraft.network.codec;
                public interface StreamMemberEncoder<B, T> { void encode(T t, B b); }
                """);
        STUBS.put("net.minecraft.network.codec.StreamCodec", """
                package net.minecraft.network.codec;
                import java.util.function.*;
                public interface StreamCodec<B, T> extends StreamEncoder<B, T>, StreamDecoder<B, T> {
                    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                            StreamCodec<? super B, T2> c2, Function<C, T2> g2, BiFunction<T1, T2, C> f) { return null; }
                    static <B, T> StreamCodec<B, T> unit(T t) { return null; }
                }
                """);
        STUBS.put("net.minecraft.network.codec.ByteBufCodecs", """
                package net.minecraft.network.codec;
                public class ByteBufCodecs {
                    public static final StreamCodec<Object, Integer> VAR_INT = null;
                    public static final StreamCodec<Object, Boolean> BOOL = null;
                    public static final StreamCodec<Object, Long> LONG = null;
                    public static final StreamCodec<Object, String> STRING_UTF8 = null;
                    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, Integer> CONTAINER_ID = new StreamCodec<net.minecraft.network.FriendlyByteBuf, Integer>() {
                        public Integer decode(net.minecraft.network.FriendlyByteBuf b) { return 0; }
                        public void encode(net.minecraft.network.FriendlyByteBuf b, Integer v) { b.writeVarInt(v); }
                    };
                }
                """);
        STUBS.put("net.minecraft.network.protocol.Packet", """
                package net.minecraft.network.protocol;
                import net.minecraft.network.codec.*;
                public interface Packet<T> {
                    static <B extends net.minecraft.network.FriendlyByteBuf, P extends Packet<?>> StreamCodec<B, P> codec(StreamMemberEncoder<B, P> e, StreamDecoder<B, P> d) { return null; }
                }
                """);
        STUBS.put("net.minecraft.network.protocol.PacketType", """
                package net.minecraft.network.protocol;
                public class PacketType<T> { }
                """);
        STUBS.put("net.minecraft.network.protocol.game.GamePacketTypes", """
                package net.minecraft.network.protocol.game;
                import net.minecraft.network.protocol.PacketType;
                public class GamePacketTypes {
                    public static final PacketType<?> CLIENTBOUND_FOO = createClientbound("foo");
                    public static final PacketType<?> CLIENTBOUND_BAR = createClientbound("bar");
                    public static final PacketType<?> CLIENTBOUND_BRANCHY = createClientbound("branchy");
                    public static final PacketType<?> SERVERBOUND_PING = createServerbound("ping_it");
                    public static final PacketType<?> CLIENTBOUND_CONTAINER = createClientbound("container");
                    static <T> PacketType<T> createClientbound(String s) { return new PacketType<>(); }
                    static <T> PacketType<T> createServerbound(String s) { return new PacketType<>(); }
                }
                """);
        // write 메서드 방식: 필드 순서대로 버퍼에 쓴다 (varint, 목록<문자열>, 선택<uuid>)
        STUBS.put("net.minecraft.network.protocol.game.ClientboundFooPacket", """
                package net.minecraft.network.protocol.game;
                import java.util.*;
                import net.minecraft.network.FriendlyByteBuf;
                import net.minecraft.network.codec.*;
                import net.minecraft.network.protocol.*;
                public class ClientboundFooPacket implements Packet<Object> {
                    public static final StreamCodec<FriendlyByteBuf, ClientboundFooPacket> STREAM_CODEC = Packet.codec(ClientboundFooPacket::write, ClientboundFooPacket::new);
                    private final int id; private final List<String> names; private final Optional<UUID> who; private final long time;
                    private ClientboundFooPacket(FriendlyByteBuf b) { id = 0; names = null; who = null; time = 0; }
                    private void write(FriendlyByteBuf b) {
                        b.writeVarInt(this.id);
                        b.writeCollection(this.names, FriendlyByteBuf::writeUtf);
                        b.writeOptional(this.who, FriendlyByteBuf::writeUUID);
                        b.writeLong(this.time);
                    }
                    public PacketType<ClientboundFooPacket> type() { return (PacketType) GamePacketTypes.CLIENTBOUND_FOO; }
                }
                """);
        // composite 방식
        STUBS.put("net.minecraft.network.protocol.game.ClientboundBarPacket", """
                package net.minecraft.network.protocol.game;
                import net.minecraft.network.codec.*;
                import net.minecraft.network.protocol.*;
                public record ClientboundBarPacket(int id, String name) implements Packet<Object> {
                    public static final StreamCodec<Object, ClientboundBarPacket> STREAM_CODEC = StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, ClientboundBarPacket::id, ByteBufCodecs.STRING_UTF8, ClientboundBarPacket::name, ClientboundBarPacket::new);
                    public PacketType<ClientboundBarPacket> type() { return (PacketType) GamePacketTypes.CLIENTBOUND_BAR; }
                }
                """);
        // 분기가 있는 쓰기 메서드 → 손으로 남는 것
        STUBS.put("net.minecraft.network.protocol.game.ClientboundBranchyPacket", """
                package net.minecraft.network.protocol.game;
                import net.minecraft.network.FriendlyByteBuf;
                import net.minecraft.network.codec.*;
                import net.minecraft.network.protocol.*;
                public class ClientboundBranchyPacket implements Packet<Object> {
                    public static final StreamCodec<FriendlyByteBuf, ClientboundBranchyPacket> STREAM_CODEC = Packet.codec(ClientboundBranchyPacket::write, ClientboundBranchyPacket::new);
                    private final boolean flag; private final long a; private final long b;
                    private ClientboundBranchyPacket(FriendlyByteBuf buf) { flag = false; a = 0; b = 0; }
                    private void write(FriendlyByteBuf buf) {
                        buf.writeBoolean(this.flag);
                        if (this.flag) buf.writeLong(this.a); else buf.writeLong(this.b);
                    }
                    public PacketType<ClientboundBranchyPacket> type() { return (PacketType) GamePacketTypes.CLIENTBOUND_BRANCHY; }
                }
                """);
        // 값 코덱을 익명 클래스로 만든 잎(새 판에서 생기는 꼴) — 표에 없어도 encode 몸통에서 읽는다
        STUBS.put("net.minecraft.network.protocol.game.ClientboundContainerPacket", """
                package net.minecraft.network.protocol.game;
                import net.minecraft.network.codec.*;
                import net.minecraft.network.protocol.*;
                public record ClientboundContainerPacket(int containerId, boolean on) implements Packet<Object> {
                    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ClientboundContainerPacket> STREAM_CODEC = StreamCodec.composite(
                            ByteBufCodecs.CONTAINER_ID, ClientboundContainerPacket::containerId, ByteBufCodecs.BOOL, ClientboundContainerPacket::on, ClientboundContainerPacket::new);
                    public PacketType<ClientboundContainerPacket> type() { return (PacketType) GamePacketTypes.CLIENTBOUND_CONTAINER; }
                }
                """);
        // 필드 없는 서버쪽 패킷(unit)
        STUBS.put("net.minecraft.network.protocol.game.ServerboundPingPacket", """
                package net.minecraft.network.protocol.game;
                import net.minecraft.network.codec.*;
                import net.minecraft.network.protocol.*;
                public class ServerboundPingPacket implements Packet<Object> {
                    public static final ServerboundPingPacket INSTANCE = new ServerboundPingPacket();
                    public static final StreamCodec<Object, ServerboundPingPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);
                    public PacketType<ServerboundPingPacket> type() { return (PacketType) GamePacketTypes.SERVERBOUND_PING; }
                }
                """);
    }

    private static final class Src extends SimpleJavaFileObject {
        private final String code;

        Src(String name, String code) {
            super(java.net.URI.create("string:///" + name.replace('.', '/') + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignore) { return code; }
    }

    private static Path stubJar(Path dir) throws IOException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);
        List<JavaFileObject> files = new ArrayList<>();
        STUBS.forEach((n, c) -> files.add(new Src(n, c)));
        boolean ok = jc.getTask(null, null, null, List.of("-d", classes.toString(), "-proc:none", "-Xlint:none"), null, files).call();
        assertTrue(ok, "stub sources do not compile");
        Path jar = dir.resolve("stub.jar");
        try (OutputStream os = Files.newOutputStream(jar); ZipOutputStream z = new ZipOutputStream(os); Stream<Path> w = Files.walk(classes)) {
            for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
                z.putNextEntry(new ZipEntry(classes.relativize(p).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(p));
                z.closeEntry();
            }
        }
        return jar;
    }

    private static PacketLayout.Packet find(List<PacketLayout.Packet> ps, String wire) {
        return ps.stream().filter(p -> wire.equals(p.wireName())).findFirst().orElseThrow(() -> new AssertionError("no packet " + wire + " in " + ps));
    }

    @Test
    void readsWriteMethodCompositeUnitAndAnonymousLeaf(@TempDir Path dir) throws IOException {
        List<PacketLayout.Packet> ps = PacketLayout.fromJar(stubJar(dir));
        assertEquals(5, ps.size(), ps.toString());

        PacketLayout.Packet foo = find(ps, "foo");
        assertEquals("game", foo.state());
        assertEquals(false, foo.serverbound());
        assertEquals("codec", foo.form());
        assertNull(foo.custom());
        assertEquals("varint list<string> optional<uuid> i64", foo.layout());
        assertEquals("id", foo.ops().get(0).field());
        assertEquals("names", foo.ops().get(1).field());

        PacketLayout.Packet bar = find(ps, "bar");
        assertEquals("composite", bar.form());
        assertNull(bar.custom());
        assertEquals("varint string", bar.layout());

        PacketLayout.Packet container = find(ps, "container");
        assertNull(container.custom());
        assertEquals("varint bool", container.layout(), "CONTAINER_ID 는 표에 없지만 익명 클래스 encode 에서 varint 로 읽는다");

        PacketLayout.Packet ping = find(ps, "ping_it");
        assertEquals(true, ping.serverbound());
        assertEquals("unit", ping.form());
        assertEquals("", ping.layout());
        assertNull(ping.custom());

        PacketLayout.Packet branchy = find(ps, "branchy");
        assertEquals("branch", branchy.custom());
    }

    @Test
    void lockLinesHideNamesAndSortStably(@TempDir Path dir) throws IOException {
        List<PacketLayout.Packet> ps = PacketLayout.fromJar(stubJar(dir));
        List<String> lock = PacketLayout.lockLines(ps);
        Path again = Files.createDirectories(dir.resolve("again"));
        assertEquals(lock, PacketLayout.lockLines(PacketLayout.fromJar(stubJar(again))), "같은 입력이면 같은 줄");
        String all = String.join("\n", lock);
        assertTrue(all.contains("game\tC\tfoo\tcodec\t-\tvarint@id list<string>@names optional<uuid>@who i64@time"), all);
        assertTrue(all.contains("game\tS\tping_it\tunit\t-\t-"), all);
        assertTrue(all.contains("game\tC\tbranchy\tcodec\tbranch\t"), all);
        assertNotNull(all);
    }

    @Test
    void opaqueReferencesBecomeFingerprints() {
        assertEquals("list<ref#" + PacketLayout.fingerprint("Foo.CODEC") + ">", PacketLayout.neutralToken("list<ref(Foo.CODEC)>"));
        assertEquals("?", PacketLayout.neutralToken("?Some.thing"));
        assertEquals("map<identifier,?>", PacketLayout.neutralToken("map<identifier,?lambda-branch>"));
    }
}
