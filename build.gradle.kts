// SPDX-License-Identifier: GPL-3.0-only
// 멀티모듈 루트. 빌드 로직은 각 서브모듈이 보유한다.
import java.util.Properties

// V-01: 마인크래프트 판은 gradle/minecraft.properties 한 곳에서만 정한다. 서브모듈은 rootProject.extra["mc"] 로 읽는다.
extra["mc"] = Properties().apply { file("gradle/minecraft.properties").reader(Charsets.UTF_8).use { load(it) } }

// `./gradlew build` 는 테스트를 돌리지 않는다(테스트는 약 13분). 테스트는 따로 부른다:
//   ./gradlew -PskipOracle=true :furnace-server:test   (또는 check)
subprojects {
    // T-160: 시험이 실패하면 단언 메시지·기대값/실제값·스택을 콘솔(=CI 로그)에 그대로 남긴다.
    // 기본값(SHORT)은 "AssertionFailedError at X.java:227" 한 줄뿐이라 CI 에서만 가끔 실패하는 시험의 원인을 볼 수 없었다.
    tasks.withType<Test>().configureEach {
        testLogging {
            events(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
    plugins.withId("java") {
        afterEvaluate {
            tasks.findByName("build")?.setDependsOn(listOf("assemble"))
        }
    }
}
