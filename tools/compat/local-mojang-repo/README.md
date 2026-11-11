# Mojang 라이브러리 로컬 저장소 (호환 모듈 빌드용)

`furnace-compat-paper` 는 authlib(그리고 다음 단계에서 DataFixerUpper)을 compileOnly 로 쓴다. 이 jar 들은 Maven Central 에 없고 `libraries.minecraft.net` 에만 있다. CI 와 보통의 개발 환경은 그 저장소에서 그대로 받는다.

그 호스트에 닿지 않는 곳(샌드박스 등)에서는 바닐라 1.21.1 서버 jar(번들러) 안에 같은 jar 가 들어 있으므로, 그것을 꺼내 로컬 저장소로 가리킨다. sha256 은 번들러의 `META-INF/libraries.list` 와 맞춰 본다. 호스트 차단을 우회하는 것이 아니라 이미 가진 바닐라 jar 를 쓰는 것이다.

```sh
# 1) 번들러 jar 준비(저장소 밖). 예: git show origin/main:tools/vanilla-data/minecraft_server.1.21.1.jar > /tmp/x/bundler.jar
tools/compat/local-mojang-repo/make-repo.sh /tmp/x/bundler.jar /tmp/x/mojang-repo
# 2) init 스크립트로 저장소를 바꿔 빌드
FURNACE_MOJANG_REPO=/tmp/x/mojang-repo ./gradlew -I tools/compat/local-mojang-repo/mojang-repo.init.gradle -PskipOracle=true :furnace-compat-paper:test
```

- 꺼낸 jar 는 Mojang 것이다. 저장소 안에 두거나 커밋하지 않는다(`make-repo.sh` 는 저장소 안 출력 폴더를 거부한다).
- Central 미러가 필요한 환경이면 그 init 스크립트와 함께 `-I` 를 두 번 준다.
- `:furnace-api:test`·`:furnace-server:test` 는 호환 모듈을 컴파일하지 않으므로 이것이 필요 없다. `:furnace-server:fatJar` 와 `:furnace-compat-paper:*` 는 필요하다.
