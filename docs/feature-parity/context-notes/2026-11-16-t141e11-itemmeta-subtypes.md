# T-141 E11 — ItemMeta 하위 형(MetaKind)·ItemFactory·가죽/머리/현수막 메타 (2026-11-16)

원문 기준: Paper 1.21.1 `CraftItemFactory`·`CraftItemMetas`(재료→메타 표)·`CraftMetaItem`·`CraftMetaSkull`·`CraftMetaBanner`·`CraftMetaLeatherArmor`·`SerializableMeta`
(Youer 1.21.1 사본 + Paper `33ff3dff` 패치 대조, 동작 비교용으로만 읽음). 조각: **PR1 = 기반(MetaKind·ItemFactory·가죽 메타·직렬화), PR2 = SkullMeta·BannerMeta·`Player#setPlayerProfile`.**

## 설계 (한 쪽)
**문제.** 호환 계층의 `ItemMeta` 구현이 하나(`CompatItemMeta`)뿐이라 `SkullMeta`·`BannerMeta`·`LeatherArmorMeta` 가 없고, `Bukkit.getItemFactory()` 도 없었다.
플러그인은 `(SkullMeta) stack.getItemMeta()` 로 받고, Paper 로 저장한 설정(`meta-type: SKULL` …)을 읽는다.

**핵심 아이디어: 종류별 값을 메타 필드로 두지 않고 컴포넌트 사본 위에서 읽고 쓴다.**
`CompatItemMeta` 는 이미 아이템의 모든 컴포넌트를 `work` 로 들고 있다(Paper 는 종류별 필드 + `unhandled` 로 나눠 든다). 그래서 하위 형은 상태 없는 얇은 껍데기다.
- 가죽 색 = `minecraft:dyed_color`, 머리 주인 = `minecraft:profile`, 음표 소리 = `minecraft:note_block_sound`, 현수막 무늬 = `minecraft:banner_patterns`.
- 장점: 종류를 바꿔 옮기거나(`asMetaFor`) 되읽어도 값이 새지 않고, `clone`·`equals`·`hashCode`·`applyTo` 가 공통 코드 하나로 끝난다. 불변 ItemStack 규칙은 그대로 — 메타는 사본이고 `setItemMeta` 가 새 스택을 슬롯에 넣는다.
- `MetaKind` 열거형이 (아이템 종류 → 메타 종류) 표, 종류가 맡는 컴포넌트(`handledComponents`), 만드는 법, `meta-type` 이름을 든다. 새 종류는 열거형 상수 하나 + 하위 클래스 하나.

**Paper 와 같게 한 동작**
- `getItemMeta()`: 재료에 맞는 종류. 공기는 `null`(이전에는 빈 메타). 아이템이 아닌 재료는 일반 메타.
- `setItemMeta(meta)`: null·빈 메타 → 컴포넌트 모두 삭제(applicable 검사 전). 붙일 수 없는 종류(머리 메타를 돌에)면 **아무것도 바꾸지 않고 `false`**. 일반 메타는 어떤 아이템에도 붙고 종류에 맞게 옮겨진다.
  Furnace 가 만들지 않은 메타는 `IllegalArgumentException`.
- `asMetaFor`: 같은 종류면 같은 객체, 일반 메타로 옮기면 종류별 컴포넌트를 버림, 일반→종류별은 공통·나머지 컴포넌트를 모두 가져감(Paper 의 `unhandled` 가 같은 컴포넌트를 들고 간다).
- 직렬화: `meta-type` 이 종류 이름(`LEATHER_ARMOR` …). 종류별 키는 Paper 키(`color`)로, 그 컴포넌트는 `unhandled` 에 또 넣지 않는다. 가죽은 기본 가죽 색(0xA06540)과 같은 색은 적지 않는다(Paper `hasColor(meta)`).
  되읽기: 아직 없는 종류(`BOOK` 등)는 이전처럼 일반 메타 + `unhandled` 로 산다.
- `equals`: 컴포넌트·표시 항목·영속 데이터가 같으면 같다(종류가 달라도 — Paper 도 한쪽의 종류별 값이 비면 종류가 달라도 같다). 빈 메타는 `null` 과 같다.

**일부러 다른 점**
- Paper 는 메타 구현 클래스가 `CraftMeta*`(패키지 전용)지만 Furnace 는 `CompatItemMeta` 하위 클래스다. 플러그인이 구현 클래스 이름에 기대면 어긋난다(그런 코드는 거의 없다).
- 가죽 투구·갑옷·바지·신발·늑대 갑옷의 Paper 형은 `ColorableArmorMeta`(= `ArmorMeta` + `LeatherArmorMeta`)인데 방어구 장식(`ArmorTrim`·`TrimMaterial`·`TrimPattern`)이 아직 없어 `LeatherArmorMeta` 하나로 준다. `(LeatherArmorMeta)` 캐스트·색 읽기/쓰기는 그대로 되고,
  `ArmorMeta`(장식) 를 넣을 때 `MetaKind.COLORABLE_ARMOR` 와 금속 방어구용 `ARMOR` 를 더한다. 그전엔 금속 방어구의 `trim` 컴포넌트는 일반 메타의 `unhandled` 로 산다.
- `ItemFactory`: `getItemMeta`·`isApplicable`×2·`equals`·`asMetaFor`×2·`getDefaultLeatherColor`·`getSpawnEgg`·`ensureServerConversions` 는 동작한다.
  `createItemStack(String)`·`enchantItem`×3·`enchantWithLevels`·`getI18NDisplayName` 은 `UnsupportedFeature`(이 계층에 아이템 문자열 파서·서버 마법 부여·번역표가 없다). Adventure·Bungee 내용물을 쓰는 메서드(`asHoverEvent` 등)와 `RegistryKeySet` 판은 해당 형이 없어 인터페이스에 없다.
- 이전에는 `ItemStack(Material.AIR).getItemMeta()` 가 빈 메타였지만 Paper 는 `null` 이다(위).

## PR2 계획 (SkullMeta·BannerMeta)
- `SkullMeta`: `profile`(E10 의 `ProfileValue` 로 NBT 읽기·쓰기)·`note_block_sound`. `setOwner(name)` 은 접속 중인 플레이어면 그 프로필, 아니면 UUID 0 + 이름(Paper). 길이 16 초과 false. `setOwningPlayer(CraftPlayer)` 는 프로필 사본.
  직렬화 키 `skull-owner`(Paper `PlayerProfile` 객체 또는 옛 문자열 이름)·`note_block_sound`.
- `BannerMeta`: `banner_patterns` `[{pattern, color}]`, 읽을 때 20겹까지·인라인 무늬는 건너뜀(Paper), 빈 목록은 컴포넌트를 쓰지 않음. 키 `patterns`.
- `Player#setPlayerProfile`: E10 의 `PlayerProfile` 재사용.
- Paper `applyToItem` 은 이름만 있는 머리(텍스처 없음)를 적용한 뒤 `update()` 로 비동기로 스킨을 채워 그 스택에 다시 쓴다(SPIGOT-6558). 결정 항목 참고(user-todo entries).
