# NMS shim 전수 대조 실행 결과 (2026-11-17, 도미니코 PC)

`compat-nms-shim-audit-2026-11-17.md` 의 5장·T-216 이 남겨 둔 "Paper 번역 jar 로 shim 전체 견주기"를 처음 실제 jar 로 돌렸다. 날것의 도구 출력은 `compat-nms-shim-audit-full-run-2026-11-17-raw.md`(UTF-8, 150KB)에 있다.

## 1. 실행 조건

- 기준: 브랜치 `claude/quirky-wright-lra834` 최신(PR #489·#490·#491·#492 병합 뒤)을 작업 브랜치에 합쳐서 돌렸다.
- Paper: 공식 `paper-1.21.1-133.jar` 를 받아 `-Dpaperclip.patchonly=true` 로 한 번 실행해 `versions/1.21.1/paper-1.21.1.jar`(번역 jar)를 만들었다. 서버 본체는 켜지 않았고 jar 는 읽기만 했다(저장소에 넣지 않음).
- 명령: `./gradlew --offline :furnace-compat-paper:nmsShimAudit -PskipOracle=true -PauditJars=<…/furnace/plugins/bukkit-plugins> -PpaperJar=<paper-1.21.1.jar>` (55초).
- **플러그인 표본이 4개뿐이다.** 이 PC 에 있는 jar 는 FAWE 2.16.2·LuckPerms·ProtocolLib·Vault 뿐이다. 지난 번 21개 표본(squaremap·CommandAPI·Geyser 등)은 클라우드 컨테이너에서 받은 것이라 이 PC 에는 없고, 이번엔 다시 받지 않았다. 그래서 "플러그인이 부르는 것" 쪽 숫자는 사실상 FAWE 하나다. Mojang 이름을 쓰는 것은 FAWE 뿐이고(LuckPerms·ProtocolLib 은 NMS 직접 호출 0, Vault 는 Spigot 이름·0).

## 2. 플러그인이 부르는 것 중 shim 에 없는 것 (FAWE, 심볼 655 중 77)

| 종류 | 건수 |
|---|--:|
| 클래스 통째로 없음 | 18 |
| shim 클래스를 상속(정의 거부) | 1 (`PaperweightFakePlayer extends ServerPlayer`) |
| 멤버 — 없음(Paper 에 있음) | 37 |
| 멤버 — 서명 다름 | 18 |
| 멤버 — 없음(Paper 에도 없음) | 2 |
| 멤버 — 표 모름 | 1 |

- 클래스 없음 대표: `Util`·`GsonHelper`·`StringUtil`·`InteractionResult(Holder)`·`ItemInteractionResult`·`DyeColor`·`WorldGenLevel`·`StructureStart`·`BoundingBox`·`WorldData`·`LevelEntityGetter`·`PersistentEntitySectionManager`·`BlockableEventLoop`·`CraftBlockState`·`BlockStateListPopulator`.
- 멤버 없음 대표: `ServerLevel` 의 `captureBlockStates`·`captureTreeGeneration`·`capturedBlockStates`·`addFreshEntity(…SpawnReason)`·`destroyBlock`·`getBiomeManager`·`getChunkIfLoadedImmediately`·`getEntities`·`getShade`·`getStructureManager`·`structureManager`·`paperConfig`·`enabledFeatures`, `Entity.generation`·`spawnReason`, `MinecraftServer.getWorldData`, `ServerChunkCache.randomState`, `ServerPlayer.<init>(…ClientInformation)`. 지난 보고서의 A 묶음(T-211 계열)과 같은 목록이고 새 종류는 없다.
- **이상 신호 — "서명 다름" 18건 중 도구가 찍은 shim 서술자가 플러그인이 필요로 하는 서술자와 글자 그대로 같다**(예: `ListTag.iterator ()Ljava/util/Iterator;`, `ServerLevel.getSectionsCount ()I`, `LevelAccessor.registryAccess ()Lnet/minecraft/core/RegistryAccess;`). 이름·서술자가 같은데도 "없다"고 나온 것이다. 원인 후보는 (가) 멤버가 소유 클래스가 아니라 상위 인터페이스(`LevelHeightAccessor` 등 default 메서드)에만 있어 서버의 `NmsCompatReport` 가 소유 클래스 기준으로만 찾아 놓치는 것, (나) 서술자 한 줄은 같지만 접근 제한자·static 여부가 다른 것이다. 이번엔 원인을 파지 않았다(범위 밖). 실제로 링크가 끊기는지는 FAWE 를 켜 봐야 알 수 있다. 후속 확인 필요.

## 3. Paper 전체 대조 (이번에 처음 가능해진 부분)

- Paper 1.21.1 의 NMS·CraftBukkit 클래스 **7139개** 중 shim 에 있는 것 **946개**, 없는 것 **6193개**.
- shim 에 있는 클래스의 Paper 공개·보호 멤버: **서명 다름 570건**(244개 클래스), **없음 9881건**.
- 서명 다름이 많은 클래스: `FriendlyByteBuf` 81(대부분 netty `ByteBuf` 반환 타입을 shim 이 `ByteBuf` 로 두고 Paper 는 `FriendlyByteBuf` 로 좁힌 것 — 반환 타입이 다르면 컴파일된 플러그인의 링크가 끊긴다), `CraftWorld` 43, `CraftPlayer` 38, `CraftServer` 23, `CraftEntity` 19, `Level` 11, `CraftBlockData` 10, `CraftHumanEntity` 10, `CraftMinecartCommand` 10.
- 없는 멤버가 가장 많은 클래스(플러그인이 쓴 구멍이 있는 것 우선): `Entity`(461 메서드 + 91 필드, 쓴 것 7), `ServerLevel`(143+9, 6), `ItemStack`(91+11, 4), `MinecraftServer`(182+39, 1), `ServerPlayer`(174+41, 1). 나머지 상위는 `LivingEntity`·`CraftPlayer`·`Player`·`CraftServer`·`CraftWorld`·`Level` 순.
- 없는 클래스가 몰린 패키지: `world.level.block` 288, `util.datafix.fixes` 208, `craftbukkit.block.impl` 173, `world.item` 118, `ai.behavior` 99, `advancements.critereon` 95. 대부분 데이터 수정·블록 구현·AI 로 플러그인이 직접 부를 일이 드문 영역이다.
- 해석 주의: "Paper 에 있는데 shim 에 없는" 총량은 크지만(약 1만) 플러그인이 실제 쓴 것은 그중 극히 일부다(표본 FAWE 기준 약 57). 전수 목록은 "빠진 것 전부 채운다"가 아니라 새 플러그인을 붙일 때 어느 클래스가 이미 많이 비었는지 보는 지도로 쓰는 것이 맞다.

## 4. 후속 제안

1. "서명 다름이지만 서술자가 같은" 18건의 원인 확인(소유 클래스 vs 상위 인터페이스 조회, 위 2장). 도구 쪽 오탐이면 도구를 고치고, 실제 링크 끊김이면 shim 을 고친다.
2. `FriendlyByteBuf` 의 netty 반환 타입 81건은 일괄 처리 후보(오버라이드로 좁은 반환 타입을 되돌려 주기). 실제 쓰는 플러그인(ProtocolLib 등)이 켜질 때 확인.
3. 표본을 다시 21개로 넓히려면 squaremap·CommandAPI·Geyser jar 를 이 PC 에 받아 같은 명령에 `-PauditJars` 를 늘리면 된다. 이번엔 받지 않았다.
