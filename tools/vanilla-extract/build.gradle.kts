// SPDX-License-Identifier: GPL-3.0-only
// V-05: 바닐라 데이터 추출 도구 모듈. 바닐라 서버 jar 를 Mojang 공식 대응표(server mappings)로 이름을 풀어 두고,
// Mojang 이름으로 쓴 추출 도구(src/main/resources/exporters)를 그 위에서 돌려 `resources/**/<버전>` 데이터와 시험 정답표를 만든다.
// 서버 클래스·플러그인 API 와 이어진 곳이 없다 — 서버 실행 경로(핫패스)에 영향 0.
//
//   ./gradlew :vanilla-extract:run -Pmc=1.21.1                    전 도구 실행 → 이 모듈 build/out/<버전>/ 에 저장소 경로 그대로 쓴다
//   ./gradlew :vanilla-extract:run -Pmc=1.21.1 -Pmode=verify      같은 결과를 저장소의 커밋된 리소스와 견준다(.gz 는 푼 내용 비교)
//   ./gradlew :vanilla-extract:run -Pmc=1.21.2 -Pmode=install     저장소 resources 에 바로 덮어쓴다(새 판으로 올릴 때)
//   ./gradlew :vanilla-extract:run -Pmc=1.21.1 -Pmode=proof -PlegacyRef=<이전 도구가 있던 커밋>   전·후·커밋 비교 + 포팅 점검(V-05 증명)
//   -Pmode=verify -Ptools=tags,builtin-registries,block-tags,item-components,block-definitions,damage-types   옛 Java 도구가 없던 파이썬 6개만 커밋 파일과 견줌
//   -PtoolArgs="shape-vectors=<블록들>;shape-vectors=<이웃 블록들>;shape-vectors=<시도 수>;shape-vectors=<시드>"   shape-vectors 처럼 재료를 받는 도구의 인자
//   -Pmode=sig-table   서명표(T-227): 클래스·멤버 선언만 모은 표를 캐시에 만든다(-PsigPaperApi=true 는 paper-api, -PsigJar -PsigName 은 임의 jar)
//   -Ptools=block-attrs,block-light   일부 도구만   -Plist=true   도구 목록   -Pcache=<폴더>   내려받기 캐시   -PjavaHome=<JAVA_HOME>   실행 JDK
// 바닐라 jar·대응표·내려받은 결과는 캐시 폴더에만 둔다(EULA — 커밋하지 않는다).
plugins {
    id("application")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("org.domi.furnace.tools.extract.Main")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
}

tasks.named<JavaExec>("run") {
    // 저장소 루트에서 실행한 것처럼 동작하도록 작업 폴더를 루트로 둔다.
    workingDir = rootProject.projectDir
    val a = mutableListOf<String>()
    (findProperty("mc") as String?)?.let { a += listOf("--mc", it) }
    (findProperty("mode") as String?)?.let { a += listOf("--mode", it) }
    (findProperty("tools") as String?)?.let { a += listOf("--tools", it) }
    (findProperty("cache") as String?)?.let { a += listOf("--cache", it) }
    (findProperty("javaHome") as String?)?.let { a += listOf("--java-home", it) }
    (findProperty("out") as String?)?.let { a += listOf("--out", it) }
    (findProperty("serverJar") as String?)?.let { a += listOf("--server-jar", it) }
    (findProperty("mappings") as String?)?.let { a += listOf("--mappings", it) }
    (findProperty("legacyRef") as String?)?.let { a += listOf("--legacy-ref", it) }
    // 도구별 추가 인자: -PtoolArgs="shape-vectors=a,b;shape-vectors=c,d;shape-vectors=50;shape-vectors=1" (세미콜론으로 여러 개)
    (findProperty("toolArgs") as String?)?.split(";")?.filter { it.isNotBlank() }?.forEach { a += listOf("--tool-arg", it.trim()) }
    (findProperty("protocol") as String?)?.let { a += listOf("--protocol", it) }
    // 서명표(-Pmode=sig-table): -PsigJar=<jar> -PsigName=<이름> [-PsigPrefix=a/,b/]  또는  -PsigPaperApi=true
    (findProperty("sigJar") as String?)?.let { a += listOf("--sig-jar", it) }
    (findProperty("sigName") as String?)?.let { a += listOf("--sig-name", it) }
    (findProperty("sigPrefix") as String?)?.let { a += listOf("--sig-prefix", it) }
    (findProperty("sigPaperApi") as String?)?.let { if (it != "false") a += "--sig-paper-api" }
    (findProperty("list") as String?)?.let { if (it != "false") a += "--list" }
    args = a
}
