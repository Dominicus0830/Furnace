// furnace.properties 의 모든 키 목록(키 표)과 시작 점검. 새 옵션을 만들면 여기에도 한 줄 넣는다(없으면 시험이 실패한다).
//
// 규칙(R4, docs/feature-parity/option-keys-2026-11-07.md):
//   1. 새 옵션의 기본값은 바닐라와 같은 동작이다.
//   2. 모든 새 옵션은 키로 ON/OFF 또는 수치를 바꿀 수 있다(코드 상수·-D 시스템 속성만으로 두지 않는다).
//   3. 잘못된 값(true/false 가 아닌 글자, 범위 밖 수)은 조용히 넘기지 않고 기동 때 어느 키가 문제인지 알리며 멈춘다.
//   4. 위험한 값(보안·바닐라와 다른 게임 속도 등)은 시작 로그에 경고한다 — {@link #audit}.
//   5. 모르는 키(오타)는 가장 비슷한 키를 알려 주며 경고한다.
package org.domi.furnace.server.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class OptionKeys {

    private OptionKeys() {}

    /** 키 하나의 설명. {@code def} 는 기본값 글자(빈 글자 = 꺼짐/없음), {@code range} 는 허용 값. */
    public record Option(String key, String type, String def, String range, String description) {}

    /** 키가 아니라 이름 접두로 받는 가족(플러그인별 로그 수준, 차원별 시드). */
    private static final List<String> PREFIXES = List.of("plugins.log-level.", "dimension-seed.", "spawn.limit.", "spawn.player-limit.");

    private static final String[] STATES = {"handshake", "status", "login", "configuration", "play"};

    /** furnace.properties 키 표(접속 방어의 단계별 키는 {@link #all()} 이 펼친다). */
    private static final List<Option> FIXED = List.of(
            // 기본 틀
            new Option("tick-nanos", "수", "50000000", "1 이상", "한 틱 길이(나노초). 바꾸면 게임 속도가 바닐라와 달라진다"),
            new Option("entity-view-distance", "수", "8", "1 이상", "엔티티가 보이는 거리(청크). view-distance 보다 크게 효과 없음"),
            new Option("region-section-shift", "수", "4", "1~8", "동적 region 구역 크기(2^값 청크)"),
            new Option("region-merge-radius", "수", "1", "1 이상", "region 합치기 반경(구역 단위)"),
            new Option("region-threads", "수", "CPU 수", "1 이상", "region 작업 스레드 수(공유 풀이면 서버 전체, 끄면 차원마다)"),
            new Option("shared-region-pool", "ON/OFF", "true", "true|false", "모든 차원·추가 월드가 region 워커 풀 하나를 쓴다. false 면 차원마다 region-threads 개"),
            new Option("region-pool-dim-cap", "수", "0", "0 이상", "다른 차원 큐에 일이 있을 때 한 차원이 쓸 수 있는 워커 상한(0 = 워커 수−1)"),
            new Option("region-pool-starve-ticks", "수", "2", "1 이상", "차원의 가장 이른 일이 이 틱 수만큼 밀리면 그 차원을 먼저 돌린다"),
            new Option("io-threads", "수", "4", "1 이상", "청크 입출력 스레드 수"),
            new Option("autosave-ticks", "수", "6000", "1 이상", "자동 저장 간격(틱)"),
            new Option("chunk-eviction-grace-ticks", "수", "100", "1 이상", "쓰지 않는 청크를 내리기 전 유예(틱)"),
            new Option("spawn-chunk-radius", "수", "2", "0 이상", "스폰 청크 반경"),
            new Option("chunks-per-tick", "auto|수", "auto", "auto 또는 1 이상", "플레이어당 틱마다 보내는 청크 수. auto = 클라이언트가 알린 만큼(최대 64, 바닐라), 준비된 청크만 배치로. 수 = 그 수로 고정(예전 동작)"),
            new Option("velocity-secret", "글자", "", "", "비어 있지 않으면 Velocity 모던 포워딩을 켠다(프록시와 같은 비밀값)"),
            new Option("bungeecord", "ON/OFF", "false", "true|false", "BungeeCord 레거시 IP 포워딩. velocity-secret 과 함께 쓸 수 없다"),
            // 운영 옵션(O1)
            new Option("chat.strict-order", "ON/OFF", "true", "true|false", "true(바닐라): 순서가 어긋난 채팅이면 서명 체인을 끊는다. false: 그 메시지만 버린다"),
            new Option("chat.unsigned-as-system", "ON/OFF", "false", "true|false", "채팅을 서명 없는 시스템 메시지로 보낸다(채팅 신고 불가)"),
            new Option("server-brand", "글자", "Furnace", "", "F3 서버 브랜드와 level.dat ServerBrands"),
            new Option("gravity-block-portal-dupe", "ON/OFF", "true", "true|false", "엔드 포털로 넘어간 중력 블록(모래·자갈 등)이 제자리에도 놓이는 바닐라 복제 현상. false 면 복제되지 않는다(바닐라와 달라짐)"),
            new Option("commands.send-namespaced", "ON/OFF", "true", "true|false", "바닐라 명령의 minecraft:이름 별칭을 접속자 명령 트리·자동완성에 보낸다(Paper 와 같다). false 면 안 보여도 입력하면 실행된다"),
            new Option("commands.region-drain", "ON/OFF", "true", "true|false", "명령 큐가 도는 동안 region 에 보낸 급한 작업을 다음 틱을 기다리지 않고 곧바로 돌린다(전역 틱당 명령 블록 예산 20ms 공유, 넘으면 다음 틱). false 면 region 접근 한 줄이 한 틱씩 걸린다"),
            new Option("announce-sleep-status", "ON/OFF", "true", "true|false", "자는 사람 수·밤 건너뛰기 액션바"),
            new Option("motd-format", "plain|legacy|markup", "plain", "plain|legacy|markup", "server.properties motd 해석 방식(plain=바닐라)"),
            new Option("confirm-interrupt", "ON/OFF", "false", "true|false", "콘솔 Ctrl+C 를 두 번 눌러야 종료"),
            new Option("confirm-interrupt-seconds", "수", "5", "1~60", "두 번째 Ctrl+C 를 기다리는 초"),
            // 접속 방어(N2)
            new Option("connection.read-timeout-seconds", "수", "30", "1~3600", "읽기 무응답 제한(초)"),
            new Option("limits.chat-length", "수", "256", "1~256", "채팅 최대 글자 수(바닐라 256)"),
            new Option("limits.command-length", "수", "256", "1~256", "명령 최대 글자 수(바닐라 256)"),
            new Option("limits.item.display-name", "수", "0", "0~1000000", "아이템 표시 이름 글자 수 상한(0 = 바닐라, 상한 없음)"),
            new Option("limits.item.item-name", "수", "0", "0~1000000", "아이템 기본 이름 글자 수 상한(0 = 상한 없음)"),
            new Option("limits.item.lore-line", "수", "0", "0~1000000", "아이템 설명 한 줄 글자 수 상한(0 = 상한 없음)"),
            new Option("limits.item.book-page", "수", "0", "0~1000000", "책 한 쪽 글자 수 상한(0 = 상한 없음)"),
            new Option("limits.item.book-author", "수", "0", "0~1000000", "책 저자 글자 수 상한(0 = 상한 없음)"),
            new Option("proxy-protocol", "ON/OFF", "false", "true|false", "PROXY 프로토콜(HAProxy v1/v2) 헤더로 실제 클라이언트 주소를 받는다"),
            new Option("proxy-protocol.trusted", "목록", "", "IP/CIDR,...", "PROXY 헤더를 받아 줄 앞단 주소(비면 모두 — 위험)"),
            // 플러그인
            new Option("plugins.fail-fast", "ON/OFF", "false", "true|false", "플러그인이 하나라도 실패하면 서버 시작을 멈춘다"),
            new Option("plugins.data-root", "경로", "", "", "플러그인 데이터 폴더 뿌리(비면 plugins/<이름>/)"),
            new Option("plugins.dev-mode", "ON/OFF", "false", "true|false", "개발 모드 — 플러그인 핫 리로드를 허용한다"),
            new Option("plugins.dev-mode.auto-reload", "ON/OFF", "false", "true|false", "개발 모드에서 jar 가 바뀌면 자동으로 다시 읽는다"),
            // 월드
            new Option("worlds.autoload", "목록", "", "이름,...", "시작 때 불러올 추가 월드"),
            new Option("worldgen-hooks.overworld", "키", "", "namespace:path", "오버월드에 걸 플러그인 월드젠 훅(비면 바닐라)"),
            new Option("worldgen-hooks.the_nether", "키", "", "namespace:path", "네더에 걸 플러그인 월드젠 훅(비면 바닐라)"),
            new Option("worldgen-hooks.the_end", "키", "", "namespace:path", "엔드에 걸 플러그인 월드젠 훅(비면 바닐라)"),
            new Option("enchantments.disabled", "목록", "", "id,...", "끌 마법부여 id(예: minecraft:mending)"),
            // 아래는 다른 묶음에서 로더만 생기고 키 표에 빠져 있던 키(2026-11-07 furnace.properties 자동 생성 때 채움 — 없으면 오타 경고가 났다)
            new Option("io-uring", "ON/OFF", "false", "true|false", "리눅스 io_uring 으로 접속을 받는다(못 쓰면 경고 후 epoll/NIO)"),
            new Option("rcon.max-clients", "수", "16", "1 이상", "RCON 동시 연결 수 상한"),
            new Option("rcon.max-clients-per-ip", "수", "4", "1 이상", "RCON 주소 하나당 동시 연결 수 상한"),
            new Option("rcon.max-fails-per-connection", "수", "5", "1 이상", "RCON 연결 하나당 인증 실패 허용 수"),
            new Option("rcon.max-fails-per-ip", "수", "10", "1 이상", "RCON 주소 하나당 60초 인증 실패 허용 수"),
            new Option("metrics.prometheus.enabled", "ON/OFF", "false", "true|false", "Prometheus 형식 메트릭을 HTTP GET /metrics 로 내보낸다"),
            new Option("metrics.prometheus.bind", "글자", "127.0.0.1", "주소", "메트릭 HTTP 바인딩 주소"),
            new Option("metrics.prometheus.port", "수", "9225", "0~65535", "메트릭 HTTP 포트(0 = 빈 포트)"),
            new Option("metrics.prometheus.token", "글자", "", "", "비어 있지 않으면 Authorization: Bearer <값> 이 맞아야 응답"),
            new Option("metrics.jmx.enabled", "ON/OFF", "false", "true|false", "같은 값을 JMX MBean 으로 등록"),
            new Option("idle-pause.empty-seconds", "수", "0", "0~86400", "접속자가 없으면 이 초 뒤 게임 틱을 멈춘다(0 = 끔, 바닐라)"),
            new Option("idle-pause.flush-caches", "ON/OFF", "true", "true|false", "일시정지할 때 캐시를 비운다"),
            new Option("backup.interval-minutes", "수", "0", "0~525600", "주기 백업 간격(분, 0 = 끔)"),
            new Option("backup.directory", "경로", "", "", "백업 폴더(비면 backups)"),
            new Option("backup.keep", "수", "10", "0~100000", "남길 백업 수(0 = 지우지 않음)"),
            new Option("backup.format", "글자", "zip", "zip|folder", "백업 형식"),
            new Option("backup.skip-if-no-players", "ON/OFF", "true", "true|false", "접속자가 없었으면 백업을 건너뛴다"),
            new Option("backup.hold-timeout-seconds", "수", "10", "1~600", "백업 기준점에서 저장을 기다리는 최대 초"),
            new Option("plugins.compat.bukkit", "ON/OFF", "true", "true|false", "Bukkit·Paper 플러그인(plugin.yml) 호환 어댑터 — 끄면 plugin.yml jar 를 읽지 않는다(호환 모듈이 있을 때)"),
            new Option("plugins.compat.nms.enabled", "ON/OFF", "true", "true|false", "Bukkit 호환 플러그인을 올릴 때 NMS·CraftBukkit 이름 변환(CraftBukkit 버전 접미사 v1_21_R1 풀기)을 한다 — 끄면 그런 플러그인은 그 이름에서 실패한다"),
            new Option("plugins.compat.nms.strict", "ON/OFF", "false", "true|false", "미지원 NMS·CraftBukkit 심볼을 쓰는 Bukkit 호환 플러그인은 불러오지 않는다(끄면 그 호출만 실행 때 실패하고 나머지는 돈다)"),
            new Option("plugins.compat.nms.version-package", "글자", "plain", "plain|versioned", "플러그인이 읽는 CraftBukkit 패키지 이름 — plain 은 Paper 처럼 접미사 없음, versioned 는 Spigot 처럼 v1_21_R1 을 붙인다(두 이름 모두 클래스는 풀린다)"),
            new Option("plugins.compat.nms.spigot-mappings", "글자", "download", "download|files|off", "download(기본)면 Spigot 이름으로 컴파일된 NMS 플러그인이 있을 때만 Mojang·Spigot 매핑을 처음 한 번 받아(캐시 plugins.libraries.cache) Spigot → Mojang 이름으로 바꿔 올린다 — 그런 플러그인이 없으면 받지 않는다. files 면 받지 않고 plugins.compat.nms.spigot-mappings.files 의 세 파일을 쓴다. off 면 그 NMS 호출은 실패한다"),
            new Option("plugins.compat.nms.spigot-mappings.files", "글자", "", "경로 구분자로 이은 세 파일", "spigot-mappings=files 일 때 쓰는 Mojang server.txt, Spigot 클래스 csrg, Spigot 멤버 csrg 경로"),
            new Option("plugins.chunk-tickets.max-per-plugin", "수", "16384", "1~100000000", "플러그인 하나가 청크 티켓으로 붙잡을 수 있는 청크 수 상한(겹침 포함)"),
            new Option("plugins.compat.config.paper-default-separator", "ON/OFF", "false", "true|false", "플러그인이 options().pathSeparator 를 바꿨을 때 addDefault 기본값 트리를 Paper 처럼 언제나 '.' 로 쪼갠다(getDefaults() 구분자도 '.'). 끄면(기본) 플러그인이 정한 구분자로 쪼갠다 — 켜면 저장 파일 모양·contains·getKeys 와 setDefaults 와 섞어 쓸 때·저장 뒤 다시 읽을 때의 값도 Paper 와 같아진다(Paper 처럼 다시 읽으면 기본값이 빠질 수 있다)"),
            new Option("plugins.compat.scheduler.async-threads", "글자", "platform", "platform|virtual", "Bukkit 비동기 작업(runTaskAsynchronously 등)을 도는 스레드 — platform: 이름 붙은 플랫폼 스레드 풀(Paper 와 같은 모양, synchronized 안에서 기다리는 옛 DB 드라이버에도 안전), virtual: 가상 스레드"),
            new Option("plugins.compat.libraries", "글자", "needed", "needed|all|off", "Paper 가 싣는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등)를 호환 플러그인이 쓸 때만(needed)·호환 플러그인이 있으면 전부(all) 받아 준다, off 는 안 받는다"),
            new Option("plugins.compat.libraries.mojang", "ON/OFF", "true", "true|false", "Brigadier·authlib·DataFixerUpper 를 Mojang 저장소(libraries.minecraft.net)에서 받는다"),
            new Option("plugins.compat.libraries.declared", "ON/OFF", "true", "true|false", "호환 플러그인 plugin.yml 의 libraries 를 Paper·Spigot 처럼 받는다(plugins.libraries.enabled 와 별개)"),
            new Option("plugins.libraries.enabled", "ON/OFF", "false", "true|false", "플러그인 설명 파일의 libraries 를 Maven Central 에서 받아 붙인다"),
            new Option("plugins.libraries.offline", "ON/OFF", "false", "true|false", "캐시에 있는 라이브러리만 쓴다(Furnace·호환 플러그인 내려받기 모두)"),
            new Option("plugins.libraries.transitive", "ON/OFF", "true", "true|false", "라이브러리의 compile/runtime 의존까지 받는다(enabled=true 일 때)"),
            new Option("plugins.libraries.require-checksum", "ON/OFF", "false", "true|false", "sha256·잠금 파일 기록이 없는 jar 는 받지 않는다(enabled=true 일 때)"),
            new Option("plugins.libraries.cache", "경로", "", "", "받은 라이브러리 폴더(비면 plugins/.libraries)"),
            new Option("plugins.libraries.timeout-seconds", "수", "30", "1~600", "라이브러리 요청 하나의 제한 시간(초)"),
            new Option("plugins.libraries.max-size-mb", "수", "64", "1~1024", "라이브러리 파일 하나의 크기 상한(MB)"),
            // 설정 자체(O2)
            new Option(ConfigMigration.KEY, "수", Integer.toString(ConfigMigration.CURRENT), "1 이상", "설정 파일 형식 버전. 키 이름이 바뀌면 서버가 파일을 옮기고 이 값을 올린다(원본은 .bak 으로 남는다)"),
            new Option(ConfigPresets.KEY, "글자", "none", "none|vanilla|performance", "파일에 없는 키를 프리셋 값으로 채운다(파일에 적은 키가 이긴다)"),
            new Option(JvmCheck.KEY, "ON/OFF", "true", "true|false", "기동 때 Java 버전·힙·GC 옵션을 점검해 경고한다"),
            new Option("console.jline", "ON/OFF", "true", "true|false", "콘솔 줄 편집·자동완성·기록(터미널이 아니면 자동으로 꺼진다)"),
            new Option("console.history-size", "수", "500", "0~100000", "콘솔 명령 기록 줄 수(0 = 파일에 저장 안 함)"));

    /** 접속 방어의 단계별 키는 단계 다섯 개마다 같은 모양이라 펼쳐서 만든다. */
    public static List<Option> all() {
        List<Option> out = new ArrayList<>(FIXED);
        for (String s : STATES) {
            out.add(new Option("connection." + s + ".packets-per-second", "수", "200", "0~1000000", s + " 단계 초당 패킷 평균 상한(0 = 제한 없음)"));
            out.add(new Option("connection." + s + ".packets-burst", "수", "400", "1~10000000", s + " 단계 순간 패킷 상한"));
            out.add(new Option("connection." + s + ".max-frame-bytes", "수", s.equals("play") ? "2097152" : "단계별", "16~2097152", s + " 단계 한 패킷 최대 크기(바이트)"));
            if (!s.equals("play")) {
                out.add(new Option("connection." + s + ".timeout-seconds", "수", s.equals("login") ? "30" : "0", "0~86400", s + " 단계 제한 시간(초, 0 = 제한 없음)"));
            }
        }
        return List.copyOf(out);
    }

    /** server.properties 의 값이 이 키 대신 쓰이는 키(furnace.properties 에 써도 무시 — 따로 경고한다). */
    public static Set<String> vanillaKeys() { return Config.VANILLA_KEYS; }

    /** 알려진 키 전부(접두 가족 제외). */
    public static Set<String> keys() {
        Set<String> k = new LinkedHashSet<>();
        for (Option o : all()) k.add(o.key());
        return k;
    }

    public static boolean isKnown(String key) {
        if (Config.VANILLA_KEYS.contains(key)) return true;
        for (Option o : all()) if (o.key().equals(key)) return true;
        for (String p : PREFIXES) if (key.startsWith(p) && key.length() > p.length()) return true;
        return false;
    }

    /** 모르는 키와 가장 비슷한 키(편집 거리 3 이하), 없으면 null. */
    public static String suggest(String key) {
        String best = null;
        int bestD = 4;
        List<String> pool = new ArrayList<>(keys());
        pool.addAll(Config.VANILLA_KEYS);
        for (String p : PREFIXES) pool.add(p + "<이름>");
        for (String cand : pool) {
            int d = distance(key.toLowerCase(Locale.ROOT), cand.toLowerCase(Locale.ROOT));
            if (d < bestD) {
                bestD = d;
                best = cand;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** 파일에 있는데 키 표에 없는 키(오타 후보), 정렬해서. */
    public static List<String> unknownKeys(Properties p) {
        return new TreeSet<>(p.stringPropertyNames()).stream().filter(k -> !isKnown(k)).toList();
    }

    /** furnace.properties 를 읽어 {@link #audit(Properties, Consumer)} 를 돌린다. 파일이 없으면 아무것도 하지 않는다. */
    public static void audit(Path file, Consumer<String> warn) {
        if (!Files.exists(file)) return;
        Properties p;
        try {
            p = FurnaceProps.read(file);
        } catch (RuntimeException e) {
            return;   // 읽지 못하는 것은 각 로더가 이미 알린다
        }
        audit(p, ConfigDump.lineNumbers(file), warn);
    }

    /**
     * 시작 때 한 번: 오타 키와 위험한 값을 경고한다(값 자체의 형식·범위 검사는 각 로더가 한다 — 여기는 형식이 맞는 값만 본다).
     * 동작은 바꾸지 않는다.
     */
    public static void audit(Properties p, Consumer<String> warn) {
        audit(p, java.util.Map.of(), warn);
    }

    /** lines: 키 → 파일 줄 번호(1부터). 있으면 오타 경고에 줄 번호를 붙인다. */
    public static void audit(Properties p, java.util.Map<String, Integer> lines, Consumer<String> warn) {
        for (String k : unknownKeys(p)) {
            String near = suggest(k);
            Integer line = lines.get(k);
            warn.accept("furnace.properties 의 '" + k + "'" + (line == null ? "" : "(" + line + "번째 줄)") + " 는 모르는 키라 무시합니다"
                    + (near == null ? "" : " — '" + near + "' 를 말한 건가요?"));
        }
        if (isTrue(p, "proxy-protocol") && p.getProperty("proxy-protocol.trusted", "").isBlank()) {
            warn.accept("proxy-protocol=true 인데 proxy-protocol.trusted 가 비어 있습니다: 누구든 PROXY 헤더로 접속 주소를 꾸밀 수 있습니다(밴·접속 제한 우회). 앞단 주소만 적고, 서버 포트는 방화벽으로 앞단에만 열어 두세요");
        }
        if (isTrue(p, "plugins.dev-mode")) {
            warn.accept("plugins.dev-mode=true: 플러그인 핫 리로드가 열려 있습니다. 개발용이니 운영 서버에서는 끄세요");
        }
        String tick = p.getProperty("tick-nanos");
        if (tick != null && !tick.strip().equals("50000000")) {
            warn.accept("tick-nanos=" + tick.strip() + ": 틱 길이가 바닐라(50000000 = 20 TPS)와 다르면 게임 속도가 바닐라와 달라집니다");
        }
        for (String s : STATES) {
            String k = "connection." + s + ".packets-per-second";
            if ("0".equals(p.getProperty(k, "").strip())) {
                warn.accept(k + "=0: " + s + " 단계 패킷 속도 제한이 꺼져 있습니다(접속 폭주에 취약)");
            }
        }
        if ("0".equals(p.getProperty("connection.login.timeout-seconds", "").strip())) {
            warn.accept("connection.login.timeout-seconds=0: 로그인을 끝내지 않고 붙들고 있는 접속을 끊지 않습니다(바닐라는 30초)");
        }
    }

    private static boolean isTrue(Properties p, String key) {
        return "true".equalsIgnoreCase(p.getProperty(key, "").strip());
    }

    /** 엄격한 정수 읽기: 없으면 def, 숫자가 아니거나 min~max 밖이면 어느 키가 문제인지 알리며 거부한다. */
    public static int strictInt(Properties p, String key, int def, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        int n;
        try {
            n = Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + v, e);
        }
        if (n < min || n > max) throw new IllegalArgumentException("설정 '" + key + "' 값 " + n + " 이(가) 잘못됐습니다: " + min + "~" + max);
        return n;
    }

    /** 엄격한 ON/OFF 읽기: 없으면 def, true/false 외의 글자는 어느 키가 문제인지 알리며 거부한다. */
    public static boolean strictBool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        String t = v.strip();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("설정 '" + key + "' 값 '" + t + "' 이(가) 잘못됐습니다: true 또는 false");
    }
}
