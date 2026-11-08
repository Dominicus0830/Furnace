## 2026-11-08 Paper API 클래스 격리 — 호환 어댑터 지연 로딩 (paper-api-isolation)

- **발견한 결합 하나:** `PluginBootstrapper.loadFormats()` 가 시작 때마다(호환 켜짐 기본) `ServiceLoader` 로 `CompatFormat` 을 만들었다. 인스턴스를 만들면 클래스 링크 과정에서 `org.bukkit.plugin.*`·`Bukkit`·`JavaPlugin` 등이 줄줄이 올라온다(플러그인이 하나도 없어도). 서버 코드 어디에도 `org.bukkit` 직접 참조는 없었다(`ServerLibraries` 의 문자열 제외).
- **고침:** `PluginFormats`(서버 `plugin/`) — `discover` 는 `META-INF/services/…PluginFormat` 자원이 있는지만 본다(클래스 안 읽음). `get()` 은 `readCandidate` 가 Furnace 설명 파일 없는 jar 를 처음 만났을 때 한 번 `ServiceLoader` 로 만든다. `plugins/bukkit-plugins/` 훑기는 `available()` 로 결정(전과 같음: 호환이 있을 때만).
- **함정:** 설명 파일 없는 낯선 jar 가 `plugins/` 에 있으면(원래도 오류 jar) 그 판정을 위해 어댑터가 올라온다. 허용했다 — 진짜 Furnace/Bukkit 플러그인만 있는 서버에서는 안 생긴다.
- **시험 요령:** 같은 JVM 에서 "안 올라왔다" 를 보려면 다른 시험이 올린 클래스에 흔들린다 → 별도 JVM + `-Xlog:class+load=info:file=…` 를 파싱한다(`CompatIsolationTest`, 자식 main 은 패키지를 `org.domi.furnace.isolationcheck` 로 둬 접두 검사에 섞이지 않게). 필터로 호환 모듈 클래스패스 항목(`furnace-compat-paper/build/{classes/java,resources}/main`, ASM)을 빼면 "모듈이 없는" 상황도 만든다. 고치기 전 코드로 되돌려 보면 두 시험이 실패한다(대조 확인).
- **네이티브 플러그인에 숨기기:** `PluginClassLoader.loadClass` 가 `CompatPackages` 접두 이름은 부모를 건너뛰고 자기 jar→다른 플러그인 jar 에서만 찾는다(format==null 인 로더만). 함정: 호환 코어를 네이티브 시험 플러그인(`CompatTestPlugin`)으로 부리는 호환 모듈 시험 4개 클래스는 `exposeCompatToNative(true)` 가 필요하다. 시험용 호환 클래스는 서버 모듈 시험에 `org.bukkit.HiddenProbe` 를 둬서 흉내 낸다.
- **독립 검토 반영(F1~F5):** 깨진 어댑터(자원은 있고 생성 실패)는 bukkit-plugins jar 를 실패로 세지 않고 경고 후 건너뛴다(`SKIPPED`).
