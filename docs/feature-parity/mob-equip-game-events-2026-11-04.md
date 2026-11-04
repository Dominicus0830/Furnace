# 몹·갑옷 거치대 장착 이벤트(진동) (2026-11-04)

바닐라 `LivingEntity.onEquipItem`(btn.a(bsy,cuq,cuq))은 `Mob.setItemSlot`(btp)·`ArmorStand.setItemSlot`(ciw)이 칸을 바꿀 때마다 불린다.
둘 다 비었거나 같은 아이템·컴포넌트이거나 첫 틱이면 없음. 관전자가 아니면 조용하지 않고 새 것이 그 칸의 장착 아이템(Equipable)일 때 장착 소리(엔티티 난수 nextLong 씨앗),
`doesEmitEquipEvent`(플레이어만 방어구 칸으로 덮음, 나머지 모두 true)면 새 것이 장착 아이템이면 `equip`, 아니면 `unequip`.

## 교체와 제자리 수정을 나눔
Furnace 의 `ItemStack` 은 불변이라, 바닐라가 스택을 그 자리에서 고치는 곳도 새 스택을 칸에 넣는다. 그래서 칸 쓰기를 둘로 나눴다.
- `setItemSlot` — 교체. 바닐라처럼 `onEquipItem` 을 부른다(`Mob`, `ArmorStand`).
- `updateItemSlot` — 제자리 수정. 이벤트·소리·난수 없음. 쓰는 곳:

| 바닐라 동작 | Furnace 위치 |
|---|---|
| `hurtAndBreak`(내구도·부서짐 shrink) | `Mob.hurtEquipped`, `EnchantEffects`(몹 쪽) |
| 햇빛 투구 `setDamageValue`(안 부서졌을 때) | `Zombie`, `AbstractSkeleton` — 부서지면 바닐라도 `setItemSlot(EMPTY)` 라 unequip |
| 늑대 갑옷 수리 `setDamageValue` | `Wolf` |
| 토템 `shrink(1)` | `LivingEntity.checkTotemDeathProtection` |
| 쇠뇌 장전·발사·중단(`CHARGED_PROJECTILES`) | `Crossbow`, `RangedCrossbowAttackGoal`, `CrossbowAttack` |
| `enchantItemFromProvider`(스폰 장비 마법) | `Mob.enchantSpawnedEquipment` 자리, `Pillager` 관통, `SkeletonHorse` 함정 해골 |
| `convertTo` 의 `copyAndClear`(옛 몹 칸 비우기) | `Mob.convertTo` — 새 몹에는 `setItemSlot`(첫 틱이라 이벤트 없음) |
| 여우가 다 먹어 빈 칸(바닐라는 setItemSlot 안 부름) | `Fox.aiStep` |
| 말 몸통 칸 split·replaceInPlace | `AbstractHorse.bodyArmorAccess` |

그 밖의 `setItemSlot` 호출(죽을 때 장비 떨굼, 줍기, `/item replace`, 발사기, 상인·알레이·판다·돌고래·마녀·피글린 손 바꾸기 등)은 바닐라도 `setItemSlot`/`setItemInHand` 라 그대로 둔다.

## 같이 정리한 것
- 말·늑대 몸통 갑옷의 개별 장착 소리·이벤트 코드를 지우고 일반 경로로 합쳤다. 말 갑옷 칸(ArmorSlot.setByPlayer)은 바닐라처럼 `onEquipItem` 을 먼저 한 번 부르고 뒤따르는 set 이 한 번 더 부른다(cpx 바이트코드 확인).
- 발사기 갑옷 입히기(몹·거치대)의 수동 소리·이벤트를 지웠다(setItemSlot 이 낸다). 플레이어는 인벤토리 칸이라 그대로.
- 양털 양탄자(`minecraft:wool_carpets`, WoolCarpetBlock)를 몸통 장착 아이템으로, 장착 소리 `entity.llama.swag`. 이끼 양탄자는 아니다. 방패·조각한 호박·머리는 `item.armor.equip_generic`.

## 검증
- 단위: `MobEquipGameEventsTest` 8건(교체·같은 것·첫 틱·조용함·제자리 수정·쇠뇌·토템·변환·말/늑대·양탄자·거치대). 전체 시험 api 250 / server 2433(39 스킵) 통과.
- 오프라인 서버 + mineflayer 봇(`tools/bot-test/mobequip.js`), 조각 감지기 `last_vibration_frequency`:

| 행동 | 기대 | 기준(#13 브랜치) jar | 변경 jar |
|---|---|---|---|
| `/item replace` 좀비 투구 씌우기 | 5 | 0 | 5 |
| 좀비 투구 벗기기 | 4 | 0 | 4 |
| 좀비 손에 철 칼(장착 아이템 아님) | 4 | 0 | 4 |
| 같은 칼 다시 | 0 | 0 | 0 |
| 낮에 다이아 투구가 햇빛에 닳기(제자리 수정) | 0 | 0 | 0 |
| 좀비가 떨어진 금 투구 줍기 | 5 | 0 | 5 |
| 플레이어가 갑옷 거치대에 흉갑 입히기 | 5 | 0 | 5 |

- 함정: 시험 좀비는 낮이면 불타서 entity_damage(7)가 덮어쓴다 — 장착 시험은 자정에, 햇빛 시험만 정오에.
- 소리 재생은 봇으로 못 듣는다(단위 시험에서 소리 이름만 확인).
