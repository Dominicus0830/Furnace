# 늑대 스폰 NPE·청크 가장자리 바이옴·FAWE 가 부르는 빠진 Bukkit API (2026-11-14)

계기: 사용자 latest.log(빌드 e19110ca, FAWE 2.16.2·ProtocolLib·LuckPerms·Vault).

## 1. 늑대 NPE 의 원인 — 게임플레이 바이옴 조회가 이웃 청크에서 null

- 로그: `[SPAWN] 청크 생성 동물 스폰 실패 ChunkPos[x=4, z=-17]` ← `Wolf.vanillaVariantForBiome` 의 `switch (null)`.
- `ChunkLevel.biomeAt` = 바닐라 `Level.getBiome(pos)`: 확대 지터(BiomeZoom)는 `블록-2` 의 quart 와 그 +1 중에서 고르므로 청크 가장자리
  두 칸(x&15 ∈ {0,1,14,15} 등)에서는 **이웃 청크의 quart** 를 고를 수 있다. 예전 구현은 그 청크를 지금 스레드가 갖지 않으면(다른 region·로드 안 됨) null.
  청크 4,-17 은 region(shift 4) 남쪽 끝 줄이라 이웃이 다른 region 이었다.
- 바닐라 `LevelReader.getNoiseBiome`: 청크가 있으면(BIOMES 이상) 저장 바이옴, 없으면 `getUncachedNoiseBiome`(생성기 바이옴 소스, 높이 안 자름).
  청크 생성 스폰은 WorldGenRegion 이라 반경 1 이웃의 저장 바이옴을 읽는다 — 새로 생성된 이웃이면 생성기 바이옴과 같다.
- 고침(`ServerCore.lootWorldFor.biomeAt`): 그 quart 청크를 이 스레드가 갖고 로드돼 있으면 저장 바이옴, 아니면 생성기 바이옴(`ServerCore.generatorBiome` —
  노이즈 월드는 `NoiseWorldSource.biomeAt`, 평지·디버그는 고정 바이옴). **남은 차이(가정)**: 이웃 청크가 다른 region 에 로드돼 있고 생성 뒤 바이옴이 바뀐 경우
  (/fillbiome·플러그인 setBiome·옛 버전 청크)만 다르다. 다른 region 청크를 읽는 안전한 길은 N2b 일이라 여기서는 쓰지 않았다.
- 같은 뿌리의 둘째 결함: `Region.biomeIdAtQuart` 가 quart 높이를 죄지 않아 세계 밖 quart 면 -1. `spawnMobsForChunkGeneration` 은 (minX, **319**, minZ) 바이옴을
  보는데 지터가 quart y 80(=y 320)을 고르면 null → 그 청크 동물 스폰을 통째로 건너뜀. 사용자 시드(-8273459608338939987) 1만 청크 중 1691개(17%)가 y 80 을 고르고,
  7571개(76%)가 이웃 청크 quart 를 고른다(이웃이 이 스레드에 없으면 예전엔 역시 건너뜀). 바닐라 `ChunkAccess.getNoiseBiome` 처럼 죄도록 고쳤다
  (`noiseBiomeIdAtBlock` 은 이미 죄고 있었다).
- 영향 받던 곳(모두 같은 조회): 늑대 변종, 여우(눈 여우), 개구리 변종, 주민·좀비 주민 종류, 눈골렘 녹음, 날씨(강수), 풀 뼛가루 꽃, 전리품 location_check 바이옴,
  청크 생성 스폰. `Wolf` 는 바이옴을 모를 때(데이터 팩 없는 시험 판)도 죽지 않게 null 을 pale 로 본다.
- 시험: `BiomeAtChunkEdgeTest` 3개(기준 e19110ca 에서 3개 모두 실패 확인) — 이웃 미로드 → 생성기 바이옴과 늑대 woods, y 80 죄기, 노이즈 월드 가장자리 = 생성기.

## 2. FAWE 가 부른 빠진 Bukkit API

Paper 1.21.1 원문(Youer 1.21.1 사본, Paper 패치 표식 포함)과 대조해 넣었다.
- `org.bukkit.Effect` 95 상수(번호·데이터 종류·@Deprecated 표시 Paper 와 같음 — `getById` 는 Paper 처럼 사용 중단 상수를 뺀다), `CraftEffect.getDataValue`(분기·예외 문구 같음),
  `World#playEffect` 4종(64칸 기본, `(int) 거리² ≤ 반경²`), `Player#playEffect` 2종(그 사람에게만). 패킷은 `GenericPacket(level_event)` — 필드 type/pos/data/global_event.
  Furnace BlockFace 는 6면+SELF 뿐이라 SMOKE 대각선 분기는 없다(상수가 생기면 0 으로).
- `Material.isLegacy()`(늘 false — Furnace 에는 LEGACY_ 상수가 없고 Paper 도 지금 상수는 false), `LEGACY_PREFIX`, `isRecord()`(1.21.1 jukebox_playable = MUSIC_DISC_* 전부).
  `getMaterial/matchMaterial(name, legacyName)` 는 **넣지 않았다** — Paper 는 옛 이름을 지금 상수로 바꿔 주는데 Furnace 는 옛 표가 없어 null 이 되면 조용히 다른 답이다.
- `Player#isSneaking`(바닐라 isShiftKeyDown)·`isSprinting`: furnace-api `Player.isSneaking()/isSprinting()` 을 새로 두고(서버 Player 의 volatile 필드) 호환이 읽는다.
  `Entity#isSneaking` 기본 false(몹은 이 플래그를 켜지 않는다). setSneaking/setSprinting 은 소유 스레드 일이라 T-141 로.

## 3. FAWE-Bukkit 2.16.2 jar 가 부르지만 아직 없는 것(52개, T-141 입력)

방법: Central `com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.2` 의 상수 풀(Methodref·InterfaceMethodref·Fieldref·Class)에서 org.bukkit·io.papermc·com.destroystokyo
참조를 뽑아 호환 모듈 클래스 파일(javap -p -s, 상위 형식 따라감)과 이름+서술자로 대조. 이 PR 전 59 → 뒤 52.
- 쓸 가능성 큰 것: `Player.sendBlockChange(Location, BlockData)`·`getAllowFlight`·`setFlying`·`sendTitle(5인자)`·`spigot()`, `World.getSpawnLocation`·`getEntities`·`getWorldFolder`·
  `refreshChunk`·`getChunkAtAsync(II Z[Z])`·`dropItem(Naturally)`·`generateTree`, `Block.getState(boolean)`·`getDrops`·`breakNaturally`·`getLightLevel`, `BlockData.createBlockState`,
  `Material.isSolid/isTransparent/isBurnable`(어댑터 없이 블록 재료를 Bukkit 으로 읽을 때 — 지금은 BlockTypesCache 가 먼저 죽는다, N2b), `EntityType.isAlive`, `Chunk.getEntities`.
- 그 밖: 날씨(set/hasStorm·Thunder·Duration), `Server.shutdown`·`getDatapackManager`, `Bukkit.getCurrentTick`, `WorldCreator`, `TreeType`, `BiomeProvider`, `Chest`, `Tameable`, 엔티티 인터페이스 다수(Boat·Minecart·ItemFrame·Painting 등, instanceof 용).
