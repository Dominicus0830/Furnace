# 미구현·부분 구현 종합 조사 (2026-10-31)

읽기 전용 조사 4건(블록·아이템·레드스톤 / 명령·네트워크·플레이어 / 월드젠·월드 시스템 / 플러그인 API·운영)을 합친 것이다. 몹 능력 전수 점검은 `mob-ability-audit-2026-10-31.md` 에 따로 있다.

## 읽는 법
- 근거는 대부분 코드 grep 이다. 바닐라 디컴파일과 줄 단위로 대조한 것이 아니다. "구현됨"은 바닐라와 같다는 뜻이 아니다.
- 코드 안 TODO 주석과 오래된 문서(`remaining-work-2026-10-08`, `roadmap-2026-10-09`, `vanilla-diff-audit-2026-10-28`)는 상당수가 낡았다. 착수 전에 해당 코드를 직접 확인한다.
- "가정"은 확인하지 못한 것이다.

## 1. 우선순위 (관측 영향 높음)
| 순위 | 항목 | 비고 |
|---|---|---|
| 1 | 엔티티 NBT 저장 공백(`CustomName`, `Glowing`, `Team`, `active_effects`, `AbsorptionAmount` 등) | 2026-10-28 감사 기록. 최신 반영 여부 재확인 필요(가정) |
| 2 | 셜커 상자 파괴·폭발 시 내용물 소실(`LootParams.BLOCK_ENTITY` 없음) | 재확인 필요 |
| 3 | 플레이어 이동 서버 검증 없음(비행 킥, moved too quickly), `allow-flight` 미소비 | 탈것만 검증함 |
| 4 | 블록 파괴 속도에 성급함·채굴 피로·물속 페널티 미반영 | 서버 진행도 검증 방식은 미확인 |
| 5 | 플레이어 근접 전투: 힘·나약함·성급함·피로 미반영, 철퇴 스매시·밀도·관통·돌풍, 살충 느림 | 1.21 핵심 신규 무기 |
| 6 | 인챈트 효과 공백: 가시, 물갈퀴, 서리 걸음, 영혼 가속, 호흡, 민첩한 웅크리기 | 소스에 참조 0건 |
| 7 | 명령 블록·명령 블록 수레 실행, `set_command_block`/`set_command_minecart` 패킷, `enable-command-block` 미소비 | 한 묶음 |
| 8 | 시련 스포너·금고, `trial_omen` 효과 | 월드젠이 시련의 방을 배치하지만 죽은 블록 |
| 9 | 솔질 블록(의심스러운 모래/자갈) + 솔 아이템 | 고고학 불가 |
| 10 | 발전 과제 트리거: 56종 호출 지점은 연결(작업 E)했으나 근사·미완 남음(effects_changed source → kill_axolotl_target 달성 불가 등) | `advancement-triggers-2026-10-28.md` |
| 11 | RCON·Query 서버 없음, 파싱만 하고 안 쓰는 server.properties 키 다수 | 아래 3장 |
| 12 | ~~방랑 상인 타이머 level.dat 미저장, 마을 포위(VillageSiege) 없음, 습격자 목표 AI 없음~~ **완료(2026-11-02)** — 포위·타이머 저장 구현, 습격자 AI 는 이미 있었음(context-notes 2026-11-02) | |
| 13 | 플러그인 API: 이벤트 15종, Entity/World/ItemStack/Inventory 타입 없음 | |
| 14 | 스레드·region 현황 명령, 틱 프로파일러, 워치독 없음 | 멀티스레드 서버 특성상 필요 |

## 2. 블록·아이템·레드스톤·유체·레시피·효과
### 블록 엔티티(구현 없는 종류)
command_block(3종), trial_spawner, vault, brushable_block(suspicious sand/gravel), structure_block, jigsaw, end_gateway, conduit, enchanting_table(이름 표시만), end_portal(영향 없음, 가정).
- 구현 없는 종류의 청크 NBT 는 그대로 보존된다(데이터는 사라지지 않음).
- 아이템 `block_entity_data` 가 설치 때 적용되지 않는다(주크박스·연단·표지판만 개별 처리). 이름 붙인 상자 등이 데이터를 잃는다.
- `/setblock`, `/fill`, `/clone` 에서 NBT 적용·`Clearable` 비우기·블록 술어 NBT 비교가 없다.
- 표지판 선택자/점수 해석, 호퍼의 용기 엔티티 전리품 풀기, 스포너 승객/장비 없음.
### 레드스톤
- 비교기가 아이템 액자 신호를 읽지 않음, 화살이 버튼을 누르지 못함, 타깃 블록·피뢰침이 투사체 적중에 반응하지 않음, 붉은 돌 광석 `stepOn` 없음, 음표 블록 소리 시드/머리 블록 엔티티 없음.
- 발사기: 양탄자 16색(라마 장식), 드래곤 숨결, 번들, 알/아이템 놓기 일부 없음. (몹 양동이 6종은 2026-10-30 구현)
- 부품 클래스(관측자, 비교기, 중계기, 피스톤, 호퍼, 드로퍼, 레일, 가루, 센서)는 모두 있다. 대기 시간 정확도는 실행 검증 안 함.
### 유체·레시피
- 유체: 큰 구멍 못 찾음. 레시피: 직렬화기 23종 전부 파싱·실행. 블록 정의 241종 모두 등록.
### 아이템 사용
- 의심스러운 스튜·불길한 병·합창 열매(순간이동)·염소 뿔 효과 없음, 끈·이름표·책과 깃펜(`edit_book`, `open_book`) 없음, 가마솥 가죽 갑옷/깃발 세탁 없음, 뒤틀린 균사 낚싯대 없음.
- `entity_data`/`bucket_entity_data` 가 스폰 알·수레 등에 적용되지 않는 곳이 남음. 화로 경험치는 구슬 없이 바로 더함.
### 효과·인챈트
- 효과: ~~`wind_charged`, `weaving`, `oozing`, `infested` 발동 로직 없음~~ (2026-11-03 구현, `effect/MobEffectTriggers`). `luck`/`unluck` 의 전리품·낚시 반영 TODO.
- 인챈트: 위 1장 6번. 보호·날카로움·강타·효율·내구성·수선·무한·관통·급류 등은 구현됨.
- 게임 이벤트(진동) 관련 TODO 11건, 투사체-블록 반응 다수(TNT/양초/모닥불 점화, 종, 장식 항아리 등). (2026-11-03: 엔티티·아이템 쪽 게임 이벤트는 `entity-game-events-2026-11-03.md` 에서 채움, 남은 것도 그 문서)

## 3. 명령·네트워크·플레이어·서버 기능
### 명령
- 루트 83개 중 81개 등록(`/perf` 미구현, `/publish` 는 전용 서버에 없음). 트리 모양은 `VanillaCommandTreeTest` 가 대조하나 실행 동작 일치 보증은 아님.
- `execute on leasher` 항상 빈 결과(끈 체계 없음), `/item` 슬롯 중 말·주민·알레이 등 전용 칸 없음, `/teammsg` 채팅 필터 없음.
### 패킷
- 서버 수신 미처리: `move_player_status_only`(0x1D), `set_command_block`/`set_command_minecart`, `set_structure_block`/`set_jigsaw_block`/`jigsaw_generate`, `edit_book`, `teleport_to_entity`, 플레이 단계 `custom_payload`, `change_difficulty`/`lock_difficulty`, `chunk_batch_received`, `block_entity_tag_query`/`entity_tag_query`, 구성 단계 재진입 관련.
- 서버 송신 안 하는 것(약 24종): `set_entity_link`, `hurt_animation`, `sound_entity`, `open_book`, `resource_pack_push/pop`, `server_links`, `custom_payload`, `delete_chat`, `player_combat_enter/end`, `player_look_at`, `set_simulation_distance`, `set_chunk_cache_radius` 등.
### 플레이어
- 수영·기어가기 자세, 공유 플래그(불·수영·투명·발광) 없음. 관전자 엔티티로 텔레포트 없음. 리소스팩 미구현. 자리 비움 킥(`player-idle-timeout`) 없음.
- 메뉴 25종은 모두 있다. 낙하·허기·재생·게임 모드 기본 분기는 구현됨.
### 서버
- RCON, Query 없음. 파싱만 하고 쓰이지 않는 키: `spawn-npcs`, `allow-flight`, `enable-command-block`, `max-tick-time`, `max-world-size`, `region-file-compression`, `text-filtering-config`, `enable-jmx-monitoring`, `bug-report-link`, `resource-pack*`, `broadcast-rcon-to-ops`.
- 게임룰 51종 중 참조 0건: `commandBlockOutput`, `spawnChunkRadius`, `spectatorsGenerateChunks`. (`projectilesCanBreakBlocks` 는 2026-11-03 연결)
- 화이트리스트, 밴, op, 월드 보더(차원 간 공유), 서버 목록 핑은 구현됨.

## 4. 월드 생성·월드 시스템
- (2026-11-02 갱신: `bonus_chest`·`void_start_platform`·`nether_fossil` 모두 이식됨 — context-notes 참고)
- 순정 대조 미검증 구조물: 정글 신전, 늪 오두막, 해저 신전, 전초기지, 고대 도시, 시련의 방, 보루, 트레일 유적. 사막 피라미드는 근사(허용 불일치 700~1850칸).
- 다중 청크 decoration 은 center 청크 1개만 읽고 쓰는 구조 제약(이웃과 겹치는 나무·균류).
- 커스텀 월드 타입 debug·single_biome_surface 무시(large_biomes·amplified 는 2026-11-06 구현). `/locate biome|poi` 일부, 돌고래 보물 탐색, 지도 `skipKnownStructures` 한계.
- 엔드 활성 출구 포털 판정, 드래곤 공격 후 마법 부여 효과, 사용자 이름 보스바.
- 방랑 상인 타이머 미저장, 마을 포위·좀비 피글린(포탈) 없음, 개구리알 부화·거북알 밟기 등 엔티티 접촉 훅 TODO. — 2026-11-02 에 방랑 상인 타이머·마을 포위를, 2026-11-03 에 개구리알 부화(올챙이)·스니퍼 알 부화(아기 스니퍼)·피글린 분노 호출부를 구현했다. 남은 것: 포탈 좀비 피글린, 개구리알 `entityInside`(떨어지는 블록), 거북알 밟기.
- 스폰 청크 상시 로드 없음, 청크 티켓 체계 구현 정도 미확인. 전리품 조건의 모르는 EntityPredicate/LocationPredicate 필드는 항상 거짓, 전리품 함수 15종 미지원(데이터팩·플러그인 표에만 영향).
- 성능 설계 보류: N-07, N-09, N-10, N-11, F-01, D-14(와이어 바이트/실행 시점이 바뀌어 parity 위험), 플레이어 공간 인덱스(생물×플레이어 순회), 2인 이상 청크 전송 측정.

## 5. 플러그인 API·운영·품질
- 플러그인: 이벤트 15종, Entity/World/Chunk/ItemStack/Inventory/Block 타입과 인벤토리 API 없음, 엔티티 owner 스케줄러 "추후", Paper/Bukkit 어댑터 없음(장기 확정).
- 운영: 월드 주기 자동 저장·백업·워치독·메트릭·스레드 현황 명령 없음, 틱 구간별 프로파일러 없음(`/tick`, `/jfr`, `/debug` 만).
- CI: `:furnace-server:test` 한 줄. `:furnace-api:test` 가 CI 와 커밋 전 규칙에서 빠져 있음. 바닐라 데이터가 없는 환경에서는 일부 시험이 `assumeTrue` 로 조용히 스킵될 수 있음(규칙 5 와 긴장). 테스트 없는 패키지: bossbar, effect, function, profiling, sound, trade. `Thread.sleep`/폴링 대기를 쓰는 시험 파일 38개.
- 문서: `handoff` 는 한 문서 안에서 4-2 상태가 모순되고 2026-10-18 에서 멈춤. `remaining-work-2026-10-08`, `roadmap-2026-10-09`, LLM-Wiki 4단계 노트 없음.

## 6. 확인하지 못한 것(가정)
- 구현이 있다고 적은 항목의 바닐라 일치 여부.
- 엔티티 NBT 저장 공백, 셜커 상자 내용물, `generate-structures` 반영 여부 등 2026-10-28 감사의 "작업 중" 항목의 최신 상태.
- 구조물 전리품 상자 연결의 전수 여부, 예측·아이템 수정자 지원 범위, 청크 티켓 구현 정도, `light_update` 송신 여부, 서버 측 채굴 진행도 검증 경로.
