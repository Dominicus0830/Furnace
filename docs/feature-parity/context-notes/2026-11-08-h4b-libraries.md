## 2026-11-08 호환 플러그인용 서버 제공 라이브러리 — 함정 (H4 묶음 B)

- **Mojang 라이브러리의 sha256 출처**: 바닐라 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/minecraft_server.1.21.1.jar`, 번들러)의 `META-INF/libraries.list` 에 `sha256<TAB>좌표<TAB>경로` 가 있고, jar 자체도 `META-INF/libraries/...` 에 들어 있다. `libraries.minecraft.net` 에 못 닿아도 Brigadier·authlib·DFU 의 고정값과 실물을 여기서 얻었다. Guava 등 Central 에도 있는 것은 구글 미러에서 받은 바이트가 이 값과 같았다.
- **Brigadier·authlib·DFU 는 Central 에 없다**(`com/mojang/*/maven-metadata.xml` 404).
- **공유 로더가 부모**라서 호환 플러그인은 자기 jar 에 표지가 없어도 다른 플러그인 때문에 붙은 묶음을 본다(Paper 와 같음). 시험에서 "안 보인다"를 단언하려면 그 실행에서 아무도 그 묶음을 요구하지 않아야 한다.
- **시험 서버 기본은 꺼짐**(`FurnaceTestServer.Builder.libraries`). 켜 두면 compat 모듈 시험의 실제 플러그인(helper·CommandAPI)이 CI 에서 네트워크로 받으려 든다.
- **측정 요령**: 캐시를 Maven 모양으로 채우고 `./gradlew -PskipOracle=true :furnace-compat-paper:liveProbe -Pjar=helper -PlibraryCache=<폴더>`(offline). `-Pjar=commandapi` 는 core jar 를 먼저 집으니 `commandapi-bukkit-plugin` 으로. 출력 한글이 깨지면 `-Dstdout.encoding=UTF-8`.
