plugins {
    id("java")
    id("application")
    id("maven-publish")
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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// 서버 + 의존성(furnace-api, netty, gson, slf4j, logback)을 한 jar 로 묶는다.
//   ./gradlew -PskipOracle=true :furnace-server:fatJar   → furnace-server/build/libs/furnace-server.jar
//   java -Xms2G -Xmx4G -jar furnace-server.jar
// `assemble`/`build` 에도 포함된다(테스트는 안 돈다).
// 일반 jar 는 이름을 바꿔(furnace-server-plain.jar) fatJar 의 furnace-server.jar 와 출력이 겹치지 않게 한다.
// 배포(maven-publish)에서는 분류자 없는 주 산출물이어야 POM 소비자가 찾는다 — 그래서 분류자 대신 파일 이름만 바꾼다.
tasks.jar { archiveClassifier = ""; archiveFileName = "furnace-server-plain.jar" }
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    // 런타임 의존성(furnace-api jar 등)을 먼저 만들어야 zipTree 로 합칠 수 있다
    dependsOn(configurations.runtimeClasspath)
    // Bukkit·Paper 호환 어댑터(H1)도 함께 싣는다 — 서버가 ServiceLoader 로 찾아 plugins.compat.bukkit 가 켜져 있으면 쓴다.
    // 서버 모듈이 이 모듈에 의존하지는 않는다(순환 없음): 모듈 시험은 호환 모듈이 서버에 의존한다.
    dependsOn(":furnace-compat-paper:jar")
    description = "서버와 모든 런타임 의존성을 담은 단일 실행 jar"
    archiveFileName = "furnace-server.jar"
    manifest { attributes["Main-Class"] = "org.domi.furnace.server.FurnaceMain" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    from({ zipTree(project(":furnace-compat-paper").tasks.named<Jar>("jar").get().archiveFile) })
    // 서명 파일이 남으면 합친 jar 가 SecurityException 으로 실행되지 않는다
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
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
