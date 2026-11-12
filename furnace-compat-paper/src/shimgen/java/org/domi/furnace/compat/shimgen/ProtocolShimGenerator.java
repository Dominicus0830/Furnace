package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.ProtocolIds;

/**
 * 패킷 등록부 shim 생성기(N3 1단계). 번호 표({@link ProtocolIds})와 스키마({@link PacketSchema})에서
 * 바닐라 모양의 {@code <상태>PacketTypes}(종류 상수)와 {@code <프로토콜>Protocols}(방향별 등록부 틀)를 만든다.
 * 인자: 출력 폴더. 클래스·필드 이름은 바닐라(Mojang)와 같다 — 플러그인이 리플렉션으로 이 이름들의 클래스를 찾는다.
 */
public final class ProtocolShimGenerator {

    private static final String PKG = "net.minecraft.network.protocol.";
    private static final String HEADER = "// 생성됨: furnace-compat-paper shimgen ← furnace-api packet/schema/protocol-767-ids.txt·protocol-767.schema. 고치지 말 것(표·스키마를 고친다).\n";

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        ProtocolShimGenerator g = new ProtocolShimGenerator(ProtocolIds.protocol767(), PacketSchema.protocol767());
        g.generate(out);
    }

    private final ProtocolIds ids;
    private final PacketSchema schema;

    ProtocolShimGenerator(ProtocolIds ids, PacketSchema schema) {
        this.ids = ids;
        this.schema = schema;
    }

    void generate(Path out) throws IOException {
        List<String> holders = ids.types().stream().map(ProtocolIds.Type::holder).distinct().toList();
        for (String h : holders) write(out, PKG + h + "." + cap(h) + "PacketTypes", packetTypes(h));
        for (String p : ids.protocols()) write(out, PKG + p + "." + cap(p) + "Protocols", protocols(p));
    }

    static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static String constant(PacketDirection d, String wire) { return d.name() + "_" + wire.toUpperCase(Locale.ROOT); }

    private PacketDef def(String holder, PacketDirection d, String wire) {
        return schema.packet(holder, d, wire).orElseThrow(() -> new IllegalStateException("스키마에 없는 패킷 종류: " + holder + "/" + d + "/" + wire));
    }

    private String packetTypes(String holder) {
        String cls = cap(holder) + "PacketTypes";
        StringBuilder b = new StringBuilder(HEADER);
        b.append("package ").append(PKG).append(holder).append(";\n\n");
        b.append("/** ").append(holder).append(" 패킷 종류 상수(Mojang 이름, 번호 표에서 생성). 형식 인자가 그 종류의 패킷 클래스다. */\n");
        b.append("public class ").append(cls).append(" {\n\n");
        for (ProtocolIds.Type t : ids.types(holder)) {
            PacketDef d = def(holder, t.direction(), t.wireName());
            b.append("    public static final net.minecraft.network.protocol.PacketType<").append(d.binaryName().replace('$', '.')).append("> ")
                    .append(constant(t.direction(), t.wireName())).append(" = create(net.minecraft.network.protocol.PacketFlow.")
                    .append(t.direction().name()).append(", \"").append(t.wireName()).append("\");\n");
        }
        b.append("\n    private static <T extends net.minecraft.network.protocol.Packet<?>> net.minecraft.network.protocol.PacketType<T> create(net.minecraft.network.protocol.PacketFlow flow, String name) {\n");
        b.append("        return new net.minecraft.network.protocol.PacketType<>(flow, net.minecraft.resources.ResourceLocation.withDefaultNamespace(name));\n    }\n}\n");
        return b.toString();
    }

    private static String connectionProtocol(String protocol) {
        return switch (protocol) {
            case "handshake" -> "HANDSHAKING";
            case "game" -> "PLAY";
            case "status" -> "STATUS";
            case "login" -> "LOGIN";
            case "configuration" -> "CONFIGURATION";
            default -> throw new IllegalArgumentException(protocol);
        };
    }

    private String listener(String protocol, PacketDirection d) {
        String side = d == PacketDirection.CLIENTBOUND ? "Client" : "Server";
        return PKG + protocol + "." + side + cap(protocol) + "PacketListener";
    }

    private String protocols(String protocol) {
        StringBuilder b = new StringBuilder(HEADER);
        b.append("package ").append(PKG).append(protocol).append(";\n\n");
        b.append("/** ").append(protocol).append(" 프로토콜 등록부 틀(Mojang 이름, 번호 표에서 생성). 목록 순서가 와이어 번호다. */\n");
        b.append("public class ").append(cap(protocol)).append("Protocols {\n\n");
        for (PacketDirection d : PacketDirection.values()) {
            List<ProtocolIds.Entry> list = ids.entries(protocol, d);
            if (list.isEmpty()) continue;
            String name = d.name();
            b.append("    public static final net.minecraft.network.ProtocolInfo.Unbound<").append(listener(protocol, d)).append(", io.netty.buffer.ByteBuf> ")
                    .append(name).append("_TEMPLATE = org.domi.furnace.compat.nms.ShimProtocols.unbound(\n")
                    .append("            net.minecraft.network.ConnectionProtocol.").append(connectionProtocol(protocol))
                    .append(", net.minecraft.network.protocol.PacketFlow.").append(name);
            for (ProtocolIds.Entry e : list) {
                b.append(",\n            ").append(PKG).append(e.holder()).append(".").append(cap(e.holder())).append("PacketTypes.").append(constant(d, e.wireName()));
            }
            b.append(");\n\n");
            // 게임은 틀만 둔다(바닐라도 연결할 때 바이트 버퍼를 묶는다). 나머지는 묶은 것도 바닐라처럼 노출한다.
            if (!protocol.equals("game")) {
                b.append("    public static final net.minecraft.network.ProtocolInfo<").append(listener(protocol, d)).append("> ").append(name)
                        .append(" = ").append(name).append("_TEMPLATE.bind(java.util.function.Function.identity());\n\n");
            }
        }
        b.setLength(b.length() - 1);
        b.append("}\n");
        return b.toString();
    }

    private static void write(Path out, String fqcn, String text) throws IOException {
        Path file = out.resolve(fqcn.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
