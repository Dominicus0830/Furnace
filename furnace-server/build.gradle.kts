plugins {
    id("java")
    id("application")
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
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

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
    System.getProperties().forEach { k, v ->
        if (k.toString().startsWith("furnace.")) systemProperty(k.toString(), v.toString())
    }
}

// 서버 + 의존성(furnace-api, netty, gson, slf4j, logback)을 한 jar 로 묶는다.
//   ./gradlew -PskipOracle=true :furnace-server:fatJar   → furnace-server/build/libs/furnace-server.jar
//   java -Xms2G -Xmx4G -jar furnace-server.jar
// `assemble`/`build` 에도 포함된다(테스트는 안 돈다).
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "서버와 모든 런타임 의존성을 담은 단일 실행 jar"
    archiveFileName = "furnace-server.jar"
    manifest { attributes["Main-Class"] = "org.domi.furnace.server.FurnaceMain" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    // 서명 파일이 남으면 합친 jar 가 SecurityException 으로 실행되지 않는다
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "module-info.class")
}
tasks.named("assemble") { dependsOn(fatJar) }
