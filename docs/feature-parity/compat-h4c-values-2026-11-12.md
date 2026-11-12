# H4 묶음 C: Bukkit 값 타입·열거형·레지스트리 — 2026-11-12

실제 플러그인 jar 가 부르는 값 타입(측정 문서 `compat-h4-2026-11-07.md` 의 C 묶음)을 채웠다. 기준은 **Paper 1.21.1**이다(1.21.3 에서 인터페이스로 바뀐 형들이 1.21.1 에서는 열거형이라 플러그인이 `invokevirtual`·`getstatic` 으로 부른다).
Paper 원문은 동작 비교로만 읽었고 코드는 옮기지 않았다(`furnace-paper-source-access` 방법: 1.21.1 패치 `33ff3dff`, 바뀌지 않은 CraftBukkit 본문은 최신 main).

## 무엇을 넣었나

| Bukkit | 내용 | 출처 |
|---|---|---|
| `Sound`(1611), `Particle`(109), `block.Biome`(64+`CUSTOM`), `Art`(50), `attribute.Attribute`(31), `potion.PotionType`(46) | 열거형. `tools/compat/gen-bukkit-values.py` 가 Furnace 의 1.21.1 레지스트리 자료에서 만든다 | `registries/builtin-1.21.1.txt`·`particle_types-1.21.1.txt`·`1.21.1.txt`(biome)·`entities/painting_variants-1.21.1.txt`, 물약 효과는 서버 `effect/Potions.java` |
| `Particle.DustOptions`·`DustTransition`, `Particle#getDataType` | data 형은 CraftParticle 과 같다(block·block_marker·falling_dust·dust_pillar=`BlockData`, dust=`DustOptions`, dust_color_transition=`DustTransition`, entity_effect=`Color`, item=`ItemStack`, vibration=`Vibration`, sculk_charge=`Float`, shriek=`Integer`, 나머지 `Void`) | |
| `Registry`(+`SimpleRegistry`), `Bukkit/Server#getRegistry(Class)` | `ART`·`ATTRIBUTE`·`BIOME`(CUSTOM 제외)·`ENCHANTMENT`·`ENTITY_TYPE`(UNKNOWN 제외)·`MATERIAL`·`EFFECT`·`PARTICLE_TYPE`·`POTION`·`SOUNDS`. `get`·`getOrThrow`(IAE)·`getKey`·`getKeyOrThrow`(Paper, NoSuchElementException)·`match`·`stream` | 이 계층에 형이 없는 레지스트리(구조물·트림·변종 등)는 상수 자체가 없다 — 그 필드를 읽으면 `NoSuchFieldError` 로 그 자리에서 멈춘다 |
| `Color`, `DyeColor`, `Note`(+`Tone`), `FireworkEffect`(+`Builder`), `Vibration`(+`Destination`), `SoundCategory`, `Axis`, `FluidCollisionMode`, `TreeSpecies`, `SkullType`, `inventory.EquipmentSlotGroup`, `attribute.AttributeModifier`(+`Operation`), `potion.PotionData` | 손으로 쓴 값 타입. `Color`·`FireworkEffect`·`AttributeModifier` 는 `ConfigurationSerialization` 에 등록 | DyeColor 색은 서버 `SpecialRecipes` 표와 같다 |
| `World#playSound`(위치·엔티티, 분류·씨앗 변형 전부), `World#spawnParticle`(14 변형 + Paper receivers 판) | | |
| `Player#playSound`·`stopSound`·`stopAllSounds`·`spawnParticle` | | |
| `World#getBiome/setBiome`, `Block#getBiome/setBiome` | | |
| 옛 이름 바꾸기(`ApiRenames`) | Particle·Biome·EntityType·PotionType 의 `getstatic`·`valueOf`, Enchantment·PotionEffectType 의 `getstatic`. 이미 있던 ItemFlag 와 같은 틀 | CraftBukkit `FieldRename`(모든 api-version) |

Furnace API 에 더한 것: `world.ParticleEffect`(봉인 형 — 단순·블록·먼지·먼지 전이·색·아이템·스컬크·비명·진동), `World#playSound(Sound, x,y,z[, seed])`·`playSound(Sound, Entity, seed)`·`spawnParticle(…, receivers, …, force)`·`biomeNow`·`setBiomeNow`, `Audience#playSound(…, seed)`·`playSound(Sound, Entity, seed)`·`spawnParticle(…)`. 서버 쪽은 `plugin/ApiEffects`(API 값 → 패킷)와 `WorldHandle`·`PlayerHandle` 구현, `Region#setNoiseBiomeAtBlock`.

## Paper 와 같게 맞춘 동작

- **소리 거리**: `World#playSound(Location…)` 은 같은 차원에서 거리 < `volume>1 ? 16×volume : 16` 인 사람에게(`PlayerList.broadcast`). 엔티티 소리는 그 엔티티를 보는 사람 + (플레이어면) 자신(`sendToTrackingPlayersAndSelf`). `Player#playSound` 는 그 사람에게만, 거리 무관.
- **소리 값**: 레지스트리에 있는 소리는 번호로, 없는 이름(리소스 팩)은 이름으로 싣는다(바닐라 `Holder.direct`). Bukkit 은 크기·높이를 검사하지 않으므로 Furnace `Sound` 값에 맞춰 높이는 0~2, 크기는 0 이상으로 죈다 — 클라이언트가 높이를 0.5~2 로 다시 죄고 음수 크기는 들리지 않으니 들리는 결과는 같다. 이름이 리소스 id 가 아니면 `IllegalArgumentException`.
- **null 인자**: `World/Player#playSound` 는 위치·소리·분류 중 null 이 있으면 조용히 넘어간다(Paper). `stopSound(String, null)` 은 MASTER, `stopSound((SoundCategory) null)` 은 NPE(CraftPlayer 의 `SoundSource.valueOf(category.name())`).
- **입자 force**: `World#spawnParticle` 에서 force 를 적지 않으면 **true**(Paper 패치 — Bukkit 원래는 false). force 면 칸 가운데 거리 512 미만, 아니면 32 미만. `Player#spawnParticle` 은 거리 검사 없이 보내고 force 기본 false.
- **입자 data**: CraftParticle 처럼 **입자 종류의 data 형**으로 고른다. 필요한데 null 이면 `IllegalArgumentException("missing required data …")`, 형이 다르면 `"data (…) should be …"`. `DUST` 에 `DustTransition` 을 주면 처음 색의 보통 먼지다.
- **Color**: `mixColors` 는 Bukkit 처럼 **정수 평균**을 float 로 바꿔 이득을 곱한다(실수 평균과 끝자리가 다를 수 있다 — 시험에 예). 메시지 문구(`"Extrenuous data in"` 오타 포함)도 같다.
- **바이옴**: 읽기·쓰기 모두 높이 밖은 끝 칸으로 죈다(`LevelChunk.getNoiseBiome`·`ChunkAccess.setBiome`). 레지스트리에 없는(데이터 팩) 바이옴은 `Biome.CUSTOM`. `setBiome(CUSTOM)` → `IllegalArgumentException("Cannot set the biome to CUSTOM")`. **쓰기는 클라이언트에 다시 보내지 않는다**(Paper `CraftWorld.setBiome` 과 같다 — 보이려면 청크를 다시 보내야 한다).
- **물약 효과**: `PotionType#getPotionEffects` 는 바닐라처럼 ambient=false·입자·아이콘 true(Bukkit `PotionEffect(type,d,a)` 3인자 생성자는 ambient=true 라 쓰지 않았다).

## Paper 와 다른 점 (관측 가능한 것)

- **스레드**: `getBiome`·`setBiome` 은 그 칸을 가진 region 스레드에서만 된다(아니면 `ThreadOwnershipException`) — Furnace 에는 단일 주 스레드가 없다. 로드되지 않은 청크의 `getBiome` 은 Paper 처럼 청크를 동기로 불러오지 않고 `UnsupportedFeature`, `setBiome` 은 Paper 처럼 아무 일도 없다.
- **`Vibration.Destination.EntityDestination`** 입자는 `UnsupportedFeature` — Furnace API 가 엔티티 네트워크 번호·눈높이를 아직 내주지 않는다. `BlockDestination` 은 된다.
- **소리 패킷 모양**: Paper 는 `Sound` 열거형은 레지스트리 번호로, **문자열 이름은 레지스트리에 있어도 이름 그대로**(`Holder.direct`) 싣는다. Furnace 는 둘 다 레지스트리에 있으면 번호로 싣는다. 바닐라 소리는 모두 가변 거리라 클라이언트가 내는 소리·거리는 같고, 패킷을 직접 읽는 플러그인만 차이를 본다.
- **`World#spawnParticle` 의 `source`**(숨긴 플레이어 거르기)는 쓰지 않는다 — Furnace 에 플레이어 숨기기가 없다.
- 없는 것: Paper `ParticleBuilder`, `RegistryAccess`·`RegistryKey`, `Attributable`·`AttributeInstance`, adventure `Sound.Type` 구현, `Enchantment/PotionEffectType.getByName` 의 옛 이름 다리(아래 가정 참고), `EntityType.fromName`.

## 가정 (확인하지 못한 것)

- 열거형 **순서(ordinal)**: Sound(이름순)·Biome(옛 Bukkit 순서 + 1.21 추가분)·Attribute(Bukkit 순서 기억)·Art(옛 번호 순 + 1.21 추가분 이름순)·Particle(레지스트리 순)은 1.21.1 Bukkit 원문과 대조하지 못했다. `ordinal()` 을 저장하는 플러그인만 영향을 받는다. `Art#getId` 의 1.21 전 번호는 옛 Bukkit 번호와 같다.
- `FieldRename` 의 Enchantment·PotionEffectType `getByName` 다리가 1.21.1 에도 있었는지는 확인하지 못했다(최신 main 에는 있다). 기존 `PotionEffectType.getByName` 은 옛 이름을 찾지 않는 채로 두었다.
- 1.21.1 뒤의 이름 바꿈(속성 `GENERIC_` 떼기, 소리·아이템 등)은 넣지 않았다 — 1.21.1 Paper 에 없다.

## 시험

- `BukkitValueTypesTest` — 생성 열거형이 서버 1.21.1 자료와 같다(소리·입자·바이옴·그림 크기·속성·물약 효과 값), DyeColor 색표, Registry·Color·Note·FireworkEffect·AttributeModifier 규칙, Bukkit→Furnace 소리·입자 변환.
- `BukkitEffectsTest` — 가짜 플레이어 둘로 실제 패킷 확인: 월드 소리 거리·씨앗·분류, 플레이어 소리는 한 사람, 엔티티 소리는 보는 사람+자신, stop_sound, 입자 receivers·512칸·data 검사, 바이옴 읽기/쓰기(같은 4×4×4 칸, 높이 밖, CUSTOM 거절).
- `ApiRenamesTest`·`ApiRenamePluginTest` — 옛 이름 표의 새 이름이 모두 실제 필드이고 옛 이름은 없다(110개), 옛 이름으로 컴파일한 플러그인(getstatic·valueOf·enum switch)이 새 상수를 받는다.
