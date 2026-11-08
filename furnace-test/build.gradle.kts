// SPDX-License-Identifier: GPL-3.0-only
// 헤드리스 시험 서버(B7): 플러그인 개발자가 JUnit 시험에서 실제 Furnace 서버를 메모리 월드·결정적 틱으로 띄운다.
//   testImplementation("org.domi.furnace:furnace-test:<버전>")
// 로컬 배포: ./gradlew -PskipOracle=true publishToMavenLocal  (docs/api/testing.md)
plugins {
    id("java-library")
    id("maven-publish")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

dependencies {
    api(project(":furnace-api"))
    api(project(":furnace-server"))
    // 서버는 netty 를 implementation 으로 두므로 시험 클라이언트(TestPlayer.sendPacket 의 ByteBuf)가 직접 받는다 — 서버와 같은 판
    api("io.netty:netty-all:4.1.115.Final")
    // JUnit 5 확장(FurnaceTestExtension)만 쓴다 — 시험 쪽이 junit-jupiter 를 가져온다
    compileOnly(platform("org.junit:junit-bom:6.0.0"))
    compileOnly("org.junit.jupiter:junit-jupiter-api")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "2g"
    testLogging { showStandardStreams = false }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.domi.furnace"
            artifactId = "furnace-test"
            version = (findProperty("furnaceVersion") as String?) ?: "1.21.1-SNAPSHOT"
            from(components["java"])
        }
    }
}
