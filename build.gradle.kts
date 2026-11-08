// SPDX-License-Identifier: GPL-3.0-only
// 멀티모듈 루트. 빌드 로직은 각 서브모듈이 보유한다.

// `./gradlew build` 는 테스트를 돌리지 않는다(테스트는 약 13분). 테스트는 따로 부른다:
//   ./gradlew -PskipOracle=true :furnace-server:test   (또는 check)
subprojects {
    plugins.withId("java") {
        afterEvaluate {
            tasks.findByName("build")?.setDependsOn(listOf("assemble"))
        }
    }
}
