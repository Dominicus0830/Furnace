# 조사: 월드젠 구조물 현황 + 명령 현황 (2026-10-27)

코드 수정 없는 조사 문서. 기준은 HEAD `f25f8042`. 경로는 `furnace-server/src/main/java/org/domi/furnace/server/` 기준(`S/`로 줄임).
확인하지 못한 것은 "가정"/"확인 못 함"으로 적었다. 이 조사에서 테스트나 순정 대조는 새로 돌리지 않았다(코드·fixture·커밋 이력 읽기만).

## 요약

- 로드맵(2026-10-09) 1단계의 구조물 항목(1-1~1-4, 1-6)은 **코드상 모두 구현돼 있다.** 로드맵 표는 낡았다(1-5만 ✅로 표시돼 있음).
- 구조물 type 파일 중 `minecraft:nether_fossil` **하나만 미등록**이라 생성되지 않는다(`S/world/gen/structure/StructureEngine.java:322`, 미구현 type 은 건너뜀).
- `NoopFeature` 로 남은 configured_feature 루트 타입은 **2종**: `bonus_chest`, `void_start_platform`.
- 구조물의 블록 단위 순정 대조는 gradle 테스트가 아니라 `tools/parity-audit`(StructCmp, 실서버 .mca)로 했다. 커밋 메시지에 "verified against vanilla"가 있는 것만 검증됨으로 적었다. gradle 테스트의 fixture 대조는 사막 피라미드 3개 청크와 배치 위치(structplacement 2개)뿐이다.
- 명령: 바닐라 1.21.1 전용 서버 루트 명령 83개(테스트 리소스 `vanilla-1.21.1-commands.json.gz`, 가정 아님) 중 **43개 등록(별칭 포함), 40개 없음.** 점수판·팀·함수·커스텀 보스바 시스템 클래스는 **없다.**

## A. 월드젠 구조물

판정: 구현됨 / 부분 / NoopFeature / 없음. "검증"은 순정 대조 근거.

### A-1. 로드맵 1단계 항목별

| 로드맵 | 항목 | 판정 | 근거 (파일:줄) | 검증 |
|---|---|---|---|---|
| 1-1 | 폐허 포털 | 구현됨 | `S/world/gen/structure/StructureTypes.java:12`, `RuinedPortalStructure.java`(279줄), 네더 변형 포함 | 커밋 `6555ea85` "ruined portal verified" (StructCmp). gradle fixture 없음 |
| 1-1 | 정글 신전 | 구현됨 | `StructureTypes.java:11`, `JungleTempleStructure.java`(212줄) | 커밋 `30611653`에서 추가. 순정 대조 언급 **확인 못 함** |
| 1-1 | 늪 오두막(+마녀/고양이 스폰) | 구현됨 | `StructureTypes.java:10`, `SwampHutStructure.java`, 스폰 덮어쓰기 `entity/spawn/StructureSpawnOverrideTest` | 커밋 `6555ea85` "swamp hut (witch/cat spawn requests)". 블록 대조 여부는 메시지에 없음 |
| 1-1(참고) | 사막 피라미드, 이글루 | 구현됨 | `StructureTypes.java:8,17-21`, `DesertPyramidPiece.java` | 사막 피라미드: gradle fixture `featurestruct_1234_*` 3개 + `StructureFeatureParityTest`(허용 불일치 700~1850칸, 완전 일치 아님). 이글루: 커밋 `b51f3bab` verified |
| 1-2 | 후처리(postprocess) | 구현됨 | 표시: `S/world/ChunkColumn.java:119`, `StructurePiece.java:226`, feature 쪽 `MultifaceSpreader.java:187` 등. 적용: `S/world/gen/pipeline/ChunkPipeline.java:260-279`, `:470-487`(이웃 shape 갱신 후 표시 비움) | 커밋 `36be4739`. 별도 순정 대조 기록 없음. 유체 틱 초기화는 **확인 못 함** |
| 1-3 | 광산(+메사) | 구현됨 | `StructureTypes.java:17`, `MineshaftStructure.java`(759줄) | 커밋 `c5d64baa` verified |
| 1-3 | 요새(stronghold, 동심원) | 구현됨 | `StrongholdStructure.java`(1110줄), 동심원 배치(커밋 `86e8318a`) | 커밋 `237a8d32` verified |
| 1-3 | 해저 신전 | 구현됨 | `OceanMonumentStructure.java`(1774줄) | 커밋 `d3c989e6`. 메시지에 verified 없음 |
| 1-3 | 삼림 대저택 | 구현됨 | `WoodlandMansionStructure.java`(1032줄) | 커밋 `dd75b684` verified |
| 1-4 | jigsaw 엔진 | 구현됨 | `JigsawStructure.java`(409줄: 풀 별칭 `:86`, expansion hack `:126`, 별칭 해석 `:354-386`), `JigsawPools.java`, `JigsawPiece.java` | 커밋 `c4fbf9cd` |
| 1-4 | 마을 5종 | 구현됨 | `StructureRegistry.java:185` 목록 + `resources/worldgen/1.21.1/worldgen/structure/village_*.json` | 커밋 `c4fbf9cd` "villages verified against vanilla" |
| 1-4 | 약탈자 전초기지 | 구현됨(jigsaw 데이터) | `pillager_outpost.json` → `minecraft:jigsaw` | 별도 대조 기록 **확인 못 함** |
| 1-4 | 고대 도시 | 구현됨(jigsaw 데이터) | `ancient_city.json` | 대조 기록 **확인 못 함** |
| 1-4 | 시련의 방 | 구현됨(jigsaw 데이터, 1.21 풀 별칭 지원) | `trial_chambers.json` | 대조 기록 **확인 못 함** |
| 1-4(참고) | 보루 유적·트레일 유적 | 구현됨(jigsaw 데이터) | `bastion_remnant.json`, `trail_ruins.json` | 대조 **확인 못 함** |
| 1-4 | Beardifier | 구현됨 | `S/world/gen/structure/Beardifier.java`, 연결 `S/world/gen/NoiseWorldSource.java:465,487-497` | 커밋 `c4fbf9cd`. 높이 질의용 `generate()`는 바닐라처럼 beard 없음(`:450`) |
| 1-6 | 심층 암흑 sculk | 구현됨 | `feature/SculkPatchFeature.java`, `MultifaceGrowthFeature.java`, `BiomeFeatureData.java:67-68,338,821-824` | 커밋 `5eb6e71b`. "근사"가 아니라 SculkSpreader 이식. 순정 대조 **확인 못 함** |
| 1-6 | rooted_azalea_tree | 구현됨 | `RootSystemFeature.java`, 가중 잎 provider(커밋 `52d7d369`), `BiomeFeatureData.java:439-447` | 파싱 실패 시 Noop 폴백 경로가 남아 있음(`:443`). 실제로 폴백되는지 **미확인**. 전용 테스트 없음 |
| 1-6 | fossil | 구현됨 | `feature/FossilFeature.java`, `BiomeFeatureData.java:455-459` | 커밋 `36be4739`. 전용 대조 테스트 없음 |
| 1-5(참고) | 네더 화석 **구조물** | **없음** | `nether_fossil.json`(type `minecraft:nether_fossil`)이 `StructureTypes.java` 에 등록 안 됨 → `StructureEngine.java:322` 에서 생성 안 함 | - |
| 1-5(참고) | 네더 요새·엔드 도시 | 구현됨 | `NetherFortressStructure.java`, `EndCityStructure.java` | 커밋 `54447ac0`, `3e2e0caf` |

난파선·묻힌 보물·해저 폐허도 구현됨(`ShipwreckStructure`, `BuriedTreasureStructure`, `OceanRuinStructure`; 커밋 `30611653`).

### A-2. gradle 테스트 fixture 대조 현황

| 테스트 | 대상 | 비고 |
|---|---|---|
| `StructurePlacementParityTest` | 구조물 시작 청크 위치(`structplacement_*` 2개, 시드 1234) | 배치만, 블록 아님 |
| `StructureFeatureParityTest` | 사막 피라미드 시작/이웃/구조물 없음(`featurestruct_*` 3개) | 허용 불일치 700~1850 |
| `StructureStartSharingTest` | 공유 jigsaw 시작점의 청크 생성 순서 무관성 | 순정 대조 아님 |
| `ChunkStructuresTest`, `StructureLocatorTest` | 참조·질의·NBT 왕복 / locate | 단위 |
| `NoopFeatureAuditTest` | configured_feature 폴백 타입 허용목록 감시 | 아래 목록 |

블록 단위 대조의 주력은 `tools/parity-audit`(StructCmp)이며 gradle 밖에서 수동 실행한다. 회귀 fixture 로 고정된 구조물이 사막 피라미드 하나뿐이라는 점이 약점이다.

### A-3. NoopFeature 로 남은 feature

configured_feature 전체의 루트 type 을 `BiomeFeatureData.HANDLED_CONFIGURED_FEATURE_TYPES`(`BiomeFeatureData.java:58`)와 대조해 스크립트로 확인했다.

| 타입 | 파일 | 규모 | 비고 |
|---|---|---|---|
| `minecraft:bonus_chest` | `bonus_chest.json` | 소 | 월드 생성 시 보너스 상자. 바이옴 배치 아님(가정) |
| `minecraft:void_start_platform` | `void_start_platform.json` | 소 | 엔드 스폰 발판 |

위 2종은 `NoopFeatureAuditTest.java` 의 `KNOWN_UNIMPLEMENTED_TYPES` 와 일치한다.

`NoopFeature.INSTANCE` 를 반환하는 조건부 폴백(타입은 처리하되 하위 provider 가 미지원일 때)도 있다. 위치: `BiomeFeatureData.java:357`(버섯 provider), `:443,447`(root_system), `:464`(block_pile), `:479,525,535`(provider 미지원), `:517`(알 수 없는 타입 기본값), `:556`(random_patch 내부 Noop), `:627,630`(simple_block), `:832`(multiface 가 glow_lichen 이 아닌 경우), `:863,871`(disk 비-simple provider). 현재 데이터에서 이 경로로 실제 폴백되는 configured_feature 가 있는지는 실행해 보지 않아 **미확인**이다(감시 테스트는 루트 타입만 본다).

### A-4. 빈칸 정리

| 빈칸 | 규모 | 선행 의존 |
|---|---|---|
| `nether_fossil` 구조물 type 등록·이식(단일 조각, 템플릿은 커밋 `191a09e2`에서 추가됨) | 소 | 네더 차원(있음), 템플릿 로더(있음) |
| jigsaw 파생 구조물(전초기지·고대 도시·시련의 방·보루)의 순정 대조 | 중 | 순정 서버 jar·StructCmp 실행 환경 |
| 구조물 블록 단위 gradle 회귀 fixture 확대(마을·광산 등) | 중 | oracle(`worldgen-oracle`), Paper 저장소 접근 |
| 후처리의 유체 틱 초기화 | 소~중 | 유체 틱 스케줄(가정) |
| `bonus_chest`, `void_start_platform` | 소 | loot 테이블 적용, 엔드 스폰 |
| 조건부 Noop 폴백 실제 발생 여부 점검(로드 시 폴백 목록 덤프) | 소 | 없음 |

## B. 명령

기준 목록: `furnace-server/src/test/resources/commands/vanilla-1.21.1-commands.json.gz`(1.21.1 전용 서버 루트 노드 83개). 구현 목록: `S/command/BuiltinCommands.java:13-58` 와 `command/builtin/*.java`. 하위 명령 비교는 일부 명령만 했다.

### B-1. 구현된 명령 (43)

`advancement`, `ban`, `ban-ip`, `banlist`, `datapack`, `defaultgamemode`, `deop`, `difficulty`, `effect`, `experience`, `xp`, `fill`, `gamemode`, `gamerule`, `help`, `kick`, `kill`, `list`, `locate`, `me`, `msg`, `tell`, `w`, `op`, `pardon`, `pardon-ip`, `recipe`, `reload`, `save-all`, `save-off`, `save-on`, `say`, `seed`, `setblock`, `setidletimeout`, `setworldspawn`, `spawnpoint`, `stop`, `summon`, `time`, `weather`, `whitelist`, `worldborder`.

| 명령 | 판정 | 비고 |
|---|---|---|
| `locate` | 부분 | `structure` 만. `biome`, `poi` 없음(`LocateCommand.java:44`). 규모 소~중 |
| `summon` | 부분 가능 | `SummonCommand.java`. NBT 인자 지원 여부 **확인 못 함** |
| `advancement` | 구현됨 | grant/revoke × only/from/until/through/everything |
| `effect` | 구현됨 | give(infinite 포함)/clear |
| `fill` | 구현됨 | replace/keep/outline/hollow/destroy |
| `worldborder`, `time`, `weather`, `experience`, `datapack`, `recipe`, `whitelist` | 구현됨 | 하위 명령이 바닐라 노드와 같은 이름(조회한 범위) |
| `VanillaCommandTreeTest` | - | 트리 모양을 바닐라 덤프와 맞추는 테스트가 있음(모든 등록 명령을 대조하는지는 확인 못 함) |

### B-2. 없는 명령 (40)과 규모·선행 의존

규모는 추정이다(소: 하루 안팎, 중: 수 일, 대: 1주 이상).

| 명령 | 판정 | 규모 | 선행 의존 |
|---|---|---|---|
| `execute` | 없음 | 대 | 명령 컨텍스트 포크·리다이렉트(`command/brigadier` 확장), `CommandSource` 에 실행자/위치/회전/차원 변형, 점수판(if/store score), NBT 소스, 블록/엔티티 조건, 함수 호출 |
| `data` | 없음 | 대 | NBT 경로 인자 없음, 엔티티/블록 엔티티/`storage/` 읽기·쓰기 NBT 변환, 병합 |
| `item` | 없음 | 중 | 아이템 인자, 슬롯 인자, 아이템 수정 함수(`S/loot/LootFunction` 재사용 가능) |
| `scoreboard` | 없음 | 대 | **점수판 시스템 클래스 없음.** 목표/점수/표시 슬롯, criteria, 저장, objective/score/display 패킷 |
| `team` | 없음 | 중 | 점수판 선행. 팀 패킷, 이름표·충돌·친구 사격 규칙 반영 |
| `bossbar` | 없음 | 중 | 커스텀 보스바 시스템 없음. 패킷 `net/protocol/play/BossEvent.java` 와 `world/raid/Raid.java:582-602` 내부 전송만 있음. 저장과 명령 |
| `attribute` | 없음 | 소 | 속성 시스템은 있음(`S/attribute/*`). 명령·엔티티 인자 연결만 |
| `enchant` | 없음 | 소 | 인챈트 데이터는 `S/loot/Enchantment*` 에 있음. 아이템 스택 조작 연결 |
| `loot` | 없음 | 중 | 전리품 엔진은 있음(`S/loot/*`). 소스(fish/kill/loot/mine)·목적지(give/insert/replace/spawn) 구현 |
| `particle` | 없음 | 소 | 파티클 인자, 패킷 전송. 파티클 송신 코드는 **확인 못 함** |
| `playsound`, `stopsound` | 없음 | 소 | 사운드 레지스트리·패킷 |
| `title` | 없음 | 소 | 컴포넌트 인자 없음, 제목 패킷 |
| `tellraw` | 없음 | 소 | 컴포넌트 JSON 파서 인자 |
| `tag` | 없음 | 소 | 엔티티 `Tags` 저장 필드. 셀렉터 `tag=` 지원 여부 확인 필요 |
| `schedule` | 없음 | 중 | 함수 시스템 선행, 예약 이벤트 저장 |
| `function`, `return` | 없음 | 대 | **함수 시스템 없음**: `.mcfunction` 로더, 데이터팩 function 레지스트리, 태그, 체인 한도(gamerule) |
| `clone` | 없음 | 중 | 두 영역 복사(마스크/이동 모드), 블록 엔티티 복사. `BlockCommands` fill 구조 재사용 |
| `fillbiome` | 없음 | 소~중 | 바이옴 인자, 청크 바이옴 변경, 청크 재전송 |
| `place` | 없음 | 중 | feature/jigsaw/structure/template 배치. `FeaturePlacer`, `JigsawStructure`, `TemplatePiece` 호출, region 스레드 소유권 고려 |
| `random` | 없음 | 소 | 난수 시퀀스 저장과 `roll/value/reset` |
| `ride` | 없음 | 소 | 탑승 코드는 있음. 인자·검증 연결 |
| `spreadplayers` | 없음 | 소~중 | 지형 높이 질의, 팀 옵션은 `team` 선행 |
| `teleport`, `tp` | 없음 | 중 | `Vec3/Vec2/Angle` 인자는 있음. 엔티티 이동·차원 이동·`facing` |
| `give`, `clear` | 없음 | 소 | 아이템 인자(+컴포넌트 파서) 필요. 인벤토리 코드는 있음 |
| `damage` | 없음 | 소 | 피해 유형 인자. 피해 코드는 있음 |
| `forceload` | 없음 | 중 | 청크 강제 로드 티켓 저장. region 스케줄러 쪽 청크 로딩 구조 확인 필요 |
| `teammsg`, `tm` | 없음 | 소 | `team` 선행 |
| `trigger` | 없음 | 소 | `scoreboard` 선행 |
| `spectate` | 없음 | 소 | 관전 대상 카메라 패킷 |
| `tick` | 없음 | 중 | 틱 속도 관리자 없음(패킷 `TickingState` 만), 스케줄러 연동 |
| `transfer` | 없음 | 소 | 전송 패킷 |
| `debug`, `jfr`, `perf`, `publish` | 없음 | 소 | 개발용/싱글플레이용. `publish` 는 전용 서버에서 의미가 작음(가정) |

### B-3. 시스템 클래스 유무

| 시스템 | 있음? | 근거 |
|---|---|---|
| 점수판(Scoreboard/Objective/Score) | **없음** | `S/` 에 해당 이름 클래스·파일 없음 |
| 팀(PlayerTeam) | **없음** | 클래스·패킷 처리 없음 |
| 커스텀 보스바 | **없음** | 패킷 `BossEvent` 와 습격 `Raid` 내부 사용만 |
| 함수(mcfunction) | **없음** | 로더·매니저 grep 결과 없음 |
| NBT 경로 / 아이템·컴포넌트 인자 | **없음** | `command/arguments/` 에 `CompoundTagArgument` 등만 있음 |
| 속성 | 있음 | `S/attribute/*` |
| 전리품 테이블 | 있음 | `S/loot/*` |
| 업적 | 있음 | `S/advancement/*` |
| 레시피, POI, 습격 | 있음 | `S/recipe`, `S/world/PoiManager`(테스트 `PoiManagerTest`), `S/world/raid` |

### B-4. 의존 관계 요약 (착수 순서 근거)

1. 인자 계층: `ItemArgument`, `ComponentArgument`, `NbtPath`, 슬롯, 파티클, 사운드. `give`/`clear`/`item`/`tellraw`/`title`/`data`/`particle`/`playsound` 의 공통 선행.
2. 점수판(+팀). `scoreboard`/`team`/`trigger`/`teammsg`/`execute`(score) 선행.
3. 함수 시스템. `function`/`return`/`schedule`/`execute run function` 선행.
4. `execute` 는 위 셋과 `CommandSource` 포크가 모두 필요해 맨 마지막.
5. 바로 가능한 소규모: `attribute`, `enchant`, `ride`, `random`, `tag`, `damage`, `transfer`, `fillbiome`, `clone`.
