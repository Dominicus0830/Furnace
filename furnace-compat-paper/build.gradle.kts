// SPDX-License-Identifier: GPL-3.0-only
// Bukkit·Paper 플러그인 호환 계층(H 묶음).
// H1: org.bukkit.* 의 호환 API 일부(공개 이름·서명을 따라 새로 쓴 것)와, 서버가 ServiceLoader(PluginFormat)로 불러
//     plugin.yml jar 를 Furnace 플러그인으로 돌리는 껍데기 (docs/api/compat-paper.md).
// H2: Bukkit API jar 없이 만든 "호환 코어"(명령·스케줄러의 의미) (docs/feature-parity/compat-h2-2026-11-07.md).
// 주 코드는 공개 API(furnace-api)만 쓴다 — 서버 구현에 의존하지 않는다(시험만 서버를 올린다).
plugins {
    id("java-library")
}

repositories {
    mavenCentral()
    // N-lib-2: authlib 은 Central 에 없다(Mojang 저장소에만). 컴파일·시험에만 쓰고 배포 jar 에 싣지 않는다 — 서버가 바닐라 libraries.list 의 sha256 으로 받는다(H4b).
    maven("https://libraries.minecraft.net") {
        content { includeGroup("com.mojang") }
    }
}

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

// 경로 목록은 태스크 실행 때 푼다 — 설정 단계에서 풀면 다른 모듈의 시험(:furnace-server:test)도 jar 13개를 받으려 든다.
class RealWorldJars(@get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val jars: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Dfurnace.realworld.jars=" + jars.files.joinToString(File.pathSeparator) { it.absolutePath })
}

dependencies {
    api(project(":furnace-api"))
    // 서버가 slf4j 를 싣고 있다 — 설명 파일 경고를 서버 로그로 보낸다
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    // H6: 플러그인 클래스를 올릴 때 CraftBukkit 버전 접미사·리플렉션 이름을 바꾸는 바이트코드 변환(BSD). 서버 fatJar 에 함께 싣는다.
    implementation("org.ow2.asm:asm-commons:9.7.1")
    // H7a: 없는 NMS 심볼 호출을 덫으로 바꿀 때 메서드 하나를 트리로 다룬다(asm-commons 가 이미 끌어오는 것을 명시)
    implementation("org.ow2.asm:asm-tree:9.7.1")

    // N-lib-2: 호환 계층이 서명에 쓰는 라이브러리(바닐라 1.21.1 판). 서버 공유 로더가 같은 판을 받는다(ServerLibraries).
    compileOnly("com.mojang:authlib:6.0.54") { isTransitive = false }
    compileOnly("com.google.guava:guava:32.1.2-jre") { isTransitive = false }   // PropertyMap 의 상위 타입
    testImplementation("com.mojang:authlib:6.0.54") { isTransitive = false }
    testImplementation("com.google.guava:guava:32.1.2-jre") { isTransitive = false }
    testImplementation("com.google.guava:failureaccess:1.0.1")

    // 시험은 실제 서버(헤드리스)에 플러그인 jar 를 올려 돌린다
    testImplementation(project(":furnace-server"))
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
}

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
