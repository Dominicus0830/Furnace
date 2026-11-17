# NMS shim 누락 점검 (2026-11-17)

배경: FAWE `//regen` 실측에서 `LevelAccessor#registryAccess` 가 shim 에 없어 멈췄다. 같은 종류(shim 에 빠졌거나, 이름은 있는데 인자·반환 타입이 Paper 와 다른 멤버)를 한 번에 찾는 도구를 만들고, 나온 구멍을 우선순위로 정리했다. 특정 플러그인 맞춤이 아니라 "여러 플러그인이 부르는데 없는 것"을 센다.

## 1. 결론

- 점검 도구 `NmsShimAudit`(`./gradlew :furnace-compat-paper:nmsShimAudit -PauditJars=<jar 폴더>`)를 만들었다. 실제 플러그인 jar 가 부르는 NMS·CraftBukkit 심볼 중 shim 에 없는 것을 **서명 다름 / 진짜 없음 / Paper 에도 없음 / 표 모름**으로 가르고 몇 개 플러그인이 만나는지 센다.
- 실제 플러그인 21개(Modrinth 의 1.21.1 빌드)로 돌린 결과, Mojang 이름으로 NMS 를 직접 부르는 4개(FAWE·squaremap·CommandAPI·Geyser)에서 서로 다른 없는 심볼이 **약 260개**(클래스 통째로 138, 멤버 95+10, 상속 거부 1)였다. 나머지 17개는 NMS 를 안 쓰거나(LuckPerms·Chunky·ViaVersion 등) Spigot 이름이라 이번엔 못 셌다(5장).
- **서명 다름은 10건뿐이고 거의 다 더 새 마인크래프트 판용 코드**(`Identifier` 등 1.21.2+)라 1.21.1 구멍이 아니다. 진짜 구멍은 "이름 자체가 없음"이다. 클래스 이름이 같고 반환 타입만 다른 멤버가 1.21.1 에서 발견되지 않은 것은 좋은 소식이지만, 표본이 부르는 것만 본 결과다(Paper jar 전수 대조는 아직 — 5장).
- 명확하고 안전한 것 몇 개를 고쳤다(4장). 나머지는 묶어서 TASK T-211~T-216 으로 로드맵에 적었다(3장).

## 2. 방법과 한계

1. 표본: Modrinth 에서 1.21.1 빌드가 있는 플러그인 21개(FAWE 2.16.2, WorldEdit 7.3.10, squaremap 1.3.2, CommandAPI 12.1.0, Geyser, packetevents, ViaVersion, LuckPerms, Chunky, TAB, DecentHolograms, FancyHolograms, FancyNpcs, dynmap, BlueMap, Multiverse, WorldGuard, CoreProtect, Plan, Skript, PlaceholderAPI). jar 는 저장소에 넣지 않았다.
2. 읽는 법은 서버가 플러그인을 올릴 때 쓰는 `NmsCompatReport` 와 같다(클래스·필드·메서드·생성자·람다·메서드 참조, 다른 버전 모듈 제외). 없는 심볼마다 도구용 수신자로 `{종류, 소유 클래스, 이름, 서술자}` 를 받는다(`NmsCompatReport.analyze` 의 새 인자 — 서버 동작은 그대로).
3. 분류: ① shim 클래스 사슬에 같은 이름의 멤버가 있으면 **서명 다름**(shim 서술자를 같이 적는다) ② 없고 Paper 1.21.1 멤버 이름표(`paper-1.21.1-members.txt.gz`)에 있으면 **없음(Paper 에 있음)** ③ 이름표에 없으면 **Paper 에도 없음**(다른 판·Spigot 난독화 이름 — 구멍 아님) ④ 소유 클래스가 이름표에 없으면 **표 모름**.
4. 한계
   - 정적 분석이라 리플렉션 문자열로만 부르는 멤버는 안 보인다(그쪽은 T-205 의 멤버 이름표 경고가 맡는다).
   - **Spigot 이름 jar 9개(WorldEdit·TAB·dynmap·DecentHolograms·FancyHolograms·FancyNpcs·Multiverse·PlaceholderAPI·Plan)는 건너뛰었다.** Spigot→Mojang 대응표가 필요한데 그 재료(Spigot BuildData)를 받는 호스트가 이 컨테이너에서 Cloudflare 확인에 막혀 받지 못했다(우회하지 않았다). 도구는 대응표 없이 건너뛴다고 표시한다.
   - CommandAPI·Geyser 는 여러 판 코드를 한 jar 에 싣는다(1.21.2+ 이름도 섞임). 그 숫자는 부풀려져 있어 FAWE v1_21_R1·squaremap(mc1.21.1)을 우선 본다.
   - **Paper 의 번역된 서버 jar(`paper-1.21.1.jar`)로 shim 전체를 서술자까지 견주는 모드**(`-PpaperJar=<경로>`)는 만들어 시험했지만(손으로 만든 작은 jar), 실제 jar 로는 못 돌렸다. 그 jar 를 만드는 Paper 실행이 이 컨테이너에서 거부됐다. 사용자 PC 에서 돌리는 것은 T-216 과 `docs/user-todo/entries/2026-11-17-nms-shim-audit.md`.
5. 시험: `NmsShimAuditTest`(분류 4갈래·Spigot 건너뜀·Paper jar 대조), 고친 것은 `NmsMathShimTest`·`NmsLevelMembersTest`. `:furnace-compat-paper:test` 전체 통과.

## 3. 빠진 것 — 우선순위

"플러그인 수"는 표본 4개 중 몇 개가 부르는지다. 자세한 줄은 `build/nms-shim-audit.md`·`.tsv` 로 다시 만든다.

| 순위 | 묶음 | 대표 심볼 | 부르는 곳 | TASK |
|---|---|---|---|---|
| A (P1) | FAWE 나머지 서버 월드 면 | `ServerLevel`/`Level` 의 `getBiomeManager`(2)·`getChunkIfLoadedImmediately`(2)·`captureBlockStates`·`captureTreeGeneration`·`capturedBlockStates`·`addFreshEntity(…SpawnReason)`·`destroyBlock`·`getEntities`·`getShade`·`structureManager`·`getSharedSpawnPos`·`paperConfig`, `Structure.generate`·`ConfiguredFeature.place`·`PlacedFeature.place`·`ChunkGenerator.getBiomeSource`, 클래스 `WorldGenLevel`·`StructureStart`·`BoundingBox`·`LevelEntityGetter`·`Clearable` | FAWE(`//forest`·`//feature`·구조물), squaremap | T-211 |
| B (P1) | 엔티티 쓰기·가짜 플레이어 | `Entity.absMoveTo/discard/load/save/setUUID`, Paper 필드 `generation`/`spawnReason`, `EntityType.create/canSummon/isEnabled`, **`PaperweightFakePlayer extends ServerPlayer`**(정의 거부 1건 — `ServerPlayer` 가 상속 허용이 아님)와 생성자 `(MinecraftServer, ServerLevel, GameProfile, ClientInformation)`, `ItemStack.useOn`·`BlockState.useItemOn`·`InteractionResult(Holder)` | FAWE(`//copy -e`·`//paste -e`·도구) | T-212 |
| C (P2) | 레지스트리·구성 요소 상수 | `BuiltInRegistries.ENTITY_TYPE`·`PARTICLE_TYPE`·`CHUNK_STATUS`·`DATA_COMPONENT_TYPE`, `Registries.ADVANCEMENT`·`LOOT_TABLE`·`RECIPE`, `Registry.getTags`, `DataComponents.CONTAINER`…, `ItemStack.getComponentsPatch`(3)·`DataComponentPatch.entrySet`(2)·`RegistryAccess$Frozen.createSerializationContext`(2) | CommandAPI·Geyser·FAWE | T-213(+기존 T-200) |
| D (P2) | 지도 렌더러 | `WorldBorder.getCenterX/Z/getSize`, `MapItemSavedData` 필드 5개·`Level.getMapData`, `BlockState.getMapColor`, `Biome.getSpecialEffects`·`BiomeManager`, `CropBlock`·`StemBlock`·`StainedGlassBlock`, `DimensionType.hasCeiling`·`LevelResource`·`getWorldPath`, `ChunkHolder.getLatestChunk`·`ServerChunkCache.getChunkFuture`·`ImposterProtoChunk`·`Heightmap.primeHeightmaps`, `CraftBlock.getNMS/getPosition/getCraftWorld` | squaremap, Geyser | T-214 |
| E (P3) | 서버 객체·명령 | `MinecraftServer.getCommands/getAdvancements/getRecipeManager/getFunctions/getWorldData/reloadableRegistries`, `Commands`·`CommandSourceStack`·`commands.arguments.*`(클래스 약 40) | CommandAPI | T-215 |
| 도구 | 정례화 | Paper jar 전수 대조, Spigot 이름 jar 9개 포함, 표본 확대 | — | T-216 |

서명 다름 10건의 정체(1.21.1 구멍 아님): `Identifier`(1.21.2+ 이름) 반환 6건, `RegistryAccess$Frozen.lookupOrThrow` 반환 `Registry`(1.21.2+), Spigot 이름 `CraftWorld.getHandle()→WorldServer`, `CraftPlayer.getPersistentDataContainer`(Paper 판이 반환 타입을 다르게 컴파일한 것으로 보임 — 가정, 전수 대조 때 확인), `FriendlyByteBuf.readBytes(byte[])`(shim 에 없는 오버로드).
"Paper 에도 없음" 8건과 "표 모름" 9건(`WorldBorder`·`CraftBlock`·`LevelStorageSource`·`ChunkGenerator` 가 이름표에 없음)은 이름표를 shim 이 늘 때마다 다시 만들어야 줄어든다(T-216).

## 4. 이번에 고친 것 (모두 바닐라 1.21.1 서버 jar 를 `javap -c` 로 읽어 확인)

| 항목 | 내용 |
|---|---|
| `net.minecraft.util.Mth`(새 클래스) | `sin`·`cos`(65536 칸 표, 배율 10430.378), `sqrt`, `floor`·`ceil`·`lfloor`(음수 처리), `clamp` 4종, `lerp`·`inverseLerp`·`clampedLerp`·`map`·`clampedMap`, `wrapDegrees` 3종, `degreesDifference`, `positiveModulo` 3종, `square` 4종, `frac`, `smoothstep`, `sign`, `equal`, `absMax`, `isPowerOfTwo`, 상수. **함정:** 정수 `clamp` 는 `Math.min(Math.max(v, min), max)`(min>max 면 max) 이고 실수 `clamp` 는 `v<min ? min : Math.min(v,max)`(min>max 면 min)로 서로 다르다 — 바닐라 그대로 둔다. 난수·UUID·색·`atan2` 표·`fastInvSqrt` 등은 쓰는 플러그인이 보이기 전까지 두지 않았다. |
| `net.minecraft.core.QuartPos`(새 클래스) | 블록↔쿼트↔섹션 변환(시프트 둘), 상수 BITS=2·SIZE=4·MASK=3 |
| `ChunkPos.rangeClosed(ChunkPos, ChunkPos / int)` | x 가 먼저 끝까지 가고 끝에 닿으면 z 한 칸·x 시작값으로(뱀 모양 아님), 크기 `|dx|+1 × |dz|+1`, SIZED |
| `Level.random`·`getRandom()` | 월드마다 하나인 `RandomSource.create()`(바닐라 생성자와 같음) |
| `Level.getSeaLevel()` | 상수 63 — 바닐라 1.21.1 은 차원마다 다르지 않다(`ServerLevel` 도 재정의 없음) |
| `Level.dimensionType()` | `dimensionTypeRegistration().value()`. 같은 레벨은 같은 객체가 나오게 `dimensionTypeRegistration` 의 캐시를 `ServerLevel` 에서 `Level` 로 옮겼다(동작 같음) |

주의(검토 지적): `Level.random` 은 바닐라처럼 스레드 안전하지 않은 `LegacyRandomSource` 라 두 스레드가 동시에 쓰면 `IllegalStateException` 이 난다. region 스레드가 여럿인 Furnace 에서는 플러그인이 다른 region 의 `Level.random` 을 동시에 쓰는 일이 없게 해야 하며, 필요하면 후속으로 `threadSafeRandom`(바닐라에도 있는 필드)이나 region 별 난수원을 검토한다(T-211 에 포함). 로드맵 번호 T-211~T-216 은 다른 스레드가 같은 번호를 쓰면 늦게 병합되는 쪽이 조정한다.

효과: FAWE 없는 심볼 61→57, squaremap 52→50. 나머지는 다 아래처럼 서버 쪽 다리나 큰 설계가 필요하다.

고치지 않은 것과 이유: `getSharedSpawnPos`(월드 경계 안 판정 포함 — 서버 월드 데이터 다리 필요), `WorldBorder` 크기·중심(서버 다리 확장), `Entity.discard`·`save`·`load`(엔티티 소유 스레드·NBT 규칙), `BiomeManager`(바이옴 흔들기 난수 규칙), 레지스트리 상수(엔티티 종류 목록 다리). 모두 "값을 지어내면 조용히 틀리는" 종류라 T-211~T-214 에서 Paper 원문 대조와 함께 한다. 멤버를 던지는 빈 껍데기로만 두는 것도 하지 않았다 — 그러면 `catch (NoSuchMethodError)` 기능 감지가 다른 예외를 만나 H5 원칙(없으면 링크 오류)이 깨진다.

## 5. 다음에 할 일

1. 사용자 PC 에서 `-PpaperJar=<paper-1.21.1.jar>` 로 전수 대조(T-216 ①, 사용자 확인 항목 1번).
2. Spigot 이름 jar 9개는 대응표 파일(`plugins/…/spigot-mappings` 캐시가 있는 PC)로 다시.
3. T-211(FAWE 서버 월드 면)부터 순서대로.
