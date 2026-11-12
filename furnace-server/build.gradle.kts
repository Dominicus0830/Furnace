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
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "3g"   // 통합 테스트가 월드를 많이 만들어 기본 힙으로는 모자란다
    testLogging { showStandardStreams = true }
    // 명령 블록이 명령을 같은 틱 안에 끝내려고 기다리는 시간 예산(서버 기본 20ms)을 시험 JVM 에서만 넉넉히 준다.
    // 20ms 로는 CI 처럼 바쁜 러너에서 다른 region 왕복이 늦어 CommandBlockOneTickTest 가 자주 실패했다(2026-11-05~07 집계 24건, 서버 동작 문제가 아니라 시험 시간 예산 문제).
    // 시험은 "그 틱 안에 끝난다"는 동작을 보는 것이라 예산 크기에는 기대지 않는다. 직접 정한 값(-Dfurnace.commandBlockWaitMicros=…)이 있으면 아래 반복문이 덮어쓴다.
    systemProperty("furnace.commandBlockWaitMicros", "500000")
    // @NoRetry 시험이 실패하면 NoRetryFailureListener 가 여기에 적는다. CI 의 재시도 단계가 읽는다(.github/workflows/ci.yml). 시작 전에 지운다.
    val noRetryReport = layout.buildDirectory.file("no-retry-failures.txt").get().asFile
    systemProperty("furnace.noRetryReport", noRetryReport.absolutePath)
    doFirst { noRetryReport.delete() }
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
    // CI 분할 실행: -PtestShard=2/3 → 시험 클래스 이름 해시가 (2-1) % 3 인 클래스만 돌린다(서버 시험 3천 개를 러너 여러 대로 나눈다).
    // 같은 이름은 항상 같은 조각에 들어가고(@Nested·내부 클래스는 바깥 클래스를 따른다) 조각을 모두 합치면 전체와 같다. 속성이 없으면 전부 돌린다.
    // shardSalt 는 조각 시간이 고르게 나뉘도록 tools/ci/shard-balance.py 로 고른 값이다(바꿔도 정확성에는 영향 없고 균형만 달라진다).
    (findProperty("testShard") as String?)?.let { spec ->
        val m = Regex("""(\d+)/(\d+)""").matchEntire(spec.trim()) ?: error("testShard 는 '번호/전체' 꼴이다(예: 2/3): $spec")
        val total = m.groupValues[2].toInt()
        val index = m.groupValues[1].toInt()
        val shardSalt = "100"
        require(total >= 1 && index in 1..total) { "testShard 범위 오류: $spec" }
        include(Spec<FileTreeElement> { el ->
            el.isDirectory || Math.floorMod((el.path.substringBefore('$').removeSuffix(".class") + shardSalt).hashCode(), total) == index - 1
        })
    }
}

// 줄·가지 커버리지(JaCoCo, 보고용 — 게이트 아님). 평소 시험은 에이전트 없이 돌고(느려짐·시간 민감 시험 영향 방지), -Pcoverage 를 주면 켠다:
//   ./gradlew -PskipOracle=true -Pcoverage :furnace-server:test :furnace-server:jacocoTestReport
// CI 는 기준 브랜치 push 실행의 서버 시험 조각마다 test.exec 를 올리고, 합치는 작업(coverage)이 jacocoCoverageReport 로 표 하나를 만든다(docs/ci.md).
tasks.test {
    extensions.configure<JacocoTaskExtension> { isEnabled = findProperty("coverage") != null }
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
// 호환 모듈 jar 와 그 런타임 의존(ASM) — furnace-api 는 서버 로더에 이미 있으므로 빼고, 호환 모듈 자신은 넣는다.
fun compatJars() = compatBundle.filter { it.name.endsWith(".jar") && (!it.name.startsWith("furnace-") || it.name.startsWith("furnace-compat")) }
// 호환 jar 이름 색인(N-lib-1) — 서버는 이 자원이 있으면 jar 를 꺼내 서버 제공 라이브러리 로더의 첫 항목으로 붙인다(CompatJars).
val compatIndex = tasks.register("compatIndex") {
    dependsOn(":furnace-compat-paper:jar")
    dependsOn(compatBundle)
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
distributions { main { contents { into("lib") { from(bundledLibrariesJar) } } } }
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    // 런타임 의존성(furnace-api jar 등)을 먼저 만들어야 zipTree 로 합칠 수 있다
    dependsOn(configurations.runtimeClasspath)
    // Bukkit·Paper 호환 어댑터(H1)도 함께 싣는다 — 서버가 색인을 보고 jar 를 꺼내 쓴다(plugins.compat.bukkit 가 켜져 있을 때).
    // 클래스를 풀어 넣지 않고 jar 그대로 싣는다: 서버 로더가 호환 클래스를 보지 않아야 호환 계층을 서버 제공 라이브러리 로더 아래에 올릴 수 있다(N-lib-1).
    // 서버 모듈이 이 모듈에 의존하지는 않는다(순환 없음): 모듈 시험은 호환 모듈이 서버에 의존한다.
    dependsOn(":furnace-compat-paper:jar")
    dependsOn(compatBundle)
    dependsOn(compatIndex)
    description = "서버와 모든 런타임 의존성을 담은 단일 실행 jar"
    archiveFileName = "furnace-server.jar"
    manifest { attributes["Main-Class"] = "org.domi.furnace.server.FurnaceMain" }
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
            version = (findProperty("furnaceVersion") as String?) ?: "1.21.1-SNAPSHOT"
            from(components["java"])
        }
    }
}
