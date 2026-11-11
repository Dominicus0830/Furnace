# 범용 NMS 어댑터 N0 1부 — 블록 상태·레지스트리 뼈대 (2026-11-11)

계획: `compat-nms-adapter-plan-2026-11-11.md` 3·4장의 N0. 근거 표(`compat-nms-source-survey-2026-11-11.md`)에서 `BlockState`·`Block`·`Registry`·`Holder` 를
NMS 쓰는 인기 플러그인 10개 중 7개가 쓰는데 shim 이 0개였다. 특정 플러그인 맞춤이 아니라 바닐라 클래스 모양 그대로 만든다(도미니코 지시 2026-11-11).

## 들어간 것

| 묶음 | 클래스 | 바닐라와 같게 맞춘 것 |
|---|---|---|
| 속성 | `Property`·`BooleanProperty`·`IntegerProperty`·`EnumProperty`·`DirectionProperty`, `Property.Value` | equals(종류·이름·값)·hashCode(처음 한 번 계산)·toString(Guava toStringHelper 꼴)·만들 때 오류 문구, 같은 이름·값의 속성은 블록 사이에 **같은 객체**(바닐라 `BlockStateProperties` 공유와 같은 관측) |
| 속성 열거 | `AttachFace`…`WallSide` 20종, `FrontAndTop`, `TrialSpawnerState`, `VaultState`, `StringRepresentable`; `Direction`·`Direction.Axis` 가 `StringRepresentable` | 상수 순서·직렬 이름·toString(재정의 여부까지 javap 로 확인) |
| 상태 | `StateHolder`·`StateDefinition`·`BlockBehaviour.BlockStateBase`·`BlockState` | 상태 번호마다 객체 하나(`==` 비교), `setValue`·`cycle`·`trySetValue`·`getValue`·`getOptionalValue`, 오류 문구 3종, toString `Block{minecraft:oak_stairs}[facing=north,…]` |
| 블록 | `Block`·`Blocks`(1060개, 레지스트리 순서) | `getId`(null → 0)·`stateById`(모르는 번호 → 공기)·`byItem`(블록 아이템이 아니면 공기)·`asItem`·`getDescriptionId`·`builtInRegistryHolder`·toString |
| 레지스트리 | `ResourceKey`(intern)·`Holder`(Reference·Direct)·`IdMap`·`Registry`·`DefaultedRegistry`·`BuiltInRegistries.BLOCK`·`Registries.BLOCK/ITEM` | 기본값(minecraft:air) 규칙, `getOptional` 은 비어 있음, 번호 = 바닐라 등록 순서, toString `Registry[ResourceKey[minecraft:root / minecraft:block] (Stable)]` |
| CraftBukkit | `CraftBlockData`(`getState`·`fromData`·`createData`·`newData`), `CraftMagicNumbers`(`getBlock`·`getItem`·`getMaterial` 두 개) | 블록이 아닌 Material 이면 null(CraftBukkit 과 같음). 기존 `CompatBlockData` 가 `CraftBlockData` 를 잇는다 |
| API | `BlockTypes.stateId`·`byStateId`·`stateCount`, `BlockType.item` | 서버 상태 번호 = 바닐라 번호(26684개). 숫자 리터럴을 늘리지 않고 이름 기반 표에서 셈(절대 규칙 6) |

shim 은 `furnace-api` 의 블록 종류 표만 본다. 상태 표는 처음 쓸 때 한 번 만들고, 만들 때 `기본 번호 + 순번 == 서버 번호` 를 모든 상태에서 확인한다(어긋나면 시작 실패 — 조용히 틀린 번호를 주지 않는다).

## 모듈 경계

계획 5장 1은 서버 내부가 필요한 shim 을 새 모듈 `furnace-compat-nms` 에 두기로 했다. N0 은 `furnace-api` 만 쓰고, `CompatBlockData` 가 `CraftBlockData` 를 이어야 해서
`furnace-compat-paper` 에 두었다. 새 모듈은 서버 내부에 처음 닿는 N1·N2a 에서 만든다.

## 시험

- `furnace-compat-paper` `NmsBlockStateTest` 6개: 26684개 상태 번호 전부 왕복·같은 객체, 7번째마다 서버 이름 표와 toString 대조, 속성 바꾸기·돌기·공유, 오류 문구, `Blocks` 필드 순서 = 레지스트리 번호, ResourceKey intern·Holder toString, `byItem`·`CraftMagicNumbers`·`CraftBlockData` 왕복.
- `furnace-server` `PluginBlockHandleTest.blockStateIdsRoundTrip`: API 상태 번호 왕복(공기 0, 돌 1, 26684개), 모르는 블록·속성·값 −1, `item()`(wall_torch → torch, water → air).
- 기대 문구는 바닐라 1.21.1 서버 jar 를 javap 로 읽어 손으로 만든 것(dte·dtd·dfy·duf·jm·akq·jz).

## 독립 검토 반영 (PR #209)

검토(`furnace/reviews/pr209-n0-review.md`)는 문구·규칙·상태 표·byItem 이 바닐라와 같다고 확인했고, 가장자리 셋을 고쳤다.
- R1: 상태 값 표를 바닐라 Reference2ObjectArrayMap 처럼 **동일 객체**로만 찾는다 — 따로 만든 `BooleanProperty.create("lit")` 는 없는 속성.
- R2: `CraftMagicNumbers.getMaterial((Item) null)`·모르는 아이템은 AIR(CraftBukkit `getOrDefault(item, AIR)`).
- R3: `CraftBlockData.newData`·`Bukkit.createBlockData(Material, String)` 은 등록 이름 뒤에 문자열을 그대로 붙여 읽는다 — 대괄호 없는 `facing=east` 는
  `IllegalArgumentException("Could not parse data: minecraft:oak_stairsfacing=east")`. 전에 받아 주던 시험(`BlockEntityWorldAdapterTest`)을 바꿨다.
- 남긴 것: `Blocks`·`BuiltInRegistries` 는 클래스 초기화 때 서버 블록 표를 읽는다(서버 전 정적 초기화면 실패가 굳음 — 플러그인은 서버가 뜬 뒤 적재되므로 닿지 않는다고 봄, 가정).

## 가정·남은 것

- **가정:** `Blocks` 필드 이름 = 등록 이름 경로의 대문자. 매핑 파일로 확인하지 못했다(필드 이름은 난독화 jar 에 없다). 시험은 이름 → 경로 규칙과 순서만 확인한다.
- 블록 **동작**(모양·단단함·설치·틱·상호작용)은 서버 안에 있어 두지 않았다 — 없는 멤버는 H7 링커가 호출 위치에서 `UnsupportedNmsLink` 로 알린다.
- N0 3부로 남은 것: `TagKey`·`HolderSet`·태그(`BlockState.is(TagKey)`), `FluidState`·`Fluids`, `BlockStateProperties`(받은 플러그인 소스 10개 중 쓰는 곳이 1곳뿐이라 뒤로). N-lib(authlib·DFU)는 결정 5 내려받기 틀 위에서 따로.
- `Blocks`·`Items` 는 `tools/compat/gen-nms-constants.py` 로 다시 만든다(입력: 이미 커밋된 `registries/builtin-1.21.1.txt`).

## N0 2부 (2026-11-11)

플러그인 소스 10개에서 다음으로 많이 쓰는 것: `BuiltInRegistries.ITEM`(15곳)·`Registries.ITEM/BIOME/ENTITY_TYPE`, `InteractionHand`(4개 플러그인), `SectionPos`(3개).

| 클래스 | 내용 |
|---|---|
| API `GameRegistries.builtIn(레지스트리)` | 내장 레지스트리 항목 id, 바닐라 protocol_id 순(registries/builtin-1.21.1.txt). 동기화(데이터 팩) 레지스트리는 빈 목록 |
| `BuiltInRegistries.ITEM`·`Items`(1333개) | 기본값 minecraft:air. 값은 `Item.byKey` 의 같은 객체라 `ItemStack.getItem()` 과 `==`. 바닐라 Items(`cut`)의 Item 필드도 1333개 |
| `Item.getId`·`byId`·`byBlock`·`builtInRegistryHolder` | null → 0, 모르는 번호 → 공기, 블록 아이템이 없으면 공기 |
| `Registries` 키 29개 | 값 타입 shim 이 없는 것은 `Registry<Object>`(설명자는 ResourceKey 하나라 링크 됨). `DIMENSION`·`LEVEL_STEM` 은 바닐라처럼 같은 키 |
| `SectionPos` | 묶기·풀기·상대 short·블록 ↔ 섹션·`getZeroNode`·`offset`. 바닐라 `kf` 바이트코드와 대조. 엔티티·청크 객체를 받는 생성 메서드와 좌표 스트림은 없다 |
| `InteractionHand` | MAIN_HAND·OFF_HAND |

**가정:** `Items` 필드 이름 = 등록 이름 경로 대문자(Blocks 와 같은 이유로 확인 못 함).

시험: compat `NmsItemsSectionTest` 3개, server `GameRegistriesBuiltInTest` 1개.
