// Bukkit·Paper 플러그인 호환 계층(H 묶음). 공개 API(furnace-api)만 쓴다 — 서버 구현에 의존하지 않는다.
// H2 는 Bukkit API jar 없이 만든 "호환 코어"(명령·스케줄러의 의미)이고, org.bukkit.* 클래스에 거는 얇은 껍데기는 H1·H3 에서 붙는다
// (docs/feature-parity/compat-h2-2026-11-07.md).
plugins {
    id("java-library")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    api(project(":furnace-api"))

    testImplementation(project(":furnace-test"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "2g"
}
