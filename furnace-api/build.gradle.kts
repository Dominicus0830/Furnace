plugins {
    id("java-library")
    id("maven-publish")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
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
            version = (findProperty("furnaceVersion") as String?) ?: "1.21.1-SNAPSHOT"
            from(components["java"])
        }
    }
}
