// SPDX-License-Identifier: GPL-3.0-only
plugins {
    id("java")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// 내부 접근(Internals) 예제 — 서버 jar 를 compileOnly 로 받는 플러그인의 꼴(docs/api/internals.md).
// main(공개 API 만으로 컴파일되는지 확인하는 샘플)과 섞이지 않게 따로 컴파일하고, classes 에 묶어 CI 가 함께 확인한다.
val internalsExample: SourceSet by sourceSets.creating

dependencies {
    implementation(project(":furnace-api"))
    "internalsExampleImplementation"(project(":furnace-api"))
    "internalsExampleCompileOnly"(project(":furnace-server"))
}

tasks.named("classes") { dependsOn(internalsExample.classesTaskName) }
