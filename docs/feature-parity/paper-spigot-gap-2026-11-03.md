# Paper·Spigot·바닐라(Bukkit API) 대비 미구현 목록 (2026-11-03)

기준: 브랜치 `claude/quirky-wright-lra834`(이 문서를 쓴 시점의 HEAD). 읽기 전용 조사이며 코드는 고치지 않았다.
앞선 조사 `unimplemented-survey-2026-10-31.md`(바닐라 기능 위주), `survey-plugin-api-ops-2026-10-27.md`(API·운영),
`paper-config-keys-2026-10-27.md`(Paper 설정 키)를 이어받아, **지금 코드로 다시 확인한 것만** 적었다.

## 읽는 법
- 근거는 코드 grep 이다(경로 약칭: API=`furnace-api/.../api`, SRV=`furnace-server/.../server`). 바닐라 디컴파일·Paper 원문과 줄 단위로 대조하지 않았다.
- Paper/Spigot 쪽 동작·키 이름은 작성자 기억이다(웹 원문 대조 안 함). 구현 전에 원문을 확인한다.
- AGENTS.md 원칙대로 **바닐라와 관측 결과를 바꾸는 Paper 기능은 기본 꺼짐**이 전제다. 그래서 "Paper 에만 있는 게임플레이 변경"은 하위 우선순위다.
- 우선순위: **상** = 서버 운영자/플러그인 개발자가 Paper 에서 넘어올 때 바로 막히는 것, 또는 바닐라 관측 차이. **중** = 있으면 좋고 대체 수단이 있음. **하** = 비목표에 가깝거나 장기.
- "겹침" 열: 열린 PR(#2~#13) 또는 진행 중 스레드와 겹치면 표시한다. 겹치는 항목은 그쪽이 끝난 뒤 다시 본다.

### 진행 중인 작업(겹침 표시용)
| 표시 | 내용 |
|---|---|
| PR#2 | lz4 지역 파일 읽기·쓰기, 플레이어 속성 저장 |
| PR#3 | 스트라이더 |
| PR#4 | RCON IP당 상한, `minecraft:register`, 안 보내던 패킷 4종 |
| PR#5 | 플러그인 API 2단계(EntityScheduler·PDC·Chunk·스폰/조회·상인·왼쪽 클릭·`PlayerInteractEntityEvent`) |
| PR#6 | 불안정 시험 |
| PR#7 / PR#9 | 몹·플레이어 낙하 위치, 다락문 사다리 |
| PR#8 | 팬텀·순찰·고양이 스포너 오버월드 전용 |
| PR#10 | `TickTimeRing` 최대값 경합 |
| PR#11 | 투사체-블록 반응, `projectilesCanBreakBlocks` |
| PR#12 | 엔티티 블록 훅(entityInside·stepOn·fallOn) |
| PR#13 | 엔티티·아이템 게임 이벤트(진동) |
| 스레드-시야 | 시야 거리 변경 즉시 반영, `pushEntitiesUp` |
| 스레드-몹 | 올챙이·스니퍼 생성, 피글린 분노 |

---

## 1. 플러그인 API / Bukkit 호환

현재 API: 명령·권한·이벤트 버스·스케줄러(global/region)·설정(YAML/TOML/JSON)·Text(Adventure 비슷)·`World`/`Block`/`BlockData`/`Entity`/`LivingEntity`/`Player`/`ItemStack`/`Inventory`.
구체 이벤트 33종(모두 서버에서 발화 지점 있음, grep 확인). Bukkit/Paper 는 300종 안팎.

| 우선 | 항목 | Furnace 현황(근거) | 겹침 |
|---|---|---|---|
| 상 | **Bukkit/Paper 플러그인 실행(호환 어댑터)** | 없음. `SRV/plugin/PluginManagerImpl.java:2,49` 가 `plugins/bukkit-plugins/` jar 를 **세기만** 한다. 기존 Paper 플러그인은 하나도 못 돌린다 | 장기(5단계 `furnace-compat-paper`, 확정된 방향) |
| 상 | 이벤트 확충 — 블록 | 있는 것: Break·Place. 없는 것: 물리(`BlockPhysicsEvent`)·레드스톤·피스톤 확장/수축·유체 흐름·성장/번짐/소멸·불타기·점화·폭발(`BlockExplodeEvent`)·형성·발사기·잎 썩음·표지판 변경 | — |
| 상 | 이벤트 확충 — 엔티티 | 있는 것: Damage·Death·Spawn. 없는 것: 폭발(`EntityExplodeEvent`)·대상 지정·길들이기·번식·활 쏘기·투사체 발사/적중·변신·연소·회복·허기 변화·효과 변화·아이템 스폰/병합/디스폰·탈것 승하차 | PR#5 는 상호작용(`PlayerInteractEntityEvent`)만 |
| 상 | 이벤트 확충 — 플레이어 | 없는 것: 웅크림/달리기/비행 전환·아이템 소비·손 바꾸기·들고 있는 칸 변경·침대·낚시·경험치/레벨 변화·발전 과제 달성·포털·차원 변경·킥·책 편집·양동이·리소스팩 상태·명령 탭 완성 | — |
| 상 | 이벤트 확충 — 인벤토리 | 있는 것: Open·Close·Click. 없는 것: 드래그·제작(`CraftItemEvent`/`PrepareItemCraftEvent`)·화로 제련·마법 부여·모루·양조·호퍼 이동(`InventoryMoveItemEvent`)·거래 선택 | PR#5 상인 API |
| 상 | 이벤트 확충 — 서버 | 없는 것: 서버 목록 핑(`ServerListPingEvent`/`PaperServerListPingEvent`)·탭 완성·방송 | — |
| 중 | 비동기 이벤트 표시 | `API/event/Event.java` 에 `isAsync` 없음. `AsyncChatEvent`·`AsyncPlayerPreLoginEvent` 는 이름만 비동기 | — |
| 중 | 스코어보드·팀 API | 서버 내부 `SRV/scores` 는 있으나 API 타입 없음(플러그인 사이드바·태그 불가) | — |
| 중 | 보스바 API | 서버 내부 `SRV/bossbar` 만 있음 | — |
| 중 | 오프라인 플레이어·밴/화이트리스트/op API | API 에 `OfflinePlayer`·`BanList` 류 없음(서버 내부는 있음) | — |
| 중 | 월드 만들기·내리기(`WorldCreator`, `unloadWorld`) | API `Furnace.world()/worlds()` 는 고정 3차원만. 서버도 다중 월드(차원 외 추가 레벨) 개념이 없음 | — |
| 중 | 서비스 등록(`ServicesManager`) | 없음(Vault 류 경제·권한 연동의 기반) | — |
| 중 | 아이템 API 확장(컴포넌트·이름·로어·인챈트·`ItemMeta`) | `API/item/ItemStack.java` 는 기본 정보만 | PR#5 일부 |
| 중 | 엔티티 API 확장(효과·속성·장비·AI 대상·탈것) | `API/entity/LivingEntity.java` 기본만 | PR#5 일부 |
| 하 | Paper `Registry`/`Material` 전수 열거형 | `BlockData` 문자열 기반만. 어댑터가 필요해질 때 같이 | — |
| 하 | 플러그인 핫 리로드(`/reload` 로 플러그인 다시 읽기) | `/reload` 는 데이터팩만(바닐라와 같음). Paper 도 권장 안 함 | — |

## 2. 서버 설정·명령

| 우선 | 항목 | Furnace 현황(근거) | 겹침 |
|---|---|---|---|
| 상 | **`/tps`·`/mspt`** (Paper 기본 명령) | 없음. 틱 시간은 `/furnace profile`(권한 3, `SRV/command/builtin/FurnaceAdminCommands.java:39`)과 `/tick query` 로만 본다. region 단위 서버라 "서버 TPS" 정의(전역 박동 기준 또는 가장 느린 region 기준)를 정해야 한다 | PR#10(`TickTimeRing`)이 같은 계측을 고침 — 그 위에 얹는다 |
| 중 | `/timings` 또는 spark 류 프로파일러 | 없음(`/jfr`·`/debug`·`/furnace profile` 만). 플러그인별 이벤트 처리 시간 기록 없음 | — |
| 중 | `bukkit.yml`·`spigot.yml`·`paper-*.yml` 읽기 | 로더 없음. Furnace 전용 `furnace.properties`(`SRV/config/ConfigLoader.java`)만. 운영자가 쓰던 값(스폰 한도·`ticks-per`·엔티티 추적 거리 등)을 옮길 길이 없다. 키 목록은 `paper-config-keys-2026-10-27.md` | — |
| 중 | 스폰 한도·스폰 주기 설정 키(`spawn-limits`, `ticks-per`) | 바닐라 상수만(`SRV/entity/spawn/MobCategory.java`). 키 없음 | — |
| 중 | 종류별 엔티티 추적 거리(Spigot `entity-tracking-range`) | 단일 `entity-view-distance` + 바닐라 `entity-broadcast-range-percentage` 만 | — |
| 중 | 콘솔 줄 편집·탭 완성·기록(JLine) | 없음. `SRV/ConsoleInput.java` 는 `BufferedReader` 줄 읽기 | — |
| 중 | `commands.yml` 별칭, `permissions.yml` | 없음 | — |
| 중 | 메트릭 내보내기(bStats/Prometheus/JMX) | 없음. `enable-jmx-monitoring` 은 파싱만 | — |
| 하 | 엔티티 활성 범위(Spigot `entity-activation-range`) | 없음. 켜면 바닐라와 결과가 달라지므로 기본 꺼짐 옵션으로만 | — |
| 하 | 안티 엑스레이 | 없음(grep 0건). 클라이언트가 받는 블록이 달라지므로 기본 꺼짐 옵션으로만 | — |
| 하 | Paper `unsupported-settings`(익스플로잇 허용 스위치) | 없음. 바닐라 동작 그대로 | — |
| 하 | `/perf` | 미구현(앞 조사 3장). `/publish` 는 전용 서버에 없음 | — |
| 완료 | `/version`·`/plugins` | `SRV/command/builtin/FurnaceCommands.java` | — |
| 완료 | RCON·Query·워치독·크래시 리포트·`latest.log` 보관 | `SRV/remote/*`, `SRV/ServerWatchdog.java`, `SRV/CrashReport.java`, `SRV/log/LatestLogAppender.java` | PR#4 RCON 상한 |

## 3. 게임플레이(바닐라 대비)

| 우선 | 항목 | Furnace 현황(근거) | 겹침 |
|---|---|---|---|
| 상 | **1.21 효과 발동: `wind_charged`·`weaving`·`oozing`·`infested`** | 효과 정의·물약 양조만 있고(`SRV/effect/MobEffect.java:73` 등) 죽을 때 돌풍·거미줄·슬라임·좀벌레를 내는 로직 없음(정의 파일 밖 참조 0건) | — |
| 상 | **엔드 관문 순간이동(`end_gateway` 블록 엔티티)** | `SRV/block/types/EndGatewayBlock.java:1` "엔티티 순간 이동은 TODO, 블록 동작은 없다". 드래곤 처치 후 바깥 섬으로 갈 수 없음 | — |
| 상 | 전달체(`conduit`) 활성화·효과·적대 몹 공격 | `SRV/block/types/ConduitBlock.java:1` 물 잠김만, 블록 엔티티 없음 | — |
| 중 | 구조물 블록·직소 블록 블록 엔티티와 패킷(`set_structure_block`/`set_jigsaw_block`/`jigsaw_generate`) | 블록 엔티티 없음(목록 `SRV/block/entity/`). 월드젠 처리기만 있음 | — |
| 중 | 커스텀 월드 타입(`large_biomes`·`amplified`·`debug`) | `level-type` 무시(grep 0건) | — |
| 중 | 행운(`luck`/`unluck`) 전리품·낚시 반영 | 앞 조사 2장 TODO, 재확인 필요(가정) | — |
| 중 | 포털로 좀비 피글린 스폰 | 없음(앞 조사 4장) | 스레드-몹(피글린 분노)과 이웃 — 분노 쪽이 끝난 뒤 |
| 중 | 명령 블록 체인 한 틱 완결 | 비동기라 순서만 보존(인수인계서 3장) | — |
| 하 | region 경계 차이(마을 포위·상인 대상·앵무새 어깨 등) | 인수인계서 3장 | — |
| — | 스트라이더 / 낙하 위치 / 특수 스포너 / 투사체-블록 / 블록 훅 / 진동 / 시야 거리 / 올챙이·스니퍼 | 진행 중 | PR#3·#7·#8·#9·#11·#12·#13, 스레드-시야·몹 |

## 4. 네트워크

| 우선 | 항목 | Furnace 현황(근거) | 겹침 |
|---|---|---|---|
| 상 | **BungeeCord(레거시) IP 포워딩** | 없음. Velocity 모던 포워딩만(`SRV/net/auth/VelocityForwarding.java`). BungeeCord·Waterfall 뒤에 두면 모든 플레이어가 프록시 IP·오프라인 UUID 가 된다. 이전 문서는 "하(Velocity 만)"로 제안했으나 사용자 결정 기록은 없음 | — |
| 중 | PROXY 프로토콜(HAProxy, `proxies.proxy-protocol`) | 없음 | — |
| 중 | 아이템·패킷 크기 검증(Paper `item-validation`, 책 페이지 한도 등) | 없음. 패킷 속도 제한만(`SRV/net/ConnectionHandler.java:93`) | — |
| 하 | 옛 클라이언트(1.6 이하) 서버 목록 핑(0xFE) | 없음 | — |
| 완료 | 온라인 인증·암호화·압축·epoll·transfer·쿠키·리소스팩·서버 링크 | `SRV/net/auth/*`, `SRV/net/NetworkServer.java:67` | PR#4 패킷 4종 |

## 5. 월드·저장

| 우선 | 항목 | Furnace 현황(근거) | 겹침 |
|---|---|---|---|
| 상 | **Paper/Spigot 월드 폴더 가져오기** | Furnace 는 바닐라 배치(`world/DIM-1`, `world/DIM1`, `SRV/world/DimensionType.java:38`). Paper/Spigot 은 `world_nether/DIM-1`·`world_the_end/DIM1` 로 따로 둔다. 넘어오는 서버는 네더·엔드가 새로 생성된다(변환·감지 코드 없음, grep 0건). API `World.name()` 은 이미 Paper 이름 규칙을 쓴다 | — |
| 중 | 주기 백업 | 없음(인수인계서 3장) | — |
| 중 | Paper 청크 저장 옵션(`max-auto-save-chunks-per-tick`, 청크당 엔티티 저장 상한) | 없음 | — |
| 하 | Bukkit `uid.dat`(월드 UUID) | 없음. 어댑터 단계에서 | — |
| — | lz4 지역 파일 읽기 | 진행 중 | PR#2 |

---

## 6. 후속 구현으로 고른 것 (서로 겹치지 않음)

위 "상" 가운데 열린 PR·진행 중 스레드와 파일이 겹치지 않고, 한 PR 크기로 끝나는 것을 골랐다.

1. **`/tps`·`/mspt` 명령** — 운영자가 Paper 에서 가장 먼저 찾는 명령. 서버 TPS 정의(전역 박동 기준 + 가장 느린 region 함께 표시)를 문서로 정하고 구현. PR#10 의 `TickTimeRing` 을 읽기만 한다.
2. **BungeeCord 레거시 IP 포워딩** — `bungeecord` 설정 키(기본 꺼짐), 핸드셰이크 주소 필드에서 IP·UUID·속성 꺼내기. Velocity 와 동시 사용 금지 검사.
3. **Paper/Spigot 월드 폴더 감지·가져오기** — 기동 때 `world_nether`·`world_the_end` 가 있고 `world/DIM-1` 이 없으면 옮기거나 그 경로를 읽는다(원본 보존, 로그 안내).
4. **1.21 효과 발동 4종(`wind_charged`·`weaving`·`oozing`·`infested`)** — 바닐라 `onMobRemoved`/`onMobHurt` 난수 순서까지 이식.
5. **엔드 관문 블록 엔티티와 순간이동** — 출구 찾기·바깥 섬 출구 생성·쿨다운·신호 빔(바닐라 `TheEndGatewayBlockEntity`).

이벤트 확충(1장)은 PR#5 와 같은 파일(`ConnectionHandler`·API 패키지)을 크게 건드리므로 PR#5 병합 뒤 "플러그인 API 3단계"로 따로 잡는다.
