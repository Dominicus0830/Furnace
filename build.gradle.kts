// SPDX-License-Identifier: GPL-3.0-only
// 멀티모듈 루트. 빌드 로직은 각 서브모듈이 보유한다.

// Furnace 빌드 버전 문자열(마크 버전 제외): `v<MC>-<X.Y.Z>` 태그에서 읽는다(docs/ops/version-tags.md).
//   태그 위       → 0.1.0
//   태그 뒤 n 커밋 → 0.1.0+14.gabc1234
//   v 태그 없음/git 없음 → dev+abc1234 / dev
// 서버 fatJar 의 Implementation-Version 이 되어 콘솔 첫 줄·/version·getVersion() 에 나온다. 서브모듈은 `rootProject.extra["furnaceBuildVersion"]`.
val furnaceBuildVersion: String = run {
    fun git(vararg a: String): String? = try {
        val p = ProcessBuilder(listOf("git") + a).directory(rootDir).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.errorStream.readBytes()
        if (p.waitFor() == 0 && out.isNotEmpty()) out else null
    } catch (e: Exception) { null }
    val d = git("describe", "--tags", "--match", "v[0-9]*-[0-9]*.[0-9]*.[0-9]*", "--long", "--dirty")
    val m = d?.let { Regex("""^v[0-9][0-9.]*-([0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[0-9]+)?)-([0-9]+)-g([0-9a-f]+)(-dirty)?$""").matchEntire(it) }
    if (m != null) {
        val (ver, ahead, sha, dirty) = m.destructured
        if (ahead == "0" && dirty.isEmpty()) ver else "$ver+$ahead.g$sha$dirty"
    } else {
        val sha = git("rev-parse", "--short=7", "HEAD")
        if (sha != null) "dev+$sha" else "dev"
    }
}
extra["furnaceBuildVersion"] = furnaceBuildVersion

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
