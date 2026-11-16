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
- 가죽 방어구는 Paper 와 같이 둘로 나눈다: 투구·갑옷·바지·신발·늑대 갑옷 = `COLORABLE_ARMOR`(Paper `ColorableArmorMeta`), 가죽 말 갑옷 = `LEATHER_ARMOR`(Paper `LeatherArmorMeta`, 이 메타는 가죽 방어구에도 붙고 반대는 안 붙는다). 둘 다 `LeatherArmorMeta` 구현 하나(`CompatMetaLeatherArmor`)다.
  `ColorableArmorMeta` 의 `ArmorMeta` 쪽(방어구 장식 `ArmorTrim`·`TrimMaterial`·`TrimPattern`)이 아직 없어 `ArmorMeta`·`ColorableArmorMeta` 인터페이스는 없다 — 색 읽기·쓰기·직렬화 이름은 Paper 와 같다. 장식을 넣을 때 `MetaKind.ARMOR`(금속 방어구)를 더하고 `COLORABLE_ARMOR` 에 `ArmorMeta` 를 붙인다.
  그전엔 금속 방어구의 `trim` 컴포넌트는 일반 메타의 `unhandled` 로 산다.
  Paper 는 말 갑옷 메타를 방어구에 옮길 때(`asMetaFor`) 색을 잃는데, 같은 `dyed_color` 컴포넌트라 Furnace 는 색을 남긴다(잃을 이유가 없는 차이 하나).
- `ItemFactory`: `getItemMeta`·`isApplicable`×2·`equals`·`asMetaFor`×2·`getDefaultLeatherColor`·`getSpawnEgg`·`ensureServerConversions` 는 동작한다.
  `createItemStack(String)`·`enchantItem`×3·`enchantWithLevels`·`getI18NDisplayName` 은 `UnsupportedFeature`(이 계층에 아이템 문자열 파서·서버 마법 부여·번역표가 없다). Adventure·Bungee 내용물을 쓰는 메서드(`asHoverEvent` 등)와 `RegistryKeySet` 판은 해당 형이 없어 인터페이스에 없다.
- 이전에는 `ItemStack(Material.AIR).getItemMeta()` 가 빈 메타였지만 Paper 는 `null` 이다(위).

## PR2 (E11b) — SkullMeta·BannerMeta (구현됨)
- `SkullMeta`: 소유자는 `minecraft:profile`(E10 의 `ProfileValue` 로 NBT 읽기·쓰기), 음표 소리는 `minecraft:note_block_sound`. `setOwner(name)` 은 접속 중인 플레이어면 그 프로필, 아니면 UUID 0 + 이름(Paper — 블록 `CraftSkull.setOwner` 와 달리 사용자 캐시를 보지 않는다).
  길이 16 초과 false, `setOwningPlayer`·`setPlayerProfile`·`setOwnerProfile`(이름·UUID+텍스처 검사, 이 계층이 만든 프로필이 아니면 지움). 직렬화 `skull-owner`(Paper `PlayerProfile` 객체, 옛 문자열 이름도 읽음)·`note_block_sound`.
- `BannerMeta`: `banner_patterns` `[{pattern, color}]`(블록 상태와 같은 `BannerLayers` 로 읽고 쓴다). 읽을 때 앞의 20겹·인라인 무늬는 건너뛰고, 메타를 만들 때 한 번 맞춰 두어 적용하면 읽을 수 있던 겹만 남는다(Paper 와 같다).
  **함정:** 현수막은 빈 무늬 목록이 아이템 기본값이라 무늬를 비울 때 "지움 표시"(`!banner_patterns`)가 남으면 같은 현수막끼리 쌓이지 않는다 → `clearComponent` 로 값도 표시도 지워 기본값으로 되돌린다(가죽 색도 같은 방식).
  잘못된 겹(null·null 색·등록 안 된 무늬)은 Paper 가 적용할 때 던지는 것을 Furnace 는 그 겹을 넣는 순간 `IllegalArgumentException("Banner pattern N …")` 으로 던진다(블록 상태와 같은 메시지).
- **머리 아이템 스킨 자동 채우기는 이 PR 에 없다.** Paper 는 이름만 있는 머리를 적용한 뒤 `update()` 로 비동기로 스킨을 채워 그 스택에 다시 쓴다(SPIGOT-6558). 그런데 Furnace 에는 바닐라 `PlayerHeadItem.verifyComponentsAfterLoad`
  (`/give` 나 아이템 로드 때 불완전한 프로필을 비동기로 확인해 같은 스택에 다시 쓰는 것)에 해당하는 서버 기능도 아직 없다 — 메타 안에서 흉내 내면 플러그인이 만든 스택이 인벤토리에 들어갈 때 복사돼 결과가 어디로 가는지 알 수 없다. 서버 쪽 규칙(아이템이 칸에 들어간 뒤 그 칸에 다시 쓰기)으로 한 번에 풀 일이라 후속(결정 항목 1).
- `Player#setPlayerProfile` 은 이 PR 에 없다: Paper 는 게임 프로필을 바꾸고 모든 플레이어에게 엔티티를 지웠다 다시 보여 주며(탭 목록 포함) 본인 화면을 새로 고치는 서버 기능이다(E11c).
