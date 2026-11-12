# 드롭인 시작 후속(PR #234 R4·R5, CauldronInteraction 로그) — 2026-11-12

## R4: 호환 jar 이중 읽기
- 예전: 서버 `SpigotMappingFiles.needs(jar)` 가 모든 클래스 바이트를 읽고(NMS 를 안 쓰는 jar 는 끝까지), 이어서 어댑터 `NmsCompatReport.scan` 이 다시 전부 읽었다(약 20 ms/MB × 2).
- 지금: 판정을 어댑터가 jar 를 읽는 한 번에 합쳤다. `NmsCompatReport.read(jar)` 가 클래스를 한 번 읽어 후보·`net/minecraft/`·`net.minecraft.`·craftbukkit 표지를 한 번의 바이트 훑기(`scanNames`)로 모으고, `Read.needsSpigotMappings()`(Spigot 이름 jar + NMS 이름 참조 — 옛 `needs` 와 같은 기준)가 참이면 그때만 서버에 대응표 파일을 묻는다(`PluginFormat.transformer(..., Supplier<String> spigotMappingFiles)`, 기본 구현은 묻지 않는 옛 메서드). 서버 쪽 `needs` 는 지웠다.
- 효과: NMS 를 안 쓰는 1.4 MB jar 기준 옛 needs+scan 약 50 ms → 새 read 약 23 ms(JIT 데운 뒤, 같은 컨테이너). 필요 없는 플러그인에는 대응표 받기를 시도하지도 않는다(`spigotMappingsTried()` 시험 유지).
- 주의: 서버 클래스 파일에 `net/minecraft/` 문자열을 두지 않는 격리 규칙(CompatIsolationStaticTest)은 `needs` 가 사라져 오히려 단순해졌다.

## R5: 방화벽 서버의 매번 받기
- `SpigotMappingFiles` 가 연결·제한 시간·이름 풀이 실패(`IOException`, HTTP 응답이 온 것은 제외)만 캐시 폴더의 `download-failed.properties`(`failures`, `last-failure-epoch-ms`, `reason`)에 남긴다. 다음 시작은 네트워크가 필요한 지점(캐시에 맞는 파일이 없을 때) 직전에 이 기록을 보고, 연속 실패 1회 1시간·2회 6시간·3회 이상 24시간 안이면 기다림 없이 `Backoff` 를 던져 한 줄만 로그한다. 성공하거나 캐시에 맞는 파일이 있으면 기록이 지워진다. 읽기·쓰기 실패는 시작을 막지 않는다(PR #234 R1 교훈).
- 404·해시 불일치·잠금 불일치·HTTP 403 같은 응답은 기억하지 않는다(기다림이 없고, 운영자가 고치는 일이다). `plugins.libraries.offline=true` 도 네트워크를 안 쓰므로 기록하지 않는다.
- 같은 문제가 다른 서버 제공 라이브러리 받기(Adventure·authlib)에도 있다 — `LibraryResolver.unreachable` 은 한 시작 안에서만 기억한다. 필요하면 같은 방식으로 후속.

## CauldronInteraction 정적 초기화 실패 178줄
- 원인: 블록 동작 표(`BlockBehaviors.table()`)는 첫 청크 로드 때 만들어지고, 가마솥(`CauldronInteraction.<clinit>`)·퇴비통이 `ItemContext.id(...)` 로 아이템 번호를 읽는다. 아이템 번호표는 `DataPackManager.start`(→ `NetworkComponents`)가 깔기 때문에 공식 서버 jar 를 못 받아 데이터 팩 없이 시작하면(컨테이너: Mojang 403) 번호표가 없어 정적 초기화가 실패하고, 클래스는 영구히 실패 상태라 이후 모든 청크 로드가 `NoClassDefFoundError` 로 청크 수만큼 스택을 찍고 아무 청크도 안 열렸다. 스폰 준비에서 25개, 접속·틱에서 계속(178줄 관찰).
- 진짜 서버: 번호표는 `DataPackManager.start` 가 첫 청크 로드(`startTicking` → 스폰 준비)보다 먼저 깐다 — 이 순서를 코드와 로그로 확인했다. 공식 jar 가 있는 실서버에서는 나지 않는 것으로 본다(컨테이너는 jar 403 이라 실제 데이터 팩으로는 못 돌려 봄).
- 고침: 데이터 팩 준비가 실패해 번호표가 깔리지 않았으면 `FurnaceMain` 이 내장 레지스트리(`RegistrySet`)로 `ItemContext.install` 하고, 예전 한 줄 오류에 "레시피·전리품·발전 과제·태그는 비고 아이템 번호표만 내장 레지스트리를 쓴다(지형·블록은 돈다)" 를 적었다. 시험은 이미 같은 방식(`ItemContextExtension`)으로 돈다. 확인: installDist 를 데이터 팩 없이 돌리면 `[CHUNK] 로드 실패`·`Cauldron` 줄이 0 이다(고치기 전 204줄).
