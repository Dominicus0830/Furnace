package org.domi.furnace.tools.extract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 명령줄 옵션. */
public final class Options {
    public enum Mode {
        /** 결과를 출력 폴더에만 쓴다(기본). */
        EXTRACT,
        /** 출력 폴더에 쓴 뒤 저장소의 커밋된 파일과 견준다. */
        VERIFY,
        /** 출력 폴더에 쓴 뒤 저장소 리소스를 덮어쓴다(새 판으로 올릴 때). */
        INSTALL,
        /** 이전(난독화 이름) 도구를 git 기록에서 꺼내 같은 jar 에 돌려 `<출력>/../<판>-legacy/` 에 쓴다 — 전후 비교의 "전". */
        BASELINE,
        /** 이전 도구와 새 도구의 바이트코드 호출을 견준다(이름을 대응표로 푼 뒤 같은 호출을 하는지). */
        PORT_CHECK,
        /** BASELINE + EXTRACT + PORT_CHECK 를 한 번에 하고 전·후·커밋을 모두 견준다. */
        PROOF,
        /** 서명표(T-227)만 만든다. 바닐라 jar 준비까지만 하고 데이터 생성기는 돌리지 않는다. */
        SIG_TABLE,
        /** 바닐라 데이터 생성기 보고서(`generated/reports`)까지 준비하고 그 폴더를 알린다(T-238 대조 시험이 읽는다). */
        REPORTS,
        /** 서명 관문(T-228): shim 클래스 선언을 기준 서명표와 맞대고 기준선 파일과 견준다. */
        SIG_GATE,
        /** 본문 지문표(버전 사이 동작 변경 후보 찾기). 서명표와 달리 메서드 몸통의 해시만 남긴다. */
        BODY_TABLE,
        /** 바닐라 jar 준비까지만 하고(데이터 생성기 없음) Mojang 이름 서버 jar·라이브러리 경로를 `<캐시>/<판>/vanilla-classpath.txt` 에 적는다(T-235 차분 시험이 읽는다). */
        JARS
    }

    public String mc;
    public Mode mode = Mode.EXTRACT;
    public List<String> tools = List.of();
    public Path cache = defaultCache();
    public Path repo = findRepo();
    public Path out;
    public Path javaHome = Path.of(System.getProperty("java.home"));
    public Path serverJar;
    public Path mappings;
    public String python;
    /** 프로토콜 번호를 직접 준다(오프라인 --server-jar 일 때). */
    public int protocol = -1;
    public boolean list;
    /** 이전 도구가 들어 있던 git 커밋/브랜치(BASELINE·PORT_CHECK·PROOF 에 필요). */
    public String legacyRef;
    /** 도구별 추가 인자(`--tool-arg 도구=값`, 여러 번 가능). */
    /** 서명표를 만들 임의 jar(Paper 서버 jar·API jar·컴파일된 shim). 비우면 바닐라 서버 jar. */
    public Path sigJar;
    /** 서명표에 넣을 내부 이름 접두(쉼표). 기본은 바닐라 net/minecraft/·com/mojang/. */
    public List<String> sigPrefix = List.of();
    /** 서명표 이름표. `--sig-jar` 와 함께 쓰며 파일 이름이 sig-table-<이름>.tsv.gz 가 된다. */
    public String sigName;
    /** paper-api jar 를 repo.papermc.io 에서 받아 서명표를 만든다. */
    public boolean sigPaperApi;
    /** 서명 관문이 읽을 shim 클래스 폴더(예: furnace-compat-paper/build/classes/java/main). */
    public Path sigShims;
    /** 서명 관문 기준선 파일(의도한 차이 + 이유). */
    public Path sigBaseline;
    /** 기준선 파일을 현재 차이로 다시 쓴다(이유는 이미 적힌 것을 보존, 새 줄은 기본 이유). */
    public boolean sigBaselineWrite;
    /** 서명 관문 보고서(마크다운) 출력 위치. */
    public Path sigReport;
    public final Map<String, List<String>> toolArgs = new LinkedHashMap<>();

    public static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--mc" -> o.mc = next(args, ++i, a);
                case "--mode" -> o.mode = Mode.valueOf(next(args, ++i, a).toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
                case "--tools" -> o.tools = List.of(next(args, ++i, a).split(","));
                case "--cache" -> o.cache = Path.of(next(args, ++i, a));
                case "--repo" -> o.repo = Path.of(next(args, ++i, a));
                case "--out" -> o.out = Path.of(next(args, ++i, a));
                case "--java-home" -> o.javaHome = Path.of(next(args, ++i, a));
                case "--server-jar" -> o.serverJar = Path.of(next(args, ++i, a));
                case "--mappings" -> o.mappings = Path.of(next(args, ++i, a));
                case "--python" -> o.python = next(args, ++i, a);
                case "--protocol" -> o.protocol = Integer.parseInt(next(args, ++i, a));
                case "--list" -> o.list = true;
                case "--sig-jar" -> o.sigJar = Path.of(next(args, ++i, a));
                case "--sig-prefix" -> o.sigPrefix = List.of(next(args, ++i, a).split(","));
                case "--sig-name" -> o.sigName = next(args, ++i, a);
                case "--sig-paper-api" -> o.sigPaperApi = true;
                case "--sig-shims" -> o.sigShims = Path.of(next(args, ++i, a));
                case "--sig-baseline" -> o.sigBaseline = Path.of(next(args, ++i, a));
                case "--sig-baseline-write" -> o.sigBaselineWrite = true;
                case "--sig-report" -> o.sigReport = Path.of(next(args, ++i, a));
                case "--legacy-ref" -> o.legacyRef = next(args, ++i, a);
                case "--tool-arg" -> {
                    String kv = next(args, ++i, a);
                    int eq = kv.indexOf('=');
                    if (eq <= 0) throw new IllegalArgumentException("--tool-arg 는 도구=값 형식: " + kv);
                    o.toolArgs.computeIfAbsent(kv.substring(0, eq), k -> new ArrayList<>()).add(kv.substring(eq + 1));
                }
                default -> throw new IllegalArgumentException("알 수 없는 옵션: " + a);
            }
        }
        if (!o.list && o.mc == null) throw new IllegalArgumentException("--mc <판> 이 필요하다(예 --mc 1.21.1)");
        if (o.out == null) o.out = o.repo.resolve("tools/vanilla-extract/build/out").resolve(o.mc == null ? "none" : o.mc);
        return o;
    }

    private static String next(String[] args, int i, String name) {
        if (i >= args.length) throw new IllegalArgumentException(name + " 뒤에 값이 필요하다");
        return args[i];
    }

    static Path defaultCache() {
        String env = System.getenv("FURNACE_VANILLA_CACHE");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of(System.getProperty("user.home"), ".cache", "furnace-vanilla-extract");
    }

    /** 현재 폴더에서 위로 올라가며 settings.gradle.kts 가 있는 곳을 저장소 루트로 본다. */
    static Path findRepo() {
        Path p = Path.of("").toAbsolutePath();
        for (Path q = p; q != null; q = q.getParent()) {
            if (Files.isRegularFile(q.resolve("settings.gradle.kts")) && Files.isDirectory(q.resolve("tools"))) return q;
        }
        return p;
    }
}
