# 호환 계층: helper 홀로그램용 엔티티·메타데이터·효과 API — 2026-11-08

helper 5.6.14 의 `Hologram.create(Position, List<String>).spawn()`(구현 `BukkitHologramFactory$BukkitHologram`)이 Furnace 에서 돌게 한다. 고치기 전에는 첫 줄부터 `ClassNotFoundException: org.bukkit.metadata.MetadataValue` 로 멈췄다.
helper 가 부르는 순서는 다음과 같다(바이트코드에서 그대로 옮겼다).
1. `Location.getChunk().isLoaded()/load()`, `World.getNearbyEntities`
2. `World.spawn(loc, ArmorStand.class)` 다음에 setSmall·setMarker·setArms·setBasePlate·setGravity·setVisible·setCustomName·setCustomNameVisible·setAI·setCollidable·setInvulnerable
3. `ArmorStand.class.getDeclaredMethod("setCanTick")` 리플렉션(없으면 건너뜀)
4. `World.spawnEntity(PIG)` 다음에 `addPotionEffect(new PotionEffect(INVISIBILITY, Integer.MAX_VALUE, 0, false, false))`·setCustomNameVisible·setSilent·setGravity·`setMetadata("nodespawn", new FixedMetadataValue(...))`·setAI·setCollidable·setInvulnerable
5. `ArmorStand.addPassenger(pig)`

클릭 콜백은 `PlayerInteractEntityEvent`·`EntityDamageByEntityEvent`·`PigZapEvent` 를 구독한다.

Paper 1.21.1 소스는 동작을 확인하려고만 읽었다. 코드는 새로 썼다.

## 더한 것

### Furnace API (`furnace-api`)
- `entity.EntityState`: 소유 스레드 전용 상태 손잡이(살아 있는 핸들)다. 부를 때마다 소유 스레드인지 확인하고, 아니면 `ThreadOwnershipException` 을 던진다. `MobAi` 의 `aiNow()`/`editAi` 와 같은 모양이다.
  - 다루는 것: 이름(`customName`·`setCustomName`), 이름 보임, 조용함, NoGravity, 무적, 발광 태그, 투명 읽기, noPhysics
  - 탑승: `passengers()`·`vehicle()`·`addPassenger`(force)·`removePassenger`·`ejectPassengers`·`leaveVehicle`
- `entity.LivingEntityState`: 상태 효과 읽기·더하기·지우기·모두 지우기, 밀림 여부(`isCollidable`/`setCollidable`)
- `entity.ArmorStandState`: 작게·마커·팔·받침판·투명
- `entity.MobEffect`(record): 효과 하나(종류 키·길이·세기·ambient·입자·아이콘). `INFINITE = -1`
- `Entity.stateNow()`(소유 스레드에서 곧바로)와 `Entity.editState(fn)`(소유 스레드로 넘겨서)
- `MobAi.setNoAi(boolean)`
- `EntityDamageEvent.directEntity()`/`causingEntity()`: 피해를 직접 준 엔티티와 일으킨 엔티티(없으면 null). 바닐라 `DamageSource` 의 direct·causing 과 같다.

NBT 경로(`editNbt`)는 쓰지 않았다. 그 길은 `Entity.load` 를 다시 불러 저장하지 않는 상태(뇌·경로 등)를 setter 마다 되돌린다.

### 서버 (`furnace-server`)
- `plugin.EntityStateImpl`: 위 세 손잡이의 구현이다. 옛 동기 코드의 빌림 구간 안이면 `RegionBorrow.claimEntity` 로 그 region 을 빌린다.
  - 효과는 서버 `addEffect` 를 거친다. 그래서 투명 플래그 갱신과 효과 패킷이 바닐라 그대로 나간다.
- `LivingEntity.collides`(저장하지 않는 필드)를 더했다. `isPushable` 이 이 값을 본다. Paper 의 `collides` 와 같은 자리다.
- 플러그인 `EntityDamageEvent` 에 direct·causing 엔티티를 싣는다.

### 호환 계층 (`furnace-compat-paper`)
- `org.bukkit.metadata` 전체: `Metadatable`, `MetadataValue`, `MetadataValueAdapter`, `LazyMetadataValue`(캐시 전략 셋), `FixedMetadataValue`, `MetadataStore`·`MetadataStoreBase`, 예외 둘
  - 저장소는 `org.bukkit.craftbukkit.metadata.{Entity,Player,World,Block}MetadataStore` 넷이다. 키는 각각 엔티티 UUID, 플레이어 소문자 이름, 월드 UID, 월드 UID + 블록 좌표다.
  - `Entity`·`Player`(소문자 이름 저장소)·`World`·`Block` 이 `Metadatable` 이다.
- 엔티티 형:
  - 계층: `Mob`·`Creature`·`Ageable`·`Breedable`·`Animals`·`Steerable`·`Vehicle`·`Enemy`·`Monster`·`Zombie`·`PigZombie`·`LightningStrike`
  - 구현이 있는 것: `Pig`·`ArmorStand`(`CraftPig`·`CraftArmorStand`)
  - `Compat.entity` 가 갑옷 거치대를 `CraftArmorStand` 로, 돼지를 `CraftPig` 로, 나머지 살아 있는 엔티티를 `CraftLivingEntity` 로 감싼다.
- `Entity` 에 더한 것: 이름 보임, 중력, 무적, 조용함, 발광, 승객과 탈것(`getPassengers`·`addPassenger`·`removePassenger`·`isEmpty`·`eject`·`getVehicle`·`isInsideVehicle`·`leaveVehicle`), 메타데이터
- `LivingEntity` 에 더한 것: 효과 메서드 전부, `setAI`/`hasAI`, `setCollidable`/`isCollidable`
- `org.bukkit.potion`:
  - `PotionEffectType`: 39개. 키·번호(레지스트리 번호 + 1)·분류·옛 이름이 있고, `getByKey`·`getById`·`getByName`·`values` 로 찾는다.
  - `PotionEffect`: Paper 와 같은 생성자 기본값, `serialize`/Map 생성자, `with*`
  - `PotionEffectTypeCategory`
- `World.spawn(Location, Class)` 와 `World.spawn(Location, Class, Consumer)`. `ArmorStand`·`Pig` 를 받는다.
- `Location.getChunk()`, `Chunk.load()`/`load(boolean)`
- `org.bukkit.util.NumberConversions`
- 이벤트:
  - `EntityDamageByEntityEvent`: `EntityDamageEvent` 의 처리기 목록을 쓰고, 다리가 때린 엔티티가 있을 때 이 하위 이벤트를 만든다.
  - `PlayerInteractEntityEvent`: 자기 목록을 갖고, 취소하면 바닐라 상호작용을 하지 않는다.
  - `PlayerInteractAtEntityEvent`, `EntityTransformEvent`, `com.destroystokyo.paper.event.entity.EntityZapEvent`, `PigZapEvent`: 클래스만 있다. 리스너는 올라가지만 이벤트는 불리지 않는다.
  - 다리 코드는 `Mappings.registerEntityInteract()` 에 있다. 옛 `EntityDamageEvent` 짝은 그 메서드로 옮겼다.

## 시험
- `HologramEntitiesTest`: `JarCompiler` 로 helper 의 `spawn()`·`setClickCallback` 을 그대로 옮긴 플러그인 jar 를 만들어 올린다. 옛 명령(전역 + 빌림)으로 부른다.
  1. 서버 NBT(Small·Marker·NoBasePlate·ShowArms·NoGravity·Invisible·CustomName·CustomNameVisible·Invulnerable, 돼지의 NoAI·Silent·active_effects 투명)
  2. region 에서 본 투명·밀림·승객
  3. Bukkit getter 전부
  4. 메타데이터
  5. 클라이언트가 받는 add_entity·set_passengers·set_entity_data(이름 칸 2)
  6. 실제 interact 패킷: 오른쪽 클릭이면 `PlayerInteractEntityEvent` 로 가고 취소된다. 공격이면 `EntityDamageByEntityEvent`(때린 쪽 = 플레이어, `ENTITY_ATTACK`)로 가고, 취소하면 체력이 그대로이며, `EntityDamageEvent` 리스너도 하위 이벤트를 받는다.
  7. `spawn` 의 Consumer, 추상 형과 아직 없는 형
  8. 소유 스레드 밖의 setter(예약된다)와 getter(던진다)
  9. `LazyMetadataValue`·`FixedMetadataValue` 의 뜻
- `PotionEffectTypeTest`: 서버 효과 레지스트리 전부와 키·번호·분류를 대조한다. 생성자 기본값과 직렬화도 본다.
- 실제 helper 5.6.14 jar 를 개발용 측정 도구 `helperProbe`(커밋하지 않는 도구, 라이브러리 캐시 필요)로 올려 `Hologram.create(...).spawn()` 을 불렀다. 결과는 `hologram=ok`, `isSpawned()=true` 다. 같은 측정에서 아이템 빌더·직렬화·보스바·메뉴는 아직 막혀 있었다. 이것들은 다른 묶음이 맡는다.
- `EntityStateApiTest`(서버): `stateNow` 는 소유 스레드에서만 되고, 빼돌린 손잡이도 던진다. `editState` 는 소유 스레드에서 돈다. 효과를 넣으면 투명 플래그가 켜지고, `collides` 가 `isPushable` 을 바꾸고, NoAI·탑승·내리기가 서버 엔티티에 들어간다.

## Paper 1.21.1 과 다른 점

1. **`PigZapEvent`·`EntityZapEvent`·`EntityTransformEvent`·`PlayerInteractAtEntityEvent` 는 불리지 않는다.** helper 는 `PigZapEvent` 를 취소해 번개 맞은 홀로그램 돼지가 좀비 피글린으로 바뀌는 것을 막는다. Furnace 에서는 이것이 막히지 않는다. 홀로그램 근처에 번개가 치면 돼지가 바뀌고, 그 줄은 클릭을 받지 못한다. INTERACT_AT 를 쓰는 갑옷 거치대 클릭 리스너도 아무것도 받지 않는다.
2. **`ArmorStand.setCanTick` 이 없다**(Paper 전용 API). helper 는 리플렉션이 실패하면 건너뛴다. 그래서 Furnace 의 홀로그램 거치대는 계속 틱을 돈다. 마커이고 중력도 없어서 보이는 차이는 없고, 틱 비용만 남는다.
3. **소유 스레드 밖에서는 getter 가 `ThreadOwnershipException` 을 던진다.** setter 는 소유 region 으로 예약되고 다음 엔티티 틱에 들어간다. Paper 에서 비동기 스레드의 getter 는 "대개 되는" 경쟁 상태다. 옛 동기 작업·명령·이벤트 리스너(전역 + 빌림, region 스레드) 안에서는 Paper 처럼 곧바로 읽고 쓴다. `eject()`·`leaveVehicle()` 는 결과를 돌려줘야 해서 getter 처럼 던진다.
4. **`getCustomName()` 만은 소유 스레드 밖에서 던지지 않는다.** 대신 핸들을 잡은 시점의 이름을 준다. 예를 들어 spawn 직후에 받은 객체는 이름을 달기 전 값(null)을 준다. 원래 동작을 지키려고 남겼다.
5. **플레이어의 상태 메서드는 `UnsupportedFeature` 다.** 대상은 `setCustomName`·이름 보임·중력·무적·조용함·발광·탑승·효과·`setCollidable` 이다. 플레이어에게는 아직 상태 손잡이가 없다(`stateNow` 가 UOE). 메타데이터는 플레이어도 된다.
6. **`World.spawn(.., Consumer)` 의 Consumer 는 엔티티가 월드에 들어가고 스폰 이벤트를 지난 뒤에 돈다.** Paper 는 월드에 넣기 전에 돌려, 스폰 리스너가 꾸민 뒤의 엔티티를 본다. Furnace 에서는 리스너가 꾸미기 전 엔티티를 본다. 같은 틱이라 클라이언트가 처음 받는 상태는 같다. 시험에서 확인했다.
7. **스폰이 취소되면 `spawn`·`spawnEntity` 가 `IllegalStateException` 을 던진다.** Paper 는 죽은 엔티티를 돌려준다. 이것은 기존 `spawnEntity` 와 같은 규칙이다. 스폰 이유도 기존 경로 그대로 COMMAND 이고, Paper 는 CUSTOM 이다.
8. **`spawn(loc, Class)` 는 `ArmorStand`·`Pig` 만 받는다.** 다른 실제 엔티티 형은 `UnsupportedFeature`("World#spawn(Zombie)")를 던진다. 추상 형(`Entity`·`LivingEntity` 등)과 인터페이스가 아닌 클래스는 Paper 처럼 `IllegalArgumentException` 이다.
9. **`Chunk.load()` 는 청크를 동기로 올리지 않는다.** 이미 올라와 있는지(`isLoaded()`)만 돌려준다. helper 는 플레이어 근처에 홀로그램을 두므로 대개 올라와 있다.
10. **`setCollidable(false)` 는 밀림(`isPushable`)만 끈다.** Paper 와 같은 자리이지만, Paper 의 `collidableExemptions`(엔티티별 예외 UUID 목록)는 없다.
11. **`EntityDamageByEntityEvent.isCritical()` 은 늘 false 다.** Furnace 피해 이벤트에 치명타 여부가 아직 실리지 않는다.
12. **`PotionEffectType.SATURATION.isInstant()` 는 true 다.** 바닐라 `SaturationMobEffect` 가 `InstantenousMobEffect` 를 잇는다고 보고 정했고, 1.21.1 jar 로 확인하지는 못했다(가정). 서버 `MobEffect.isInstantaneous()` 는 포화를 즉시 효과로 치지 않는다. 투척 물약 처리에 쓰인다면 서버 쪽 차이가 될 수 있다. 확인할 거리로 남긴다.
13. **`PotionEffectType` 에 없는 메서드가 있다.** `getColor()`, 속성 수정자(`getEffectAttributes`), `PotionEffectTypeWrapper` 계열, Adventure `translationKey` 형이 없다. `PotionEffect` 는 `ConfigurationSerializable` 을 구현하지만 `ConfigurationSerialization` 에 등록하지 않았다(그 파일은 다른 묶음이 맡는다).
14. **`Mob`·`Ageable`·`Steerable`·`Animals` 같은 중간 인터페이스는 메서드가 비어 있다.** 형 검사(`instanceof Pig`)만 맞춘다. 안장·번식·나이·목표 같은 메서드는 아직 없다. `Nameable` 인터페이스도 없다(이름 메서드는 `Entity` 에 직접 있다).
15. **메타데이터는 Paper 와 같이 플러그인을 꺼도 남는다.** Paper 1.21.1 은 `/reload` 때만 `removeAll` 을 부른다. 작업 지시에는 "끄면 지운다"는 가정이 있었으나 소스로 보면 그렇지 않아 Paper 를 따랐다. 플러그인 쪽 참조는 `WeakReference` 이고 저장소는 `WeakHashMap<Plugin, …>` 이다. 그래서 Furnace 의 플러그인 다시 올리기(핫 리로드)는 따로 지우지 않고 GC 에 맡긴다. 사라진 엔티티의 메타데이터가 남는 것도 Paper 와 같다(UUID 키, 엔티티가 사라질 때 지우지 않는다).
16. **`hasAI()` 는 몹이 아니면 false 이고, `setAI` 는 몹이 아니면 아무것도 하지 않는다.** 갑옷 거치대가 그 예이고, Paper 와 같다. 차이가 아니라 확인한 것으로 적는다.
