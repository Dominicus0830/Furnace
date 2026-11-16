# V-01 버전 상수 생성 — 확인할 것 (2026-11-16)

결정할 것은 없다. 클라우드에서 못 돌려 본 것 하나만 있다.

| # | 해야 할 일 | 합격 기준 | 안 하면 |
|---|---|---|---|
| 1 | `worldgen-oracle` 은 Paper 저장소(repo.papermc.io)에 닿아야 설정되는 모듈이라 클라우드에서 컴파일·실행을 못 했다. 노트북 등에서 한 번: `./gradlew :worldgen-oracle:compileJava` 와 `./gradlew :worldgen-oracle:run -Doracle.<기존 옵션>` 한 번(출력 폴더 이름이 `blockstates_1.21.1.txt`·`1.21.1.txt`·`worldgen/1.21.1/` 로 나오는지) | 컴파일 성공, 출력 파일 이름이 전과 같음 | oracle 은 정답표를 새로 뽑을 때만 쓰므로 서버·CI 에는 영향 없음. 다음에 정답표를 뽑을 때 `-Doracle.resourceTag` 관련 오류가 나면 그때 고치면 됨 |

변경이 단순하다: `OracleMain` 에 `RESOURCE_TAG`(= `-Doracle.resourceTag`, `:worldgen-oracle:run` 이 `gradle/minecraft.properties` 에서 넘김)를 도입해 문자열 3곳을 바꿨고, 개발 번들도 같은 파일에서 읽는다.
