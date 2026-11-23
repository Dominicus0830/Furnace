// SPDX-License-Identifier: GPL-3.0-only
import java.security.MessageDigest
import java.util.zip.ZipFile
plugins {
    id("java")
    id("application")
    id("maven-publish")
    id("jacoco")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":furnace-api"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.netty:netty-all:4.1.115.Final")
    // io_uring transport(옵션 io-uring=true). 클래스는 항상 싣고, 네이티브 라이브러리는 리눅스 x86_64·aarch64 만 싣는다.
    // 커널·권한이 맞지 않으면 IOUring.isAvailable() 이 false 라 epoll/NIO 로 되돌아간다.
    implementation("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.26.Final")
    runtimeOnly("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.26.Final:linux-x86_64")
    runtimeOnly("io.netty.incubator:netty-incubator-transport-native-io_uring:0.0.26.Final:linux-aarch_64")
    implementation("io.airlift:aircompressor:0.27")   // 순수 자바 zstd (region-file-compression=zstd)
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.jline:jline:3.26.3")   // 콘솔 줄 편집·자동완성·기록(console.jline). 터미널이 없으면 쓰지 않는다

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")   // NoRetryFailureListener(TestExecutionListener)가 컴파일에 쓴다
}

// V-02: gradle/state-ids.spec + blocks/definitions_<tag>.txt → StateIds(컴파일 시간 상수). build/generated 에만 생기고 커밋하지 않는다.
// 블록 상태 번호는 판마다 밀리므로 서버 코드는 숫자를 직접 쓰지 않고 이 상수를 쓴다(tools/ci/check-raw-state-ids.py 가 강제).
val stateIdsDir = layout.buildDirectory.dir("generated/stateIds")
val generateStateIds by tasks.registering {
    group = "build"
    description = "gradle/state-ids.spec 과 blocks 정의 표로 StateIds 상수 클래스를 만든다"
    @Suppress("UNCHECKED_CAST") val mc = rootProject.extra["mc"] as java.util.Properties
    val tag = mc.getProperty("mc.resourceTag") ?: error("gradle/minecraft.properties 에 mc.resourceTag 가 없다")
    val specFile = rootProject.file("gradle/state-ids.spec")
    val defsFile = file("src/main/resources/blocks/definitions_$tag.txt")
    val outDir = stateIdsDir
    inputs.file(specFile)
    inputs.file(defsFile)
    outputs.dir(outDir)
    doLast {
        class Def(val first: Int, val count: Int, val props: List<Pair<String, Int>>)
        val defs = HashMap<String, Def>()
        defsFile.readLines(Charsets.UTF_8).forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val p = line.split(" ", limit = 6)
            val props = if (p[4] == "-") emptyList() else p[4].split(";").map { it.substringBefore('=') to it.substringAfter('=').split("|").size }
            defs[p[0]] = Def(p[2].toInt(), props.fold(1) { a, b -> a * b.second }, props)
        }
        val body = StringBuilder()
        val seen = HashSet<String>()
        specFile.readLines(Charsets.UTF_8).forEachIndexed { i, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val t = line.split(Regex("\\s+"))
            fun bad(msg: String): Nothing = error("state-ids.spec:${i + 1}: $msg — $raw")
            if (t.size < 3) bad("이름 종류 블록 이 필요하다")
            val name = t[0]
            if (!seen.add(name)) bad("이름이 겹친다")
            val d = defs[t[2]] ?: bad("정의 표에 없는 블록")
            var rest = t.drop(3)
            var delta = 0
            if (rest.isNotEmpty() && Regex("[+-]\\d+").matches(rest.last())) { delta = rest.last().toInt(); rest = rest.dropLast(1) }
            val base = when (t[1]) {
                "first" -> d.first
                "last" -> d.first + d.count - 1
                "count" -> d.count
                "stride" -> {
                    val prop = rest.singleOrNull() ?: bad("stride 는 속성 이름 하나가 필요하다")
                    val at = d.props.indexOfFirst { it.first == prop }
                    if (at < 0) bad("그 블록에 없는 속성")
                    d.props.drop(at + 1).fold(1) { a, b -> a * b.second }
                }
                else -> bad("알 수 없는 종류")
            }
            body.append("    public static final int ").append(name).append(" = ").append(base + delta).append(";\n")
        }
        val src = "// 자동 생성 — gradle/state-ids.spec 과 blocks/definitions_$tag.txt 에서 만든다(generateStateIds). 손으로 고치지 않는다.\n" +
            "package org.domi.furnace.server.world;\n\n" +
            "/** 코드가 이름으로 부르는 블록 상태 번호. 모든 칸이 컴파일 시간 상수라 쓰는 자리에 값이 그대로 들어간다. */\n" +
            "public final class StateIds {\n    private StateIds() {}\n\n" + body + "}\n"
        val f = outDir.get().file("org/domi/furnace/server/world/StateIds.java").asFile
        f.parentFile.mkdirs()
        f.writeText(src)
    }
}
sourceSets.main { java.srcDir(generateStateIds.map { stateIdsDir.get() }) }

// V-03: protocol-<번호>-ids.txt(바닐라에서 뽑은 번호표) → PacketIds(컴파일 시간 상수). build/generated 에만 생기고 커밋하지 않는다.
// 서버 패킷 클래스의 ID 와 ConnectionHandler 의 case 가 숫자를 직접 쓰지 않고 이 상수를 쓴다. 상수라 case 라벨로 쓸 수 있고 javac 가 숫자를 그대로 박는다.
val packetIdsDir = layout.buildDirectory.dir("generated/packetIds")
val generatePacketIds by tasks.registering {
    group = "build"
    description = "프로토콜 번호표로 PacketIds 상수 클래스를 만든다"
    @Suppress("UNCHECKED_CAST") val mc = rootProject.extra["mc"] as java.util.Properties
    val proto = mc.getProperty("mc.protocol") ?: error("gradle/minecraft.properties 에 mc.protocol 이 없다")
    val idsFile = rootProject.file("furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-$proto-ids.txt")
    val outDir = packetIdsDir
    inputs.file(idsFile)
    outputs.dir(outDir)
    doLast {
        // 상태(번호표의 프로토콜 이름) → 상수 이름 앞붙이. 번호표에 없는 상태가 생기면 여기서 멈춘다.
        // 안쪽 클래스를 쓰지 않는다 — 쓰면 사용하는 클래스마다 InnerClasses 항목이 늘어 javap 동일 증명에 노이즈가 생긴다.
        val holders = mapOf("handshake" to "HANDSHAKE", "status" to "STATUS", "login" to "LOGIN", "configuration" to "CONFIGURATION", "game" to "PLAY")
        val body = StringBuilder()
        val seen = HashSet<String>()
        idsFile.readLines(Charsets.UTF_8).forEachIndexed { i, raw ->
            val t = raw.trim().split(Regex("\\s+"))
            if (t[0] != "id") return@forEachIndexed
            fun bad(msg: String): Nothing = error("${idsFile.name}:${i + 1}: $msg — $raw")
            if (t.size != 5) bad("id <프로토콜> <방향> <번호> <종류>:<이름> 꼴이어야 한다")
            val state = holders[t[1]] ?: bad("모르는 상태(PacketIds 생성기에 추가해야 한다)")
            val dir = when (t[2]) { "clientbound" -> "CLIENTBOUND"; "serverbound" -> "SERVERBOUND"; else -> bad("모르는 방향") }
            val name = state + "_" + dir + "_" + t[4].substringAfter(':').uppercase()
            if (!seen.add(name)) bad("상수 이름이 겹친다")
            body.append("    public static final int ").append(name).append(" = 0x").append("%02X".format(t[3].toInt())).append(";\n")
        }
        val src = "// 자동 생성 — protocol-$proto-ids.txt 에서 만든다(generatePacketIds). 손으로 고치지 않는다.\n" +
            "package org.domi.furnace.server.net.protocol;\n\n" +
            "/** 패킷 번호. 이름은 <상태>_<방향>_<와이어 이름>, 모든 칸이 컴파일 시간 상수라 쓰는 자리에 값이 그대로 들어간다. */\n" +
            "public final class PacketIds {\n    private PacketIds() {}\n\n" + body + "}\n"
        val f = outDir.get().file("org/domi/furnace/server/net/protocol/PacketIds.java").asFile
        f.parentFile.mkdirs()
        f.writeText(src)
    }
}
sourceSets.main { java.srcDir(generatePacketIds.map { packetIdsDir.get() }) }

application {
    mainClass = "org.domi.furnace.server.FurnaceMain"
    applicationDefaultJvmArgs = listOf("-Xmx6G")
}

tasks.named<JavaExec>("run") {
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
}

tasks.test {
    // T-238 의 보고서 대조 시험(태그 vanilla-reports)은 바닐라 jar 에서 만든 보고서가 있어야 해서 아래 vanillaReportsTest 가 따로 돌린다.
    useJUnitPlatform { excludeTags("vanilla-reports") }
    enableAssertions = true
    maxHeapSize = "3g"   // 통합 테스트가 월드를 많이 만들어 기본 힙으로는 모자란다
    testLogging { showStandardStreams = true }
    // 명령 블록이 명령을 같은 틱 안에 끝내려고 기다리는 시간 예산(서버 기본 20ms)을 시험 JVM 에서만 넉넉히 준다.
    // 20ms 로는 CI 처럼 바쁜 러너에서 다른 region 왕복이 늦어 CommandBlockOneTickTest 가 자주 실패했다(2026-11-05~07 집계 24건, 서버 동작 문제가 아니라 시험 시간 예산 문제).
    // 시험은 "그 틱 안에 끝난다"는 동작을 보는 것이라 예산 크기에는 기대지 않는다. 직접 정한 값(-Dfurnace.commandBlockWaitMicros=…)이 있으면 아래 반복문이 덮어쓴다.
    systemProperty("furnace.language", "en")   // 문구를 확인하는 시험이 실행 환경 언어에 흔들리지 않게
    systemProperty("furnace.commandBlockWaitMicros", "500000")
    // @NoRetry 시험이 실패하면 NoRetryFailureListener 가 여기에 적는다. CI 의 재시도 단계가 읽는다(.github/workflows/ci.yml). 시작 전에 지운다.
    val noRetryReport = layout.buildDirectory.file("no-retry-failures.txt").get().asFile
    systemProperty("furnace.noRetryReport", noRetryReport.absolutePath)
    doFirst { noRetryReport.delete() }
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
    // CI 분할 실행: -PtestShard=2/3 → 2번째 조각의 시험 클래스만 돌린다(서버 시험을 러너 여러 대로 나눈다). 속성이 없으면 전부 돌린다.
    // 같은 클래스는 항상 같은 조각에 들어가고(@Nested·내부 클래스는 바깥 클래스를 따른다) 조각을 모두 합치면 전체와 같다.
    // 배정(T-162): test-shard-times.tsv(클래스별 시험 시간 실측)에 있는 클래스는 오래 걸리는 것부터 지금 가장 가벼운 조각에 넣는다(LPT).
    // 파일에 없는 새 클래스만 이름 해시로 나눈다. 배정은 정확성에 영향이 없고 균형만 달라진다. 시간 파일 갱신은 tools/ci/shard-balance.py --write(docs/ci.md).
    // 이 규칙을 바꾸면 tools/ci/shard-balance.py 의 assign() 도 같이 바꾼다(두 곳이 같은 배정을 내야 균형 계산이 맞다).
    //
    // V-08 시험 영향 선택: -PtestSkip=<파일> 은 "이번 변경과 상관없다고 선택 도구가 판단한 시험 클래스"(줄마다 a/b/FooTest 경로 꼴)를 건너뛴다.
    // 목록에 없는 클래스는 모두 돈다(표에 없는 새 시험 포함) — 건너뛰기는 항상 이름으로 명시된 것만이다(tools/ci/test-impact.py, docs/ci.md).
    // 조각 배정은 건너뛴 클래스를 뺀 나머지로 한다(같은 LPT 규칙, 건너뛴 것까지 넣으면 조각이 한쪽으로 쏠린다).
    val skipNames: Set<String> = (findProperty("testSkip") as String?)?.let { path ->
        val f = file(path)
        require(f.isFile) { "testSkip 파일이 없다: $path" }
        inputs.file(f)
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
    } ?: emptySet()
    val shardMatch: ((String) -> Boolean)? = (findProperty("testShard") as String?)?.let { spec ->
        val m = Regex("""(\d+)/(\d+)""").matchEntire(spec.trim()) ?: error("testShard 는 '번호/전체' 꼴이다(예: 2/3): $spec")
        val total = m.groupValues[2].toInt()
        val index = m.groupValues[1].toInt()
        require(total >= 1 && index in 1..total) { "testShard 범위 오류: $spec" }
        val shardSalt = "100"   // 해시 몫(시간 파일에 없는 클래스) 전용
        val timesFile = file("test-shard-times.tsv")
        inputs.file(timesFile).optional()
        val known = HashMap<String, Int>()
        if (timesFile.isFile) {
            val rows = timesFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val parts = line.split('\t')
                require(parts.size == 2) { "${timesFile.name}: '클래스<TAB>초' 꼴이 아니다: $line" }
                parts[0] to parts[1].toDouble()
            }.filter { it.first !in skipNames }
            val load = DoubleArray(total)
            for ((name, sec) in rows.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })) {
                var best = 0
                for (i in 1 until total) if (load[i] < load[best]) best = i
                load[best] += sec
                known[name] = best
            }
        }
        val match: (String) -> Boolean = { key -> (known[key] ?: Math.floorMod((key + shardSalt).hashCode(), total)) == index - 1 }
        match
    }
    if (shardMatch != null || skipNames.isNotEmpty()) {
        include(Spec<FileTreeElement> { el ->
            if (el.isDirectory) return@Spec true
            val key = el.path.substringBefore('$').removeSuffix(".class")
            key !in skipNames && (shardMatch?.invoke(key) ?: true)
        })
    }
    // V-08 시험 영향 표 만들기(-PtestImpact): 시험 클래스마다 새 JVM 에서 JaCoCo 에이전트를 붙여 돌리고, TestImpactListener 가 클래스별 실행 목록을
    // build/test-impact/raw 에 남긴다(tools/ci/test-impact.py partial 이 읽는다). forkEvery=1 이 필요한 까닭은 TestImpactListener 머리 주석.
    // 판정용 조각에는 쓰지 않는다 — 에이전트와 클래스당 JVM 이 시험을 훨씬 느리게 한다(기준 브랜치 push 의 coverage-tests 작업에서만).
    if (findProperty("testImpact") != null) {
        val rawDir = layout.buildDirectory.dir("test-impact/raw").get().asFile
        forkEvery = 1
        // 클래스마다 JVM 이 따로라 동시에 몇 개를 돌려도 구간이 섞이지 않는다. 시간이 오래 걸리니(에이전트+클래스당 JVM) 러너 코어에 맞춰 -PtestForks=n 으로 올린다.
        maxParallelForks = (findProperty("testForks") as String?)?.toInt() ?: 1
        systemProperty("furnace.testImpact.dir", rawDir.absolutePath)
        doFirst { rawDir.deleteRecursively() }
    }
}

// ===== 벤치(O15 T-O15-03) =====
// 벤치는 src/bench 소스셋에 둔다(src/test 가 아니다). 평소 `test` 에는 들어가지 않아 시험 요약에 "건너뜀"이 섞이지 않는다.
// 같은 패키지 이름을 쓰므로 패키지 비공개 멤버에 그대로 닿고, 시험 소스(도우미·금 파일 자원)도 클래스패스에 받는다.
//   ./gradlew -PskipOracle=true :furnace-server:bench                              (전부: 약 8분)
//   ./gradlew -PskipOracle=true :furnace-server:bench --tests '*RegionTickBench*'   (하나만)
//   ./gradlew -PskipOracle=true :furnace-server:bench --tests '*RegionTickBench*' -Dfurnace.bench.scene=clock -Dfurnace.bench.ticks=600
// 모든 -Dfurnace.* 는 벤치 JVM 에 그대로 넘어간다. 벤치 속성 이름은 furnace.bench.* 하나다. 결과 JSON 은 build/bench/<벤치>.json.
sourceSets {
    create("bench") {
        java.srcDir("src/bench/java")
        resources.srcDir("src/bench/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}
configurations["benchImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["benchRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// 벤치 JVM 옵션을 한 곳에 고정한다(측정이 실행기마다 달라지지 않게). 바꿀 때는 -Pbench.heap=… / -Pbench.tickstats=false.
//  - 힙 고정: 힙 크기가 GC 빈도를 바꿔 틱 시간이 흔들린다(O04 가 3g 로 쟀다).
//  - DebugNonSafepoints: 이게 없으면 JFR 이 자기 시간을 엉뚱한 줄에 몰아 준다(O08 실측: 가짜 36% 가 실제 0.1%). stackdepth=512 는 `jfr print --stack-depth 400` 과 짝이다(기본 64·5 면 단계 분류가 전부 "기타").
//  - furnace.tickstats: Regionizer.STATS 는 static final 이라 JVM 시작 때 켜야 한다. 꺼져 있으면 RegionTickBench 의 단계별 시간이 전부 0 으로 찍힌다.
val benchCommit = providers.exec { commandLine("git", "rev-parse", "--short=12", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.orElse("unknown")
val benchTask = tasks.register<Test>("bench") {
    group = "verification"
    description = "src/bench 의 벤치를 돌린다(평소 test 와 따로). 결과 JSON: build/bench/<벤치>.json"
    testClassesDirs = sourceSets["bench"].output.classesDirs
    classpath = sourceSets["bench"].runtimeClasspath
    useJUnitPlatform()
    enableAssertions = true
    val heap = (findProperty("bench.heap") as String?) ?: "3g"
    minHeapSize = heap
    maxHeapSize = heap
    jvmArgs("-XX:+UseG1GC", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints", "-XX:FlightRecorderOptions:stackdepth=512")
    testLogging { showStandardStreams = true }
    // 결과는 매번 새로 잰다(입력이 같아도 건너뛰지 않는다)
    outputs.upToDateWhen { false }
    val outDir = layout.buildDirectory.dir("bench")
    systemProperty("furnace.bench", "true")
    systemProperty("furnace.bench.outDir", outDir.get().asFile.absolutePath)
    systemProperty("furnace.bench.commit", benchCommit.get())
    if ((findProperty("bench.tickstats") as String?) != "false") systemProperty("furnace.tickstats", "true")
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
    doFirst { outDir.get().asFile.mkdirs() }
}
// jacoco 플러그인은 모든 Test 작업에 에이전트를 붙인다(위 test 의 isEnabled 는 test 에만 걸려 있다). 벤치 JVM 에는 붙이지 않는다 — 측정이 느려진다.
benchTask.configure { extensions.configure<JacocoTaskExtension> { isEnabled = false } }
// 벤치 소스가 컴파일은 되게 한다(시험을 돌리면 같이 확인 — 벤치가 몰래 썩지 않게). 벤치를 돌리지는 않는다.
tasks.test { dependsOn("compileBenchJava") }

// 줄·가지 커버리지(JaCoCo, 보고용 — 게이트 아님). 평소 시험은 에이전트 없이 돌고(느려짐·시간 민감 시험 영향 방지), -Pcoverage 를 주면 켠다:
//   ./gradlew -PskipOracle=true -Pcoverage :furnace-server:test :furnace-server:jacocoTestReport
// CI 는 기준 브랜치 push 실행의 서버 시험 조각마다 test.exec 를 올리고, 합치는 작업(coverage)이 jacocoCoverageReport 로 표 하나를 만든다(docs/ci.md).
tasks.test {
    extensions.configure<JacocoTaskExtension> {
        isEnabled = findProperty("coverage") != null || findProperty("testImpact") != null
        // 영향 표만 만들 때는 .exec 파일이 필요 없다(실행 데이터는 TestImpactListener 가 에이전트에서 직접 꺼낸다).
        if (findProperty("coverage") == null) output = JacocoTaskExtension.Output.NONE
    }
}
tasks.jacocoTestReport {
    reports { xml.required = true; html.required = true }
}
// 조각마다 받은 .exec 파일(build/coverage-in/**.exec)을 합쳐 하나의 보고서로 만든다.
tasks.register<JacocoReport>("jacocoCoverageReport") {
    group = "verification"
    description = "build/coverage-in 아래의 모든 .exec 를 합친 서버 커버리지 보고서(xml·html)"
    executionData(fileTree(layout.buildDirectory.dir("coverage-in")) { include("**/*.exec") })
    sourceSets(sourceSets.main.get())
    reports {
        xml.required = true
        html.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/coverage/jacoco.xml")
        html.outputLocation = layout.buildDirectory.dir("reports/coverage/html")
    }
}

// 서버 + 의존성(furnace-api, netty, gson, slf4j, logback)을 한 jar 로 묶는다.
//   ./gradlew -PskipOracle=true :furnace-server:fatJar   → furnace-server/build/libs/furnace-server.jar
//   java -Xms2G -Xmx4G -jar furnace-server.jar
// `assemble`/`build` 에도 포함된다(테스트는 안 돈다).
// 일반 jar 는 이름을 바꿔(furnace-server-plain.jar) fatJar 의 furnace-server.jar 와 출력이 겹치지 않게 한다.
// 배포(maven-publish)에서는 분류자 없는 주 산출물이어야 POM 소비자가 찾는다 — 그래서 분류자 대신 파일 이름만 바꾼다.
tasks.jar { archiveClassifier = ""; archiveFileName = "furnace-server-plain.jar" }
// 호환 모듈과 그 런타임 의존(H6 의 ASM 등) — fatJar 에만 싣는다. 서버 모듈 클래스패스에는 올리지 않는다(순환·의존 없음).
val compatBundle by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies { compatBundle(project(":furnace-compat-paper")) }
// T-260: 전체 껍데기 jar(furnace-compat-shells.jar) — 서명표 캐시(tools/compat/sig-tables.sh)가 있어야 만들어진다.
// 기본: 서명표 셋이 캐시에 있으면 싣고, 없으면 경고하고 뺀다(새로 받은 저장소의 start.sh·installDist·fatJar 가 깨지지 않게 — 껍데기 없는 서버는 예전과 같다).
// -PnoFullShells(=true) 는 늘 빼고, 배포용 빌드는 -PrequireFullShells(=true) 로 표가 없으면 실패하게 한다(docs/roadmap/full-shells-2026-11-23.md).
val compatShells by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
fun fullShellsFlag(name: String): Boolean = providers.gradleProperty(name).map { it.isBlank() || it.toBoolean() }.getOrElse(false)
val fullShellsTablesPresent: Boolean = run {
    val cache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("user.home") + "/.cache/furnace-vanilla-extract")
    val v = (rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version")
    listOf("sig-table-paper-server.tsv.gz", "sig-table.tsv.gz", "sig-table-paper-api.tsv.gz").all { file("$cache/$v/$it").isFile }
}
when {
    fullShellsFlag("noFullShells") -> {
        if (fullShellsFlag("requireFullShells")) throw GradleException("-PnoFullShells and -PrequireFullShells contradict each other")
    }
    fullShellsTablesPresent || fullShellsFlag("requireFullShells") ->   // 표가 없는데 요구하면 생성기가 없는 표를 이름 대며 실패한다
        dependencies { compatShells(project(path = ":furnace-compat-paper", configuration = "fullShellsElements")) }
    else -> logger.warn("T-260: signature tables not in the cache - building without the full shells jar (run tools/compat/sig-tables.sh, or -PrequireFullShells to fail instead)")
}
// 호환 모듈 jar 와 그 런타임 의존(ASM) — furnace-api 는 서버 로더에 이미 있으므로 빼고, 호환 모듈 자신은 넣는다. 껍데기 jar 는 이름순으로 호환 jar 뒤에 온다.
fun compatJars() = compatBundle.filter { it.name.endsWith(".jar") && (!it.name.startsWith("furnace-") || it.name.startsWith("furnace-compat")) } + compatShells
// 호환 jar 이름 색인(N-lib-1) — 서버는 이 자원이 있으면 jar 를 꺼내 서버 제공 라이브러리 로더의 첫 항목으로 붙인다(CompatJars).
val compatIndex = tasks.register("compatIndex") {
    dependsOn(":furnace-compat-paper:jar")
    dependsOn(compatBundle)
    dependsOn(compatShells)
    val out = layout.buildDirectory.file("compat-index/META-INF/furnace/compat/index.txt")
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val f = out.get().asFile
        f.parentFile.mkdirs()
        // 호환 모듈 jar 를 맨 앞에(공유 로더의 첫 항목 — 이름이 겹치면 호환 계층이 이긴다), 나머지는 이름순
        val names = compatJars().files.map { it.name }.sortedWith(compareBy<String>({ !it.startsWith("furnace-compat") }, { it }))
        f.writeText(names.joinToString("\n", postfix = "\n"))
    }
}
// 서버 jar 에 jar 그대로 싣는 서버 제공 라이브러리(사용자 결정 5 정정, 2026-11-12): Guava·failureaccess·SnakeYAML.
// 풀어 넣지 않는다 — 서버 로더가 보지 않고, 호환 플러그인이 쓸 때만 꺼내 공유 로더에 붙인다(ServerLibraries.BUNDLE_DIR). 패키지 재배치 없음.
// 버전·sha256 은 ServerLibraries.java 의 고정값과 같아야 한다(fatJar 가 확인한다). 전이 의존(주석 jar 등)은 싣지 않는다 — Paper 와 같이 실행에 필요 없다.
val bundledLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    bundledLibraries("com.google.guava:guava:32.1.2-jre")
    bundledLibraries("com.google.guava:failureaccess:1.0.1")
    bundledLibraries("org.yaml:snakeyaml:2.2")
}
val serverLibrariesSource = file("src/main/java/org/domi/furnace/server/plugin/library/ServerLibraries.java")
// start.sh/start.bat 은 installDist 의 lib/* 로 띄운다 — 같은 자원(META-INF/furnace/libraries/*.jar)을 담은 jar 하나를 lib/ 에 넣어 그 실행에서도 꺼내게 한다.
// 시험 클래스패스에는 넣지 않는다(시험은 내려받기 경로를 본다).
val bundledLibrariesJar = tasks.register<Jar>("bundledLibrariesJar") {
    archiveFileName = "furnace-bundled-libraries.jar"
    destinationDirectory = layout.buildDirectory.dir("bundled-libraries")
    into("META-INF/furnace/libraries") { from(bundledLibraries) }
    into("META-INF/furnace/libraries") { from(rootProject.file("NOTICE")); from(rootProject.file("licenses/LICENSE-APACHE-2.0.txt")) }
}
// 같은 이유로 Bukkit·Paper 호환 계층도 jar 하나(META-INF/furnace/compat/ 색인 + jar 그대로)로 lib/ 에 넣는다 — 없으면 start.sh/start.bat 실행에서
// 호환 어댑터가 클래스패스에 없어 Bukkit 플러그인이 안 올라간다(2026-11-12 결정: 폴더에 넣으면 바로 작동). 클래스를 풀지 않으므로 서버 로더는 호환 클래스를 보지 않고,
// Bukkit 플러그인이 없으면 꺼내지도 않는다(CompatJars).
val compatEmbeddedJar = tasks.register<Jar>("compatEmbeddedJar") {
    dependsOn(":furnace-compat-paper:jar")
    dependsOn(compatBundle)
    dependsOn(compatShells)
    dependsOn(compatIndex)
    archiveFileName = "furnace-compat-embedded.jar"
    destinationDirectory = layout.buildDirectory.dir("compat-embedded")
    into("META-INF/furnace/compat") { from({ compatJars() }) }
    from(layout.buildDirectory.dir("compat-index"))
}
distributions { main { contents { into("lib") { from(bundledLibrariesJar); from(compatEmbeddedJar) } } } }
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    // 런타임 의존성(furnace-api jar 등)을 먼저 만들어야 zipTree 로 합칠 수 있다
    dependsOn(configurations.runtimeClasspath)
    // Bukkit·Paper 호환 어댑터(H1)도 함께 싣는다 — 서버가 색인을 보고 jar 를 꺼내 쓴다(plugins.compat.bukkit 가 켜져 있을 때).
    // 클래스를 풀어 넣지 않고 jar 그대로 싣는다: 서버 로더가 호환 클래스를 보지 않아야 호환 계층을 서버 제공 라이브러리 로더 아래에 올릴 수 있다(N-lib-1).
    // 서버 모듈이 이 모듈에 의존하지는 않는다(순환 없음): 모듈 시험은 호환 모듈이 서버에 의존한다.
    dependsOn(":furnace-compat-paper:jar")
    dependsOn(compatBundle)
    dependsOn(compatShells)
    dependsOn(compatIndex)
    description = "서버와 모든 런타임 의존성을 담은 단일 실행 jar"
    archiveFileName = "furnace-server.jar"
    // Implementation-Version = Furnace 빌드 버전(루트 build.gradle.kts, 태그 규칙은 docs/ops/version-tags.md). 마크 버전은 별도 상수.
    manifest {
        attributes["Main-Class"] = "org.domi.furnace.server.FurnaceMain"
        attributes["Implementation-Version"] = rootProject.extra["furnaceBuildVersion"] as String
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    into("META-INF/furnace/compat") { from({ compatJars() }) }
    from(layout.buildDirectory.dir("compat-index"))
    into("META-INF/furnace/libraries") { from(bundledLibraries) }
    // 실린 라이브러리의 라이선스 고지(Apache 2.0) — 저장소 최상위 NOTICE·LICENSE-APACHE-2.0 과 같은 파일
    into("META-INF/furnace/libraries") { from(rootProject.file("NOTICE")); from(rootProject.file("licenses/LICENSE-APACHE-2.0.txt")) }
    inputs.file(serverLibrariesSource)
    // 서명 파일이 남으면 합친 jar 가 SecurityException 으로 실행되지 않는다
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
    // 최상위에 호환 클래스가 하나도 없어야 한다(N-lib-1): 있으면 서버 로더와 공유 로더에 같은 클래스가 둘 정의된다.
    // 접두 목록 대신 내포 jar 의 클래스 이름 자체와 견준다(새 패키지가 생겨도 저절로 잡힌다). 최상위 PluginFormat 서비스 자원도 없어야 한다(실행 때 시작 거부).
    doLast {
        val top = mutableSetOf<String>()
        ZipFile(archiveFile.get().asFile).use { z: ZipFile -> for (e in z.entries()) top.add(e.name) }
        val bad = mutableListOf<String>()
        for (jar in compatJars().files) {
            ZipFile(jar).use { z: ZipFile ->
                for (e in z.entries()) {
                    val n: String = e.name
                    if (n.endsWith(".class") && n != "module-info.class" && !n.startsWith("META-INF/") && n in top) bad.add(n)
                }
            }
        }
        if ("META-INF/services/org.domi.furnace.api.plugin.PluginFormat" in top) bad.add("META-INF/services/org.domi.furnace.api.plugin.PluginFormat")
        if (bad.isNotEmpty()) throw GradleException("fat jar 최상위에 호환 모듈 항목이 있다(N-lib-1): ${bad.take(5)} 외 ${bad.size}개")
        // 실린 라이브러리는 ServerLibraries 의 고정 좌표·sha256 과 같아야 한다(다르면 서버가 그 jar 를 쓰지 않고 내려받으러 간다).
        // 반대로 ServerLibraries 에서 bundled(", C, B)")로 적힌 것은 모두 실려 있어야 한다.
        val src = serverLibrariesSource.readText()
        // 주석 줄은 빼고, 공백·줄바꿈·Repo.X·true 표기를 모두 받는다. 하나도 못 찾으면 형식이 바뀐 것이므로 실패한다
        val pinned = Regex("""^(?!\s*(?://|\*)).*?new Artifact\(\s*"([^"]+)",\s*"([0-9a-fA-F]{64})",\s*(?:Repo\.)?\w+,\s*(?:B|true)\s*\)""", RegexOption.MULTILINE)
            .findAll(src).associate { it.groupValues[1] to it.groupValues[2].lowercase() }
        if (pinned.isEmpty()) throw GradleException("ServerLibraries.java 에서 bundled 항목을 하나도 못 찾았다 — 표기가 바뀌었으면 이 검사도 고쳐라")
        val bundledMentions = Regex("""^(?!\s*(?://|\*)).*?new Artifact\([^;]*?,\s*(?:B|true)\s*\)""", RegexOption.MULTILINE).findAll(src).count()
        if (bundledMentions != pinned.size) throw GradleException("ServerLibraries.java 의 bundled 항목 ${bundledMentions}개 중 좌표·sha256 을 읽은 것은 ${pinned.size}개다")
        val shipped = mutableMapOf<String, String>()
        val resolved = bundledLibraries.resolvedConfiguration.resolvedArtifacts
        for (a in resolved) {
            val id = a.moduleVersion.id
            val coord = "${id.group}:${id.name}:${id.version}"
            val sha = MessageDigest.getInstance("SHA-256").digest(a.file.readBytes()).joinToString("") { "%02x".format(it) }
            shipped[coord] = sha
            val expected = pinned[coord] ?: throw GradleException("서버 jar 에 싣는 $coord 가 ServerLibraries 에 bundled 로 적혀 있지 않다")
            if (expected != sha) throw GradleException("서버 jar 에 싣는 $coord 의 sha256 이 ServerLibraries 고정값과 다르다(기대 $expected, 실제 $sha)")
            if ("META-INF/furnace/libraries/${id.name}-${id.version}.jar" !in top) throw GradleException("$coord 가 fat jar 에 실리지 않았다")
        }
        val missing = pinned.keys - shipped.keys
        if (missing.isNotEmpty()) throw GradleException("ServerLibraries 에 bundled 로 적혔는데 서버 jar 에 안 실렸다: $missing")
        // 풀린 클래스로 섞여 들어오면 서버 로더와 공유 로더에 같은 이름 클래스가 둘 생긴다
        val leaked = top.filter { it.startsWith("com/google/common/") || it.startsWith("org/yaml/snakeyaml/") }
        if (leaked.isNotEmpty()) throw GradleException("fat jar 최상위에 Guava·SnakeYAML 클래스가 있다: ${leaked.take(5)}")
    }
}
tasks.named("assemble") { dependsOn(fatJar) }

// 헤드리스 시험 서버(furnace-test)가 기대는 좌표 — publishToMavenLocal 로 함께 배포한다(docs/api/testing.md).
// 버전은 -PfurnaceVersion=... 으로 바꾼다. jar 파일 이름(project.version)은 건드리지 않는다.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.domi.furnace"
            artifactId = "furnace-server"
            version = (findProperty("furnaceVersion") as String?) ?: ((rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version") + "-SNAPSHOT")
            from(components["java"])
        }
    }
}

// T-238: 바닐라 데이터 생성기 보고서와 Furnace 코드가 쓰는 표를 직접 맞댄다(VanillaReportsParityTest). 보고서는 vanilla-extract 캐시에서 온다(저장소에 없다).
//   ./gradlew -PskipOracle=true :furnace-server:vanillaReportsTest            (-Pcache=<폴더> 또는 FURNACE_VANILLA_CACHE 로 캐시 위치)
tasks.register<Test>("vanillaReportsTest") {
    group = "verification"
    description = "바닐라 보고서(packets·blocks·registries·commands.json) ↔ Furnace 표 대조 시험"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("vanilla-reports") }
    enableAssertions = true
    dependsOn(":vanilla-extract:prepareVanillaReports")
    val mc = (rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version")
    val cache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.cache/furnace-vanilla-extract"
    systemProperty("furnace.vanillaReports", "$cache/$mc/generated/reports")
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }   // 보고서가 바뀔 수 있어 매번 돌린다
    // 태그가 바뀌거나 클래스가 빠져 시험이 하나도 안 돌면 Gradle 은 조용히 초록이다 — 비교하지 않은 초록을 막는다(규칙 5)
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ d, r ->
        if (d.parent == null && r.testCount == 0L) throw GradleException("vanillaReportsTest 가 시험을 하나도 돌리지 않았다(태그 vanilla-reports 확인)")
    }))
}
