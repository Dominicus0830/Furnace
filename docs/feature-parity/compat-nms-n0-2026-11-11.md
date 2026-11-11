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

## 가정·남은 것

- **가정:** `Blocks` 필드 이름 = 등록 이름 경로의 대문자. 매핑 파일로 확인하지 못했다(필드 이름은 난독화 jar 에 없다). 시험은 이름 → 경로 규칙과 순서만 확인한다.
- 블록 **동작**(모양·단단함·설치·틱·상호작용)은 서버 안에 있어 두지 않았다 — 없는 멤버는 H7 링커가 호출 위치에서 `UnsupportedNmsLink` 로 알린다.
- N0 2부: `BlockStateProperties` 상수(필드 이름 확인 필요), `HolderSet`·태그, `SectionPos`·`InteractionHand`, `FluidState`·`Fluids`, `BuiltInRegistries.ITEM`·`Items`. N-lib(authlib·DFU)는 결정 5 내려받기 틀 위에서 따로.
- `Blocks` 는 `tools/compat/gen-nms-blocks.py` 로 다시 만든다(입력: 이미 커밋된 `registries/builtin-1.21.1.txt`).
