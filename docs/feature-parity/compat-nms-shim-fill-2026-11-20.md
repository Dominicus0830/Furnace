# FAWE 가 부르는 NMS shim 빈 멤버 채우기 (2026-11-20)

앞 단계: `compat-nms-shim-audit-full-run-2026-11-17.md`(FAWE 2.16.2 에서 없는 심볼 77개). 로드맵 T-212(엔티티 쓰기·가짜 플레이어)와 그 둘레.

## 1. 다시 돌린 대조 (기준 `claude/quirky-wright-lra834`, 28942608)

- 도구: `./gradlew :furnace-compat-paper:nmsShimAudit -PauditJars=<FAWE 2.16.2 jar 폴더> -PpaperJar=<paper-1.21.1.jar>`.
- Paper 번역 jar 는 클라우드에서 실행 없이 만들었다: 공유 폴더의 `paper-1.21.1-133.jar`(paperclip) 패치를 Mojang 서버 jar 에 적용하고 해시(020b6831…)를 맞췄다. jar 는 읽기만 했고 저장소에 넣지 않았다.
- 결과: FAWE 심볼 667 중 **없는 것 35**(11-17 엔 77). "서명 다름" 18건은 **0건**이 됐다(그 사이 병합된 T-211 3단계 등). 클래스 통째로 없음 12, 멤버 없음 22, 상속 1.

## 2. 35개 묶음

| 묶음 | 개수 | 심볼 | FAWE 에서 쓰는 곳 | 처리 |
|---|--:|---|---|---|
| 가. 엔티티 저장·지우기·찾기 | 4 | `Entity.save`·`discard`, `ServerLevel.getEntities()`, `LevelEntityGetter` | `//copy -e`·되돌리기 기록(`PaperweightGetBlocks.entities`), 지우기 동기 작업 | 이 문서 3장 |
| 나. 엔티티 만들기·넣기 | 8 | `EntityType.create(Level)`, `Entity.load`·`absMoveTo`·`setUUID`·`generation`·`spawnReason`, `ServerLevel.addFreshEntity(…, SpawnReason)`·`addFreshEntityWithPassengers(…, SpawnReason)` (+ 이미 있지만 막혀 있던 Paper `EntityLookup.addNewEntity`) | `//paste -e`·되돌리기 | 이 문서 4장 |
| 다. 가짜 플레이어 아이템 쓰기 | 8 | `ServerPlayer` 상속·생성자, `InteractionResult`·`InteractionResultHolder`·`ItemInteractionResult`, `ItemStack.useOn`, `Item.use`, `BlockState.useItemOn` | `simulateItemUse`(아이템을 쓰는 브러시·`//apply`) | 뒤 PR |
| 라. 옛 도식 변환·아이템 이름 | 4 | `GsonHelper`, `StringUtil`, `DyeColor`, `ItemStack.getDescriptionId` | 1.13 이전 도식 변환기(`PaperweightDataConverters`), `getRichItemName` | 이 문서 5장 |
| 마. Furnace 에서 실행되지 않음 | 8 | `Util`, `ThreadingDetector`, `BlockableEventLoop`, `WorldData`·`MinecraftServer.getWorldData`, `ServerLevel.paperConfig`, `PersistentEntitySectionManager`, `LevelChunk.setBiome` | 아래 | 구현 안 함 |
| 바. 진행 중인 PR #509(T-200) | 3 | `ItemStack.applyComponents`·`getComponentsPatch`, `RegistryAccess$Frozen.createSerializationContext` | 아이템 NBT 왕복 | #509 |

마 묶음이 실행되지 않는 이유(FAWE 2.16.2 바이트코드로 확인):
- `Util.getMillis`: `MojangWatchdog` 는 `MinecraftServer.nextTickTime` 필드를 리플렉션으로 찾다 못 찾으면 만들지 않는다(감시견 없음 = Paper 의 Spigot 감시견이 없을 때와 같은 갈래). `WatchdogThread` 도 없어 Spigot 쪽도 안 탄다.
- `ThreadingDetector`: `PalettedContainer.threadingDetector` 필드를 리플렉션으로 찾다 못 찾으면 null 로 두고 그 뒤 쓰지 않는다.
- `BlockableEventLoop`·`WorldData`·`getWorldData`: WorldEdit 의 `PaperweightAdapter.regenerate` 안에만 있다. FAWE 는 `PaperweightFaweAdapter.regenerate` → `PaperweightRegen`(T-203)을 쓴다.
- `paperConfig()`: `PaperweightRegen` 이 `Level.paperConfig` 필드가 있을 때만 부른다(Furnace 에는 그 필드가 없다).
- `PersistentEntitySectionManager`: Spigot 전용 갈래. FAWE `PaperSupport.isPaper()` 가 참이라 Paper 갈래(`moonrise$getEntityLookup`)를 탄다.
- `LevelChunk.setBiome`: WorldEdit `PaperweightAdapter.setBiome(Location, …)` 에만 있다. FAWE `BukkitWorld.setBiome` 은 Bukkit `World#setBiome` 을 부른다.

## 3. 가 묶음 (엔티티 저장·지우기·찾기)

- 서버 다리 `ChunkInternals#entitySaveNow`(바닐라 `Entity#save` — 승객·플레이어·저장하지 않는 지움 이유·사라진 엔티티는 null, 아니면 "id" 와 승객까지 든 저장 NBT), `entityDiscardNow`(지움 이유 DISCARDED, 이벤트 없음).
- shim `Entity#save(CompoundTag)`: 받은 묶음에 더하고 참/거짓. `Entity#discard()`: 지운 뒤 그 shim 의 `isRemoved()` 는 참.
- 스레드 규칙 `NmsEntities.call`: 엔티티를 가진 스레드(전역 스레드의 빌림 포함)면 바로, FAWE 작업 스레드처럼 틱 스레드가 아니면 핸들이 아는 자리의 주인 region 에 보내 최대 10초 기다린다(그 사이 엔티티가 다른 region 으로 건너갔으면 엔티티를 따라가는 대기열로 한 번 더). 다른 region·전역 스레드는 교착을 피해 `ThreadOwnershipException`(T-204 `NmsOwner` 와 같은 규칙).
- `ServerLevel#getEntities()` → `LevelEntityGetter` 의 `get(int)`·`get(UUID)` 는 `getEntity(int)`·`getEntity(UUID)` 와 같다. 월드 전체를 도는 `getAll`·상자 찾기는 한 스레드가 모든 region 을 볼 수 없어 두지 않는다. 형식 인자 경계 `EntityAccess` 는 엔티티 shim 이 이미 가진 멤버(`getId`·`getUUID`·`blockPosition`)만 둔다.
- Paper 와 다른 점(알고 둔 것):
  - **저장 NBT 의 키:** Paper 의 `save` 는 Bukkit·Spigot·Paper 키(`WorldUUIDLeast/Most`, `Bukkit.updateLevel`, `Spigot.ticksLived`, `Paper.Origin`, `Paper.SpawnReason` 등)를 더한다. Furnace 는 서버가 월드 파일에 쓰는 꼴(바닐라 + `BukkitValues`)을 그대로 준다. 결정 항목은 `docs/user-todo/entries/2026-11-20-nms-shim-fill.md`.
  - **청크와 함께 내려간 엔티티:** Paper 에서는 객체가 남아 마지막 상태를 저장하지만, Furnace 핸들은 사라진 엔티티의 객체를 들지 않아 거짓을 준다(지운 엔티티의 거짓은 Paper 와 같다).
  - **플레이어 discard:** Paper 는 플레이어 엔티티를 지워 버리지만 Furnace 는 `UnsupportedNmsCall`(FAWE 는 플레이어를 지우지 않는다).
- 시험 `NmsEntityWriteTest`: 주인 스레드 저장 = 서버 저장 꼴(/data + id), 받은 묶음에 더하기, 승객은 거짓·탈것이 승객까지 저장, 작업 스레드는 주인을 기다려 같은 값, 전역 스레드(빌림 밖)는 기다리지 않음, discard 뒤 UUID 찾기 null·저장 거짓·두 번 지워도 무사, 플레이어.

## 4. 나 묶음 (엔티티 만들기·월드에 넣기)

- FAWE 붙여 넣기(`PaperweightGetBlocks` 동기 작업): `EntityType.byString(id).create(level)` → `load(tag)`(UUID 키는 지운 것) → `absMoveTo(x, y, z, yaw, pitch)` → `setUUID(원래 UUID)` → 엔티티 이벤트를 켰으면 `addFreshEntity(e, CUSTOM)`, 껐으면 `spawnReason = CUSTOM`·`generation = false` 뒤 `moonrise$getEntityLookup().addNewEntity(e)`. WorldEdit `createEntity` 는 `addFreshEntityWithPassengers(e, CUSTOM)`.
- **만들기를 넣기까지 미룬다:** Furnace 서버 엔티티는 넣을 region 의 레벨에 묶여 만들어지고(엔티티 번호·난수가 그 레벨에서 나온다), `EntityType#create` 때는 자리를 모른다. 그래서 `create` 는 "아직 월드에 없는" shim 을 주고, `load`·`absMoveTo`·`setUUID` 를 기록해 두었다가 넣을 때 그 자리의 주인 region 에서 서버 엔티티를 만들어 **같은 차례로 재생**한다(서버 다리 `ChunkInternals#addNmsEntityNow`). 재생하는 코드는 서버의 바닐라 `load`·`absMoveTo`·`setUUID` 그대로라 결과가 같다.
  - 아직 월드에 없는 shim 의 게터: 종류·레벨·자리·방향(바닐라 생성자처럼 0,0,0 에서 시작해 `load`·`absMoveTo` 를 서버와 같은 자르기로 따라간다)·지움 표시는 답한다. UUID 는 `setUUID` 나 "UUID" 든 `load` 뒤에만 답한다(바닐라의 무작위 UUID 는 넣을 때 정해진다 — 그 전에 물으면 `UnsupportedNmsCall`). `getId`·`getBukkitEntity`·`save` 도 넣은 뒤에만 된다.
  - 넣으면 그 shim 이 그 엔티티의 `CraftEntity#getHandle()` 이 된다(Paper 는 그 객체 자체). 살아 있는 종류는 `LivingEntity` shim(Bukkit 종류의 `isAlive`), 플레이어·낚시찌는 `create` 가 null(바닐라 공장이 null).
- `addFreshEntity(e, reason)` = Paper `ServerLevel#addEntity`: `generation` 을 거짓으로, `spawnReason` 이 비었으면 reason, 넣기 전에 지웠으면 거짓, 빈 아이템 엔티티는 거짓, 스폰 이벤트(취소되면 거짓), 같은 UUID 가 이미 있으면 거짓. 이미 월드에 있는 엔티티는 Paper 처럼 "Attempted Double World add" 오류 기록만 남기고 참.
  - 스폰 이벤트를 취소했거나 처리기가 엔티티를 지웠으면 Paper(`CraftEventFactory#doEntityAddEventCalling`)처럼 그 shim 을 지운 것으로 둔다(`isRemoved()` 참, 다시 넣으면 거짓). 같은 UUID 로 거절된 것은 지우지 않는다(서버 `ChunkLevel#addFreshEntityResult` 가 둘을 가른다).
- `EntityLookup#addNewEntity(e)` = 이벤트 없이 넣기(서버 `ChunkLevel#addFreshEntityWithoutEvent`). 스폰 이유·generation 은 건드리지 않는다. 이미 월드에 있으면 거짓.
- `addFreshEntityWithPassengers`: `create` 로 만든 엔티티는 승객이 없다(바닐라 `load` 도 승객을 읽지 않는다 — 그건 `EntityType.loadEntityRecursive`) → `addFreshEntity` 하나.
- 월드의 엔티티에 `load`·`absMoveTo`·`setUUID` 는 `UnsupportedNmsCall` — 살아 있는 엔티티의 UUID·자리를 패킷·색인 없이 바꾸게 된다. Bukkit API 나 Furnace `Entity#editNbt` 를 쓴다.
- Paper 필드 `spawnReason` 은 월드의 엔티티 shim 에서 읽으면 null 이다(채워 두지 않는다 — Bukkit `getEntitySpawnReason` 을 쓴다).
- Paper 와 다른 점(알고 둔 것):
  - **로드되지 않은 청크에 넣기:** Furnace 는 거짓(넣지 않음). Paper(moonrise)는 그 자리에 엔티티 조각을 만들어 넣는다. FAWE 는 붙여 넣는 청크를 먼저 읽으므로 실제 차이는 거의 없다.
  - **같은 UUID 일 때 스폰 이벤트:** Paper 는 이벤트를 먼저 내고 그 뒤 UUID 겹침으로 거절한다. Furnace 서버는 UUID 를 먼저 잡고 겹치면 이벤트 없이 거절한다(서버의 모든 스폰과 같은 차례).
  - **넣기 전 해시:** 아직 월드에 없는 shim 의 `hashCode` 는 객체 해시이고, 넣은 뒤에는 UUID 해시다(바닐라는 엔티티 번호). 넣기 전에 해시 집합에 둔 shim 은 넣은 뒤 그 집합에서 못 찾는다. FAWE 는 그렇게 쓰지 않는다.
- 스레드: 넣기는 그 자리의 주인 region(전역 스레드의 빌림 포함 — FAWE 동기 작업)에서 바로, 틱 스레드가 아닌 스레드는 기다린다(`NmsOwner`). 기록하는 조작은 한 스레드에서 한다(FAWE 는 만들기부터 넣기까지 한 동기 작업 안).
- 시험 `NmsEntityCreateTest`: 만들기 꼴(살아 있는 종류·null 종류·게터), 이벤트 없는 붙여 넣기(자리·방향·UUID·이름·스폰 이유 CUSTOM·getHandle 이 같은 shim·같은 UUID 다시 넣기 거짓), `addFreshEntity` 이벤트·취소(취소 뒤 지움)·두 번 넣기, 같은 UUID 거절(지우지 않음), 넣기 전 지우기, 빈 아이템 엔티티, WorldEdit `addFreshEntityWithPassengers`, 작업 스레드에서 넣기(이벤트 있음·없음), 전역 스레드의 빌림 구간에서 넣기, 월드 엔티티 고치기 거절. `NmsEntityWriteTest` 에 다른 region 으로 건너간 엔티티를 작업 스레드에서 저장하기(엔티티를 따라가는 갈래)를 더했다.

## 5. 라 묶음 (옛 도식 변환·아이템 이름)

- `GsonHelper.fromJson`·`fromNullableJson`(문자열·Reader, Class·TypeToken): 바닐라처럼 리더에 lenient 를 걸고 Gson 의 그 타입 어댑터로 읽는다. IO 오류는 `JsonParseException` 으로 감싸고, `fromJson` 은 결과가 null 이면 "JSON data was null or empty". FAWE 는 1.13 이전 도식의 책·표지판 글을 자기 Gson 으로 읽을 때 쓴다. 호환 모듈은 gson 을 `compileOnly` 로만 둔다(서버 jar 가 싣고 있다).
- `StringUtil.isNullOrEmpty`: null 이거나 길이 0(공백 문자열은 거짓 — `StringUtils.isEmpty`).
- `DyeColor`: 16색의 번호·이름·천 색(불투명 알파)·지도 색·폭죽 색·글 색을 jar 값 그대로. `byId`(범위 밖 흰색)·`byName(이름, 기본)`·`byFireworkColor`. 코덱 상수는 두지 않았다. FAWE 는 옛 침대 아이템 변환에서 `RED.getId()` 를 쓴다.
- `ItemStack.getDescriptionId` 는 #509 에서 이미 들어왔지만 바닐라와 달랐다(블록 아이템도 `item.` 키를 주었다 — 돌이 `item.minecraft.stone`). 고쳤다:
  - `Item#getDescriptionId()`: 블록 아이템(BlockItem·AirItem)은 블록 키 `block.…`, 그 밖은 `item.…`. 1.21.1 `Items` 등록을 jar 로 대조하면 블록 아이템의 블록 이름은 늘 아이템 이름과 같고, 블록과 이름이 같은데 블록 키가 아닌 것은 밀(그냥 Item)과 네더 사마귀(ItemNameBlockItem)뿐이다. 가루눈 양동이(SolidBucketItem)는 아이템 키. 아이템 1333개 모두 이 규칙의 키가 en_us 에 있다.
  - `Item#getDescriptionId(ItemStack)`·`ItemStack#getDescriptionId()`: 자석 나침반(`lodestone_tracker` 있는 나침반 → `item.minecraft.lodestone_compass`), 물약·투척·잔류 물약·물약 화살(키 + `.effect.` + 물약 이름, 물약이 없으면 `empty` — 물약 46가지의 기본 이름은 경로에서 `long_`·`strong_` 을 뗀 것), 바탕색 있는 방패(키 + `.` + 색). `getHoverName` 의 기본 이름도 이 키를 쓴다.
  - 서버 `RecipeManager.descriptionId`(`/give` 메시지·석재 절단기 정렬)도 같은 규칙으로 고쳤다: 밀이 `[Wheat Crops]` 로 보이던 것.
- 시험 `NmsLegacyHelpersTest`(Gson 읽기·lenient·null, 빈 문자열, 염료 16색, 아이템·스택 번역 키), 서버 `ItemCommandsTest` 에 밀·네더 사마귀.

## 6. 다 묶음 설계 (가짜 플레이어 아이템 쓰기)

결정 S2(도미니코): Paper 처럼 이벤트도 보낸다. 그래서 FAWE 의 가짜 플레이어도 Paper 처럼 **서버에 플레이어 객체가 있고 Bukkit `Player` 로 보이는** 것으로 만든다.

### 6.1 FAWE 가 하는 일 (`PaperweightAdapter#simulateItemUse`, 2.16.2)

1. 월드마다 하나 `new PaperweightFakePlayer(level)` → `ServerPlayer(server, level, 프로필 "[WorldEdit]"(UUID = nameUUIDFromBytes("worldedit")), ClientInformation)`. 재정의: `position()`(원점), `tick`·`die`·`openMenu`·`updateOptions`·`displayClientMessage`·`awardStat` 둘·`openTextEdit` 는 아무것도 안 함, `isInvulnerableTo` 참.
2. `setItemInHand(MAIN_HAND, 사본)` → `absMoveTo(x, y, z, yaw, pitch)`(누르는 면을 바라보게).
3. `stack.useOn(new UseOnContext(fake, MAIN_HAND, BlockHitResult(블록 아래 모서리, 면, 자리, false)))` → SUCCESS 가 아니면 `level.getBlockState(pos).useItemOn(stack, level, fake, MAIN_HAND, hit).consumesAction()` → 아니면 `stack.getItem().use(level, fake, MAIN_HAND).getResult()`. SUCCESS 면 참.

### 6.2 Paper 에서 생기는 일 (javap 로 확인한 것과 남은 가정)

- 생성자: 공용 스폰 자리·각도에 서고, 게임 모드는 서버 기본(생존). 통계·발전 과제 객체를 `PlayerList` 에서 받는다(그 UUID 의 파일을 읽고, 저장 때 쓴다). 플레이어 목록·월드에는 넣지 않는다. `getBukkitEntity()` 는 그 객체를 감싼 `CraftPlayer`(`isOnline()` 거짓, op 는 ops 목록 기준).
- `ItemStack#useOn`(Paper): 블록 상태를 모았다가 놓인 블록이 있으면 `BlockPlaceEvent`/`BlockMultiPlaceEvent`(플레이어 = 가짜의 `CraftPlayer`)를 부르고, 취소되면 되돌린다. 뼛가루로 나무가 자라면 `StructureGrowEvent`. → 보호 플러그인이 "[WorldEdit]" 을 보고 막을 수 있다.
- 바닐라 판정(아이템별 `useOn`·블록 `useItemOn`·`Item#use`)은 그 객체의 손·자리·방향·게임 모드로 돈다.

### 6.3 Furnace 설계

- **서버 "월드에 없는 플레이어"(`Player` 의 detached 모드):** 연결(channel) 없음, 접속자 목록·region·월드 명단에 없음, 처음부터 `isDisconnected()` 참(→ API `isOnline()` 거짓 = Paper). 패킷 보내기는 버린다. 파일(플레이어 데이터·통계·발전 과제)을 읽지도 쓰지도 않는다. 자리·방향은 이 서버 플레이어가 든다(호환 `absMoveTo` → 다리 `moveDetachedPlayer`, 어느 region 에도 없는 객체라 부른 스레드가 바로 쓴다 — Bukkit `getLocation` 도 같은 값). 손은 아이템 쓰기 다리에 부를 때마다 넘긴다. 플러그인에게는 보통 `PlayerHandle` 로 보인다 → 이벤트(`BlockPlaceEvent` 등)의 플레이어 칸에 그대로 들어가고, Bukkit 쪽에는 `CraftPlayer` 로 이어진다.
- **다리(`ChunkInternals`):** `detachedPlayer(world, uuid, name, 속성)` → API 플레이어 핸들(어느 스레드나 — region 상태를 건드리지 않는다), `moveDetachedPlayer(핸들, 자리·방향)`. `useItemNow(핸들, 쓰기 종류, 손, 두 손 스택, 자리·방향, 맞은 자리·면·점)` → 결과 + 바뀐 두 손. 쓰기 종류는 바닐라 세 갈래 그대로: `ItemStack#useOn`(서버 `BlockPlacement.useOn`), `BlockState#useItemOn`(서버 블록 동작 `useItemOn`), `Item#use`(서버 `ItemUse.use`). 맞은 칸의 주인 region 에서 돈다(전역 빌림 포함, 작업 스레드는 기다림 — `NmsOwner`). 행위자는 서버 `PlayerActor`(그 detached 플레이어 + 넘겨 받은 손) 이고, `allowPlace` 는 접속한 플레이어와 같은 `gameEvents().blockPlace(…)` 를 부른다.
- **호환 계층:**
  - `ServerPlayer` 에 `@NmsInheritable` 과 생성자 `(MinecraftServer, ServerLevel, GameProfile, ClientInformation)`. 생성자가 다리로 detached 플레이어를 만들고 그 `CraftPlayer` 를 이 객체에 묶는다(`getBukkitEntity().getHandle()` 이 그 하위 클래스 객체 = Paper). `Compat.player` 는 detached 핸들을 약한 참조 표로 따로 들고 접속 끊긴 플레이어 정리에서 빼, 같은 `CraftPlayer` 를 준다.
  - 손(`getItemInHand`·`setItemInHand`)은 그 shim 이 가진다. 서버로는 부를 때마다 넘기고, 돌아온 손을 shim 에 다시 쓴다. `isRemoved()` 는 거짓(Paper 와 같다), `connection` 은 null.
  - `InteractionResult`(1.21.1 값 7개와 `consumesAction`·`shouldSwing`·`shouldAwardStats`·`sidedSuccess`), `ItemInteractionResult`, `InteractionResultHolder`, `UseOnContext(Player, …)`(손의 스택을 읽는다), `ItemStack#useOn`, `Item#use`, `BlockState#useItemOn`.
  - 접속한 플레이어의 shim 으로 부르면(플러그인이 진짜 플레이어로 아이템을 쓰게 함) 이 단계에서는 `UnsupportedNmsCall` — 손이 인벤토리 칸이라 인벤토리 줄 차례를 지켜야 해서 따로 설계한다.
- **Paper 와 다른 점(알고 둔 것):** detached 플레이어는 통계·발전 과제를 들지 않는다. Paper 는 가짜 플레이어도 기준(예: 씨앗 심기)을 채우면 발전 과제를 주고 채팅에 알리며(`announceAdvancements`) `PlayerAdvancementDoneEvent` 를 부르고 그 UUID 의 파일을 쓴다. 이 차이는 결정 S3.

### 6.4 나눠 올릴 PR

1. 서버 detached 플레이어 + 다리 `detachedPlayerNow` + `ServerPlayer` 상속·생성자·`CraftPlayer` 연결(시험: 만들기, Bukkit 쪽 모양, 월드·목록에 없음, 패킷 없음).
2. 상호작용 결과 형식 + `useOn`·`useItemOn`·`use` 다리 + 이벤트(시험: FAWE `simulateItemUse` 흐름 — 뼛가루, 블록 놓기, `BlockPlaceEvent` 의 플레이어 이름·취소, 들고 쓰는 아이템).
