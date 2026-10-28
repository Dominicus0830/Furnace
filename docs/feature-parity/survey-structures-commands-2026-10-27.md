# 조사: 월드젠 구조물 현황 + 명령 현황 (2026-10-27)

코드 수정 없는 조사 문서. 기준은 HEAD `f25f8042`. 경로는 `furnace-server/src/main/java/org/domi/furnace/server/` 기준(`S/`로 줄임).
확인하지 못한 것은 "가정"/"확인 못 함"으로 적었다. 이 조사에서 테스트나 순정 대조는 새로 돌리지 않았다(코드·fixture·커밋 이력 읽기만).

## 요약

- 로드맵(2026-10-09) 1단계의 구조물 항목(1-1~1-4, 1-6)은 **코드는 모두 있다.** 단 "구현됨(코드 존재)"과 "parity 검증됨(순정 대조 기록)"은 다른 말이라 A-1 에서 두 열로 분리했다. parity 검증됨으로 말할 수 있는 것은 일부이고, 사막 피라미드조차 허용 불일치 700~1850칸 상태라 완전 일치가 아니다. 로드맵 표는 낡았다(1-5만 ✅).
- 구조물 type 파일 중 `minecraft:nether_fossil` **하나만 미등록**이라 생성되지 않는다(`S/world/gen/structure/StructureEngine.java:322`, 미구현 type 은 건너뜀).
- `NoopFeature` 로 남은 configured_feature 루트 타입은 **2종**: `bonus_chest`, `void_start_platform`.
- 구조물의 블록 단위 순정 대조는 gradle 테스트가 아니라 `tools/parity-audit`(StructCmp, 실서버 .mca)로 했다. 커밋 메시지에 "verified against vanilla"가 있는 것만 검증됨으로 적었다. gradle 테스트의 fixture 대조는 사막 피라미드 3개 청크와 배치 위치(structplacement 2개)뿐이다.
- 명령: 바닐라 1.21.1 전용 서버 루트 명령 83개(테스트 리소스 `vanilla-1.21.1-commands.json.gz`, 가정 아님) 중 **43개 등록(별칭 포함), 40개 없음.** 점수판·팀·함수·커스텀 보스바 시스템 클래스는 **없다.**

## A. 월드젠 구조물

판정은 두 축으로 분리한다. AGENTS.md 절대 규칙 3(parity 를 거짓으로 만들지 않는다)에 따라 코드가 있다는 사실만으로 parity 를 주장하지 않는다.

- **구현(코드)**: 구현됨 / 부분 / NoopFeature / 없음. 코드 파일이 있고 등록돼 있다는 뜻뿐이다.
- **parity**: 검증됨(순정 대조 기록이 커밋 메시지나 fixture 로 확인됨) / 근사(대조했으나 불일치가 남음) / 미검증(대조 기록을 확인 못 함) / 해당 없음.
- 대조 기록은 커밋 메시지의 "verified against vanilla" 문구를 근거로 했다. 이 조사에서 StructCmp 를 다시 돌리지는 않았으므로 "검증됨"도 "당시 커밋 작성자가 그렇게 기록함" 수준이다.

#### A-1a. 두 축 요약

| 항목 | 구현(코드) | parity |
|---|---|---|
| 폐허 포털 | 구현됨 | 검증됨(`6555ea85`) |
| 정글 신전 | 구현됨 | 미검증 |
| 늪 오두막 | 구현됨 | 미검증(스폰 요청만 기록, 블록 대조 문구 없음) |
| 사막 피라미드 | 구현됨 | **근사**(gradle fixture 3개, 허용 불일치 700~1850칸) |
| 이글루 | 구현됨 | 검증됨(`b51f3bab`) |
| 후처리(postprocess) | 구현됨(유체 틱 초기화는 확인 못 함 → 부분 가능) | 미검증(별도 대조 기록 없음) |
| 광산(+메사) | 구현됨 | 검증됨(`c5d64baa`) |
| 요새 | 구현됨 | 검증됨(`237a8d32`) |
| 해저 신전 | 구현됨 | 미검증 |
| 삼림 대저택 | 구현됨 | 검증됨(`dd75b684`) |
| jigsaw 엔진 | 구현됨 | 마을 경로로 검증됨(`c4fbf9cd`) |
| 마을 5종 | 구현됨 | 검증됨(`c4fbf9cd`) |
| 약탈자 전초기지 / 고대 도시 / 시련의 방 / 보루 / 트레일 유적 | 구현됨(jigsaw 데이터) | 미검증 |
| Beardifier | 구현됨 | 마을과 함께 검증됨(`c4fbf9cd`)으로 보이나 단독 기록은 없음(가정) |
| sculk_patch(1-6) | 구현됨(SculkSpreader 이식) | 미검증 |
| rooted_azalea_tree | 구현됨(런타임 폴백 여부 미확인) | 미검증, 전용 테스트 없음 |
| fossil | 구현됨 | 미검증 |
| 네더 요새 | 구현됨 | 검증됨(`54447ac0`) |
| 엔드 도시 | 구현됨 | 미검증(문구 없음; 엔드 feature 는 `000f005d` 에서 순정 End 월드 대조) |
| nether_fossil 구조물 | **없음** | 해당 없음 |

즉 "구현됨" 20여 개 중 parity 검증됨은 약 8개, 근사 1개, 나머지는 미검증이다. R1(아래 "검토자 반박에 대한 답")에서 이유를 적는다.

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
| `locate` | 구현 | `structure`·`biome`·`poi` (2026-10-28 추가, 차이는 `context-notes.md` 같은 날짜 항목) |
| `summon` | 구현됨 | NBT 인자 있음: `SummonCommand.java:52-53` 에서 `CompoundTagArgument` 를 받아 `summon()` 에 전달(확인 완료) |
| `advancement` | 구현됨 | grant/revoke × only/from/until/through/everything. 바닐라 트리와 `VanillaCommandTreeTest` 전수 비교 대상(B-5) |
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
| `particle` | 없음 | 중 | 파티클 인자(옵션 데이터가 종류별로 다름), 레벨 파티클 패킷. 확인: `S/` 에 파티클 송신 코드는 `ExplodePacket`·`SetEntityData` 등 관련 패킷 외에 없음 → 소에서 중으로 상향 |
| `playsound`, `stopsound` | 없음 | 소 | 사운드 레지스트리·패킷 |
| `title` | 없음 | 소 | 컴포넌트 인자 없음, 제목 패킷 |
| `tellraw` | 없음 | 소 | 컴포넌트 JSON 파서 인자 |
| `tag` | 없음 | 소~중 | 엔티티 `Tags` 저장 필드 없음. 셀렉터 `tag=`/`team=` 는 빈 값만 참인 스텁이다(`EntitySelectorParser.java:256-261`)라 셀렉터 쪽도 같이 고쳐야 함 |
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
| `damage` | 없음 | 중 | `damage_type` 레지스트리 id 는 있음(`registries/1.21.1.txt` 에 `damage_type` 줄 47개, 태그 줄 포함 가능) 하지만 서버의 `entity/damage/DamageType.java` enum 은 상수 18개(줄 수 grep)뿐이라 바닐라 전체가 아님 임의 유형을 못 쓴다. 명령 자체는 `damage <대상> <양> [유형] [at 위치 | by 엔티티 [from 엔티티]]` 로 10노드. 선행: DamageType 전체 이식(소~중) + 위치/원인 엔티티를 받는 피해 소스 생성. 따라서 소가 아니라 중으로 수정 |
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

### B-4a. execute 단계 분할

`execute` 는 트리 노드 269개이고 컨텍스트 포크가 핵심이다. 한 번에 만들지 말고 나눈다.

| 단계 | 내용 | 규모 | 선행 의존 |
|---|---|---|---|
| E1 최소형 | `as`, `at`, `positioned`(as 포함), `rotated`, `facing`, `in`, `anchored`, `align`, `run`, `if/unless` 중 `entity`·`block`·`blocks`·`dimension`·`loaded`·`biome` 제외한 것 | 중 | `CommandSource` 가 실행자·위치·회전·차원을 복사·변형 가능해야 함. Brigadier 사본의 fork/redirect 모디파이어 지원(`command/brigadier`; 현재 `xp` 별칭이 `redirect` 를 쓰는 정도만 확인) |
| E2 조건형 | `if/unless entity|block|blocks|dimension|loaded|biome|predicate|function`(`predicate` 는 loot 조건 재사용) | 중 | E1, 블록/영역 비교, 바이옴 질의, 전리품 조건 파서 |
| E3 점수판형 | `if/unless score`, `store ... score`, `on`, `summon` 하위 | 중 | 점수판 시스템(대), E1 |
| E4 데이터형 | `store ... block|entity|storage|bossbar`, `if/unless data` | 대 | `data` 의 NBT 경로(대), `bossbar`(중) |
| E5 함수형 | `if function`, `run function`, return 값 전파 | 중 | 함수 시스템(대) |

점수판 없이도 E1+E2 로 `/execute as @a at @s run ...` 같은 대부분의 일상 용도는 막힌다. 다만 `run` 대상이 될 `tp`, `give`, `tellraw` 등이 없어 실효가 제한적이므로 인자 계층과 `teleport` 가 E1 과 함께 필요하다.

### B-4. 의존 관계 요약 (착수 순서 근거)

0. `execute` 는 위 B-4a 로 쪼개 E1(최소형)부터 시작할 수 있다.
1. 인자 계층: `ItemArgument`, `ComponentArgument`, `NbtPath`, 슬롯, 파티클, 사운드. `give`/`clear`/`item`/`tellraw`/`title`/`data`/`particle`/`playsound` 의 공통 선행.
2. 점수판(+팀). `scoreboard`/`team`/`trigger`/`teammsg`/`execute`(score) 선행.
3. 함수 시스템. `function`/`return`/`schedule`/`execute run function` 선행.
4. `execute` 는 위 셋과 `CommandSource` 포크가 모두 필요해 맨 마지막.
5. 바로 가능한 소규모: `attribute`, `enchant`, `ride`, `random`, `tag`, `damage`, `transfer`, `fillbiome`, `clone`.

### B-5. 하위 트리 동일성 검증 (VanillaCommandTreeTest)

- `furnace-server/src/test/java/org/domi/furnace/server/command/VanillaCommandTreeTest.java` 가 **전수 재귀 대조** 테스트다. `implementedCommandsMatchVanillaTree`(`:127` 부근)가 `vanilla-1.21.1-commands.json.gz` 를 읽고, 권한 4 로 받은 `treeFor(...)` 트리의 **루트 모든 자식**을 순회해 `compare()`(`:95-114`)를 호출한다.
- `compare()` 가 노드마다 비교하는 것: 노드 종류(root/literal/argument), 실행 가능 여부, 인자 파서 id, 파서 속성(최소/최대 등), 리다이렉트 유무, **자식 이름 집합 전체**. 이어서 모든 자식에 재귀.
- 예외 목록은 `NOT_YET`(`:93`) 하나뿐이다: `/locate` 의 `biome`, `poi`. 즉 이미 구현된 43개 명령에서 서브트리 누락은 **locate 외에 없다**(테스트가 통과하는 한; 이 조사에서 테스트를 다시 돌리지는 않았다). 코드로 확인한 사실은 "테스트가 그렇게 짜여 있다"까지다.
- 보증의 한계: (1) 등록된 명령만 본다(없는 명령 40개는 대상 아님 → B-2). (2) 트리 모양만 비교하며 실행 동작의 동일성은 보증하지 않는다. (3) 권한 0 일 때 보이는 명령 집합도 바닐라와 대조한다(`help list me msg tell w`).
- 닫는 방법(추가 대조표 제안): 바닐라 덤프에서 루트별 총 노드 수를 구해 `treeFor` 결과와 비교하는 표를 테스트에 추가하면 "누락 서브트리"를 수치로도 볼 수 있다. 아래는 바닐라 쪽 수치(스크립트로 산출, 자기 자신 포함). 구현 쪽 수치는 이 조사에서 돌리지 않았으나, 위 `compare()` 가 자식 집합을 전부 비교하므로 locate 를 뺀 구현 명령은 같을 것으로 추론한다(가정).

| 명령 | 바닐라 노드 수 | | 명령 | 바닐라 노드 수 |
|---|---|---|---|---|
| advancement | 25 | | gamerule | 103 |
| datapack | 14 | | locate | 7(구현은 biome·poi 제외) |
| effect | 13 | | experience | 15 |
| fill | 10 | | time | 13 |
| worldborder | 20 | | weather | 7 |
| whitelist | 9 | | recipe | 9 |
| summon | 4 | | kill / seed / stop | 2 / 1 / 1 |
| (미구현 큰 것) data | 482 | | execute | 269 |
| loot | 121 | | scoreboard | 70 |
| clone | 67 | | playsound | 62 |
| team | 44 | | item | 41 |
| bossbar | 39 | | attribute | 23 |

미구현 40개의 바닐라 노드 총합은 대략 1,300개 이상이어서(`data`+`execute`+`loot`+`scoreboard`+`clone`+`playsound`+`team`+`item`+`bossbar` 만 1,200+) 규모 판단의 보조 지표로 쓸 수 있다.

## 검토자 반박에 대한 답

- **R1. "구현됨"과 "parity 검증됨"을 섞은 이유.** 섞으려던 것은 아니고, 첫 판에서 한 열에 "구현됨"을 쓰고 검증 근거를 옆 열에 따로 두었는데, 요약 문장("코드상 모두 구현")이 parity 를 암시할 수 있었다. 이번에 A-1a 로 두 축을 분리했다. 결론은 이렇게 바뀐다: 로드맵 1단계 구조물은 코드는 모두 있으나(nether_fossil 구조물 제외), 순정 대조 기록이 확인되는 것은 이글루·폐허 포털·광산·요새·삼림 대저택·마을(+jigsaw 엔진·Beardifier)·네더 요새 정도다. 사막 피라미드는 불일치가 남은 근사다. 정글 신전·늪 오두막·해저 신전·전초기지·고대 도시·시련의 방·보루·sculk·fossil·azalea 는 대조 기록을 확인하지 못했으므로 "미검증"이며, 검증됐다고 말할 근거가 없다. ("기록이 없다"가 "틀리다"는 아니다. 확인 못 한 것이다.)
- **R2. 명령 하위 트리 동일성의 보증.** B-5 참조. `VanillaCommandTreeTest` 가 바닐라 덤프와 노드 종류·실행 가능·파서·속성·리다이렉트·자식 집합을 재귀적으로 전수 비교하며, 예외는 `/locate biome·poi` 하나(`NOT_YET`)다. 따라서 등록된 43개 명령 중 서브트리 미구현은 locate 외에 없다고 코드 근거로 말할 수 있다. 단 (a) 이 테스트를 이번 조사에서 실행하지는 않았고, (b) 보증 범위는 트리 모양이지 동작이 아니며, (c) 미등록 40개는 범위 밖이다.
