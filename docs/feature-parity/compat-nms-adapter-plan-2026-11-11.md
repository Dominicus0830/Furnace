# 범용 NMS 어댑터 계획 (결정 B, 2026-11-11) — 초안

> 상태: **초안**. 우선순위 판단·측정 검증은 독립 검토(Fable)에 맡겼다. 결과가 오면 6장을 고친다.

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
3. **청크 내부는 C 로 두기엔 너무 널리 쓴다.** `ChunkStatus`(5), `ServerChunkCache`(5), `LevelChunk`·`ChunkAccess`(4), `PalettedContainer`·`LevelChunkSection`(3): 맵(dynmap·squaremap), 편집기(FAWE·WorldEdit), 스크립트(Denizen), NPC(Citizens).
   다행히 Furnace 청크 저장이 바닐라와 같은 모양이다 — `ChunkSection` 이 블록 16³·바이옴 4³ 팔레트, YZX 인덱스, 비공기 수·무작위 틱 수를 가진다(`furnace-server/.../world/ChunkSection.java`). 그래서 "보기(view)" shim 으로 정확하게 만들 수 있는 여지가 있다.
4. **패킷은 6개 소스 + 리플렉션 라이브러리 전부가 쓴다.** 게임 패킷 클래스 46종(소스), `Connection`·`FriendlyByteBuf`·`SynchedEntityData`, Netty 파이프라인(ProtocolLib·packetevents·ViaVersion·TAB·DecentHolograms). ProtocolLib 는 다른 수많은 플러그인의 바탕이라 파급이 가장 크다.
5. **상속이 생각보다 흔하다.** 가짜 플레이어 `extends ServerPlayer` — FAWE·WorldEdit·Citizens·Denizen(4). 연결 처리기 `extends ServerGamePacketListenerImpl` — Citizens·Denizen. 몹 상속은 Citizens 가 264개 클래스(모든 몹 종류), Denizen 11개.
6. **라이브러리 클래스도 표면이다.** authlib `GameProfile`(7)·`Property`(4), DataFixerUpper `Codec`·`DynamicOps`·`Pair`(5~6), Brigadier. 서버가 이 라이브러리를 싣지 않으면 NMS shim 이 있어도 클래스 로드에서 죽는다(결정 5·H4 묶음 B 와 이어짐).

## 3. 묶음 (범용 표면 단위)

| 묶음 | 표면 | 쓰는 플러그인(소스 10개 중) | 지금 | 핵심 어려움 |
|---|---|---|---|---|
| **N0 블록 상태·레지스트리 뼈대** | `BlockState`·`Block`·`Blocks`·`StateDefinition`·`Property` 계열, `Holder`·`HolderSet`·`Registry`·`BuiltInRegistries`·`Registries`·`ResourceKey`, `CraftBlockData`·`CraftMagicNumbers`, `SectionPos`·`InteractionHand`·`FluidState` | 7~8 | `ResourceLocation`·좌표만 있음 | 상태 id(약 2.6만) ↔ 객체 하나 표. raw id 숫자를 늘리지 않고 `BlockRegistry`·`BlockStateKey` 위에 얹기(절대 규칙 6) |
| **N1 서버·월드·엔티티 핸들 깊이** | `ServerLevel`·`ServerPlayer`·`MinecraftServer`·`Entity`·`LivingEntity`·`EntityType` 의 자주 쓰는 멤버(블록 읽기·쓰기, 청크 얻기, 연결, Bukkit 엔티티 얻기), CraftBukkit 엔티티 클래스 나무(134개, 종류별 `CraftZombie` 등 — 생성 파일로), `DamageSource` | 8~10 | 클래스는 있으나 멤버가 적음 | 소유 스레드(H5 4.3). 엔티티 종류별 Craft 클래스는 `EntityType` 표에서 생성 |
| **N2 청크 내부(C→B)** | `ServerChunkCache`·`ChunkMap`(보이는 청크 얻기), `ChunkAccess`·`LevelChunk`·`LevelChunkSection`·`PalettedContainer`·팔레트 종류·`ChunkStatus`, 바이옴 컨테이너, 하이트맵, 빛 `DataLayer` | 4~6 | 없음 | ① shim 이 서버 청크의 **보기**여야 한다(복사본이면 쓰기가 안 닿는다) → 호환 모듈이 서버 내부에 닿는 경로가 필요(모듈 경계 결정) ② 리플렉션이 **비공개 필드 이름**으로 들어온다(FAWE: `PalettedContainer.data`·`LevelChunkSection.tickingBlockCount` 등) → shim 이 같은 이름 필드를 진짜로 가져야 한다 ③ FAWE 는 섹션을 **자기 스레드에서** 고친다 → region 스레드 규칙과 정면 충돌(결정 6 의 region 빌리기로 풀 수 있는지) |
| **N3 패킷·네트워크** | 게임 패킷 클래스(값 + 바닐라 직렬화), `Packet`·`FriendlyByteBuf`·`StreamCodec`·`ProtocolInfo`, `SynchedEntityData`·`EntityDataAccessor`, `Connection.channel`·Netty 처리기 이름(`packet_handler`·`encoder`·`decoder`), `ServerGamePacketListenerImpl.send`, `ServerConnectionListener`(주입 지점) | 6 + 리플렉션 라이브러리 전부 | 래퍼 34종 + shim 11개 | 패킷마다 손으로 감싸면 범용이 아니다 → **바닐라 프로토콜 정의에서 패킷 클래스·코덱을 생성**하는 길을 검토. ProtocolLib 는 `ProtocolInfo`·`IdDispatchCodec` 로 패킷 표를 읽는다 |
| **N4 상속 가능한 서버 객체** | 가짜 `ServerPlayer`(연결 없는 생성자), `ServerGamePacketListenerImpl` 상속, 몹 상속(`extends Zombie` 등)과 AI(`Goal`·`PathNavigation`·`MoveControl`) | 4(가짜 플레이어) / 2(몹) | 정의 때 거부 | 가짜 플레이어는 "문맥 객체"로만 쓰여 비교적 쉽다. 몹 상속은 서버가 플러그인 클래스의 가상 메서드를 불러야 해서 Furnace 엔티티 구조에 훅이 필요 — 가장 깊다 |
| **N5 기타** | 스코어보드(`world.scores`), 명령(Brigadier·`CommandSourceStack`), 통계, 메뉴(`AbstractContainerMenu`), 데이터 팩·월드젠 내부 | 2~4 | 일부 | 항목별 |
| **N-lib 라이브러리** | authlib, DataFixerUpper(`Codec`·`DynamicOps`·`Dynamic`), Brigadier | 5~7 | 없음 | 결정 5(필요할 때 내려받기) 의 대상에 넣는다. 라이선스·배포 확인 |

## 4. 제안 순서 (초안 — Fable 판단 대기)

1. **N0 + N-lib(authlib·DFU)** — 모든 묶음의 바탕. 시험: 상태 id ↔ `BlockState` 왕복 전부, `Block.defaultBlockState`·`Property` 값 바꾸기가 바닐라 표와 같음, `BuiltInRegistries.BLOCK.get(ResourceLocation)`.
2. **N1** — 핸들 멤버. 시험: 합성 플러그인으로 `((CraftWorld) w).getHandle().getBlockState/setBlock` 이 Furnace 블록 API 와 같은 결과, 소유 스레드 밖 호출은 규칙대로.
3. **N3** — 파급이 가장 크다(ProtocolLib 기반 플러그인 전부). 시험: ProtocolLib 5.4.0 실제 jar 기동·패킷 듣기, H5 표본(scoreboard-library·glowingentities·signgui) 기동 시험.
4. **N2** — 맵·편집기. 시험: FAWE(사용자 Paper jar 가 있으면 실제 어댑터)·squaremap 형 읽기 합성 플러그인, 섹션 쓰기 뒤 클라이언트에 보이는 블록이 같음(봇 시험).
5. **N4 가짜 플레이어** → **N4 몹 상속** → **N5**.

각 묶음은 H4 식 대조표·기준표(없는 심볼 수가 늘면 실패)를 NMS 판으로 넓혀 진척을 숫자로 본다. FAWE 는 N0·N1·N2·N4(가짜 플레이어)가 모두 끝나야 편집이 된다 — **FAWE 편집은 4번째 단계 끝에서야 확인된다.**

## 5. 열린 질문 (판단 필요)

- **모듈 경계**: N2 는 서버 청크의 보기여야 한다. 지금 `furnace-compat-paper` 는 `furnace-api` 만 본다. ① `Internals` 다리에 청크 섹션 보기 API 를 더하거나 ② NMS shim 만 서버에 기대는 새 모듈(H5 4.1 의 `furnace-compat-nms`)을 두거나.
- **스레드**: FAWE·WorldEdit 은 Paper 에서 주 스레드 밖에서 청크를 읽고(FAWE 는 쓰기도) 잠금(`PalettedContainer` 의 `ThreadingDetector`)으로 버틴다. Furnace 에서 이것을 region 빌리기로 받을지, 소유 스레드로 넘기는 대기열로 받을지.
- **패킷 생성**: 46종을 손으로 쓸지, 바닐라 패킷 정의를 기계로 읽어 생성할지(생성 도구는 바닐라 jar 를 읽되 결과물만 커밋 — 절대 규칙 7 확인 필요).
- **Spigot 이름 jar**: dynmap·DecentHolograms·TAB 의 1.21.1 모듈은 Spigot 이름이다. H7d 변환표(사용자 첫 실행 검증 대기)가 맞아야 이 묶음들이 그 플러그인들에 닿는다.

## 6. 검토 결과 (대기)

(Fable 판단·검증이 오면 채운다.)
