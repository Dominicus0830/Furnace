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

    /** 모든 옵션(섹션 순서대로). 새 키는 OptionKeys 키 표와 여기 둘 다에 넣는다 — FurnacePropertiesFileTest 가 빠진 키를 잡는다. */
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
                commented("worldgen-hooks.overworld", "", "오버월드에 걸 플러그인 월드젠 훅 키(namespace:path). 주석이면 바닐라."),
                commented("worldgen-hooks.the_nether", "", "네더에 걸 플러그인 월드젠 훅 키. 위와 같다."),
                commented("worldgen-hooks.the_end", "", "엔드에 걸 플러그인 월드젠 훅 키. 위와 같다."),
                opt("enchantments.disabled", "", "끌 마법부여 id(쉼표로 구분, 예: minecraft:mending). 비우면 모두 켜짐(바닐라).")
        )));
        s.add(new Section("틱·region 스레드", List.of(
                opt("tick-nanos", d.tickNanos(), "한 틱의 길이(나노초). 50000000 = 초당 20틱(바닐라)."),
                commented("region-threads", "", "region 을 돌리는 스레드 수(공유 풀이면 서버 전체, 끄면 차원마다). 주석이면 CPU 코어 수."),
                opt("shared-region-pool", RegionPoolSettings.DEFAULT.shared(), "true 면 모든 차원·추가 월드가 region 워커 풀 하나를 함께 쓴다. false 면 차원마다 region-threads 개."),
                opt("region-pool-dim-cap", RegionPoolSettings.DEFAULT.dimCap(), "공유 풀에서 다른 차원에 일이 있을 때 한 차원이 쓸 수 있는 워커 상한(0 = 워커 수−1)."),
                opt("region-pool-starve-ticks", RegionPoolSettings.DEFAULT.starveTicks(), "차원의 가장 이른 일이 이 틱 수만큼 밀리면 그 차원을 먼저 돌린다. 1 이상."),
                opt("region-section-shift", d.regionSectionShift(), "구역 크기 = 2^값 청크(4 → 16×16 청크). 1~8."),
                opt("region-merge-radius", d.regionMergeRadius(), "이 반경(구역 단위) 안의 구역은 같은 region 으로 합친다. 1 이상.")
        )));
        s.add(new Section("채팅·운영", List.of(
                opt("chat.strict-order", o.strictChatOrder(), "true(바닐라)면 순서가 어긋난 채팅에서 서명 체인을 끊는다. false 면 그 메시지만 버린다."),
                opt("chat.unsigned-as-system", o.unsignedChatAsSystem(), "true 면 채팅을 서명 없는 시스템 메시지로 보낸다(채팅 신고 불가)."),
                opt("server-brand", o.serverBrand(), "F3 화면에 보이는 서버 브랜드."),
                opt("commands.send-namespaced", o.sendNamespacedCommands(), "true(기본, Paper 와 같다)면 minecraft:give 같은 바닐라 명령 별칭이 자동완성에 보인다. false 면 안 보이지만 쳐서 실행은 된다."),
                opt("announce-sleep-status", o.announceSleepStatus(), "false 면 자는 사람 수 액션바를 보내지 않는다(기본 true = 바닐라)."),
                opt("world.tick-when-empty", o.tickWhenEmpty(), "플레이어도 /forceload 도 없는 차원의 몹·호퍼: vanilla 면 15초 뒤 쉰다(바닐라), always 면 늘 돈다(Paper·Spigot 기본), auto(기본)면 Bukkit 플러그인이 있을 때만 always."),
                opt("gravity-block-portal-dupe", o.gravityBlockPortalDupe(), "true(기본 = 바닐라) 면 엔드 포털로 넘어간 모래·자갈 같은 중력 블록이 제자리에도 놓여 복제된다. false 면 복제되지 않는다."),
                opt("motd-format", o.motdFormat(), "server.properties 의 motd 해석: plain(기본, 바닐라) | legacy(& 색 코드) | markup(<gradient:red:blue>…</gradient> 같은 태그)."),
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
        s.add(new Section("네트워크", List.of(
                opt("io-uring", org.domi.furnace.server.net.TransportOptions.DEFAULT.ioUring(), "리눅스 io_uring 으로 접속을 받는다. 못 쓰면 경고하고 epoll/NIO 로 돌아간다.")
        )));
        var rcon = org.domi.furnace.server.remote.RconLimits.DEFAULT;
        s.add(new Section("RCON 한도(server.properties enable-rcon=true 일 때)", List.of(
                opt("rcon.max-clients", rcon.maxClients(), "RCON 동시 연결 수 상한."),
                opt("rcon.max-clients-per-ip", rcon.maxClientsPerIp(), "주소 하나당 동시 연결 수 상한."),
                opt("rcon.max-fails-per-connection", rcon.maxFailsPerConnection(), "연결 하나당 인증 실패 허용 수."),
                opt("rcon.max-fails-per-ip", rcon.maxFailsPerIp(), "주소 하나당 60초 인증 실패 허용 수.")
        )));
        var m = org.domi.furnace.server.metrics.MetricsOptions.DEFAULT;
        s.add(new Section("메트릭", List.of(
                opt("metrics.prometheus.enabled", m.prometheus(), "Prometheus 형식 메트릭을 HTTP GET /metrics 로 내보낸다."),
                opt("metrics.prometheus.bind", m.bind(), "바인딩 주소(루프백이 아니면 token 을 꼭 정할 것)."),
                opt("metrics.prometheus.port", m.port(), "포트(0 = 빈 포트)."),
                opt("metrics.prometheus.token", m.token(), "비어 있지 않으면 Authorization: Bearer <값> 이 맞아야 응답한다."),
                opt("metrics.jmx.enabled", m.jmx(), "같은 값을 JMX MBean 으로 등록한다.")
        )));
        var mt = org.domi.furnace.server.maintenance.MaintenanceOptions.DEFAULT;
        s.add(new Section("빈 서버 일시정지·주기 백업(기본 꺼짐)", List.of(
                opt("idle-pause.empty-seconds", mt.idlePauseSeconds(), "접속자가 없으면 이 초 뒤 게임 틱을 멈춘다. 0 = 끔(바닐라)."),
                opt("idle-pause.flush-caches", mt.idlePauseFlush(), "일시정지할 때 캐시를 비운다."),
                opt("backup.interval-minutes", mt.backupIntervalMinutes(), "주기 백업 간격(분). 0 = 끔."),
                opt("backup.directory", "", "백업 폴더. 비우면 " + mt.backupDirectory() + "."),
                opt("backup.keep", mt.backupKeep(), "남길 백업 수. 0 = 지우지 않음."),
                opt("backup.format", mt.backupFormat().name().toLowerCase(java.util.Locale.ROOT), "zip 또는 folder."),
                opt("backup.skip-if-no-players", mt.backupSkipIdle(), "지난 백업 뒤 접속자가 없었으면 건너뛴다."),
                opt("backup.hold-timeout-seconds", mt.backupHoldTimeoutSeconds(), "백업 기준점에서 저장을 기다리는 최대 초(1~600).")
        )));
        s.add(new Section("설정 도구·콘솔(/furnace config 로 적용된 값을 본다)", List.of(
                opt("config-version", ConfigMigration.CURRENT, "설정 파일 형식 버전. 키 이름·기본값이 바뀌면 서버가 파일을 옮기고 이 값을 올린다(원본은 .bak). 지우지 마세요."),
                opt("config.preset", "none", "none | vanilla | performance. 기본값 그대로인 키를 프리셋 값으로 바꾼다(기본값과 다르게 적은 키가 이긴다)."),
                opt("jvm.check", true, "기동 때 Java 버전·힙·GC 옵션을 점검해 경고한다."),
                opt("console.jline", true, "콘솔 줄 편집·기록·Tab 자동완성(터미널이 아니면 자동으로 꺼진다)."),
                opt("console.history-size", org.domi.furnace.server.ConsoleInput.DEFAULT_HISTORY_SIZE, "콘솔 명령 기록 줄 수. 0 이면 .console_history 파일에 남기지 않는다.")
        )));
        s.add(new Section("플러그인", List.of(
                opt("plugins.fail-fast", false, "플러그인 하나라도 불러오기·켜기에 실패하면 서버 시작을 멈춘다."),
                opt("plugins.data-root", "", "비우면 데이터 폴더 = plugins/<이름>/, 쓰면 <값>/<이름>/."),
                opt("plugins.dev-mode", false, "개발 모드(플러그인 핫 리로드 /plugins reload <이름>)."),
                opt("plugins.dev-mode.auto-reload", false, "개발 모드에서 plugins/*.jar 가 바뀌면 알아서 다시 읽는다."),
                commented("plugins.log-level.<플러그인이름>", "INFO", "플러그인별 로그 수준(SEVERE, WARNING, INFO, FINE ...)."),
                opt("plugins.compat.bukkit", true, "Bukkit·Paper 플러그인(plugin.yml) 호환 어댑터. 끄면 plugins/bukkit-plugins/ 와 plugin.yml jar 를 읽지 않는다."),
                opt("plugins.compat.nms.enabled", true, "Bukkit 호환 플러그인의 NMS·CraftBukkit 이름 변환(CraftBukkit 버전 접미사 v1_21_R1 을 접미사 없는 이름으로). 끄면 그런 이름을 쓰는 플러그인은 그 자리에서 실패한다."),
                opt("plugins.compat.nms.strict", false, "미지원 NMS 심볼을 쓰는 Bukkit 호환 플러그인은 불러오지 않는다. 끄면 그 호출만 실행 때 실패하고 나머지는 돈다."),
                opt("plugins.compat.nms.version-package", "plain", "plain | versioned. 플러그인이 읽는 CraftBukkit 패키지 이름(plain = Paper 처럼 접미사 없음, versioned = Spigot 처럼 v1_21_R1)."),
                opt("plugins.compat.nms.spigot-mappings", org.domi.furnace.server.plugin.PluginSettings.SPIGOT_MAPPINGS_DEFAULT, "download | files | off. download(기본)면 Spigot 이름 NMS 플러그인이 있을 때만 Mojang·Spigot 매핑을 처음 한 번 받아 Spigot → Mojang 이름으로 바꿔 올린다(받은 파일은 캐시에만 남는다, 그런 플러그인이 없으면 받지 않는다). files 면 받지 않고 아래 키의 세 파일을 쓴다. off 면 바꾸지 않는다(그 NMS 호출은 실패)."),
                opt("plugins.compat.nms.spigot-mappings.files", "", "spigot-mappings=files 일 때: Mojang server.txt, Spigot 클래스 csrg, Spigot 멤버 csrg 경로를 경로 구분자(윈도 ; 그 밖 :)로 이어 적는다."),
                opt("plugins.chunk-tickets.max-per-plugin", org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER, "플러그인 하나가 청크 티켓(chunkTickets API)으로 붙잡을 수 있는 청크 수 상한(겹침 포함, 1 이상). 넘으면 add 가 거부된다."),
                opt("plugins.compat.config.paper-default-separator", false, "Bukkit 설정에서 플러그인이 options().pathSeparator 를 바꿨을 때 addDefault 기본값 트리를 Paper 처럼 언제나 '.' 로 쪼갠다(getDefaults() 구분자도 '.'). 끄면 플러그인이 정한 구분자로 쪼갠다. 켜면 저장 파일 모양·contains·getKeys 와 setDefaults 와 섞어 쓸 때·저장 뒤 다시 읽을 때의 값도 Paper 와 같아진다(Paper 처럼 다시 읽으면 기본값이 빠질 수 있다)."),
                opt("plugins.compat.scheduler.async-threads", "platform", "Bukkit 비동기 작업(runTaskAsynchronously 등)을 도는 스레드. platform: 이름 붙은 플랫폼 스레드 풀(Paper 와 같은 모양 — 코어 4, 늘어나면 무제한, 30초 놀면 정리). virtual: 가상 스레드(JDK 21 에서는 synchronized 안에서 소켓·파일을 기다리는 옛 DB 드라이버가 운반 스레드를 붙잡을 수 있다)."),
                opt("plugins.compat.libraries", "needed", "Paper 가 싣는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등)를 Furnace jar 에 넣지 않고 필요할 때 받는다. needed: 호환 플러그인 클래스가 쓰는 것만, all: 호환 플러그인이 있으면 전부, off: 받지 않는다(그것을 쓰는 플러그인은 실패)."),
                opt("plugins.compat.libraries.mojang", true, "Brigadier·authlib·DataFixerUpper 는 Mojang 저장소(libraries.minecraft.net)에서 받는다. false 면 그 셋은 빼고 경고."),
                opt("plugins.compat.libraries.declared", true, "호환 플러그인 plugin.yml 의 libraries 를 Paper·Spigot 처럼 받는다(plugins.libraries.enabled 와 별개)."),
                opt("plugins.libraries.enabled", false, "Furnace 플러그인 설명 파일의 libraries 를 Maven Central 에서 받아 붙인다."),
                commented("plugins.libraries.offline", false, "캐시에 있는 것만 쓰고 네트워크에 나가지 않는다(위 내려받기 모두에 적용)."),
                commented("plugins.libraries.transitive", true, "라이브러리의 compile/runtime 의존까지 받는다(enabled=true 일 때만)."),
                commented("plugins.libraries.require-checksum", false, "체크섬 기록이 없는 jar 는 받지 않는다(enabled=true 일 때만)."),
                opt("plugins.libraries.cache", "", "받은 라이브러리 폴더. 비우면 plugins/.libraries."),
                opt("plugins.libraries.timeout-seconds", 30, "요청 하나의 제한 시간(1~600초)."),
                opt("plugins.libraries.max-size-mb", 64, "파일 하나의 크기 상한(1~1024MB).")
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
