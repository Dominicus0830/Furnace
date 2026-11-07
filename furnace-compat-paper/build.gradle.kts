// Bukkit·Paper 플러그인 호환 어댑터 (H1). org.bukkit.* 의 호환 API 일부(공개 이름·서명을 따라 새로 쓴 것)와,
// 서버가 ServiceLoader(PluginFormat)로 불러 plugin.yml jar 를 Furnace 플러그인으로 돌리는 껍데기. docs/api/compat-paper.md
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
    // 서버가 slf4j 를 싣고 있다 — 설명 파일 경고를 서버 로그로 보낸다
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    // 시험은 실제 서버(헤드리스)에 플러그인 jar 를 올려 돌린다
    testImplementation(project(":furnace-server"))
    testImplementation(project(":furnace-test"))
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
    maxHeapSize = "2g"
}
