# Furnace 세션 인수인계 (작성일 2026-11-01)

> 파일 이름의 2026-10-31 은 작업 G 를 시작한 세션 날짜이고, 이 문서의 작성·최종 갱신일은 2026-11-01 이다.

**이 문서가 `docs/handoff/2026-10-18-session-handoff.md` 의 "상태" 부분을 대체한다.** 옛 문서는 지우지 않았다. 대체 범위는 아래와 같다.

| 옛 문서(2026-10-18) | 지금 |
|---|---|
| 1.3 지금 어디까지 왔나, 6장 진행 상황, 7장 미구현 목록 | **낡음.** 이 문서 2~3장과 `docs/feature-parity/unimplemented-survey-2026-10-31.md` 를 본다 |
| **8장 "다음 할 일 — 4-2 메뉴 프레임워크"** | **낡음. 4-2 는 끝났다**(아래 2장). 옛 문서는 한 문서 안에서 "다음은 4-2"와 4-2 이후 상태가 섞여 모순이었다. 8장은 이 문서 4장으로 대체 |
| 2장(확정된 결정·설계 원칙), 3장(규칙), 4장(환경·도구), 5장(아키텍처) | **그대로 유효.** 단 CLAUDE.md/AGENTS.md 가 더 최신이면 그쪽이 우선 |
| `CLAUDE.md` 의 "현재 다음 작업은 4-2" 문구 | 낡음(CLAUDE.md 는 사용자 소유라 고치지 않았다 — `docs/user-todo.md` 결정 기록 참고) |

- 작성 시점: 2026-11-01. 기준 브랜치 `claude/quirky-wright-lra834`, 이 문서를 쓴 작업 G 의 시작점은 `8a447c1a`.
- 사용자에게는 항상 한국어. 커밋 메시지·코드 주석도 한국어, 간결하게. 커밋·PR 에 모델 ID 를 쓰지 않는다.
- **먼저 읽을 순서:** `CLAUDE.md` → `AGENTS.md` → `.claude/rules/*.md`(경로별) → 이 문서 → 옛 인수인계서 2~3장 → `docs/user-todo.md`(사용자 결정·시험 대기) → `docs/feature-parity/unimplemented-survey-2026-10-31.md`.

## 1. 한눈에 보기

Furnace 는 처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버(Java 21 / Gradle)다. 바닐라/Paper 와 관측 결과가 같고 더 빠른 것이 목표다(옛 문서 1.1·2장). 스레드 모델은 전역 스레드 1개 + 동적 region(Folia 방식) 워커 풀이며 규칙은 `.claude/rules/scheduler.md`.

작업 습관은 옛 문서 3.4 와 같다. 이번 세션에서 확인된 환경 규칙:
- 테스트는 `ORG_GRADLE_PROJECT_skipOracle=true flock /tmp/furnace-gradle.lock ./gradlew --offline :furnace-server:test` 를 백그라운드로 돌리고 로그를 기다린다. 락 대기가 길다(여러 세션이 gradle 을 공유).
- 격리 worktree 에서 일하고 푸시하지 않는다(사용자가 요청할 때만). 메인 디렉터리에서 파일 수정·checkout 을 하지 않는다.
- 불안정 시험은 `TabListTest` 하나로 알려져 있다. 그것만 실패하면 단독으로 다시 돌려 확인하고 보고한다.

## 2. 2026-10-18 이후 완료된 것 (요약 — 상세는 각 문서)

**4-2 컨테이너 메뉴 프레임워크는 완료됐다.** `furnace-server/.../menu/` 에 메뉴 25종(`AbstractContainerMenu`, 제작·화로·모루·마법 부여·양조·베틀·지도 제작대·석재 절단기·숫돌·대장장이·신호기·연단·상인·말 등)이 있다(`unimplemented-survey-2026-10-31.md` 3장 "메뉴 25종은 모두 있다"). 이후 단계도 대부분 들어왔다. 옛 인수인계서(코드 기준 `3738fd79`) 이후 커밋이 많다(개수는 `git log --no-merges 3738fd79..HEAD` 로 직접 센다 — 이 문서에는 적지 않는다). 굵직한 것:

- **엔티티·몹**: 몹 목표/AI 다수, 마을 주민 AI(T3 직업·WORK), 철 골렘 마을 AI, 늑대 변종·갑옷, 알레이, 가죽끈, 몹 양동이, 엔더의 눈, 생물끼리 밀기·`maxEntityCramming`, 파괴수·워든·스컬크, 습격자 순찰 등. 몹 능력은 `mob-ability-audit-2026-10-31.md`.
- **플레이어**: 수면·밤 건너뛰기·리스폰 정박기, 겉날개, 토템, 철퇴·밀도·관통·돌풍·가시, 이동 서버 검증, 파괴 속도 효과, 수영·기어가기 자세, 근접 속성.
- **월드**: 오버월드·네더·엔드 세 차원 호스팅과 차원 이동(`multi-dimension-2026-10-26.md`), 포털, 게임 이벤트·진동, 명령 블록 3종·수레, 제작기·스포너 블록 엔티티, 깃발·지도 표식.
- **명령**: `/execute`·함수 시스템(매크로 포함)·`/tick sprint`·`/loot`·`/item`·`/give`·`/data`·`/place`·`/clone`·`/attribute` 등. 루트 83개 중 81개 등록(`/perf` 미구현, `/publish` 는 전용 서버에 없음) — 이 수치는 `unimplemented-survey-2026-10-31.md` 3장 인용이며 이 세션에서 다시 세지 않았다.
- **스케줄러 성능**: S1~S4 공유 스케줄러(기본 꺼짐, `-Dfurnace.sharedRegionPool=true`), 측정 문서는 `docs/feature-parity/shared-scheduler-*.md`·`perf-c*.md`. 기본값을 켤지는 사용자 결정(`user-todo.md`).
- **배포**: `start.sh`/`start.bat`(JVM 옵션), 단일 실행 jar `:furnace-server:fatJar`.
- **설정 기본값**: `sync-chunk-writes` 기본 false(사용자 결정 2026-10-28).

### 이 세션(작업 G, 서버 운영·관측)에서 한 것
1. **server.properties 운영 키 연결** (`docs/feature-parity/context-notes.md` "2026-10-31 서버 운영 키 연결"): RCON 서버, UDP Query 서버, `max-tick-time` 워치독(스레드 덤프 크래시 리포트), `spawn-npcs`(+`spawn-animals`) 틱 때 지우기, `max-world-size`(월드 보더 절대 한계), `region-file-compression`(deflate·gzip·none; lz4 미지원), `text-filtering-config`(미구현 — 경고만), `broadcast-rcon-to-ops`.
2. **관측 명령** `/furnace threads | regions [개수] | profile`(권한 3): 전역·차원별 region 수·대기열·워커 상태·틱 시간 p50/p95/최대·큐 지연·가장 느린 region. 기존 `Regionizer` 스케줄 계측을 재사용하고 틱 시간 고리(`TickTimeRing`)를 더했다.
3. **CI**: `.github/workflows/ci.yml` 에 `:furnace-api:test`, `:furnace-plugin-test:classes`, `:furnace-server:fatJar` 단계 추가(러너·트리거·캐시 정책은 그대로). CLAUDE.md 수정 제안은 `docs/user-todo.md` 결정 기록에 남김.
4. 이 문서(`docs/handoff/2026-10-31-session-handoff.md`).
5. **독립 검토 후속(2026-11-01)**: 워치독 시작 직후 오탐(B1) 수정 — 전역 박동은 첫 `scheduledTick` 전에는 0(멈춤 아님)이고, 워치독은 "멈춘 시간"을 자신이 만들어진 뒤 경과 시간으로 상한한다(기동에 `max-tick-time` 이상 걸려도 시작 직후 크래시하지 않음, 가짜 시계 시험 있음). RCON 보안 보강(인증 실패 지연·연결당/IP당 실패 제한·동시 연결 16·인증 전 30초 제한·상수 시간 비교·스레드 이름에서 IP 제거·0.0.0.0 경고), Query 챌린지 맵 상한 1024, Query hostIp 기동 때 한 번 계산. 바닐라와 달라지는 점은 context-notes.
6. **경고**: `region-file-compression=lz4` 는 쓰지 못할 뿐 아니라, 1.20.5+ 바닐라에서 lz4 로 저장한 월드를 가져오면 청크 읽기가 실패한다(읽기 미지원).

검증 숫자(전체 시험 개수·실패·스킵)는 이 세션의 마지막 전체 실행 결과를 보고서에 적었다. 이 문서에는 적지 않는다 — 다음 세션이 직접 돌려서 확인한다.

## 3. 남아 있는 큰 구멍 (우선순위는 `unimplemented-survey-2026-10-31.md` 1장)

- 확정 버그 U1~U7(`user-todo.md` 상단)과 "접속 시험 요청" 표 — 서버 단위 시험은 통과했으나 실접속은 사용자가 확인해야 한다.
- 인챈트 일부(물갈퀴·서리 걸음·영혼 가속·호흡·민첩한 웅크리기·물 친화는 플레이어 속성 시스템이 없어 미구현), 시련 스포너·금고, 솔질 블록, 발전 과제 트리거 약 33/56종.
- 플러그인 API: 이벤트 15종뿐, Entity/World/ItemStack/Inventory 타입 없음, Paper/Bukkit 어댑터(5단계 `furnace-compat-paper`)는 아직 없다.
- 서버 패킷 일부 미처리(`edit_book`, `set_structure_block` 등)와 미송신 약 24종.
- 월드젠: 일부 구조물은 순정 대조 미검증, 다중 청크 decoration 구조 제약, 커스텀 월드 타입 무시.
- 운영: 워치독·`/furnace` 명령은 들어왔지만 월드 주기 백업·메트릭 내보내기는 없다. `enable-jmx-monitoring`·`bug-report-link`·`resource-pack*` 등 일부 키는 여전히 파싱만 한다.
- 테스트 없는 패키지: bossbar, effect, function, profiling, sound, trade. 바닐라 데이터가 없는 환경에서 `assumeTrue` 로 조용히 스킵되는 시험이 있을 수 있다(AGENTS.md 규칙 5 와 긴장 — 점검 필요).

## 4. 다음 할 일 (제안 순서)

1. **사용자 접속 시험 결과 반영** — `user-todo.md` 의 U1~U7 과 접속 시험 요청 표. 실패한 항목부터 바닐라 디컴파일로 대조해 고친다.
2. **미구현 우선순위 1~10**(`unimplemented-survey-2026-10-31.md` 1장): 엔티티 NBT 저장 공백 재확인, 셜커 상자 내용물, 시련 스포너·금고, 솔질 블록, 발전 과제 트리거 호출 지점.
3. **작업 G 후속**: Query/RCON 을 실제 클라이언트(mcstatus, `mcrcon`)로 접속해 확인(지금은 단위·루프백 시험만), `/furnace` 명령을 실서버에서 부하 중 확인, 워치독을 일부러 멈춰 본 실측. 결과는 `context-notes.md` 에 '확인함/가정' 구분해 적는다.
4. **플러그인 API 확장**(이벤트·Entity/World 타입) → 이후 5단계 `furnace-compat-paper`.
5. **문서 정리**: 옛 `roadmap-2026-10-09.md`·`remaining-work-2026-10-08.md`·`vanilla-diff-audit-2026-10-28.md` 는 낡았다(조사 문서가 경고). 새 로드맵이 필요하면 사용자와 순서를 정한다.
6. **사용자 결정 대기**(바로 하지 않는다): CI 러너·트리거·캐시·외부 접근, 공유 스케줄러 S4 기본값, CLAUDE.md 의 커밋 전 규칙에 `:furnace-api:test` 추가 여부 및 "현재 다음 작업" 문구 갱신, Mojang jar 히스토리 정리 시점.

## 5. 지도·도구

- `graphify-out/` 코드 구조 지도가 있다. 코드 위치·연결을 찾을 때는 파일을 뒤지기 전에 `graphify query` 를 쓴다(CLAUDE.md). 갱신은 로드맵 한 단계를 끝냈을 때만 한다 — 이번 작업 G 는 그 단위가 아니라서 갱신하지 않았다.
- 바닐라 디컴파일(읽기 전용)은 세션 스크래치패드에만 있고 커밋하지 않는다(EULA). 난독화 이름은 grep 으로 찾는다. 새 컨테이너에서는 `tools/vanilla-data/README.md` 로 재생성해야 한다.
- 이 세션이 디컴파일에서 읽은 난독화 이름(1.21.1 서버 jar 기준, 이 문서 작성 시점에 jar 로 재확인하지는 않았다 — 쓰기 전에 grep 으로 다시 확인할 것): `avk`(RconThread)·`avj`(RconClient)·`avi`(QueryThreadGs4)·`avd`(쿼리 응답 버퍼)·`apq`(서버 워치독)·`apn`(DedicatedServer)·`aqu`(ServerLevel, `i` = shouldDiscardEntity)·`dwr`(RegionFileVersion).
