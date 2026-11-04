# 엔티티·아이템 게임 이벤트(진동) 누락분 (2026-11-03)

바닐라 1.21.1 서버 클래스를 `javap -c -p` 로 훑어 `GameEvent`(`dxz`) 필드를 읽는 호출 지점을 모두 뽑고(`getstatic dxz.*`), 엔티티·아이템 쪽에서 Furnace 가
빠뜨린 것을 채웠다. 블록·블록 엔티티 쪽(`OpenersBlockEntity` 등)은 대부분 이미 있었고, 블록의 `TODO(엔티티)` 훅은 다른 작업 줄기라 건드리지 않았다.

## 채운 것 (바닐라 호출 지점 → Furnace)
| 이벤트 | 바닐라 | Furnace | 출처 엔티티 / 자리 |
|---|---|---|---|
| splash | `Entity.doWaterSplashEffect`(bsr.bo) | `Entity.doWaterSplashEffect` 끝 | 자기 / 자기 자리. 세기는 조종 승객이 있으면 그 승객 움직임 × 0.9(바닐라와 같게 고침, 전에는 늘 자기 × 0.2) |
| splash | `Boat.onAboveBubbleCol`(cov.l) | `Boat.onAboveBubbleCol` | 조종 승객 |
| splash | `Player.doWaterSplashEffect` | `Region.playerWaterSplash`(생존 틱에서 물에 처음 닿을 때) | 플레이어. 첨벙 소리도 같이(본인 제외). 관전자·배 탑승 중은 없음 |
| entity_mount / entity_dismount | `Entity.addPassenger`/`removePassenger`(bsr.p/q) | 같은 곳 | 승객 / 탈것 자리 |
| item_interact_start / finish | `LivingEntity.startUsingItem`/`stopUsingItem`(btn.c/fx) | 몹: `LivingEntity` 같은 곳. 플레이어: `Player.startUsingItem`/`stopUsingItem` 로 모음 | 자기. 끝은 "쓰던 중이었을 때"만(다 먹음·놓음·취소 모두) |
| item_interact_finish | `LivingEntity.checkTotemDeathProtection`(btn.g) | 몹 토템(플레이어는 이미 있었음) | 자기 |
| item_interact_start / finish | `FishingRodItem.use`(ctx) | `FishingRods.use` 던지기 / 감기 | 플레이어 |
| item_interact_finish | `BoneMealItem.useOn`(csq) | `ItemUse.boneMeal` 두 갈래 | 플레이어 자리 |
| eat | `LivingEntity.eat`(btn) | `Consuming.finish` 음식 | 플레이어 |
| eat | `Mob.ate`(btp.Q), `AbstractHorse/Camel.handleEating`(chi, cgv) | `Sheep.ate`, `AbstractHorse.handleEating`, `Camel.handleEating` | 자기. 라마는 바닐라에도 없음 |
| drink | `PotionItem.finishUsingItem`(cvl) | 플레이어 물약, 떠돌이 상인 물약 | 자기(우유는 바닐라에도 없음) |
| equip | `Player.setItemSlot → onEquipItem`(방어구 칸만) | `ItemUse.equip` 우클릭 장착 | 플레이어 |
| equip / unequip | `LivingEntity.onEquipItem`(몸통) | `AbstractHorse.onBodyArmorEquip`(말 갑옷·양탄자) | 자기 |
| equip | `SaddleItem.interactLivingEntity`(cvp) | 말·돼지 안장 | 대상 몹 |
| entity_damage | `VehicleEntity.hurt`(cpg) | `AbstractMinecart`·`Boat` hurtBy | 공격자(`source.getEntity()`) |
| entity_damage | `ArmorStand.hurt`·`causeDamage`(ciw) | 같은 곳 | 공격자 |
| entity_damage | `ItemEntity.hurt`(cjh) | 같은 곳 | 공격자 |
| entity_damage | `ShulkerBullet.destroy`(cns.t) | `destroy()` — 맞을 때·피해 받을 때 모두 | 탄 |
| container_open / close | `ChestBoat.interact`·`openCustomInventoryScreen`·`stopOpen`(cow), `MinecartChest.interact`·`stopOpen`(cpa) | 같은 곳. 닫기는 `EntityContainer.stopOpen` 이 엔티티 region 으로 보내 부른다 | 플레이어 / 엔티티 자리. 깔때기 수레는 바닐라에도 없음 |
| container_open / close | `Shulker.setRawPeekAmount`(ckj.b) | 같은 곳 | 자기 |
| entity_action | 아르마딜로 말기·풀기(cgn.gm/gn), 낙타 앉기·서기·바로 서기(cgv.gv/gw/gx), 거북 알 낳기(cgf), 스니퍼 파기 10틱마다(chv, 머리 칸), 파괴수 포효(cki.gw) | 같은 곳 | 자기 |
| entity_place | 아르마딜로 껍데기 떨굼(cgn.ab), 소환사 송곳니·벡스(cjw$a, cjw$c), 불길한 아이템 생성기(btu.w) | 같은 곳 | 자기 / 소환사 / 나온 엔티티 |
| entity_interact | 아르마딜로 솔질(cgn.go) | `brushOffScute` | 자기 |

## 남긴 것
- **몹 일반 장착 이벤트**(`Mob.setItemSlot → onEquipItem` 의 소리·equip/unequip): 바닐라는 몹 칸이 바뀔 때마다 부른다. Furnace 는 `ItemStack` 이 불변이라
  내구도 깎기·쇠뇌 장전처럼 바닐라가 스택을 제자리에서 고치는 곳도 `setItemSlot` 을 부른다(84곳). 그대로 걸면 투구가 햇빛에 닳을 때마다 장착 소리가 난다.
  "교체"와 "제자리 수정"을 나눈 뒤에 한다.
- 낙타 돌진(`entity_action`)은 돌진 자체가 아직 없다. 스폰 알을 물에 쓰기(`entity_place`)도 기능이 없다.
- 상자 배·상자 수레를 열 때 피글린 분노(`TODO(피글린)`)는 블록 쪽 피글린 훅 작업과 함께 한다. 스트라이더 안장 equip 은 스트라이더 PR(#3) 쪽.
- `Player.interactOn` 의 아이템 상호작용(`entity_interact`)은 이름표만 확인했다(염료·가위 등 다른 아이템 경로는 각 몹 코드에 흩어져 있어 전수 확인 못 함).

## 검증
- 단위: `EntityGameEventsTest` 13건(종류·출처·자리).
- 오프라인 서버 + mineflayer 봇(`tools/bot-test/prge.js`): 조각 감지기를 새로 놓고 각 행동 뒤 `last_vibration_frequency` 를 읽었다.
  기준 브랜치 jar 는 10가지 모두 0, 이번 변경 jar 는 10가지 모두 기대값.

| 행동 | 기대 빈도 | 기준 | 변경 |
|---|---|---|---|
| 상자 수레 열기 / 닫기 | 10 / 9 | 0 / 0 | 10 / 9 |
| 배 타기 / 내리기 | 6 / 5 | 0 / 0 | 6 / 5 |
| 방패 내리기, 낚싯대 감기 | 3, 3 | 0, 0 | 3, 3 |
| 철 투구 우클릭 장착, 돼지 안장 | 5, 5 | 0, 0 | 5, 5 |
| 황금 사과 먹기 | 8 | 0 | 8 |
| 물에 떨어지기 | 2 | 0 | 2 |
