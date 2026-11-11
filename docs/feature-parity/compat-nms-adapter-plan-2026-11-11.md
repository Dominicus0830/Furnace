# 범용 NMS 어댑터 계획 (결정 B, 2026-11-11) — 초안

> 상태: **확정(검토 반영)**. 독립 검토(Fable, 2026-11-11) `furnace/reviews/nms-adapter-plan-review.md` — 측정 재현됨, 순서 판단·열린 질문 추천을 4·5장에 반영.

## 0. 결정과 방향

- 도미니코 결정(2026-11-11): FAWE 블록 편집은 **B. NMS shim 확장**으로 간다.
- 같은 날 지시: **어댑터는 특정 플러그인 맞춤이면 안 되고, 웬만한 플러그인이 호환되는 범용이어야 한다.** FAWE 는 여러 검증 대상 중 하나다.
- 그래서 묶음은 "FAWE 가 부르는 심볼"이 아니라 **여러 플러그인이 같이 쓰는 NMS 표면**으로 나눈다. 기준 측정은
  [`compat-nms-source-survey-2026-11-11.md`](compat-nms-source-survey-2026-11-11.md)(소스 10개)와 H5 표
  [`compat-h5-nms-calls-2026-11-07.md`](compat-h5-nms-calls-2026-11-07.md)(Central jar 19개)다.
- H5 설계(`compat-h5-2026-11-07.md`)의 등급·실패 방식(5장: 없는 심볼은 부를 때만 `UnsupportedNmsCall`, 리플렉션은 진짜처럼 실패)·H7 덫 구조는 그대로 쓴다.
  **바뀌는 것은 H5 2.1 표에서 C 였던 "청크 내부"와 "상속"이다** — 결정 B 는 이 둘을 B(되는 범위는 정확히, 밖은 던짐)로 올리는 결정이다.

## 1. 표본

| 종류 | 플러그인 | 이름 체계 | 비고 |
|---|---|---|---|
| 소스(이번) | FAWE, WorldEdit, Citizens, Shopkeepers, Denizen, dynmap, DecentHolograms, TAB, LibsDisguises, squaremap | Mojang(paperweight) 또는 Spigot | 1.21.1 모듈. Citizens·squaremap 은 1.21.3 판 근사 |
| 소스, 리플렉션식(이번, 참고) | ProtocolLib, packetevents, ViaVersion, Item-NBT-API | 문자열 | 클래스 이름을 문자열로 찾는다(ProtocolLib `getMinecraftClass` 90곳). 표에는 안 들어감 |
| NMS 없음(이번) | LuckPerms, Vault, EssentialsX, Multiverse, mcMMO, Slimefun, CoreProtect, PlaceholderAPI | — | Bukkit 계열 모듈에서 `net.minecraft` 참조 0 |
| jar(H5) | CommandAPI 두 판, signgui, IF, scoreboard-library, glowingentities, ParticleNativeAPI, fastboard 등 19개 | 섞임 | 기준표·nmsReport |

이 환경은 Maven Central 과 GitHub 소스만 닿는다(enginehub·codemc·citizens·Modrinth·Hangar·Spiget 막힘). 그래서 **jar 로 실제 기동 검증할 수 있는 것은 FAWE(Central 조립판, 어댑터 없음)·ProtocolLib 5.4.0·LuckPerms·Vault(사용자 업로드)·H5 Central jar 뿐**이다.
FAWE 1.21.1 어댑터 클래스는 사용자의 Paper jar 에만 있다(Central 판에는 없음).

## 2. 측정이 알려 준 것

1. **NMS 를 쓰는 인기 플러그인은 모두 같은 뼈대를 쓴다.** 10개 중 7~10개가 쓰는 묶음: CraftBukkit 엔티티 클래스(10), `resources`(9), NBT(8), `world.entity`·`server.level`·`core.registries`(8), 블록(`world.level.block`)·`core`·authlib·채팅·`CraftItemStack`(7).
2. **블록 상태 객체(`BlockState`·`Block`·`Blocks`·`Holder`·`Registry`)가 비어 있다.** 7개 플러그인이 쓰는데 shim 이 0이다. 청크·블록 쓰기·패킷 어느 것도 이것 없이는 안 된다 — 가장 앞에 둬야 한다.
3. **청크 내부는 C 로 두기엔 너무 널리 쓴다.** `LevelChunk`·`ChunkAccess` 를 메서드로 쓰는 것은 6개(FAWE·WorldEdit·Denizen·Citizens·dynmap·squaremap), 섹션·팔레트·`DataLayer` 를 직접 만지는 것은 3개(FAWE·squaremap·Denizen)다. `ChunkMap` 은 Citizens·LibsDisguises·Denizen 이 **엔티티 추적기**(`entityMap`·`TrackedEntity`)로 쓰는 것이라 청크 내부가 아니라 N3 쪽이다.
   Furnace 청크 저장은 바닐라와 같은 모양이다(인덱스 YZX, `BitStorage` 배치 같음, 블록 팔레트 단계 0/4/8/전역 같음 — 검토가 바닐라 바이트코드로 확인). 그래서 **메서드 수준(N2a)은 서버 청크의 "보기"로 정확히** 만들 수 있다. 단 FAWE 식 필드·배열 수준 쓰기(N2b)는 보기로는 불가능하고(4장 N2b), 팔레트 자람 순서가 달라 raw `long[]` 은 바닐라와 비트 단위로 같지 않다(팔레트로 풀어 읽는 한 무관).
4. **패킷은 8개 소스(`net.minecraft.network.*`) + 리플렉션 라이브러리 전부가 쓴다.** 게임 패킷은 서로 다른 40종(Spigot 이름 합산), 2개 이상이 쓰는 것 15종, `Connection`·`FriendlyByteBuf`·`SynchedEntityData`, Netty 파이프라인(ProtocolLib·packetevents·ViaVersion·TAB·DecentHolograms). ProtocolLib 는 다른 수많은 플러그인의 바탕이라 파급이 가장 크다.
5. **상속이 생각보다 흔하다.** 가짜 플레이어 `extends ServerPlayer` — FAWE·WorldEdit·Citizens·Denizen(4개, 독립 계통으로는 2). 연결 처리기 `extends ServerGamePacketListenerImpl` — Citizens·Denizen. 몹 상속은 Citizens 가 264개 클래스(모든 몹 종류), Denizen 11개.
6. **라이브러리 클래스도 표면이다.** authlib `GameProfile`(7)·`Property`(4), DataFixerUpper `Codec`·`DynamicOps`·`Pair`(5~6), Brigadier. 서버가 이 라이브러리를 싣지 않으면 NMS shim 이 있어도 클래스 로드에서 죽는다(결정 5·H4 묶음 B 와 이어짐).

## 3. 묶음 (범용 표면 단위)

| 묶음 | 표면 | 쓰는 플러그인(소스 10개 중) | 지금 | 핵심 어려움 |
|---|---|---|---|---|
| **N0 블록 상태·레지스트리 뼈대** | `BlockState`·`Block`·`Blocks`·`StateDefinition`·`Property` 계열, `Holder`·`HolderSet`·`Registry`·`BuiltInRegistries`·`Registries`·`ResourceKey`, `CraftBlockData`·`CraftMagicNumbers`, `SectionPos`·`InteractionHand`·`FluidState` | 7~8 | `ResourceLocation`·좌표만 있음 | 상태 id(약 2.6만) ↔ 객체 하나 표. raw id 숫자를 늘리지 않고 `BlockRegistry`·`BlockStateKey` 위에 얹기(절대 규칙 6) |
| **N1 서버·월드·엔티티 핸들 깊이** | `ServerLevel`·`ServerPlayer`·`MinecraftServer`·`Entity`·`LivingEntity`·`EntityType` 의 자주 쓰는 멤버(블록 읽기·쓰기, 청크 얻기, 연결, Bukkit 엔티티 얻기), CraftBukkit 엔티티 클래스 나무(134개, 종류별 `CraftZombie` 등 — 생성 파일로), `DamageSource` | 8~10 | 클래스는 있으나 멤버가 적음 | 소유 스레드(H5 4.3). 엔티티 종류별 Craft 클래스는 `EntityType` 표에서 생성 |
| **N2 청크 내부(C→B)** — N2a 메서드 수준 / N2b 필드·배열 수준 | `ServerChunkCache`·`ChunkMap.getVisibleChunkIfPresent`(보이는 청크 얻기), `ChunkAccess`·`LevelChunk`·`LevelChunkSection`·`PalettedContainer`·팔레트 종류·`ChunkStatus`, 바이옴 컨테이너, 하이트맵, 빛 `DataLayer` | 6(메서드) / 3(섹션 직접) | 없음 | ① shim 이 서버 청크의 **보기**여야 한다(복사본이면 쓰기가 안 닿는다) → 호환 모듈이 서버 내부에 닿는 경로가 필요(모듈 경계 결정) ② 리플렉션이 **비공개 필드 이름**으로 들어온다(FAWE: `PalettedContainer.data`·`LevelChunkSection.tickingBlockCount` 등) → shim 이 같은 이름 필드를 진짜로 가져야 한다 ③ FAWE 는 섹션을 **자기 스레드에서** 고친다 → region 스레드 규칙과 정면 충돌(결정 6 의 region 빌리기로 풀 수 있는지) |
| **N3 패킷·네트워크** | 게임 패킷 클래스(값 + 바닐라 직렬화), `Packet`·`FriendlyByteBuf`·`StreamCodec`·`ProtocolInfo`, `SynchedEntityData`·`EntityDataAccessor`, `Connection.channel`·Netty 처리기 이름(`packet_handler`·`encoder`·`decoder`), `ServerGamePacketListenerImpl.send`, `ServerConnectionListener`(주입 지점), 엔티티 추적기 `ChunkMap.entityMap`·`TrackedEntity` | 6 + 리플렉션 라이브러리 전부 | 래퍼 34종 + shim 11개 | 패킷마다 손으로 감싸면 범용이 아니다 → **바닐라 프로토콜 정의에서 패킷 클래스·코덱을 생성**하는 길을 검토. ProtocolLib 는 `ProtocolInfo`·`IdDispatchCodec` 로 패킷 표를 읽는다 |
| **N4 상속 가능한 서버 객체** | 가짜 `ServerPlayer`(연결 없는 생성자), `ServerGamePacketListenerImpl` 상속, 몹 상속(`extends Zombie` 등)과 AI(`Goal`·`PathNavigation`·`MoveControl`) | 4(가짜 플레이어) / 2(몹) | 정의 때 거부 | 가짜 플레이어는 "문맥 객체"로만 쓰여 비교적 쉽다. 몹 상속은 서버가 플러그인 클래스의 가상 메서드를 불러야 해서 Furnace 엔티티 구조에 훅이 필요 — 가장 깊다 |
| **N5 기타** | 스코어보드(`world.scores`), 명령(Brigadier·`CommandSourceStack`), 통계, 메뉴(`AbstractContainerMenu`), 데이터 팩·월드젠 내부 | 2~4 | 일부 | 항목별 |
| **N-lib 라이브러리** | authlib, DataFixerUpper(`Codec`·`DynamicOps`·`Dynamic`), Brigadier | 5~7 | 없음 | 결정 5(필요할 때 내려받기) 의 대상에 넣는다. 라이선스·배포 확인 |

## 4. 순서 (검토 반영 — 묶음을 쪼갠다)

1. **N0 + N-lib(authlib·DFU)** — 모든 묶음의 바탕. 시험: 상태 id ↔ `BlockState` 왕복 전부, `Block.defaultBlockState`·`Property` 값 바꾸기가 바닐라 표와 같음, `BuiltInRegistries.BLOCK.get(ResourceLocation)`.
2. **N1 + N2a** — 핸들 멤버와 청크의 **메서드 수준** 표면(`getChunk`·`getChunkNow`, `LevelChunk.getBlockState/setBlockState`, `getSections()[i]` 의 `hasOnlyAir`·`getStates().get()/copy()`·`getBiomes()` 읽기, `ChunkStatus`, 하이트맵 읽기). WorldEdit·squaremap·Denizen·dynmap 이 이 수준에서 멈춘다. 멤버 목록은 1.21.1 판이 남은 플러그인 기준(Citizens 1.21.3 근사 제외).
3. **N3a** — `Connection.channel`·처리기 이름, `ServerGamePacketListenerImpl.send`, `Packet`·`FriendlyByteBuf`·`StreamCodec` 뼈대, `SynchedEntityData`, 엔티티 추적기(`ChunkMap.entityMap`·`TrackedEntity`), 2개 이상이 쓰는 패킷 15종. 시험: ProtocolLib 5.4.0 실제 jar, H5 Central jar(scoreboard-library·glowingentities·signgui).
4. **N4a 가짜 플레이어** — 여기까지 오면 **WorldEdit 편집**이 첫 편집기 검증점(jar 는 사용자 제공 필요).
5. **N3b** — 패킷 긴 꼬리(25종이 플러그인 하나만 씀)를 생성으로(5장 3).
6. **N2b** — FAWE 식 필드·배열 수준 쓰기와 Paper 패치 표면(`paperConfig`·`moonrise$getEntityLookup`·`wasAccessibleSinceLastSave`). shim `LevelChunkSection` 이 바닐라 배치의 자기 데이터를 진짜로 갖고, 맞춤 지점(`setUnsaved(true)`·주 스레드 뒷일·청크 보내기)에서 바뀐 섹션을 주인 region 스레드에서 들여온다. 맞춤 지점은 FAWE 호출 순서에 기대므로 되는 범위 밖은 H5 원칙대로 던진다. **FAWE 편집 확인은 여기서**(사용자 Paper jar 필요).
7. **N4b 몹 상속**, **N5**.

각 묶음은 H4 식 대조표·기준표(없는 심볼 수가 늘면 실패)를 NMS 판으로 넓혀 진척을 숫자로 본다. 빠진 부류(패킷 라이브러리 위 플러그인·Geyser·SkinsRestorer·FancyNpcs 등, 기억 기준)를 넣어도 N3 무게만 올라가 순서는 같다.

## 5. 열린 질문의 결정

1. **모듈 경계 → 새 모듈 `furnace-compat-nms`(서버에 기댐).** `furnace-compat-paper` 는 지금처럼 `furnace-api` 만 보고, 서버 내부가 필요한 NMS shim(N2·N3a 파이프라인·N4)만 새 모듈에 둔다. N0 의 값 shim 은 어느 쪽이든 되지만 뒤 묶음이 경계를 넘으므로 처음부터 나눈다.
2. **스레드 → 플러그인 스레드는 살아 있는 `ChunkSection` 을 직접 고치지 못한다.** 주 스레드 밖 읽기는 주인 region 이 뜬 사본(`blocksReadCopy`·`biomesReadCopy`), 쓰기는 주인 region 에서 들여오기, 주 스레드 뒷일은 결정 6 의 region 빌리기. `ThreadingDetector` 잠금은 흉내 내지 않는다(Paper 로 판별되면 FAWE `applyLock` 이 아무것도 안 함).
3. **패킷 생성 → 이름·생성자·필드 순서만 생성하고 본문은 Furnace 패킷 인코더에 맡긴다.** 절대 규칙 7(생성 결과물 커밋 금지)과의 관계는 **도미니코 결정** — `docs/user-todo/entries/2026-11-11-nms-adapter.md`. 안전안: 손으로 쓴 중립 스키마(패킷 이름·필드 타입)를 커밋하고, 바닐라 jar 와 맞는지는 커밋하지 않는 로컬 도구로 확인.
4. **Spigot 이름 jar → 기다리지 않는다.** shim 은 Mojang 이름으로 만들고 변환은 로드 때 하므로 N0~N3 은 H7d 검증과 무관하다. 검토가 합친 Spigot 이름 58개를 변환표 시험 점검 목록으로 쓴다.

## 6. 독립 검토 요약 (2026-11-11)

- 측정 표는 그대로 재현됨(137줄 같음). 도구 결함 둘: Spigot 이름 따로 집계(머리말 주장 틀림 → 고침, 합산은 표 머리말에), 와일드카드 import 버림(→ 도구가 세게 고침).
- Citizens·squaremap 1.21.3 근사: 1.21.2 이후 클래스 7개(1.5%), 두 플러그인을 빼도 상위 순서 같음.
- 표본 계통 겹침(FAWE·WorldEdit, Citizens·Denizen) — 숫자에 계통 수도 함께 볼 것.
- 곁가지 의심: `ChunkSection.compact()` 는 `value != AIR` 로, `setBlock`·`loadIndexed` 는 `isAir` 로 세어 동굴 공기만 있는 섹션의 `nonAirCount` 가 바닐라와 다를 수 있다(재현 안 함 → 코디네이터에 넘김).
