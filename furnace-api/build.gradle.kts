// SPDX-License-Identifier: MIT
import java.util.Properties
plugins {
    id("java-library")
    id("maven-publish")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// V-01: gradle/minecraft.properties → GameVersion(컴파일 시간 상수). 결과물은 build/generated 에만 생기고 커밋하지 않는다.
@Suppress("UNCHECKED_CAST") val mcProps = rootProject.extra["mc"] as Properties
val gameVersionDir = layout.buildDirectory.dir("generated/gameVersion")
val generateGameVersion by tasks.registering {
    group = "build"
    description = "gradle/minecraft.properties 에서 GameVersion 상수 클래스를 만든다"
    val propsFile = rootProject.file("gradle/minecraft.properties")
    val outDir = gameVersionDir
    inputs.file(propsFile)
    outputs.dir(outDir)
    doLast {
        fun p(k: String) = mcProps.getProperty(k) ?: error("gradle/minecraft.properties 에 $k 가 없다")
        val src = """
            |// 자동 생성 — gradle/minecraft.properties 에서 만든다(generateGameVersion). 손으로 고치지 않는다.
            |package org.domi.furnace.api;
            |
            |/**
            | * 이 서버가 따르는 마인크래프트 판. 모든 칸이 컴파일 시간 상수라 쓰는 자리에 값이 그대로 들어간다.
            | */
            |public final class GameVersion {
            |    private GameVersion() {}
            |
            |    /** 게임 버전 문자열(예: "1.21.1"). */
            |    public static final String MINECRAFT = "${p("mc.version")}";
            |    /** 네트워크 프로토콜 번호. */
            |    public static final int PROTOCOL = ${p("mc.protocol").toInt()};
            |    /** 월드 데이터 버전(DataVersion). */
            |    public static final int DATA_VERSION = ${p("mc.dataVersion").toInt()};
            |    /** 리소스 파일 이름의 판 꼬리표({@code blocks/definitions_<tag>.txt}, {@code worldgen/<tag>/} …). */
            |    public static final String RESOURCE_TAG = "${p("mc.resourceTag")}";
            |    /** 크래프트 패키지 접미사({@code org.bukkit.craftbukkit.<suffix>}). */
            |    public static final String CRAFT_PACKAGE_SUFFIX = "${p("mc.craftPackageSuffix")}";
            |    /** 데이터 팩 pack_format. */
            |    public static final int PACK_FORMAT_DATA = ${p("mc.packFormat.data").toInt()};
            |}
            |""".trimMargin()
        val f = outDir.get().file("org/domi/furnace/api/GameVersion.java").asFile
        f.parentFile.mkdirs()
        f.writeText(src)
    }
}
sourceSets.main { java.srcDir(generateGameVersion.map { gameVersionDir.get() }) }

// 이식 다리(docs/roadmap/port-bridge-2026-11-27.md): 생성기 소스셋 portgen — api jar 에 싣지 않는다(명세를 읽는 코드라 net.minecraft 글자를 가진다).
// 서버·호환 모듈은 portgenElements 로 이 생성기를 받아 같은 명세(furnace-compat-paper/src/shimspec/ports-<판>/*.ports)에서 자기 몫을 만든다.
val portgen: SourceSet = sourceSets.create("portgen")
val portSpecDir = rootProject.file("furnace-compat-paper/src/shimspec/ports-" + mcProps.getProperty("mc.version"))
val portsApiDir = layout.buildDirectory.dir("generated/ports/java")
val generatePorts by tasks.registering(JavaExec::class) {
    group = "build"
    description = "이식 명세에서 api.internal.port.<Area>Port 인터페이스를 만든다"
    classpath = portgen.runtimeClasspath
    mainClass.set("org.domi.furnace.portgen.PortGen")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    inputs.files(portgen.runtimeClasspath).withNormalizer(ClasspathNormalizer::class)
    inputs.dir(portSpecDir).withPathSensitivity(PathSensitivity.RELATIVE).optional()
    outputs.dir(portsApiDir)
    argumentProviders.add(CommandLineArgumentProvider { listOf("api", portSpecDir.absolutePath, portsApiDir.get().asFile.absolutePath) })
}
sourceSets.main { java.srcDir(generatePorts.map { portsApiDir.get() }) }
val portgenJar by tasks.registering(Jar::class) {
    from(portgen.output)
    archiveFileName = "furnace-portgen.jar"
    destinationDirectory = layout.buildDirectory.dir("portgen")
}
val portgenElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts { add(portgenElements.name, portgenJar) }

dependencies {
    testImplementation(portgen.output)
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
}

// 헤드리스 시험 서버(furnace-test)가 기대는 좌표 — publishToMavenLocal 로 함께 배포한다(docs/api/testing.md).
// 버전은 -PfurnaceVersion=... 으로 바꾼다. jar 파일 이름(project.version)은 건드리지 않는다.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.domi.furnace"
            artifactId = "furnace-api"
            version = (findProperty("furnaceVersion") as String?) ?: (mcProps.getProperty("mc.version") + "-SNAPSHOT")
            from(components["java"])
        }
    }
}
