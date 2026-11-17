# T-141 물결 1 — A: Material 블록 성질·EntityType.isAlive (2026-11-14)

계기: FAWE-Bukkit 2.16.2 jar 가 부르지만 호환 계층에 없던 `Material.isSolid/isTransparent/isBurnable`, `EntityType.isAlive`
(context-notes/2026-11-14-wolf-biome-edge-fawe-api.md 3장). 같은 표에서 나오는 성질을 한꺼번에 넣었다.

## Paper 1.21.1 동작(원문 대조: MohistMC/Youer 1.21.1 사본 + Paper 저장소 paper-api a47a008c)
- `Material.isSolid/isFlammable/isBurnable/isOccluding/hasGravity/getHardness/getBlastResistance/getSlipperiness` 와 Paper `isCollidable` 은
  `asBlockType()` → `CraftBlockType` 이 바닐라 블록 **기본 상태**를 읽는다:
  isSolid = `blocksMotion()`, isFlammable = `ignitedByLava()`, isBurnable = `FireBlock.igniteOdds > 0`, isOccluding = `isRedstoneConductor`,
  hasGravity = `block instanceof Fallable`, hardness = `destroySpeed`, blast = `getExplosionResistance()`, slip = `getFriction()`, isCollidable = `Block.hasCollision`.
  블록이 아니면 boolean 은 false(isCollidable 만 `IllegalArgumentException("<이름> isn't a block type")` — Paper `Add-hasCollision-methods-to-various-places.patch`. Material 에 hasCollision 은 없다), 수치 셋은 `IllegalArgumentException("The Material is not a block!")`.
- `isTransparent()` 는 @Deprecated 손 관리 목록(바닐라 값이 아님). 이름 집합만 `tools/compat/bukkit-transparent-1.21.1.txt` 에 옮겨 적었다(LEGACY_ 50개 제외 127개). 유리·물은 목록에 없다(Paper 와 같다).
- `EntityType.isAlive` = Bukkit 클래스가 LivingEntity 하위, `isSpawnable` = `independent`(낚시찌·플레이어·UNKNOWN 만 false).

## Furnace 구현
- `tools/compat/gen-bukkit-material.py` 가 서버 리소스(stateflags 의 blocksMotion·redstoneConductor, blockprops, hardness, fire, definitions 의 블록 부류)에서 상수마다
  플래그·수치를 만든다. Fallable 부류 = anvil·colored_falling·concrete_powder·dragon_egg·pointed_dripstone·brushable(비계는 아님).
- `tools/compat/gen-bukkit-registries.py` 가 `entity_data/owners-1.21.1.txt`(LivingEntity 를 거치는 종류)로 isAlive 를 만든다. 생성기에 빠져 있던 손 편집(getName·fromName)도 생성기로 옮겼다.
- 넣지 않은 것: `isInteractable`(Paper 는 블록 클래스가 use 메서드를 덮어쓰는지로 정한다 — 서버 표가 없음), `getEntityClass`(엔티티 인터페이스가 다 생긴 뒤, 물결 1-D).
