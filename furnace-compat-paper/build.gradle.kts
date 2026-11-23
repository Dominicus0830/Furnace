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
val lockForPackets = layout.projectDirectory.file("src/shimspec/shim-" + (rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version") + ".lock")
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
    // T-229: 연결 명세 생성기가 만드는 클래스(잠금 파일의 C 줄)는 빈 채움 shim 을 만들지 않는다
    inputs.files(lockForPackets).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(out)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(out.get().asFile.absolutePath, hand.asFile.absolutePath, extras.asFile.absolutePath, lockForPackets.asFile.absolutePath)
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

// T-229·T-232: 연결 명세(src/shimspec/*.spec) + 잠금 파일(src/shimspec/shim-<판>.lock) + 손 조각(src/shims/java) → net.minecraft shim 소스.
// 바닐라 jar 없이 돈다(잠금 파일이 서명표에서 푼 선언을 담는다). 형식: src/shimspec/README.md. 결과물은 build/generated 에만(커밋 안 함).
dependencies {
    "shimgenImplementation"(project(":vanilla-extract"))   // 잠금 파일 만들 때 서명표 읽개(SigTable)
    "shimgenImplementation"("org.ow2.asm:asm:9.8")            // 제네릭 서명 읽기
}
val mcVersion: String = (rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version")
val shimSpecDir = layout.projectDirectory.dir("src/shimspec")
val shimLockFile = layout.projectDirectory.file("src/shimspec/shim-$mcVersion.lock")
val shimFragments = layout.projectDirectory.dir("src/shims/java")
val handShims = layout.projectDirectory.dir("src/main/java")
val packetShimOut = layout.buildDirectory.dir("generated/sources/nms-packets/java")
val protocolShimOut = layout.buildDirectory.dir("generated/sources/nms-protocols/java")
val nmsShimOut = layout.buildDirectory.dir("generated/sources/nms-shims/java")
val nmsShimRes = layout.buildDirectory.dir("generated/resources/nms-shims")
val generateNmsShims by tasks.registering(JavaExec::class) {
    group = "build"
    description = "연결 명세·잠금 파일·손 조각에서 net.minecraft shim 소스를 만든다(T-229)"
    dependsOn(generateNmsPacketShims, generateNmsProtocolShims)
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.ShimGenerator")
    jvmArgs("-Dstdout.encoding=UTF-8")
    inputs.files(shimgen.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    inputs.dir(shimSpecDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(shimFragments).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(handShims).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(packetShimOut).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(protocolShimOut).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(nmsShimOut)
    outputs.dir(nmsShimRes)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(shimSpecDir.asFile.absolutePath, shimLockFile.asFile.absolutePath, shimFragments.asFile.absolutePath,
            nmsShimOut.get().asFile.absolutePath, nmsShimRes.get().asFile.absolutePath, handShims.asFile.absolutePath,
            packetShimOut.get().asFile.absolutePath + File.pathSeparator + protocolShimOut.get().asFile.absolutePath)
    })
}
// 생성기 시험(ShimGeneratorTest·ShimLockTest)은 호환 계층 시험에서 돈다
dependencies {
    testImplementation(shimgen.output)
    testImplementation(project(":vanilla-extract"))
}
the<SourceSetContainer>()["main"].java.srcDir(files(nmsShimOut).builtBy(generateNmsShims))
the<SourceSetContainer>()["main"].resources.srcDir(files(nmsShimRes).builtBy(generateNmsShims))

// 잠금 파일 쓰기·검사: 서명표 캐시(tools/compat/sig-tables.sh)가 있어야 한다. 검사는 CI vanilla-gates 가 돈다. 캐시 위치는 -Pcache=<폴더> 또는 FURNACE_VANILLA_CACHE(기본 ~/.cache/furnace-vanilla-extract).
fun shimLockTask(name: String, mode: String, what: String) = tasks.register<JavaExec>(name) {
    group = "verification"
    description = what
    dependsOn(generateNmsPacketShims, generateNmsProtocolShims)
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.ShimLockTool")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val cache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("user.home") + "/.cache/furnace-vanilla-extract")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(mode, mcVersion, "$cache/$mcVersion", shimSpecDir.asFile.absolutePath, shimLockFile.asFile.absolutePath,
            listOf(handShims.asFile.absolutePath, shimFragments.asFile.absolutePath, packetShimOut.get().asFile.absolutePath,
                protocolShimOut.get().asFile.absolutePath).joinToString(File.pathSeparator))
    })
}
shimLockTask("shimLock", "write", "연결 명세를 서명표로 풀어 잠금 파일(src/shimspec/shim-<판>.lock)을 쓴다")
shimLockTask("shimLockCheck", "check", "잠금 파일이 서명표·연결 명세와 같은지 본다(CI vanilla-gates)")

// T-264: CraftBukkit 계층 잠금(src/shimspec/craft-hierarchy-<판>.lock — 이름만, Mojang 이름은 지문). 서명표 캐시가 있어야 한다.
val craftLockFile = layout.projectDirectory.file("src/shimspec/craft-hierarchy-$mcVersion.lock")
fun craftLockTask(name: String, mode: String, what: String) = tasks.register<JavaExec>(name) {
    group = "verification"
    description = what
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.CraftHierarchyLock")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val cache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("user.home") + "/.cache/furnace-vanilla-extract")
    argumentProviders.add(CommandLineArgumentProvider { listOf(mode, mcVersion, "$cache/$mcVersion", craftLockFile.asFile.absolutePath) })
}
craftLockTask("craftLock", "write", "Paper 서명표로 CraftBukkit 계층 잠금 파일을 쓴다(T-264)")
craftLockTask("craftLockCheck", "check", "CraftBukkit 계층 잠금 파일이 서명표와 같은지 본다(CI vanilla-gates, T-264)")

// T-181: authlib(GameProfile·Property·PropertyMap) 대역. Mojang 저장소에만 있어 컴파일 경로에 받지 않는다 — 호환 계층이 부르는 멤버만
// 진짜 authlib 6.0.54 와 같은 서술자로 손으로 쓴 것을 컴파일·시험에만 쓰고 jar 에는 넣지 않는다(서버는 쓰는 플러그인이 있으면 진짜를 받는다).
// CompatLinkTest 는 이 대역을 뺀 로더로 링크를 본다.
val authlibStub: SourceSet = the<SourceSetContainer>().create("authlib")
dependencies {
    compileOnly(authlibStub.output)
    testCompileOnly(authlibStub.output)
    testRuntimeOnly(authlibStub.output)
}

// N2b-6: DataFixerUpper(Codec·DataResult·DynamicOps) 대역 — authlib 과 같은 방식(Mojang 저장소에만 있어 컴파일 경로에 받지 않는다). NMS shim 의
// Codec 상수(DataComponentPatch.CODEC 등)가 쓰는 모양만 손으로 썼고 jar 에는 넣지 않는다(서버는 쓰는 플러그인이 있으면 묶음 datafixerupper 를 받는다).
val dfuStub: SourceSet = the<SourceSetContainer>().create("dfu")
dependencies {
    // 로컬 대조용(커밋 대상 아님·CI 는 안 쓴다): -PdfuJar=<진짜 datafixerupper-8.0.16.jar> 를 주면 시험이 대역 대신 진짜 DFU 로 돈다 — NMS shim 이 진짜와
    // 같은 서술자·결과인지 보는 길. 대역 앞에 놓아야 먼저 읽힌다(같은 이름 클래스). guava·fastutil·gson 은 DFU 가 끌어 쓴다.
    providers.gradleProperty("dfuJar").orNull?.let { jar ->
        testRuntimeOnly(files(jar))
        testRuntimeOnly("com.google.guava:guava:32.1.2-jre")
        testRuntimeOnly("it.unimi.dsi:fastutil:8.5.12")
        testRuntimeOnly("com.google.code.gson:gson:2.10.1")
        testRuntimeOnly("org.slf4j:slf4j-api:2.0.16")
    }
    compileOnly(dfuStub.output)
    testCompileOnly(dfuStub.output)
    testRuntimeOnly(dfuStub.output)
}

// 5번 묶음: Brigadier(ArgumentType·StringReader·CommandContext …) 대역. 진짜는 Mojang 저장소(libraries.minecraft.net)에만 있어 컴파일 경로에 받지 않는다 —
// 명령 인자 shim(BlockPredicateArgument·ItemPredicateArgument)이 ArgumentType 을 구현하는 데 필요한 모양만 손으로 썼고 jar 에는 넣지 않는다
// (서버는 쓰는 플러그인이 있으면 묶음 brigadier 를 받아 공유 로더로 준다).
val brigadierStub: SourceSet = the<SourceSetContainer>().create("brigadier")
dependencies {
    // 로컬 대조용(커밋 대상 아님·CI 는 안 쓴다): -PbrigadierJar=<진짜 brigadier-1.3.10.jar> 를 주면 시험이 대역 대신 진짜 Brigadier 로 돈다(dfuJar 와 같은 방식).
    providers.gradleProperty("brigadierJar").orNull?.let { jar -> testRuntimeOnly(files(jar)) }
    compileOnly(brigadierStub.output)
    testCompileOnly(brigadierStub.output)
    testRuntimeOnly(brigadierStub.output)
}

// T-248: Adventure(Component·GsonComponentSerializer) 대역 — 컴파일 전용. 시험은 Maven Central 의 진짜 adventure 를 쓴다(서버는 쓰는 플러그인이 있으면 받는다).
val adventureStub: SourceSet = the<SourceSetContainer>().create("adventure")
dependencies {
    compileOnly(adventureStub.output)
    testCompileOnly(adventureStub.output)
    testRuntimeOnly("net.kyori:adventure-text-serializer-gson:4.17.0")
}

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
    // T-212: NMS GsonHelper(FAWE 옛 도식 변환기)의 서명이 Gson 형식을 받는다. 서버가 gson 을 싣고 있다(Paper 도 플러그인에 gson 을 준다).
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    // 1번 묶음: FriendlyByteBuf#writeVector3f·writeQuaternion 의 서명이 JOML 형식을 받는다. Paper 처럼 서버 라이브러리 묶음 joml 이 준다.
    compileOnly("org.joml:joml:1.10.5")
    testImplementation("org.joml:joml:1.10.5")
    // 4번 묶음: StateDefinition#getPossibleStates(ImmutableList)·ClientboundSectionBlocksUpdatePacket(ShortSet) 의 서명. 서버 라이브러리 묶음(Guava 는 서버 jar,
    // fastutil 은 내려받기)이 그 이름을 쓰는 플러그인에게 공유 로더로 준다 — shim 은 그 메서드가 불릴 때만 이 형식에 닿는다.
    compileOnly("com.google.guava:guava:32.1.2-jre")
    testImplementation("com.google.guava:guava:32.1.2-jre")
    compileOnly("it.unimi.dsi:fastutil:8.5.12")
    testImplementation("it.unimi.dsi:fastutil:8.5.12")
    // H6: 플러그인 클래스를 올릴 때 CraftBukkit 버전 접미사·리플렉션 이름을 바꾸는 바이트코드 변환(BSD). 서버 fatJar 에 함께 싣는다.
    implementation("org.ow2.asm:asm-commons:9.7.1")
    // H7a: 없는 NMS 심볼 호출을 덫으로 바꿀 때 메서드 하나를 트리로 다룬다(asm-commons 가 이미 끌어오는 것을 명시)
    implementation("org.ow2.asm:asm-tree:9.7.1")
    // T-141 E: Player.spigot() 의 BungeeCord 채팅 컴포넌트. 서버에 싣지 않는다 — 쓰는 플러그인이 있으면 서버 라이브러리 묶음 bungeecord-chat 으로 받는다
    compileOnly("net.md-5:bungeecord-chat:1.20-R0.2")

    // 시험은 실제 서버(헤드리스)에 플러그인 jar 를 올려 돌린다
    testImplementation(project(":furnace-server"))
    // T-075: 호환 기준 장면은 서버의 시험 도우미(IdentityKit·장면 라이브러리 인터페이스)를 그대로 쓴다 — docs/testing/compat-scenes.md
    testImplementation(serverTest)
    testImplementation(project(":furnace-test"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("net.md-5:bungeecord-chat:1.20-R0.2")
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
    useJUnitPlatform { excludeTags("full-shells") }   // T-260: 실제 서명표로 만든 전체 껍데기 시험은 fullShellsTest 가 돈다(서명표 캐시 필요)
    enableAssertions = true
    maxHeapSize = "2g"
    jvmArgumentProviders.add(RealWorldJars(realWorld))
    systemProperty("furnace.language", "en")   // 서버 문구를 확인하는 시험이 실행 환경 언어에 흔들리지 않게
    // CompatLinkTest 가 authlib·DataFixerUpper 대역을 뺀 클래스패스로 링크를 본다
    val stubDirs = authlibStub.output.classesDirs + dfuStub.output.classesDirs + brigadierStub.output.classesDirs + adventureStub.output.classesDirs
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dfurnace.test.authlibStub=" + stubDirs.asPath) })
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

// 2026-11-17: NMS shim 점검 — 실제 플러그인 jar 가 부르는 NMS 심볼 중 shim 에 없는 것을 종류별(서명 다름·진짜 없음·Paper 에도 없음)로 가르고 몇 개 플러그인이
// 만나는지 센다. jar 는 저장소에 넣지 않는다: -PauditJars=<폴더 또는 jar 경로, 경로 구분자로 여러 개>. 결과 build/nms-shim-audit.md · .tsv (docs/feature-parity/compat-nms-shim-audit-2026-11-17.md)
val auditJars: FileCollection = providers.gradleProperty("auditJars").map { v ->
    files(v.split(File.pathSeparator).filter { it.isNotBlank() }.map { p ->
        val f = file(p)
        if (f.isDirectory) fileTree(f) { include("**/*.jar") } else f
    })
}.orElse(files()).get()
tasks.register<JavaExec>("nmsShimAudit") {
    group = "verification"
    description = "실제 jar 가 부르는 NMS 심볼 중 shim 에 없는 것을 종류별로 센다(-PauditJars=<jar 폴더>)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.domi.furnace.compat.realworld.NmsShimAudit")
    jvmArgs("-Dstdout.encoding=UTF-8")
    args(layout.buildDirectory.file("nms-shim-audit.md").get().asFile.absolutePath, layout.buildDirectory.file("nms-shim-audit.tsv").get().asFile.absolutePath)
    jvmArgumentProviders.add(RealWorldJars(auditJars))
    // -PpaperJar=<versions/1.21.1/paper-1.21.1.jar> 를 주면 Paper 의 NMS 클래스 전수와 shim 을 서술자까지 견주는 절이 더해진다(jar 는 읽기만 한다)
    providers.gradleProperty("paperJar").orNull?.let { systemProperty("furnace.audit.paperJar", it) }
    // T-234: -PrankingOut=<.md>(인기 가중치 순위 + Class.forName 감지 대상) -Pmanifest=<sample-manifest.tsv>(tools/compat/fetch-sample-plugins.py 가 만든다)
    providers.gradleProperty("rankingOut").orNull?.let { systemProperty("furnace.audit.ranking", it) }
    providers.gradleProperty("manifest").orNull?.let { systemProperty("furnace.audit.manifest", it) }
    // -PspigotDir=<폴더>: Spigot 이름 jar 도 잰다. 폴더에 server.txt(Mojang 공식 매핑)·bukkit-1.21.1-cl.csrg(또는 bukkit-cl.csrg)가 있어야 한다(멤버 csrg 는 없어도 된다).
    // 표 파일은 저장소에 넣지 않는다(Mojang·Spigot 저작물) — 경로만 받는다(서버가 받는 길은 SpigotMappingFiles)
    providers.gradleProperty("spigotDir").orNull?.let { systemProperty("furnace.audit.spigotDir", it) }
    doFirst { layout.buildDirectory.get().asFile.mkdirs() }
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

// T-260: 전체 껍데기 — Paper 1.21.1 내부 클래스(net.minecraft·CraftBukkit 7,139 + Paper 서버 전용) 가운데 호환 계층에 없는 것 전부를 서명표에서
// "모양만" 만든다(docs/roadmap/full-shells-2026-11-23.md). 서명표는 캐시에만 있으므로(tools/compat/sig-tables.sh) 저장소에는 표의 지문(src/shimspec/full-shells-<판>.fp)만 둔다.
// 결과는 호환 jar 와 따로 furnace-compat-shells.jar 로 서버 fatJar 에 실린다. 서버 빌드는 서명표가 캐시에 없으면 경고하고 뺀다(-PnoFullShells 로 늘 빼기, -PrequireFullShells 로 없으면 실패).
//   ./gradlew -PskipOracle=true :furnace-compat-paper:fullShellsJar      (-PfullShellsFpWrite=true: 표를 일부러 다시 만들었을 때 지문 다시 쓰기)
//   ./gradlew -PskipOracle=true :furnace-compat-paper:fullShellsTest     (전부 올리고 초기화 + 크기·시간 보고 — CI vanilla-gates)
val fullShellsCache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
    ?: (System.getProperty("user.home") + "/.cache/furnace-vanilla-extract")
val fullShellsTables = listOf("sig-table-paper-server.tsv.gz", "sig-table.tsv.gz", "sig-table-paper-api.tsv.gz")
    .map { "$fullShellsCache/$mcVersion/$it" }
val fullShellsLibs = "$fullShellsCache/$mcVersion/work/libs"   // 바닐라 서버 jar 가 싣는 라이브러리(vanilla-extract 가 풀어 둔 것)
val fullShellsMain: SourceSet = the<SourceSetContainer>()["main"]
// 서버가 플러그인에게 주는 라이브러리(ServerLibraries 의 목록 그대로 — Adventure·SnakeYAML 등). 껍데기의 상위 형식이 이것이면 실행 때 있다.
val fullShellsServerLibs by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
Regex("new Artifact\\(\"([^\"]+)\"").findAll(rootProject.file("furnace-server/src/main/java/org/domi/furnace/server/plugin/library/ServerLibraries.java").readText())
    .map { it.groupValues[1] }.filter { !it.startsWith("com.mojang:") }   // Mojang 라이브러리는 Mojang 저장소에만 있다 — 바닐라 라이브러리 폴더(work/libs)에 이미 있다
    .forEach { dependencies.add(fullShellsServerLibs.name, it) }
val fullShellsTestSources: SourceSet = the<SourceSetContainer>()["test"]
val fullShellsDir = layout.buildDirectory.dir("full-shells/classes")
val fullShellsReport = layout.buildDirectory.file("full-shells/report.md")
val fullShellsFp = layout.projectDirectory.file("src/shimspec/full-shells-$mcVersion.fp")
val generateFullShells by tasks.registering(JavaExec::class) {
    group = "build"
    description = "서명표에서 호환 계층에 없는 Paper 내부 클래스 전부의 껍데기를 만든다(T-260)"
    val main = fullShellsMain
    dependsOn(main.classesTaskName)
    classpath = shimgen.runtimeClasspath
    mainClass.set("org.domi.furnace.compat.shimgen.FullShells")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    maxHeapSize = "2g"
    inputs.files(shimgen.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    inputs.files(fullShellsTables).withPathSensitivity(PathSensitivity.NONE)
    inputs.files(main.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    inputs.files(fullShellsServerLibs).withNormalizer(ClasspathNormalizer::class)
    inputs.files(fullShellsFp).withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(fullShellsDir)
    outputs.file(fullShellsReport)
    val writeFp = providers.gradleProperty("fullShellsFpWrite").isPresent
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(fullShellsDir.get().asFile.absolutePath, fullShellsReport.get().asFile.absolutePath, fullShellsFp.asFile.absolutePath, writeFp.toString(),
            (main.runtimeClasspath.files + fullShellsServerLibs.files).joinToString(File.pathSeparator) { it.absolutePath }) + fullShellsTables + fullShellsLibs +
            // T-261: 호환 jar 자신의 클래스 — 그 안의 범위 클래스에 빠진 멤버를 합쳐 껍데기 jar 에 같은 이름으로 넣는다(서버는 껍데기 jar 를 앞에 둔다)
            main.output.classesDirs.files.joinToString(File.pathSeparator) { it.absolutePath }
    })
}
val fullShellsJar by tasks.registering(Jar::class) {
    group = "build"
    description = "전체 껍데기 jar(furnace-compat-shells.jar) — 서버가 호환 jar 앞에 싣는다(T-260 껍데기 + T-261 빠진 멤버를 합친 손 shim)"
    from(generateFullShells.map { fullShellsDir.get() })
    archiveFileName = "furnace-compat-shells.jar"
    destinationDirectory = layout.buildDirectory.dir("full-shells")
}
val fullShellsElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add(fullShellsElements.name, fullShellsJar)
// 실제 서명표로 만든 껍데기 전부를 호환 계층·라이브러리와 한 로더에 올려 초기화까지 하고, 크기·시간을 build/full-shells/load-report.md 에 쓴다.
// 라이브러리 jar 는 바닐라 서버 jar(캐시) 안의 META-INF/libraries 에서 꺼내 쓴다(Paper 처럼 실행 때 서버 라이브러리로 있는 것).
tasks.register<Test>("fullShellsTest") {
    group = "verification"
    description = "전체 껍데기를 전부 올리고 초기화하는 시험 + 크기·시간 보고(T-260, 서명표 캐시 필요)"
    val test = fullShellsTestSources
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    useJUnitPlatform { includeTags("full-shells") }
    enableAssertions = true
    maxHeapSize = "2g"
    dependsOn(fullShellsJar)
    inputs.files(fullShellsJar)
    systemProperty("furnace.fullShells.jar", layout.buildDirectory.file("full-shells/furnace-compat-shells.jar").get().asFile.absolutePath)
    systemProperty("furnace.fullShells.report", layout.buildDirectory.file("full-shells/load-report.md").get().asFile.absolutePath)
    // T-264: -Dfurnace.craftLive.write=true 로 Craft 진짜 몸통 표(craft-live-api-methods-<판>.txt)를 다시 쓴다
    System.getProperty("furnace.craftLive.write")?.let { systemProperty("furnace.craftLive.write", it) }
    dependsOn(tasks.jar)
    // T-261: 껍데기 jar 가 앞이므로 합친 목록 밖 이름이 호환 jar 에도 있으면 손 shim 이 조용히 가려진다 — 시험이 막는다
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dfurnace.fullShells.compatJar=" + tasks.jar.get().archiveFile.get().asFile.absolutePath) })
    // 실제 실행과 같은 모양의 따로 된 로더: 껍데기 jar(합친 손 shim 이 이긴다, T-261) → 호환 jar → 호환 실행 의존 → 서버 제공 라이브러리 → 바닐라 라이브러리(부모는 플랫폼 로더)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        // 바닐라의 log4j→slf4j 백엔드(log4j-slf4j2-impl)는 Furnace 가 싣지 않는다 — 서버 제공 log4j-to-slf4j 와 함께 있으면 로그 초기화가 실패한다
        val libs = fileTree(fullShellsLibs) { include("**/*.jar"); exclude("**/log4j-slf4j2-impl-*.jar") }.files.sorted()
        val cp = listOf(fullShellsJar.get().archiveFile.get().asFile, tasks.jar.get().archiveFile.get().asFile) +
            fullShellsMain.runtimeClasspath.files.filter { it.isFile } + fullShellsServerLibs.files + libs
        listOf("-Dfurnace.fullShells.classpath=" + cp.joinToString(File.pathSeparator) { it.absolutePath })
    })
    testLogging { showStandardStreams = true }
}

// ===== 바닐라 차분 시험(T-235) =====
// 이름 푼 바닐라 jar(캐시, :vanilla-extract:prepareVanillaJars)를 격리 로더에 올리고 shim 과 같은 입력으로 결과를 견준다.
// 시험은 src/vanillaDiff 에 두어 평소 `test` 에 안 들어간다(바닐라 jar 가 있어야 하므로). CI vanilla-gates 가 돌린다.
//   ./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest [-PvdiffClasses=<클래스,…>] [-PvdiffIterations=<N>] [-Pcache=<폴더>]
// 보고서: build/reports/vanilla-diff/shim-proof-report.md. 대상: src/vanillaDiff/resources/vanilla-diff-targets.txt
val vanillaDiffSources = the<SourceSetContainer>().create("vanillaDiff") {
    compileClasspath += mainSources.output + testSources.compileClasspath
    runtimeClasspath += mainSources.output + testSources.runtimeClasspath
}
tasks.register<Test>("vanillaDiffTest") {
    group = "verification"
    description = "바닐라 jar ↔ shim 값 클래스 차분 시험(T-235). 보고서 build/reports/vanilla-diff/shim-proof-report.md"
    testClassesDirs = vanillaDiffSources.output.classesDirs
    classpath = vanillaDiffSources.runtimeClasspath
    useJUnitPlatform()
    enableAssertions = true
    dependsOn(":vanilla-extract:prepareVanillaJars")
    val mc = (rootProject.extra["mc"] as java.util.Properties).getProperty("mc.version")
    val cache = (findProperty("cache") as String?) ?: System.getenv("FURNACE_VANILLA_CACHE")?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/.cache/furnace-vanilla-extract"
    systemProperty("furnace.vdiff.classpath", "$cache/$mc/vanilla-classpath.txt")
    systemProperty("furnace.vdiff.mc", mc)
    systemProperty("furnace.vdiff.specDir", file("src/shimspec").absolutePath)
    // shim 클래스가 이 폴더들(호환 계층 main 출력)에서 왔는지 시험이 단언한다
    systemProperty("furnace.vdiff.shimDirs", mainSources.output.classesDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
    systemProperty("furnace.vdiff.report", layout.buildDirectory.file("reports/vanilla-diff/shim-proof-report.md").get().asFile.absolutePath)
    (findProperty("vdiffClasses") as String?)?.let { systemProperty("furnace.vdiff.classes", it) }
    (findProperty("vdiffIterations") as String?)?.let { systemProperty("furnace.vdiff.iterations", it) }
    (findProperty("vdiffSeed") as String?)?.let { systemProperty("furnace.vdiff.seed", it) }
    // 대상 파일에서 budget= 으로 시간 예산을 켠 클래스의 예산(초)을 바꾼다(DiffRunner.HARD_WATCHDOG_SECONDS 설명)
    (findProperty("vdiffBudget") as String?)?.let { systemProperty("furnace.vdiff.memberBudgetSeconds", it) }
    testLogging { showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    outputs.upToDateWhen { false }
    // 시험이 하나도 안 돌면 Gradle 은 조용히 초록이다 — 비교하지 않은 초록을 막는다(규칙 5)
    afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ d, r ->
        if (d.parent == null && r.testCount == 0L) throw GradleException("vanillaDiffTest ran no test")
    }))
}
// 차분 시험 소스가 컴파일은 되게 한다(평소 시험 때 같이 확인 — 몰래 썩지 않게).
tasks.test { dependsOn("compileVanillaDiffJava") }
