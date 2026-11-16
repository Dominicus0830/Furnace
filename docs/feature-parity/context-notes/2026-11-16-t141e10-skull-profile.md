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
