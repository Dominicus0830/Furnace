# 스폰 정책·엔티티 수·자세·마을 API (작업 묶음 E3, 2026-11-07)

불만 표 23행(스폰 정책), 24행(자세·`setNoPhysics`), 25행(청크별·종류별 엔티티 수), 26행(주민·마을·습격). API 는 `furnace-api` 의 `org.domi.furnace.api.world.{SpawnPolicy,SpawnFilter,SpawnCandidate,EntityCounts,PointOfInterest,PoiOccupancy,RaidInfo,RaidStatus}`,
`org.domi.furnace.api.entity.{Pose,Villager,GossipKind,ReputationEvent}` 와 `World`·`Chunk`·`Entity` 의 새 메서드다. 서버 구현은 `furnace-server/.../plugin/{SpawnPolicyImpl,EntityTally,WorldHandle,ChunkHandle,EntityHandle}.java`,
`entity/spawn/{SpawnConfig,SpawnPolicyState,WorldSpawnPolicy,NaturalSpawner}.java`.

## 1. 원칙

- **기본은 바닐라 그대로(D3).** 정책을 걸지 않으면 스폰 루프의 상한은 바닐라 상수(`MobCategory.max`)이고 필터 목록은 비어 있어 바닐라 경로에 늘어난 일은 "목록이 비었는지" 한 번 보는 것뿐이다. 활성 범위·몹 수를 지우는 일은 하지 않는다 — 정책은 "새로 스폰할지" 만 정한다.
- **버전 중립.** 종류는 `SpawnCategory`(키 문자열), 엔티티 종류·POI 종류는 레지스트리 키(`minecraft:zombie`, `minecraft:home`), 자세·소문 종류는 키가 있는 enum 이다. 내부 클래스(`MobCategory`·`PoiType`·`Raid`·`GossipContainer`)는 노출하지 않는다.
- **스레드.** 읽고 쓰는 일은 그 칸·청크·엔티티를 가진 region 스레드에서 실행되고 Future 로 끝을 알린다(기존 `World`·`Entity` 규칙). 스폰 정책 객체만 아무 스레드에서 바꾸고 다음 틱부터 반영된다.

## 2. 스폰 정책 (`World.spawnPolicy()`)

```java
SpawnPolicy sp = world.spawnPolicy();
sp.setCategoryCap(SpawnCategory.MONSTER, 40);            // 월드 상한: 청크 289 칸당 40마리 (바닐라 70)
sp.setPlayerCap(SpawnCategory.CREATURE, 6);              // 모든 플레이어의 플레이어별 상한 (바닐라 10)
sp.setPlayerCap(vipUuid, SpawnCategory.MONSTER, 20);     // 그 사람만
var off = sp.addFilter(plugin, c -> !c.type().equals("minecraft:creeper") || c.y() > 0);   // 조건 술어
off.unsubscribe();
```

- **상한 두 가지는 바닐라가 쓰는 그대로다.** 월드 상한: 종류별 `상한 × 스폰 대상 청크 수 ÷ 289` 보다 이미 적어야 스폰한다. 플레이어별 상한: 그 청크 중심 128칸 안 플레이어(관전자 제외) 중 하나라도 그 종류 몹 수가 상한보다 적어야 스폰한다(바닐라 `LocalMobCapCalculator`). **0** 이면 그 종류는(플레이어별이면 그 플레이어 근처는) 새로 스폰되지 않는다. 범위 0..10000, MISC(`SpawnCategory.MISC`)는 자연 스폰이 없어 거부(`IllegalArgumentException`).
- **해석 순서**: 플레이어별 값 > 월드별 값(이 객체) > `furnace.properties` > 바닐라 상수. `reset*` 은 한 칸씩 지우고, 전부 지우면 정책이 아무것도 안 건 상태(공유 불변 칸)로 돌아온다.
- **저장하지 않는다.** 플러그인이 켜질 때마다 건다. 월드 상한 계산은 region 마다 그 region 의 청크·엔티티만 본다(`NaturalSpawner` 헤더에 적은 기존 차이 — region 경계 근처에서만 바닐라와 다르다).
- **필터** `SpawnFilter.allow(SpawnCandidate)`: 바닐라 조건(바이옴 목록·자리·빛·거리·에너지 예산)을 다 통과하고 **몹을 만들기 직전**에 묻는다. false 면 그 칸의 이번 시도는 바닐라 조건에 걸린 것처럼 `continue` — 몹을 만들지 않고 난수도 더 쓰지 않는다. 후보: 종류·종류 묶음·차원·칸·바이옴·가장 가까운 플레이어. 여러 개면 모두 true 여야 한다.
  - region 틱 스레드에서 스폰 시도마다 여러 region 에서 동시에 불린다 — 가볍고 스레드에 안전하게. 예외를 던지면 로그 한 번 남기고 빠지고, 주인 플러그인이 꺼져도 다음 호출에서 빠진다(같은 틱 안에서는 다시 부르지 않는다).
  - 적용 범위는 **자연 스폰 루프**(`NaturalSpawner.spawnForChunk`)뿐이다. 청크 생성 때 동물, 팬텀·순찰·고양이·마을 포위·떠돌이 상인 특수 스포너, 스포너 블록, 알·명령은 거치지 않는다 — 그 경로는 `CreatureSpawnEvent` 로 막는다.
- **설정 키(`furnace.properties`, 모든 월드 바탕값)**: `spawn.limit.<종류>`(월드 상한), `spawn.player-limit.<종류>`(플레이어별 상한). `<종류>` = `monster creature ambient axolotls underground_water_creature water_creature water_ambient`. 기본은 키가 없으면 바닐라 상수.
  시작 로그 경고: 값이 바닐라의 **3배를 넘으면**(스폰·틱 비용), **0**(그 종류가 스폰 안 됨), 모르는 `spawn.*` 키(오타). 숫자가 아니거나 0..10000 밖이면 기동 실패(`IllegalArgumentException`).

## 3. spawn-animals / spawn-npcs = false — 바닐라 대조 (도미니코 OK 조건)

바닐라 1.21.1 바이트코드와 대조했다(`tools/vanilla-data/server-1.21.1`, 난독화 이름으로 추적):
- `DedicatedServer.isSpawningAnimals()` = `spawn-animals && super()`(super 는 늘 true), `areNpcsEnabled()` = `spawn-npcs && super()`, `isSpawningMonsters()` = `spawn-monsters && difficulty != PEACEFUL`. `MinecraftServer.updateMobSpawningFlags` 가 `ServerLevel.setSpawnSettings(monsters, animals)` 로 전달.
- `ServerLevel.shouldDiscardEntity(e)`: **동물이 꺼졌고 `Animal`·`WaterAnimal` 이면 true**, 그 밖에는 **npc 가 꺼졌고 `Npc` 이면 true**(`Npc` 구현은 `AbstractVillager` 하나 = 주민·떠돌이 상인). 엔티티 틱 목록을 도는 중에 지운다(틱하는 청크의 엔티티만).
- `ServerChunkCache.tickChunks`: 청크마다 `doMobSpawning && (spawnEnemies || spawnFriendlies) && 월드 경계 안` 이면 `spawnForChunk(level, chunk, state, spawnFriendlies, spawnEnemies, 400틱 주기)`, 끝나고 `doMobSpawning` 이면 `tickCustomSpawners(spawnEnemies, spawnFriendlies)`. 특수 스포너: 팬텀·순찰·마을 포위 = `spawnEnemies`, 고양이 = `spawnFriendlies`, 떠돌이 상인은 둘 다 안 본다(다만 npc 가 꺼지면 위 규칙으로 지워진다).
- Furnace 는 같은 인자로 같은 곳에서 부른다(`RegionLevel.tickBlocks`, `ChunkLevel.tickEntities`/`shouldDiscardEntity`). **차이 없음.** 시험: `SpawnDiscardTest` — 서버가 만들 수 있는 모든 엔티티 종류(100종 넘게)로 `shouldDiscardEntity` 가 바닐라 클래스 계통(`Animal`·`WaterAnimal`·`AbstractVillager`)과 같은 결과인지, `SpawnPolicySpawnTest.spawnAnimalsFalseFlagSpawnsNoAnimalsButMonstersStillSpawn`(spawn 루프에 친화 종류 끔/몬스터 끔).
- 알려 둘 점(바닐라와 같다): 지우는 일은 **틱을 도는 엔티티만** 대상이라 시뮬레이션 거리 밖에 저장된 동물·주민은 그 청크가 틱할 때 지워진다. 명령 `/summon` 으로 만든 동물도 다음 틱에 지워진다.

## 4. 자세·물리 (`Entity.pose()` / `setPose` / `noPhysics()` / `setNoPhysics`)

- `Pose` 는 바닐라 `Pose` 와 같은 번호 순서(STANDING … INHALING). `pose()`·`noPhysics()` 는 핸들을 만든 시점 값이고 `setPose`·`setNoPhysics` 는 엔티티를 가진 region 스레드에서 실행해 `Boolean`(사라졌으면 false)으로 끝난다.
- **몹**: `setPose` 는 데이터 동기화 값만 바꿔 보는 플레이어 화면에 반영된다(판정 상자는 서버가 바꾸지 않는다). 워든·개구리·브리즈처럼 AI 가 자기 자세를 다시 정하는 몹은 곧 덮인다(바닐라와 같다). `null` 은 서 있는 자세.
- **플레이어**: `setPose(pose)` 는 서버 계산값(웅크림·수영·잠·겉날개) 위에 자세를 **고정**해 다른 플레이어가 보는 모습만 바꾼다(자기 화면·판정 상자는 그대로, Paper 의 `setPose(pose, fixed=true)` 와 같은 뜻). `null` 로 푼다. 서버가 계산한 값은 `Player.computedPose()`.
- `setNoPhysics(true)`: 바닐라 `Entity.noPhysics` — `move` 가 충돌 검사 없이 속도대로 옮긴다(중력은 그대로라 바닥을 뚫고 떨어진다). 저장되지 않는다. 아이템처럼 틱마다 스스로 정하는 엔티티는 다음 틱에 덮인다. 플레이어는 클라이언트가 움직임을 정하므로 `UnsupportedOperationException`.

## 5. 엔티티 수 (`Chunk.entityCounts()`, `World.entityCounts()`)

- `EntityCounts(total, byType, byCategory)`: 종류 키별·`SpawnCategory` 별(플레이어는 `minecraft:player` 로 byType 에만). 지워진 엔티티와 끊긴 플레이어는 세지 않는다. 불변.
- 청크: region 의 엔티티 색인(`EntityStore` 구역별 목록, 청크 열 하나)을 읽을 뿐 핸들을 만들지 않아 `Chunk.entities()` 보다 싸다. 로드되지 않은 청크는 `EntityCounts.EMPTY`.
- 월드: region 마다 자기 엔티티를 세어 합친다(`forEachRegion`) — 서로 다른 틱 순간의 값이 섞일 수 있다. 로드된 청크 전체.

## 6. 주민·마을·POI·습격

- **`Villager`**(주민 핸들, `Merchant` + `Mob`): `profession()`(`minecraft:farmer`, 없으면 `minecraft:none`)·`professionLevel()` 은 핸들 시점, 평판은 소유 region 에서 `reputation(uuid)`(= 종류별 값 × 가중치 합, 바닐라 `getPlayerReputation`), `gossip(uuid)`(종류별 값), `addGossip`·`clearGossip`·`applyReputationEvent`(바닐라 `onReputationEvent` 와 같은 값: 치료 큰 긍정 20 + 작은 긍정 25, 거래 2, 다침 작은 부정 25, 죽임 큰 부정 25). 값은 종류 최댓값으로 잘리고 2 미만이면 지워진다(바닐라 `GossipContainer`). 집·직업 블록·모임 장소는 두뇌 기억이다(`MobAi.brain()`, E2).
- **마을**: `World.isVillage(pos)`(= 점유된 마을 POI 가 든 구역에서 구역 1칸 이내), `sectionsToVillage(pos)`(0..6, 더 멀면 7). 바닐라에는 "마을 객체" 가 없다 — POI 와 점유가 마을이다(`entity/spawn` 의 마을 중심 판정 문서 참고).
- **POI**: `World.pointsOfInterest(center, radius, types, occupancy)` — 구 모양 거리 안, 가까운 순, 종류 키 집합(비면 전부, 네임스페이스 생략 가능)·`PoiOccupancy`(HAS_SPACE/IS_OCCUPIED/ANY). `PointOfInterest(type, pos, freeTickets, maxTickets)`.
- **습격**: `World.raids()`(올라와 있는 활성 습격, 번호 순), `raidAt(pos)`(바닐라 96칸 안 가장 가까운 활성 습격), `stopRaid(id)`(바닐라 `Raid.stop`). `RaidInfo`: 번호·차원·중심·상태·시작 여부·징조 단계·나온 파도·전체 파도(보너스 포함)·살아 있는 습격자·체력 합·남은 대기 틱·파도 사이·마을의 영웅.
- **한계(바닐라와 다른 점)**: POI·습격은 region 마다 올라온 청크만 본다 — 다른 region 청크의 POI 는 조회에서 빠지고(`PoiManager` 헤더에 적은 기존 차이), 중심 청크가 내려가 쉬는 습격은 `raids()` 에 안 나온다. 습격을 새로 만드는 API 는 넣지 않았다(나쁜 징조 효과나 `/effect` 로 시작한다).

## 7. 시험

- `entity/spawn/SpawnPolicyStateTest`(8): 기본 = 바닐라·무비용, 해석 순서, 검증, 플레이어별 상한(0 포함), 월드 상한 비례, 필터 정리, 설정 파싱·경고·오류.
- `entity/SpawnPolicySpawnTest`(6): 실제 `spawnForChunk` 에 걸기 — 상한 0·낮은 상한·플레이어 상한 0·필터로 종류 고르기·spawn-animals/monsters 끄는 flags.
- `plugin/EntityPolicyApiTest`(8, region 스레드): 정책 손잡이, 필터 해제, 청크·월드 엔티티 수, 자세·noPhysics, 플레이어 자세 고정, 주민 평판, POI·마을, 습격 읽기·멈추기. `world/NoPhysicsRegionTest`: 켠 소는 바닥을 뚫고 보통 소는 선다. `block/SpawnDiscardTest`: 위 3장.
