# T-141 물결 1-D: 엔티티 종류별 Bukkit 형 (2026-11-14)

## 한 일
- `tools/compat/bukkit-entity-hierarchy-1.21.1.txt`: Paper 1.21.1 `CraftEntityTypes` 등록표(종류 → 인터페이스 → Craft 클래스 이름)와
  `org.bukkit.entity` 인터페이스의 상위 형 관계를 **이름만** 적은 표. 코드는 옮기지 않았다.
- `tools/compat/gen-bukkit-entity-classes.py`: 표에서
  - 손으로 쓴 것이 없는 인터페이스를 메서드 없는 표시 인터페이스로,
  - 종류별 `Craft*` 클래스(가장 가까운 기존 칸 CraftAnimals·CraftAgeable·CraftCreature·CraftMob·CraftLivingEntity·CraftProjectile·CraftEntity 상속),
  - `CompatEntityKinds`(종류 이름 → 감싸개 클래스·생성)를 만든다. 126종.
- 생성 파일 첫 줄의 머리말을 지우면 생성기가 그 파일을 덮어쓰지 않는다. 메서드를 손으로 옮길 때 그렇게 한다.
- `Compat.entity` 는 `CompatEntityKinds` 를 먼저 본다. PLAYER·ITEM·ARMOR_STAND·PIG 는 지금 손으로 쓴 길 그대로.
- `Tameable#isTamed` 만 메서드를 넣었다(생성기 MIXINS). 소유 스레드면 `LivingEntityState.tamed()`(지금 값), 아니면 핸들 시점 값(`Mob.tamed()`).

## 함정
- Paper 1.21.1 의 Cod·Salmon·TropicalFish 는 `io.papermc.paper.entity.SchoolableFish`(→ Fish) 아래다. 이 칸을 빼면 기반 칸을 못 찾는다.
- `MINECART` 종류의 인터페이스는 `Minecart` 가 아니라 `minecart.RideableMinecart` 다(`Minecart` 는 공통 상위 형).
- 1.21.1 에는 `BOAT`·`CHEST_BOAT` 한 종류씩이다(나무별 종류는 1.21.2+).
- `LivingEntity` 는 Paper 처럼 `Damageable`·`ProjectileSource` 를, `HumanEntity` 는 `AnimalTamer`·`InventoryHolder` 를 상위로 둔다.

## 남긴 것
- `World#spawn(Location, Class)` 는 아직 ArmorStand·Pig 만. 형 → 종류 표는 생성기로 쉽게 만들 수 있지만, Paper 의 종류별 소환 특례(소환 불가 종류 등)를 확인한 뒤 한다.
- 종류별 인터페이스 메서드(Villager 직업, Minecart 속도 등)는 빈도순 물결에서 손으로 옮긴다.
