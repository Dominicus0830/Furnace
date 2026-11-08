# 호환 계층: 아이템 직렬화 (BukkitObjectOutputStream · ConfigurationSerialization · ItemStack.serialize) — 2026-11-08

helper 5.6.14 의 `InventorySerialization.encodeItemStackToString`·`decodeItemStack`(와 `ItemStack[]`·Inventory 변형)이 `ClassNotFoundException: org.bukkit.util.io.BukkitObjectOutputStream` 로 실패했다(`helperProbe` 의 `serialize.item=FAIL`). 이 묶음이 그 빈칸을 메운다. 기준은 Paper 1.21.1 의 관측 동작이다(내부 구현은 새로 썼다).

## 추가한 것

| 영역 | 클래스 | 내용 |
|---|---|---|
| 객체 스트림 | `org.bukkit.util.io.BukkitObjectOutputStream`·`BukkitObjectInputStream`·`Wrapper`(패키지 전용) | Serializable 이 아닌 `ConfigurationSerializable` 을 `Wrapper`(`==` 별칭 + `serialize()` 맵)로 바꿔 쓰고, 읽을 때 `ConfigurationSerialization` 으로 되돌린다. `Wrapper` 의 serialVersionUID(`-986209235411767547L`)·필드(`map: Map`)는 Paper 와 같다 |
| 등록부 | `ConfigurationSerialization`, `SerializableAs`, `DelegateDeserialization` | `registerClass`·`unregisterClass`·`deserializeObject`·`getClassByAlias`·`getAlias`, `SERIALIZED_TYPE_KEY = "=="`. 기본 등록: `ItemStack`(별칭 = 클래스 이름 `org.bukkit.inventory.ItemStack`), `ItemMeta`(별칭 `ItemMeta`) |
| 아이템 | `ItemStack implements ConfigurationSerializable`, `serialize()`, `static deserialize(Map)` | 맵 `v`(3955)·`type`·`amount`(1 아니면)·`meta`(내용 있으면). 옛 `damage`·`enchantments` 키도 읽는다 |
| 메타 | `ItemMeta extends ConfigurationSerializable`, `CompatItemMeta#serialize`, `org.bukkit.craftbukkit.inventory.SerializableMeta`, `ItemMetaSerialization`, `NbtBinary` | 맵 키: `meta-type`, `display-name`·`item-name`·`lore`(JSON 문자열), `custom-model-data`, `enchants`(`minecraft:x` → 레벨), `repair-cost`, `ItemFlags`, `hide-tool-tip`, `Unbreakable`, `enchantment-glint-override`, `fire-resistant`, `max-stack-size`, `rarity`, `Damage`, `max-damage`, `unhandled`·`custom`(base64 gzip NBT), `removed`, `PublicBukkitValues`(SNBT 문자열) |
| 설정 | `MemorySection`·`ConfigurationSection` | `set` 이 `ConfigurationSerializable`(안쪽·리스트 안 포함)을 `==` 별칭이 맨 앞인 맵으로 저장하고, `get`·`getList` 가 별칭이 등록된 `==` 맵을 객체로 되읽는다. `getItemStack`·`isItemStack`·`getSerializable` 추가. 직렬화 객체 경로는 `getConfigurationSection` 이 null |
| 기타 | `CraftItemStack` | `@DelegateDeserialization(ItemStack.class)` 한 줄(별칭이 클래스 이름으로 새지 않게) |

## 설계 결정

- **Guava 를 쓰지 않는다.** Paper 의 `Wrapper` 는 맵을 Guava `ImmutableMap` 으로 담지만, 호환 모듈은 Guava 가 클래스패스에 있다고 가정할 수 없다(서버 제공 라이브러리는 플러그인이 선언했을 때만 올라온다). 우리는 삽입 순서를 지키는 `LinkedHashMap` 을 쓴다. Paper 가 우리 스트림을 읽는 것은 문제없다(필드 선언 타입이 `Map`). Paper 가 쓴 스트림을 읽으려면 `ImmutableMap$SerializedForm` 을 풀 Guava 가 플러그인 클래스로더에 보여야 한다(`ObjectInputStream` 은 호출한 플러그인의 로더로 클래스를 찾는다 — helper 는 guava 를 선언한다).
- **컴포넌트를 잃지 않는다.** 호환 `ItemMeta` 가 모델링하지 않는 컴포넌트(염색 색, 포션, 머리, 속성 수정자 …)는 Paper 의 `unhandled` 와 같은 모양(컴포넌트 패치 NBT 를 gzip → base64)으로 담는다. 컴포넌트가 SNBT 로 저장돼 있어서 SNBT → NBT 이진 변환(`NbtBinary`)만 새로 썼다. `custom_data` 의 `PublicBukkitValues` 밖 값은 Paper 처럼 `custom` 에 담는다.
- **아이템 종류를 모르는 메타.** `ItemMeta` 만 따로 되읽을 때는 임시 종류(stone) 위에 만들고, `ItemStack#setItemMeta` 가 실제 종류에 옮긴다. 아이템 기본값과 같은 값을 직접 붙인 컴포넌트(예: 곡괭이에 `max_stack_size=64`)가 임시 종류의 기본값과 같다고 지워지지 않게 정수·희귀도는 날 SNBT 로 넣는다(시험 있음). 그래서 `CompatItemMeta#equals` 는 종류를 비교하지 않는다(Bukkit 메타도 그렇다).
- **설정에는 사본을 저장한다.** `ConfigSection` 이 값을 일반 맵으로 들고 있어서 `set` 때 맵으로 바꾼다.

- **원문을 옮기지 않는다(결정 7 GPLv3 미정).** `ConfigurationSerialization`·`BukkitObjectInputStream`·`BukkitObjectOutputStream` 은 공개·protected 서명과 예외·로그 문구, 되읽기 순서(deserialize → valueOf → 생성자)만 Bukkit 에 맞추고 몸통은 새로 썼다(독립 검토가 처음 판이 원문과 문장 단위로 같다고 짚어 다시 씀).

## Paper 1.21.1 과 관측되는 차이

1. **`meta-type` 은 항상 `UNSPECIFIC`.** Paper 는 아이템에 따라 `ARMOR`·`COLORABLE_ARMOR`·`POTION`·`SKULL`·`TILE_ENTITY`·`ENCHANTED` 등을 쓰고 종류별 키(`potion-type`, `skull-owner`, `color`, `pages` …)를 쓴다. 우리는 해당 컴포넌트를 `unhandled` 에 넣는다. 읽을 때는 Paper 의 meta-type 이름을 모두 받아들이지만 종류별 키는 무시한다(Paper 가 쓴 포션·머리·책 메타의 그 데이터는 잃는다). 모르는 meta-type 은 `IllegalArgumentException`, 없으면 `NoSuchElementException`(Paper 와 같다).
2. **`attribute-modifiers`·`food` 등 모델링하지 않는 키.** Paper 는 `attribute-modifiers` 를 `AttributeModifier` 객체 맵으로 쓰는데 우리는 `AttributeModifier` 클래스·별칭이 없어, Paper 가 쓴 그런 스트림은 `IOException: Failed to deserialize object` 로 실패하고 Paper YAML 의 해당 키는 무시된다. 우리가 쓰는 쪽은 컴포넌트 전체가 `unhandled` 에 산다.
3. **JSON 글 모양이 다를 수 있다.** `display-name`·`item-name`·`lore` 는 JSON 문자열이지만 `Text#toJson` 이 만든 모양이다. `setDisplayName("§6x")` 한 이름의 JSON 구조가 CraftChatMessage 의 것(`{"extra":[...],"text":""}` 꼴)과 글자 단위로 같지 않을 수 있다. 읽을 때는 JSON·§ 서식 둘 다 받는다. 두 서버가 같은 글을 보여 주는지는 확인하지 못했다(가정).
4. **키 순서는 근사.** 순서는 Paper 1.21.4 소스를 따라 두었지만 1.21.1 CraftBukkit 원본은 읽지 못했다. 1.21.4 에서 가져온 키 이름(`fire-resistant`·`hide-tool-tip`·`max-stack-size`·`rarity`·`max-damage`·`enchantment-glint-override`)이 1.21.1 에서도 같은지는 **가정**이다. 1.21.2 이후 키(`tooltip-style`·`item-model`·`glider`·`damage-resistant`·`enchantable`·`tool`·`use-cooldown`·`equippable` 등)는 쓰지도 읽지도 않는다.
5. **`PublicBukkitValues` SNBT 서식.** Paper 는 `SnbtPrinterTagVisitor` 서식, 우리는 한 줄 `Tag.toString` 서식(`Snbt.write`)이다. 둘 다 서로 읽는다. 옛(1.20.5 이전) 맵 서식 `PublicBukkitValues` 는 읽지 못하고 `IllegalArgumentException` 이다.
6. **옛 형식 변환 없음.** `v` 가 없거나 3955 보다 낮은 맵(Spigot 1.13 이전·옛 데이터 버전)의 Material 이름 변환·DataFixer 가 없다. 지금 이름의 `Material.getMaterial` 로 읽고, 없는 이름은 `IllegalArgumentException` 이다(Paper 는 변환 후 그래도 못 찾으면 실패). 또 Paper 는 `v < 3837`(1.20.5 전) 맵에 `HIDE_ADDITIONAL_TOOLTIP` 이 있으면 `HIDE_STORED_ENCHANTS` 를 더하고, `enchantments` 키가 있으면 `meta` 를 무시한다 — Furnace 는 둘 다 하지 않는다(독립 검토).
7. **`Wrapper` 의 맵이 `LinkedHashMap`.** 위 설계 결정. Paper 스트림을 읽으려면 Guava 가 보여야 한다.
8. **알아보는 마법 부여 한정.** 읽을 때 `Enchantment` 상수(1.21.1 바닐라 목록)에 없는 키(데이터 팩 마법)와 레벨 1~255 밖의 값은 건너뛴다.
9. **Material 로 나타낼 수 없는 아이템은 `serialize()` 가 `UnsupportedFeature`.** `getType()` 이 던지기 때문이다.
10. **`HIDE_DESTROYS`·`HIDE_PLACED_ON`·`HIDE_ARMOR_TRIM` 표시 항목은 스택에 남지 않는다**(기존 호환 메타의 한계). 메타 사본을 직접 `serialize()` 하면 `ItemFlags` 에 나오지만, 스택에 `setItemMeta` 한 뒤의 `ItemStack#serialize` 에는 없다.
11. **설정은 사본으로 저장·사본으로 읽는다.** Bukkit 은 `set(path, item)` 이 같은 객체를 들고 있어 이후 `item` 을 바꾸면 저장 값도 바뀌고 `get` 은 같은 객체를 준다. 여기서는 `set` 순간의 값이 저장되고 `get` 은 매번 새 객체를 준다.
12. **설정 키 목록.** `getKeys(true)`·`getValues(true)` 가 직렬화 객체 안쪽(`item.==`, `item.type`, `item.meta.…`)까지 풀어 준다. Bukkit 은 그 객체를 잎 값으로 본다.
13. **모르는 별칭.** Bukkit 의 YAML 읽기는 등록되지 않은 `==` 값에서 `YAMLException` 을 던진다. 여기서는 읽기에 실패하지 않고 그 구역은 평범한 구역(`getConfigurationSection`)으로 남는다.
14. **기본 등록 클래스가 적다.** Paper 는 `Vector`·`BlockVector`·`Color`·`PotionEffect`·`FireworkEffect`·`Pattern`·`Location`·`AttributeModifier`·`BoundingBox`·`SpawnRule` 도 등록한다. 우리는 `ItemStack`·`ItemMeta` 만(나머지 클래스는 호환 계층에 없거나 아직 `ConfigurationSerializable` 이 아니다).
15. **Paper 의 바이트 직렬화 API 는 없다.** `ItemStack#serializeAsBytes`·`deserializeBytes`·`serializeItemsAsBytes` 와 JSON 직렬화는 이번 범위가 아니다.

## 시험

- `ItemSerializationTest`(19개): 맵 모양, 객체 스트림 왕복(단일·배열·null·메타 단독·맵 속), `Wrapper` 의 Paper 호환 필드, `unhandled`·`removed`·`custom` 보존(표준 GZIP 으로 풀어 확인), 기본값과 같은 명시 값 보존, Paper 모양 맵 읽기, 옛 키·오류, `ConfigurationSerialization` 등록부(별칭·위임·생성자·해제), YAML·메모리 설정 왕복, `NbtBinary`.
- `ItemSerializationPluginTest`(1개): 컴파일한 진짜 플러그인 jar 가 helper 와 같은 코드로 `encodeItemStack`·`decodeItemStacks`·YAML 왕복을 하고, 플러그인이 등록한 `ConfigurationSerializable` 을 스트림으로 보낸다.
- 호환 모듈 전체 시험 245개 통과(실패·건너뜀 0).
- 실제 helper `helperProbe`(`serialize.item`)로는 재지 않았다(해당 태스크는 메인 작업 사본에만 있다).
