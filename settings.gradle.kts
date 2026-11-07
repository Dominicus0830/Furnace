pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}

rootProject.name = "Furnace"

// worldgen-oracle(paperweight-userdev)은 configuration 시점에 repo.papermc.io 에서 dev-bundle 을 받는다.
// Paper 저장소에 못 닿는 환경(샌드박스/오프라인/차단된 CI)에서는 -PskipOracle=true (또는 환경변수
// ORG_GRADLE_PROJECT_skipOracle=true)로 모듈 자체를 제외한다. 기본(미지정)은 종전과 동일하게 포함한다.
// 값 없는 -PskipOracle 은 Gradle 이 "" 로 전달하므로 isPresent 만 쓰면 안 되고, 명시적 "false" 일 때만 포함으로 본다.
val skipOracle = providers.gradleProperty("skipOracle")
    .map { !it.equals("false", ignoreCase = true) }
    .getOrElse(false)

include("furnace-api", "furnace-server", "furnace-plugin-test", "furnace-test")
if (skipOracle) {
    if (gradle.startParameter.taskNames.any { it.contains("worldgen-oracle") }) {
        throw GradleException("worldgen-oracle 이 skipOracle 로 제외됨 — 플래그를 빼고 다시 실행하세요")
    }
    logger.lifecycle("[settings] skipOracle: worldgen-oracle 제외 (:worldgen-oracle:* 태스크 사용 불가)")
} else {
    include("worldgen-oracle")
}
