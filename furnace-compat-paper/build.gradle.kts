// SPDX-License-Identifier: GPL-3.0-only
// Bukkit·Paper 플러그인 호환 계층(H 묶음).
// H1: org.bukkit.* 의 호환 API 일부(공개 이름·서명을 따라 새로 쓴 것)와, 서버가 ServiceLoader(PluginFormat)로 불러
//     plugin.yml jar 를 Furnace 플러그인으로 돌리는 껍데기 (docs/api/compat-paper.md).
// H2: Bukkit API jar 없이 만든 "호환 코어"(명령·스케줄러의 의미) (docs/feature-parity/compat-h2-2026-11-07.md).
// 주 코드는 공개 API(furnace-api)만 쓴다 — 서버 구현에 의존하지 않는다(시험만 서버를 올린다).
plugins {
    id("java-library")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// H4: 실제 Bukkit·Paper API 로 컴파일된 jar(Maven Central 의 실제 플러그인·플러그인 라이브러리). 클래스패스에 올리지 않고
// 파일 경로만 시험에 넘긴다 — 바이트코드를 읽어 호환 계층과 견주고(Coverage), 플러그인 jar 는 헤드리스 서버에 올려 본다.
val realWorld by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// H5: NMS·CraftBukkit 을 직접 쓰는 jar 표본(호출 빈도 조사용). 기준표 시험에는 넣지 않는다(realWorld 와 별개) — nmsReport 에만 붙는다.
// commandapi-bukkit-plugin-mojang-mapped 는 Mojang 이름 쪽, commandapi-bukkit-plugin·scoreboard-library 구현은 버전별 Spigot 난독화 이름 쪽 표본이다.
val nmsSurvey by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// N3: 중립 패킷 스키마(furnace-api packet/schema/protocol-767.schema)에서 net.minecraft 패킷 shim 소스를 빌드 때 만든다.
// 결과물은 build/generated 에만 생기고 커밋하지 않는다. 손으로 쓴 shim(src/main/java)이 있는 클래스는 만들지 않는다.
val shimgen: SourceSet = the<SourceSetContainer>().create("shimgen")
dependencies { "shimgenImplementation"(project(":furnace-api")) }
val generateNmsPacketShims by tasks.registering(JavaExec::class) {
    group = "build"
    description = "패킷 스키마에서 net.minecraft 패킷 shim 소스를 만든다"
    val out = layout.buildDirectory.dir("generated/sources/nms-packets/java")
    val hand = layout.projectDirectory.dir("src/main/java")
    val extras = layout.projectDirectory.file("src/shimgen/resources/packet-shim-extras.txt")
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.PacketShimGenerator")
    jvmArgs("-Dstdout.encoding=UTF-8")
    inputs.files(shimgen.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    inputs.dir(hand).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(extras)
    outputs.dir(out)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(out.get().asFile.absolutePath, hand.asFile.absolutePath, extras.asFile.absolutePath)
    })
}
the<SourceSetContainer>()["main"].java.srcDir(generateNmsPacketShims)

// N3 1단계: 패킷 번호 표(protocol-767-ids.txt)와 스키마에서 *PacketTypes·*Protocols(패킷 등록부) shim 소스를 만든다.
val generateNmsProtocolShims by tasks.registering(JavaExec::class) {
    group = "build"
    description = "패킷 번호 표에서 net.minecraft 패킷 등록부(*PacketTypes·*Protocols) shim 소스를 만든다"
    val out = layout.buildDirectory.dir("generated/sources/nms-protocols/java")
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.ProtocolShimGenerator")
    jvmArgs("-Dstdout.encoding=UTF-8")
    inputs.files(shimgen.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    outputs.dir(out)
    argumentProviders.add(CommandLineArgumentProvider { listOf(out.get().asFile.absolutePath) })
}
the<SourceSetContainer>()["main"].java.srcDir(generateNmsProtocolShims)

// 경로 목록은 태스크 실행 때 푼다 — 설정 단계에서 풀면 다른 모듈의 시험(:furnace-server:test)도 jar 13개를 받으려 든다.
class RealWorldJars(@get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jars: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Dfurnace.realworld.jars=" + jars.files.joinToString(File.pathSeparator) { it.absolutePath })
}

// T-075: 서버의 시험 소스(IdentityKit·장면 라이브러리) 클래스 — 호환 기준 장면이 같은 금 파일 장치를 쓴다.
evaluationDependsOn(":furnace-server")
val serverSources = project(":furnace-server").extensions.getByType<JavaPluginExtension>().sourceSets
val serverTest: FileCollection = serverSources["test"].output
val serverBench: FileCollection = serverSources["bench"].output

dependencies {
    api(project(":furnace-api"))
    // 서버가 slf4j 를 싣고 있다 — 설명 파일 경고를 서버 로그로 보낸다
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    // N3 1단계: 패킷 등록부 shim 의 형식 인자(ByteBuf)·서버 소켓 채널 목록(ChannelFuture). 서버가 netty 를 싣고 있다.
    compileOnly("io.netty:netty-codec:4.1.115.Final")   // transport·buffer 를 함께 끌어온다
    // H6: 플러그인 클래스를 올릴 때 CraftBukkit 버전 접미사·리플렉션 이름을 바꾸는 바이트코드 변환(BSD). 서버 fatJar 에 함께 싣는다.
    implementation("org.ow2.asm:asm-commons:9.7.1")
    // H7a: 없는 NMS 심볼 호출을 덫으로 바꿀 때 메서드 하나를 트리로 다룬다(asm-commons 가 이미 끌어오는 것을 명시)
    implementation("org.ow2.asm:asm-tree:9.7.1")

    // 시험은 실제 서버(헤드리스)에 플러그인 jar 를 올려 돌린다
    testImplementation(project(":furnace-server"))
    // T-075: 호환 기준 장면은 서버의 시험 도우미(IdentityKit·장면 라이브러리 인터페이스)를 그대로 쓴다 — docs/testing/compat-scenes.md
    testImplementation(serverTest)
    testImplementation(project(":furnace-test"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("ch.qos.logback:logback-classic:1.5.12")   // H4: 서버 로그의 예외를 모아 플러그인 실패 원인을 읽는다
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 실제 플러그인(plugin.yml 이 있는 jar)
    realWorld("me.lucko:helper:5.6.14")
    realWorld("dev.jorel:commandapi-bukkit-plugin:10.1.2")
    // 실제 플러그인들이 흔히 가져다 쓰는 라이브러리(실제 Bukkit API 로 컴파일됨)
    realWorld("org.bstats:bstats-bukkit:3.2.1")
    realWorld("org.bstats:bstats-base:3.2.1")
    realWorld("com.github.cryptomorin:XSeries:14.0.0")
    realWorld("org.incendo:cloud-bukkit:2.0.1")
    realWorld("org.incendo:cloud-paper:2.0.1")
    realWorld("dev.jorel:commandapi-bukkit-core:12.1.0")
    realWorld("me.lucko:commodore:2.2")
    realWorld("com.github.stefvanschie.inventoryframework:IF:0.12.2")
    realWorld("net.kyori:adventure-platform-bukkit:4.4.1")
    realWorld("fr.mrmicky:fastboard:2.2.2")
    realWorld("dev.dejvokep:boosted-yaml:1.3.8")

    nmsSurvey("dev.jorel:commandapi-bukkit-plugin-mojang-mapped:10.1.2")
    nmsSurvey("de.rapha149.signgui:signgui:2.5.4")
    nmsSurvey("fr.skytasul:glowingentities:2.0.2")
    nmsSurvey("com.github.fierioziy.particlenativeapi:ParticleNativeAPI-core:4.5.1")
    nmsSurvey("xyz.jpenilla:reflection-remapper:0.1.3")
    nmsSurvey("net.megavex:scoreboard-library-implementation:2.8.3")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "2g"
    jvmArgumentProviders.add(RealWorldJars(realWorld))
    // T-075: -Dfurnace.identity.bless=<금 파일 이름> 등 furnace.* 속성을 시험 JVM 에 넘긴다(서버 시험과 같다)
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
}

// ===== 호환 기준 장면 벤치(T-075) =====
// 서버의 bench 소스셋(furnace-server/build.gradle.kts)과 같은 모양: 벤치는 src/bench 에 두어 평소 `test` 에 안 들어간다(시험 요약에 "건너뜀"이 안 섞인다).
// 서버 쪽 BenchReport(JSON 출력)·시험 도우미를 클래스패스로 받는다. 수동 실행(O15 결정 — 야간 벤치 미승인):
//   ./gradlew -PskipOracle=true :furnace-compat-paper:compatSceneBench [--tests '*CompatSceneBench*'] [-Dfurnace.bench.scenes=compat-held-item,...]
// 결과 JSON: furnace-compat-paper/build/bench/CompatScene-<장면>.json (docs/testing/compat-scenes.md)
val mainSources = the<SourceSetContainer>()["main"]
val testSources = the<SourceSetContainer>()["test"]
val benchSources = the<SourceSetContainer>().create("bench") {
    java.srcDir("src/bench/java")
    val main = mainSources
    val test = testSources
    compileClasspath += main.output + test.output + test.compileClasspath + serverBench
    runtimeClasspath += main.output + test.output + test.runtimeClasspath + serverBench
}
val benchCommit = providers.exec { commandLine("git", "rev-parse", "--short=12", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.orElse("unknown")
tasks.register<Test>("compatSceneBench") {
    group = "verification"
    description = "호환 기준 장면(T-075)을 FULL 크기로 돌려 시간을 잰다(수동 실행). 결과 JSON: build/bench/CompatScene-<장면>.json"
    val bench = benchSources
    testClassesDirs = bench.output.classesDirs
    classpath = bench.runtimeClasspath
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "3g"
    minHeapSize = "3g"
    jvmArgs("-XX:+UseG1GC", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints")
    jvmArgumentProviders.add(RealWorldJars(realWorld))
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
    val outDir = layout.buildDirectory.dir("bench")
    systemProperty("furnace.bench", "true")
    systemProperty("furnace.bench.outDir", outDir.get().asFile.absolutePath)
    systemProperty("furnace.bench.commit", benchCommit.get())
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
    doFirst { outDir.get().asFile.mkdirs() }
}
// 벤치 소스가 컴파일은 되게 한다(시험을 돌리면 같이 확인 — 벤치가 몰래 썩지 않게). 벤치를 돌리지는 않는다.
tasks.test { dependsOn("compileBenchJava") }

// 저장소에 넣지 않는 추가 jar(라이선스·크기): -PextraJars=<폴더 또는 jar 경로, 경로 구분자로 여러 개>. compatReport·liveProbe 에만 붙는다.
// 시험(test)에는 붙지 않아 기준표 시험이 사용자 jar 때문에 흔들리지 않는다.
val extraJars: FileCollection = providers.gradleProperty("extraJars").map { v ->
    files(v.split(File.pathSeparator).filter { it.isNotBlank() }.map { p ->
        val f = file(p)
        if (f.isDirectory) fileTree(f) { include("*.jar") } else f
    })
}.orElse(files()).get()
val withExtras = !extraJars.isEmpty

// 실제 jar 대 호환 계층 대조표를 다시 만든다(docs/feature-parity/compat-h4-coverage-2026-11-07.md).
// 추가 jar 를 주면 저장소 문서·기준표는 건드리지 않고 build/compat-h4-extra-report.md 에 쓴다.
val sourceSets = the<SourceSetContainer>()
tasks.register<JavaExec>("compatReport") {
    group = "verification"
    description = "실제 플러그인·라이브러리 jar 가 요구하는 Bukkit API 를 호환 계층과 견준 표를 만든다"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.domi.furnace.compat.realworld.CoverageReport")
    if (withExtras) {
        args(layout.buildDirectory.file("compat-h4-extra-report.md").get().asFile.absolutePath)
    } else {
        args(
            rootProject.layout.projectDirectory.file("docs/feature-parity/compat-h4-coverage-2026-11-07.md").asFile.absolutePath,
            layout.projectDirectory.file("src/test/resources/realworld-baseline.properties").asFile.absolutePath,
        )
    }
    jvmArgumentProviders.add(RealWorldJars(realWorld + extraJars))
}

// 개발용: 실제 플러그인 jar 하나를 헤드리스 서버에 올려 결과를 본다 (-Pjar=<jar 이름 접두>, 추가 jar 는 -PextraJars)
tasks.register<JavaExec>("liveProbe") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.domi.furnace.compat.realworld.LiveProbeMain")
    // -PlibraryCache=<폴더>: 그 폴더를 라이브러리 캐시로 offline 으로 써서 서버 제공 라이브러리(H4 묶음 B)를 준 상태로 잰다(online 이면 실제로 받는다)
    args(providers.gradleProperty("jar").orElse("helper").get(), providers.gradleProperty("libraryCache").orElse("").get(),
        providers.gradleProperty("commands").orElse("").get())
    jvmArgumentProviders.add(RealWorldJars(realWorld + extraJars))
}

// 개발용: helper 를 쓰는 플러그인을 흉내 내 helper 기능마다 된다/안 된다를 잰다(-PlibraryCache=<폴더> 필요 — helper 는 Guava 를 쓴다)
tasks.register<JavaExec>("helperProbe") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.domi.furnace.compat.realworld.HelperUsageProbe")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(providers.gradleProperty("libraryCache").orElse("").get())
    jvmArgumentProviders.add(RealWorldJars(realWorld))
}

// H5: NMS·CraftBukkit 호출 빈도 표(docs/feature-parity/compat-h5-nms-calls-2026-11-07.md)를 다시 만든다.
// 표본 = realWorld 13개 + nmsSurvey. 추가 jar(-PextraJars)를 주면 저장소 문서는 건드리지 않고 build/compat-h5-extra-report.md 에 쓴다.
tasks.register<JavaExec>("nmsReport") {
    group = "verification"
    description = "실제 jar 의 net.minecraft·CraftBukkit 참조 빈도·이름 체계·리플렉션 표를 만든다(H5)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.domi.furnace.compat.realworld.NmsReport")
    if (withExtras) {
        args(layout.buildDirectory.file("compat-h5-extra-report.md").get().asFile.absolutePath)
    } else {
        args(rootProject.layout.projectDirectory.file("docs/feature-parity/compat-h5-nms-calls-2026-11-07.md").asFile.absolutePath)
    }
    jvmArgumentProviders.add(RealWorldJars(realWorld + nmsSurvey + extraJars))
}
