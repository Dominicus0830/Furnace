// furnace.properties 자동 생성: 없으면 모든 옵션을 기본값·설명과 함께 쓰고, 있으면 빠진 옵션만 끝에 덧붙인다(바닐라 server.properties 처럼).
// 사용자가 적은 값·주석·순서는 건드리지 않는다. 기본값 그대로 쓰므로 파일이 생겨도 서버 동작은 바뀌지 않는다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.server.net.ConnectionLimits;
import org.domi.furnace.server.net.protocol.ConnectionState;

public final class FurnacePropertiesFile {

    private FurnacePropertiesFile() {}

    /**
     * 옵션 한 줄. commented 면 "#key=value" 로 쓴다 — 비워 두는 것과 적는 것이 뜻이 다르거나(신뢰 목록·차원 시드),
     * 기본값이 기계마다 달라(코어 수) 고정하면 안 되는 옵션.
     */
    record Option(String key, String value, boolean commented, String help) {}

    record Section(String title, List<Option> options) {}

    public enum Result { CREATED, APPENDED, UNCHANGED }

    private static Option opt(String key, Object value, String help) { return new Option(key, String.valueOf(value), false, help); }

    private static Option commented(String key, Object value, String help) { return new Option(key, String.valueOf(value), true, help); }

    /** 모든 옵션(섹션 순서대로). 새 키를 읽는 코드를 넣으면 여기에도 넣는다 — FurnacePropertiesFileTest 가 빠진 키를 잡는다. */
    static List<Section> sections() {
        Config d = Config.DEFAULT;
        OperatorOptions o = OperatorOptions.DEFAULT;
        List<Section> s = new ArrayList<>();
        s.add(new Section("청크 전송·월드", List.of(
                opt("chunks-per-tick", "auto",
                        "플레이어 한 명에게 한 틱에 보내는 청크 수. auto = 클라이언트가 받을 수 있다고 알리는 만큼(최대 64, 바닐라와 같다), "
                                + "이미 만들어진 청크는 생성 중인 청크를 기다리지 않고 바로 보낸다. 숫자(1 이상)를 쓰면 그 수로 고정한다(예전 동작, 예전 기본 16)."),
                opt("spawn-chunk-radius", d.spawnChunkRadius(), "접속 직후 한 묶음으로 먼저 보내는 스폰 주변 청크 반경(2 → 5×5)."),
                opt("io-threads", d.ioThreads(), "청크 디스크 읽기·쓰기 스레드 수."),
                opt("autosave-ticks", d.autosaveTicks(), "자동 저장 간격(틱). 6000 = 5분(바닐라)."),
                opt("chunk-eviction-grace-ticks", d.chunkEvictionGraceTicks(), "아무도 보지 않는 청크를 메모리에서 내리기 전 기다리는 틱."),
                opt("entity-view-distance", d.entityViewDistance(), "엔티티가 보이는 거리(청크). view-distance 보다 크면 view-distance 까지만."),
                opt("worlds.autoload", "", "서버 시작 때 함께 여는 추가 월드 이름(쉼표로 구분)."),
                commented("dimension-seed.the_nether", "", "네더 생성 시드. 주석이면 주 월드 시드(바닐라). 숫자 또는 글자."),
                commented("dimension-seed.the_end", "", "엔드 생성 시드. 위와 같다."),
                opt("enchantments.disabled", "", "끌 마법부여 id(쉼표로 구분, 예: minecraft:mending). 비우면 모두 켜짐(바닐라).")
        )));
        s.add(new Section("틱·region 스레드", List.of(
                opt("tick-nanos", d.tickNanos(), "한 틱의 길이(나노초). 50000000 = 초당 20틱(바닐라)."),
                commented("region-threads", "", "region 을 돌리는 스레드 수. 주석이면 CPU 코어 수."),
                opt("region-section-shift", d.regionSectionShift(), "구역 크기 = 2^값 청크(4 → 16×16 청크). 1~8."),
                opt("region-merge-radius", d.regionMergeRadius(), "이 반경(구역 단위) 안의 구역은 같은 region 으로 합친다. 1 이상.")
        )));
        s.add(new Section("채팅·운영", List.of(
                opt("chat.strict-order", o.strictChatOrder(), "true(바닐라)면 순서가 어긋난 채팅에서 서명 체인을 끊는다. false 면 그 메시지만 버린다."),
                opt("chat.unsigned-as-system", o.unsignedChatAsSystem(), "true 면 채팅을 서명 없는 시스템 메시지로 보낸다(채팅 신고 불가)."),
                opt("server-brand", o.serverBrand(), "F3 화면에 보이는 서버 브랜드."),
                opt("announce-sleep-status", o.announceSleepStatus(), "false 면 자는 사람 수 액션바를 보내지 않는다(기본 true = 바닐라)."),
                opt("confirm-interrupt", o.confirmInterrupt(), "true 면 콘솔 Ctrl+C 를 한 번 더 눌러야 종료한다."),
                opt("confirm-interrupt-seconds", o.interruptConfirmSeconds(), "Ctrl+C 두 번째 입력을 기다리는 시간(1~60초).")
        )));
        s.add(new Section("프록시", List.of(
                opt("velocity-secret", d.velocitySecret(), "Velocity 모던 포워딩 비밀 키. 비우면 끔."),
                opt("bungeecord", d.bungeecord(), "BungeeCord 레거시 IP 포워딩. velocity-secret 과 함께 켤 수 없다."),
                opt("proxy-protocol", ConnectionLimits.DEFAULT.proxyProtocol(), "true 면 모든 접속이 PROXY 프로토콜(v1·v2) 헤더를 먼저 보내야 한다."),
                commented("proxy-protocol.trusted", "", "헤더를 보내도 되는 앞단 주소(쉼표로 구분한 IP/CIDR). 주석이면 모두 허용.")
        )));
        s.add(new Section("접속 방어(기본값 = 바닐라와 같은 결과)", connectionOptions()));
        s.add(new Section("플러그인", List.of(
                opt("plugins.fail-fast", false, "플러그인 하나라도 불러오기·켜기에 실패하면 서버 시작을 멈춘다."),
                opt("plugins.data-root", "", "비우면 데이터 폴더 = plugins/<이름>/, 쓰면 <값>/<이름>/."),
                opt("plugins.dev-mode", false, "개발 모드(플러그인 핫 리로드 /plugins reload <이름>)."),
                opt("plugins.dev-mode.auto-reload", false, "개발 모드에서 plugins/*.jar 가 바뀌면 알아서 다시 읽는다."),
                commented("plugins.log-level.<플러그인이름>", "INFO", "플러그인별 로그 수준(SEVERE, WARNING, INFO, FINE ...).")
        )));
        return s;
    }

    private static List<Option> connectionOptions() {
        ConnectionLimits c = ConnectionLimits.DEFAULT;
        List<Option> l = new ArrayList<>();
        l.add(opt("connection.read-timeout-seconds", c.readTimeoutSeconds(), "아무것도 보내지 않는 소켓을 끊기까지의 초(바닐라 30)."));
        for (ConnectionState st : ConnectionState.values()) {
            String n = st.name().toLowerCase(java.util.Locale.ROOT);
            ConnectionLimits.StateLimits sl = c.of(st);
            l.add(opt("connection." + n + ".packets-per-second", sl.packetsPerSecond(), n + " 단계 초당 패킷 수(토큰 버킷 회복량). 0 = 제한 없음."));
            l.add(opt("connection." + n + ".packets-burst", sl.packetsBurst(), n + " 단계 순간 허용 패킷 수."));
            l.add(opt("connection." + n + ".max-frame-bytes", sl.maxFrameBytes(), n + " 단계에서 받는 패킷 최대 바이트."));
            if (st != ConnectionState.PLAY) {
                l.add(opt("connection." + n + ".timeout-seconds", sl.timeoutSeconds(), n + " 단계에 머물 수 있는 최대 초. 0 = 끔."));
            }
        }
        l.add(opt("limits.chat-length", c.chatLength(), "채팅 최대 글자 수(1~256, 바닐라 256)."));
        l.add(opt("limits.command-length", c.commandLength(), "명령 최대 글자 수(1~256, 바닐라 256)."));
        l.add(opt("limits.item.display-name", c.displayNameLength(), "클라이언트가 보낸 아이템 이름 글자 수 상한. 0 = 끔(바닐라)."));
        l.add(opt("limits.item.item-name", c.itemNameLength(), "아이템 item_name 글자 수 상한. 0 = 끔."));
        l.add(opt("limits.item.lore-line", c.loreLineLength(), "아이템 설명 한 줄 글자 수 상한. 0 = 끔."));
        l.add(opt("limits.item.book-page", c.bookPageLength(), "책 한 쪽 글자 수 상한. 0 = 끔."));
        l.add(opt("limits.item.book-author", c.bookAuthorLength(), "책 저자 글자 수 상한. 0 = 끔."));
        return l;
    }

    /** 이 파일에 쓰는 모든 키. */
    public static Set<String> keys() {
        Set<String> k = new LinkedHashSet<>();
        for (Section s : sections()) for (Option o : s.options()) k.add(o.key());
        return k;
    }

    private static final String HEADER = """
            # Furnace 전용 설정. 서버가 처음 켜질 때 만들고, 새 버전에서 생긴 옵션은 파일 끝에 덧붙인다(적어 둔 값은 그대로 둔다).
            # 바닐라 설정(motd, server-port, network-compression-threshold, view-distance, level-name, level-seed)은 server.properties 에서 정한다.
            # 줄 앞의 # 를 지우면 그 옵션이 적용된다.
            """;

    /**
     * 없으면 모든 옵션으로 만들고, 있으면 아직 적히지 않은 옵션(주석으로 적힌 것 포함)만 덧붙인다.
     * 쓰기에 실패하면 IOException — 호출자는 경고만 남기고 기본값으로 계속한다.
     */
    public static Result ensure(Path path) throws IOException {
        if (!Files.exists(path)) {
            StringBuilder sb = new StringBuilder(HEADER);
            for (Section s : sections()) appendSection(sb, s.title(), s.options());
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            return Result.CREATED;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);   // 잘못된 바이트는 대체 문자로(예외 없음) — 키 찾기에만 쓴다
        Set<String> present = writtenKeys(text);
        StringBuilder sb = new StringBuilder();
        for (Section s : sections()) {
            List<Option> missing = s.options().stream().filter(o -> !present.contains(o.key())).toList();
            if (!missing.isEmpty()) appendSection(sb, s.title() + " (자동 추가)", missing);
        }
        if (sb.isEmpty()) return Result.UNCHANGED;
        String prefix = text.isEmpty() || text.endsWith("\n") ? "" : System.lineSeparator();
        Files.writeString(path, prefix + sb, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        return Result.APPENDED;
    }

    private static void appendSection(StringBuilder sb, String title, List<Option> options) {
        sb.append('\n').append("# ===== ").append(title).append(" =====\n");
        for (Option o : options) {
            sb.append("# ").append(o.help()).append('\n');
            sb.append(o.commented() ? "#" : "").append(o.key()).append('=').append(o.value()).append('\n');
        }
    }

    // 키 줄: 앞 공백, 주석 표시(#·!)와 공백을 지난 뒤 키, 그 뒤 = : 공백 중 하나. 설명 주석("# 플레이어 한 명에게…")은 키 모양이 아니라 걸리지 않는다.
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*[#!]?\\s*([^\\s=:#!]+)\\s*[=:]");

    /** 파일에 이미 적힌 키(주석으로 적힌 것 포함). */
    static Set<String> writtenKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            Matcher m = KEY_LINE.matcher(line);
            if (m.find()) keys.add(m.group(1));
        }
        return keys;
    }
}
