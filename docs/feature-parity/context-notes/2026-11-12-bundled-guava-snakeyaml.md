# Guava·SnakeYAML 서버 jar 포함 (2026-11-12, 사용자 결정 5 정정)

도미니코 결정 5 정정: Guava·SnakeYAML 은 서버 jar 에 그냥 넣는다. Adventure 는 (처음엔 셋 다였다가 정정) 지금처럼 필요할 때 받는다. authlib 도 받기 유지.

- 방식: fat jar `META-INF/furnace/libraries/` 에 jar **그대로**(guava-32.1.2-jre 3.0 MB, failureaccess-1.0.1, snakeyaml-2.2 0.3 MB). 풀지 않고 재배치도 없다.
  - 풀어 넣으면 서버 로더에 들어가 공유 로더(SharedLibraryLoader)와 두 벌이 되거나, 호환 계층(공유 로더 첫 항목, N-lib-1)이 서버 로더 쪽 Guava 를 보게 되어 로더 구조가 갈린다. jar 그대로 두고 필요할 때 캐시로 꺼내면 기존 needed 판단·공유 로더·핫 리로드 경로를 그대로 쓴다.
  - 재배치(shading)하면 플러그인이 `com.google.common` 을 못 찾는다 — Paper 처럼 원래 이름.
- `ServerLibraries.Artifact.bundled` → `LibraryResolver.ensurePinned` 가 캐시 확인 뒤, 받기 전에 서버 jar 자원을 읽어 고정 sha256 과 같으면 캐시 자리(`plugins/.libraries/<maven 경로>`)에 써서 쓴다. offline 이어도 된다. 바이트가 다르면 쓰지 않고 받기로 넘어간다. 자원이 없으면(gradle 개발 실행·시험) 예전처럼 받는다.
- 지연 로딩: 그대로 needed. `plugins/bukkit-plugins/` 가 비면 꺼내지도 올리지도 않는다(fat jar 기동으로 확인 — `plugins/.libraries` 가 생기지 않음). H5 "폴더가 비면 기능을 켜지 않는다" 와 충돌 없음.
- 빌드 확인: `fatJar` doLast 가 실은 jar 의 좌표·sha256 이 ServerLibraries.java 의 `new Artifact("…", "<sha>", C, B)` 와 같은지, B 로 적힌 것이 모두 실렸는지, 최상위에 `com/google/common/`·`org/yaml/snakeyaml/` 클래스가 없는지 본다(고의로 sha 를 틀리게·B 를 빼서 실패하는 것 확인).
- fat jar 기동 탐침(offline=true, 네트워크 없음): Bukkit 플러그인 하나가 `ImmutableList`·`Futures`·`new Yaml().load` 를 써서 onEnable 통과, 둘 다 `furnace-server-libraries` 로더에서 왔다.
- 라이선스: 셋 다 Apache 2.0. 저장소 최상위 `NOTICE`, `licenses/LICENSE-APACHE-2.0.txt`(guava jar 의 META-INF/LICENSE 와 같은 본문), fat jar `META-INF/furnace/libraries/NOTICE`·`LICENSE-APACHE-2.0.txt`.
- 서버 jar 크기: 약 22 MB → 25.6 MB.
- 검토 반영(PR #235): start 스크립트(installDist `-cp lib/*`)는 fat jar 가 아니라 자원이 없었다 → `bundledLibrariesJar`(같은 자원만 담은 jar)를 배포 lib/ 에 넣는다. fatJar 정규식은 주석 줄 제외·공백·줄바꿈·`Repo.X`·`true` 표기를 받고, 못 읽은 bundled 항목이 있거나 0개면 실패. NOTICE 의 "각 jar META-INF 를 따른다" 문구는 사실과 달라 고침(fat jar 에는 logback LICENSE 하나뿐).
