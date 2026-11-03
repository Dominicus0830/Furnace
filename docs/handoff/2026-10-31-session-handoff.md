# Furnace 세션 인수인계 (최종 갱신 2026-11-03)

> 파일 이름의 2026-10-31 은 작업 G 를 시작한 세션 날짜이고, 이 문서는 2026-11-01 에 쓰고 **2026-11-03 에 갱신**했다(11월 1~2일 두 라운드 반영).

**이 문서가 `docs/handoff/2026-10-18-session-handoff.md` 의 "상태" 부분을 대체한다.** 옛 문서는 지우지 않았다. 대체 범위는 아래와 같다.

| 옛 문서(2026-10-18) | 지금 |
|---|---|
| 1.3 지금 어디까지 왔나, 6장 진행 상황, 7장 미구현 목록 | **낡음.** 이 문서 2~3장과 `docs/feature-parity/unimplemented-survey-2026-10-31.md` 를 본다 |
| **8장 "다음 할 일 — 4-2 메뉴 프레임워크"** | **낡음. 4-2 는 끝났다**(아래 2장). 옛 문서는 한 문서 안에서 "다음은 4-2"와 4-2 이후 상태가 섞여 모순이었다. 8장은 이 문서 4장으로 대체 |
| 2장(확정된 결정·설계 원칙), 3장(규칙), 4장(환경·도구), 5장(아키텍처) | **그대로 유효.** 단 CLAUDE.md/AGENTS.md 가 더 최신이면 그쪽이 우선 |
| `CLAUDE.md` 의 "현재 다음 작업은 4-2" 문구 | 낡음(CLAUDE.md 는 사용자 소유라 고치지 않았다 — `docs/user-todo.md` 결정 기록 참고) |

- 갱신 시점: 2026-11-03. 기준 브랜치 `claude/quirky-wright-lra834`, 갱신 당시 HEAD 는 `393ce2e1`(플러그인 API 1단계 병합). 그 시점 전체 시험(`:furnace-api:test` + `:furnace-server:test`)은 BUILD SUCCESSFUL 이었다.
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

### 2026-10-30 ~ 11-02 라운드 (병렬 워커 + 독립 검토 방식)
각 항목은 워커가 격리 worktree 에서 구현 → 독립 검토 에이전트가 바닐라 디컴파일과 대조 → 블로커는 워커가 수정 → 병합 후 전체 시험 → 푸시 순서였다. 상세 근거·바닐라와 다른 점·가정은 모두 `docs/feature-parity/context-notes.md` (날짜별 절).

**접속 시험 버그 U1~U7 처리 (2026-10-31)**: 주민 폭죽 자해(폭죽 소유자 미전달 → 수정, 겉날개 폭죽 이중 피해도 수정), 파괴수 방패 막힘 기절·밀치기 구현, 방어구 아이콘(플레이어 방어 속성을 클라이언트로 보내지 않던 것 → 수정). 바닐라와 같음으로 확인되어 코드를 안 고친 것: 주민 문 열고 닫기, 말 재탑승 쿨다운 60틱(웅크림 입력이 서버에 남는 레이스는 추정만, 재현 못 함), 좀비 연속 타격.

**2026-11-01 라운드**
- 엔티티 NBT 저장(대부분 이미 구현돼 있었음, `Team` 로드·`Invisible` 위치 바닐라화), 셜커 상자 크리에이티브 드롭, 아이템 `block_entity_data` 설치 적용.
- 플레이어 **속성 시스템**(최대 체력·이동 속도 등 `update_attributes` 동기)과 그 위의 인챈트: 물갈퀴·호흡·민첩한 웅크리기·친수성·영혼 가속·서리 걸음.
- **시련의 방**: 시련 생성기·금고 블록 엔티티, 불길한 병, `trial_omen`.
- **고고학**: 솔질 블록·솔. 아이템 효과(의심스러운 스튜·합창 열매·염소 뿔·이름표·책과 깃펜 `edit_book`/`open_book`·가마솥 씻기).
- **발전 과제 트리거** 56종 호출 지점 연결과 `EntityPredicate` 확장, 보상 전리품 인벤토리 지급. 못 이루는 조건 일부 남음(`effects_changed` 의 `source` → `kill_axolotl_target`).
- 이동 서버 검증(`PlayerMoveValidator`), 파괴 속도 효과, 근접 전투 속성, 수영·기어가기 자세 서버 계산.
- 철퇴(스매시·밀도·관통·돌풍)·가시, 가죽끈 체계·늑대 변종과 갑옷·알레이 수신·가스트 불덩이 되치기, 명령 블록 3종·수레·설정 패킷.
- 작업 G(운영·관측): 위 "이 세션(작업 G, 서버 운영·관측)에서 한 것".

**2026-11-02 라운드**
- 몹 게임 이벤트(워든·스컬크 센서가 반응), 충전 크리퍼 머리 드롭, 펫 사망 메시지, 돼지 안장·당근 낚싯대 조종, 거미줄 면역.
- **마을 포위(VillageSiege)**, 방랑 상인 타이머 level.dat 저장(상인 스포너는 오버월드 전용으로 변경), 생성기가 주민·습격자에 스폰 이유 전달. **`Zombie.finalizeSpawn` 이 바닐라에 없는 `nextBoolean()` 을 소비해 모든 좀비 스폰의 난수가 밀려 있던 것을 수정.**
- 패킷: `hurt_animation`·전투 진입/종료·`server_links`·`player_look_at`, `teleport_to_entity`·플러그인 채널(`custom_payload`)·난이도 변경·태그 질의·적응형 청크 전송(`chunk_batch_received`), **서버 리소스팩**(구성 단계 push + 대기 중 keep-alive), `player-idle-timeout` 킥.
- 월드젠: `nether_fossil` 구조물, `void_start_platform`·`bonus_chest` feature 이식. oracle 대조는 못 했다(디컴파일 손계산 시험만).
- 돌고래-플레이어·보물 찾기, 앵무새 어깨, 바람 구슬 되치기·브리즈 튕김, 보트·말·낙타 끈, 일러저 Johnny 등.
- **플러그인 API 1단계**: 설계 문서 `docs/api/entity-world-design.md`, `World`·`Block`·`Entity`·`ItemStack`·`Inventory` 타입과 이벤트 13종. 다른 region 상태를 동기로 읽는 메서드는 만들지 않았다(스냅샷·`CompletableFuture`·소유 스레드 검사, 서버 스레드에서 미완료 Future 의 `get()` 거부).

**동작이 바뀌어 알아야 하는 것**: `spawn-animals=false`·`spawn-npcs=false` 는 기존 동물·주민도 지운다(바닐라와 같음) / 방랑 상인 스포너 오버월드 전용 / 몹 흡수 체력이 피해를 막는다 / `Zombie.finalizeSpawn` 난수 순서 정정 / 일반 jar 이름이 `furnace-server-plain.jar` 로 바뀌고 `furnace-server.jar` 는 fatJar 만 만든다.

검증 숫자(전체 시험 개수·실패·스킵)는 이 세션의 마지막 전체 실행 결과를 보고서에 적었다. 이 문서에는 적지 않는다 — 다음 세션이 직접 돌려서 확인한다.

## 3. 남아 있는 큰 구멍 (우선순위는 `unimplemented-survey-2026-10-31.md` 1장 — 단, 그 문서의 일부 항목은 이후 구현돼 낡았다. 착수 전에 코드를 직접 확인한다)

- **플러그인 API는 1단계다.** 설계 문서 7장 "다음 단계": `LEFT_CLICK`, 상자·상인·엔티티 컨테이너 `Inventory`, 엔티티를 따라가는 `EntityScheduler`, `PersistentDataContainer`, `Chunk` 객체, 엔티티 스폰·조회 API, `PlayerMoveEvent` 의 회전, 이벤트 세분화(`DamageCause` 등). Paper/Bukkit 어댑터(5단계 `furnace-compat-paper`)는 아직 없다.
- **스트라이더**(안장·조종·뒤틀린 균류 막대·좀비 피글린 기수), 몹 낙하 위치 기록(몹 사망 메시지의 낙하형), 순찰·팬텀·고양이 스포너의 오버월드 전용화.
- **region 단위로 동작하는 차이**: 마을 포위 상태·상인 대상 플레이어 선택·앵무새 어깨 앉히기·돌고래의 플레이어 효과 부여 일부(소유권 위임 미완), 명령 블록 체인이 region 경계를 넘으면 끊김.
- **명령 블록은 비동기라 바닐라처럼 한 틱에 체인이 끝나지 않는다**(순서는 보존, 시간이 다름). 한 틱 완결이 필요한 회로는 박자가 다를 수 있다.
- 월드젠: 일부 구조물은 순정 대조 미검증(정글 신전·늪 오두막·해저 신전·전초기지·고대 도시·시련의 방·보루·트레일 유적, 사막 피라미드는 근사), `nether_fossil`·`void_start_platform`·`bonus_chest` 도 oracle 미검증, `bonus_chest` 는 월드 생성 때 스폰 지점 자동 배치 미연결, 다중 청크 decoration 구조 제약, 커스텀 월드 타입 무시.
- 발전 과제: 일부 조건은 못 이룬다(`effects_changed` 의 `source`, 철퇴 뒤 `fall_after_explosion` 의 원인, 드래곤의 숨결 `interacted`). 못 걸고 있는 패킷 4종: `sound_entity`·`delete_chat`·`set_simulation_distance`·`set_chunk_cache_radius`(직렬화만 있음).
- 운영: `/tps`·`/mspt` 는 2026-11-03 구현(`docs/feature-parity/tps-mspt-2026-11-03.md` — 서버 TPS 정의, 전역 박동을 바닐라 고정 박자로 고쳐 게임 시간이 1% 느리던 것도 수정). 플러그인용 `getTPS()` 는 없음. RCON IP당 동시 연결 상한 없음, 월드 주기 백업·메트릭 내보내기 없음, `enable-jmx-monitoring` 등 일부 키는 파싱만. 플러그인 채널 등록(`minecraft:register`) 미구현. lz4 지역 파일 읽기 미지원(1.20.5+ 바닐라 lz4 월드를 가져오면 청크 읽기 실패).
- 플레이어 속성 맵의 저장·복원 없음(`/attribute base set` 값은 재접속하면 사라짐), 영혼 가속 입자·소리, 서리 걸음 마그마 면역.
- 테스트: 바닐라 데이터가 없는 환경에서 `assumeTrue` 로 조용히 스킵되는 시험(AGENTS.md 규칙 5 와 긴장), 테스트 없는 패키지(bossbar·effect·function·profiling·sound·trade).
- **불안정 시험**: `TabListTest`(단독 약 4000회에 1회 실패, 원인 미확정)와 `GeneratedVillageProfessionTest`(전체 스위트에서 한 번 실패, 단독 3회 통과, 낮 시간을 직접 조작하며 틱을 돌리는 방식). `LandMobTest`·`WindChargeTest` 의 간헐 실패는 `ArenaLevel` 둘레 벽·바닥 확장으로 고쳤다(몹이 허공으로 떨어져 죽던 것).

## 4. 다음 할 일 (제안 순서)

1. **사용자 접속 시험 결과 반영** — `docs/user-todo.md` 의 접속 시험 요청 표. 실패한 항목부터 바닐라 디컴파일로 대조해 고친다. **이번 라운드 신규 기능(이동 취소 보정, 리소스팩, 마을 포위, 앵무새 어깨, 돼지 조종, 시련의 방 등)의 접속 시험 항목은 `user-todo.md` 에 아직 정리하지 않았다.**
2. **실제 클라이언트 확인이 한 번도 없다.** 지금까지 검증은 단위 시험·전체 시험까지다. 가장 위험한 것: 플레이어 이동 서버 검증(정상 플레이가 뒤로 끌려가지 않는지), 리소스팩 접속 흐름, 플러그인 API 이벤트 발화 비용.
3. **플러그인 API 2단계**(위 3장) → 이후 5단계 어댑터.
4. **미구현 우선순위**(`unimplemented-survey-2026-10-31.md`)에서 아직 남은 것: 스트라이더, region 단위 차이 정리, 월드젠 순정 대조(oracle 이 되는 환경에서).
5. 작업 G 후속: RCON/Query 를 실제 클라이언트(`mcrcon`, `mcstatus`)로, `/furnace` 명령을 부하 중, 워치독을 일부러 멈춰 본 실측. 결과는 `context-notes.md` 에 '확인함/가정' 구분해 적는다.
6. **불안정 시험 근본 원인**: `TabListTest`, `GeneratedVillageProfessionTest`.
7. **사용자 결정 대기**(바로 하지 않는다): CI 러너·트리거·캐시·외부 접근, 공유 스케줄러 S4 기본값, `CLAUDE.md` 의 커밋 전 규칙에 `:furnace-api:test` 추가 및 "현재 다음 작업" 문구 갱신, Mojang jar 히스토리 정리 시점.

## 5. 지도·도구

- `graphify-out/` 코드 구조 지도가 있다. 코드 위치·연결을 찾을 때는 파일을 뒤지기 전에 `graphify query` 를 쓴다(CLAUDE.md). 갱신은 로드맵 한 단계를 끝냈을 때만 한다 — 이번 작업 G 는 그 단위가 아니라서 갱신하지 않았다.
- 바닐라 디컴파일(읽기 전용)은 세션 스크래치패드에만 있고 커밋하지 않는다(EULA). 난독화 이름은 grep 으로 찾는다. 새 컨테이너에서는 `tools/vanilla-data/README.md` 로 재생성해야 한다.
- 이 세션이 디컴파일에서 읽은 난독화 이름(1.21.1 서버 jar 기준, 이 문서 작성 시점에 jar 로 재확인하지는 않았다 — 쓰기 전에 grep 으로 다시 확인할 것): `avk`(RconThread)·`avj`(RconClient)·`avi`(QueryThreadGs4)·`avd`(쿼리 응답 버퍼)·`apq`(서버 워치독)·`apn`(DedicatedServer)·`aqu`(ServerLevel, `i` = shouldDiscardEntity)·`dwr`(RegionFileVersion).

## 6. 작업 방식에서 배운 것 (병렬 워커 + 독립 검토)

- **독립 검토는 값을 했다.** 워커의 "완료" 보고와 달리 검토가 잡은 실제 문제: 워치독 시작 직후 오탐 크래시, 이동 취소 보정이 위치를 영구히 어긋나게 하던 것, 돌고래 보물 구조물 태그 오류, 시험이 헛통과하던 것(설치 시 손 아이템이 소모돼 `assertFalse` 가 우연히 통과), 좀비 난수 순서 오류, 디컴파일 근거가 없는 문서 서술. 검토 에이전트는 읽기 전용(`git show`/`git diff`)으로만 보게 한다.
- **검토 에이전트가 메인 디렉터리에서 `git checkout --detach` 를 하면 병합 커밋이 분리된 HEAD 위에 만들어진다.** 두 번 있었다. 병합 전에 `git branch --show-current` 로 브랜치를 확인하고, 분리됐으면 `git checkout -B claude/quirky-wright-lra834 <커밋>` 으로 브랜치를 옮긴다.
- **병합은 하나씩, 매번 컴파일을 먼저.** 워커 둘이 같은 기능을 따로 만들면 컴파일 오류(`luck()` 중복)나 중복 구현(불길한 병 마시기)이 생기고, 단위 시험만으로는 안 잡힌다. 전체 시험이 잡았다.
- **`docs/feature-parity/context-notes.md` 는 매번 병합 충돌이 난다**(모든 워커가 끝에 덧붙이므로). 양쪽 기록을 모두 남기면 된다.
- **gradle 락 대기가 길다.** 전체 시험 약 12분이고, 워커 여러 개가 동시에 돌리면 대기가 몇 시간까지 간다. 5시간 사용 한도에 걸리면 워커가 멈추지만 worktree 작업은 보존되어 `SendMessage` 로 재개할 수 있다.
- "동작이 같아 보이는 것"과 "난수 호출 순서까지 같은 것"은 다르다. 한 번 어긋나면 이후 모든 난수가 밀린다(좀비 사례).
- `.claude/worktrees/` 에 에이전트 worktree 와 `worktree-agent-*` 브랜치가 80개 넘게 남아 있다(이 갱신 당시 `git worktree list` 82줄). 디스크를 차지하니 병합이 끝난 것은 `git worktree remove`/`git worktree prune` 과 브랜치 삭제로 정리한다(브랜치 삭제는 사용자 확인 후).
