package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `--mode packet-layout` (T-250). 바닐라 서버 jar(Mojang 이름)의 패킷 코덱 와이어 모양을 뽑는다.
 *
 * <ul>
 *   <li>캐시 {@code <캐시>/<판>/packet-layout.tsv} — 전체 표(Mojang 클래스 이름 포함, 커밋 금지)</li>
 *   <li>{@code --layout-install}: 중립 잠금 {@code furnace-api/.../packet/schema/protocol-<프로토콜>.layout} 을 저장소에 쓴다
 *       (Mojang 이름이 든 불투명 참조는 이름의 해시 앞 8자리로 바꾼다)</li>
 *   <li>{@code --layout-verify}: 새로 뽑은 잠금을 저장소에 커밋된 것과 견준다(다르면 실패 — 판을 올리거나 추출기를 고친 뒤 확인)</li>
 * </ul>
 */
final class PacketLayoutCommand {
    static final String LOCK_DIR = "furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/";
    static final String MAGIC = "#furnace-packet-layout";

    private PacketLayoutCommand() {}

    static boolean run(Options o) throws IOException {
        Path dir = o.cache.resolve(o.mc);
        Files.createDirectories(dir);
        Prepared p = new Preparer(o).prepare(false);
        List<PacketLayout.Packet> packets = PacketLayout.fromJar(p.serverJar());
        Path full = dir.resolve("packet-layout.tsv");
        Files.write(full, PacketLayout.fullLines(packets), StandardCharsets.UTF_8);
        long custom = packets.stream().filter(x -> x.custom() != null).count();
        System.out.println("[패킷 모양] " + o.mc + ": 패킷 " + packets.size() + "개, 손으로 남는 것 " + custom + "개 → " + full);
        int protocol = o.protocol >= 0 ? o.protocol : p.protocol() >= 0 ? p.protocol() : jarProtocol(p.serverJar());
        if (protocol < 0) throw new IllegalStateException("프로토콜 번호를 모른다(--protocol)");
        List<String> lock = lockFile(protocol, o.mc, packets);
        Path lockPath = o.repo.resolve(LOCK_DIR).resolve("protocol-" + protocol + ".layout");
        if (o.layoutInstall) {
            Files.write(lockPath, lock, StandardCharsets.UTF_8);
            System.out.println("[패킷 모양] 잠금 파일 → " + lockPath);
        }
        if (o.layoutVerify) {
            if (!Files.isRegularFile(lockPath)) {
                System.out.println("[패킷 모양] 커밋된 잠금이 없다: " + lockPath);
                return false;
            }
            List<String> committed = Files.readAllLines(lockPath, StandardCharsets.UTF_8);
            if (!committed.equals(lock)) {
                System.out.println("[패킷 모양] 새로 뽑은 잠금이 커밋된 것과 다르다: " + lockPath);
                for (int i = 0; i < Math.max(committed.size(), lock.size()); i++) {
                    String a = i < committed.size() ? committed.get(i) : "(없음)";
                    String b = i < lock.size() ? lock.get(i) : "(없음)";
                    if (!a.equals(b)) System.out.println("  - " + a + "\n  + " + b);
                }
                return false;
            }
            System.out.println("[패킷 모양] 커밋된 잠금과 같다(" + packets.size() + "개)");
        }
        return !packets.isEmpty();
    }

    /** 서버 jar 안의 version.json 에서 프로토콜 번호를 읽는다(없으면 -1). */
    static int jarProtocol(Path jar) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry e = zf.getEntry("version.json");
            if (e == null) return -1;
            try (java.io.InputStream in = zf.getInputStream(e)) {
                com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return j.has("protocol_version") ? j.get("protocol_version").getAsInt() : -1;
            }
        }
    }

    /** 잠금 파일 본문(머리말 + 정렬된 줄). */
    static List<String> lockFile(int protocol, String mc, List<PacketLayout.Packet> packets) {
        List<String> out = new ArrayList<>();
        out.add(MAGIC + " 1 " + protocol);
        out.add("# 바닐라 " + mc + " 서버 jar 의 패킷 코덱 바이트코드에서 기계로 뽑은 와이어 모양(tools/vanilla-extract -Pmode=packet-layout). 손으로 고치지 않는다.");
        out.add("# 열(탭): 상태, 방향(C 클라이언트로·S 서버로), 와이어 이름, 방식(composite·codec·unit·expr·multi·none), 손으로 남는 이유 종류(없으면 -), 와이어 토큰(필드)");
        out.add("# 토큰: 스키마 와이어 이름(varint·i32·string·uuid·identifier·block_pos·nbt…), list<x>·optional<x>·map<k,v>, ref#해시(Mojang 이름이 든 안쪽 값 코덱의 이름 지문), ?(못 푼 것).");
        out.add("# 손으로 남는 이유가 있는 줄의 토큰은 분기 전까지의 일부일 수 있다(대조 시험은 이런 줄의 순서·개수를 믿지 않는다).");
        out.addAll(PacketLayout.lockLines(packets));
        return out;
    }
}
