# T-141 E10 — Skull·PlayerProfile·BlockFace (2026-11-16)

원문 기준: Paper 1.21.1 `CraftSkull`·`CraftPlayerProfile`(Spigot·Paper 두 클래스)·`CraftPlayerTextures`, Spigot `Skull`.

## 함정·결정
- **프로필 클래스가 둘**: Spigot `org.bukkit.craftbukkit.profile.CraftPlayerProfile`(final)과 `com.destroystokyo.paper.profile.CraftPlayerProfile` 둘 다 직렬화 별칭 `PlayerProfile` 이고 Paper 쪽이 나중에 등록돼 이긴다. 같은 순서로 `ConfigurationSerialization` 에 등록.
- **authlib 없음**: 호환 계층 컴파일 경로에 authlib 가 없어 프로필 로직은 `ProfileValue`(id·이름·속성) 위에 짠다. `getGameProfile`·`buildGameProfile` 은 없다.
- **비우기**: 바닐라 `load` 는 없는 키의 옛 값을 지우지 않는다. Paper `CraftSkull.applyTo` 는 사람 머리일 때 프로필을 명시적으로 쓴다 → 사본에 `profile` 이 없으면 서버 `SkullBlockEntity.beforeSnapshotLoad` 가 옛 프로필을 지운다(`/data merge` 동작은 그대로).
- **오프라인 이름 캐시**: 바닐라 `GameProfileCache.get` 은 이름을 소문자로 바꿔 오프라인 프로필을 만든다(시험도 소문자 기대).
- **textures**: Base64 JSON(`SKIN.url`·`metadata.model=slim`·`CAPE.url`), `< > & = '` 는 `\u00xx` 로 이스케이프, URL 은 `https://textures.minecraft.net/texture/…` 만. 서명은 Mojang `profilePropertyKeys`(SHA1withRSA).
- **Mojang 조회**: `session/minecraft/profile/<id>?unsigned=false`, `minecraft/profile/lookup/name/<이름>`. 오류·204·404·429 는 빈 결과. 인증(온라인 모드) 서버에서만.
- **BlockFace**: Paper 순서 19개. `CraftEffect` SMOKE 의 switch 는 `default -> 0` 로.
- 가정: `setRotation` 이 잘못된 면에 던지는 예외 종류, `validateSkullProfile` 의 조건 해석은 Paper 원문 사본 없이 기억·대조로 맞췄다.

시험: `BukkitSkullTest`·`BukkitProfileOnlineTest`(가짜 Mojang HTTP)·`ProfileFetchTest`·`ServicesKeysPropertyKeysTest`.

## 독립 검토 반영 (같은 날)
- **프록시 뒤 서버(R1)**: Paper 의 기본 플래그는 `usesAuthentication`(온라인 모드 또는 BungeeCord·Velocity). Furnace 는 `GameProfileCache.profileLookups`(FurnaceMain 에서 `online-mode || bungeecord || velocity-secret`)와 `Furnace#profileLookups()` 로 따로 둔다 — `usesAuthentication` 은 오프라인 프로필 만들기·명령 쪽에 영향이 있어 건드리지 않는다. 서버 쪽 `fetchProfile*` 은 부르면 묻고(게이트 없음), 기본값을 정하는 것은 호환 계층이다. `fetchProfileByName` 은 접속자·캐시(`peek`)만 먼저 보고 오프라인 프로필을 만들어 넣지 않는다(프록시 뒤 오프라인 서버도 모르는 이름은 Mojang 에 묻는다).
- **SMOKE(R2)**: DOWN·NORTH_EAST·NORTH_WEST·SOUTH_EAST·SOUTH_WEST·SELF 는 0, 보조 16방위 8개(19 − 기본 6 − 대각 4 − SELF 1)는 `Bad smoke direction!`. 검토 글의 "12개"는 8개가 맞다.
- **스레드(권장)**: Spigot형·Paper형 `update()` 가 공용 `ProfileExecutor`(고정 4개, 유휴 30초 뒤 정리)를 쓴다.
- 시험: `dataMergeKeepsTheOwner`(/data merge 가 profile 을 지우지 않음), `BukkitProfileOnlineTest`(오프라인+프록시 조회), SMOKE 보조 방향.
- 후속 후보: 오프라인 모드 `profileByName` 이 새 이름마다 usercache.json 을 동기 저장(이전부터), 429 debug 로그.
