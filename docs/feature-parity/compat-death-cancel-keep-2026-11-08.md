# 사망 취소·getItemsToKeep·몹 사망 드롭 — 2026-11-08

PR #164(플레이어 사망 드롭) 후속. `compat-death-drops-2026-11-08.md` 의 "아직 다른 점" 1~3번을 채운다. Paper 1.21.1(커밋 33ff3dff) 패치
`Improve-death-events`·`PlayerDeathEvent-getItemsToKeep`·`PlayerDeathEvent-shouldDropExperience`·`Restore-vanilla-entity-drops-behavior` 의 관측 동작을 따랐다(코드는 옮기지 않았다).

## 플레이어 (`PlayerDeathEvent`)

- **취소**: Furnace `PlayerDeathEvent` 가 `Cancellable` 이 됐다. Bukkit `setCancelled(true)` 도 이제 `UnsupportedFeature` 가 아니다.
  취소하면 죽지 않는다 — 체력이 0 이하면 `reviveHealth`(기본 = 죽는 순간 최대 체력)로 되돌리고, 사망 메시지·사망 화면(`PlayerCombatKill`)·
  엔티티 이벤트 3·드롭·인벤토리 비우기·경험치 구슬·통계·마지막 사망 위치·`entity_die`(스컬크 촉매)·불 끄기가 모두 없다.
  체력 패킷은 틱 끝에 한 번 나가므로 클라이언트는 체력 0 을 보지 않는다(Paper 가 hurt 동안 체력 패킷을 미뤄 두는 것과 같은 결과).
- 그래서 이벤트를 죽음 처리 맨 앞으로 옮겼다(Paper 처럼 `entity_die` 는 취소 검사 뒤). 사망 메시지는 이벤트 전에 만든다.
- **`reviveHealth`/`getReviveHealth`·`setReviveHealth`**: 0 이하(최대 체력이 0 이 아닐 때)·최대 체력 초과는 `IllegalArgumentException`(Paper 와 같다).
- **`itemsToKeep()`/`getItemsToKeep()`**: keepInventory 가 false 일 때만 본다. 인벤토리를 비울 때 칸마다(주 인벤토리 → 갑옷 → 왼손) 목록에 같은 값
  (종류·개수·컴포넌트 — Bukkit `ItemStack.equals`)이 있으면 그 칸을 남기고 목록에서 하나 지운다. 소멸 저주 아이템은 목록에 있어도 칸을 비운다.
  맞는 칸이 없던 남은 항목은 `CraftInventory.addItem` 처럼 주 인벤토리 0..35 의 덜 찬 같은 아이템 칸 → 첫 빈 칸 순으로 넣고, 자리가 없으면 버린다.
  남긴 아이템을 `drops()` 에서 빼지 않으면 복제된다(Paper 문서 그대로).
- **`shouldDropExperience`**: false 면 `droppedExp` 와 무관하게 구슬이 없다.

## 몹 (`EntityDeathEvent`)

- Furnace `EntityDeathEvent` 에 `drops()`(바꿀 수 있는 목록)·`droppedExp`·`reviveHealth`·취소가 생겼다. Bukkit 몹 `getDrops`·`getDroppedExp`·
  `setDroppedExp`·`setCancelled`·`getReviveHealth`·`setReviveHealth` 가 이어진다(`UnsupportedFeature` 없음). Bukkit `EntityEvent#getEntityType` 도 더했다.
- **구독자가 없으면 바닐라 그대로**(드롭을 바로 던진다, 비용 0). 구독자가 있으면:
  1. 전리품 표 → 특수 전리품 → 장비(안장·상자·주머니)를 바닐라 순서대로 굴려 아이템 엔티티를 **만들기만** 한다(위치·속도 난수는 바닐라와 같은 자리).
     몹 장비 칸 비우기(`Mob` 장비 드롭)·상자 떼기(`AbstractChestedHorse`)는 이벤트 뒤로 미룬다(Paper `clearedEquipmentSlots`·`postDeathDropItems`).
  2. 경험치 기본값은 바닐라 `dropExperience` 의 조건(플레이어가 잡음·doMobLoot 등)을 지금 본 값(CraftBukkit `getExpReward`). 사망 소리의 음 높이 난수도 바닐라 자리에서 뽑아 둔다.
  3. 이벤트. 취소면 `dead=false`, 체력 = `reviveHealth`(기본 최대 체력) — 드롭·경험치·사망 소리·엔티티 이벤트 3·점수 없음, 장비·안장·상자 그대로.
  4. 아니면 미룬 정리 → 사망 소리 → 처치 점수 → `killed:` 통계·`killedEntity` → `entity_die` → 목록 순서대로 드롭 → 경험치(플러그인이 정한 양, 0 이하면 없음,
     촉매가 가져갔으면 없음).
- 손대지 않은 아이템은 미리 만든 엔티티를 그대로 넣는다(네더의 별 긴 수명 등 유지). 바뀌었거나 새로 넣은 아이템은 몸 위치에 새 아이템 엔티티(`CraftWorld.dropItem` 과 같은 꼴, 줍기 지연 10).

## Paper 와 아직 다른 점

1. 사망 소리 API(`shouldPlayDeathSound`·`setDeathSound`·`setDeathSoundVolume` 등)는 없다. 소리는 바닐라대로 나고, 취소하면 나지 않는다.
2. 갑옷 거치대(`ArmorStand`)는 여전히 `EntityDeathEvent` 를 부르지 않는다(Furnace 갑옷 거치대는 `die` 를 거치지 않는다).
3. Paper 는 몹 경험치를 `entity_die` 전에 떨궈서 스컬크 촉매가 있어도 구슬이 나오고 촉매도 충전된다(바닐라와 다름). Furnace 는 바닐라처럼 촉매가 가져가면 구슬이 없다.
   또 Paper 는 `killedEntity`(좀비가 주민을 감염) 결과와 무관하게 드롭하지만 Furnace 는 바닐라처럼 감염되면 드롭하지 않는다.
4. 이벤트를 들을 때 스컬크 촉매가 근처에 있으면 촉매의 꽃 피는 소리 난수와 아이템 엔티티 속도 난수의 순서가 바닐라와 바뀐다(아이템을 먼저 만든다). Paper 도 순서가 바뀐다.
5. 개수만 바뀐 아이템(같은 종류)은 Paper 가 원래 떨굼 방식(높이 보정 등)을 쓰지만 Furnace 는 새 엔티티를 몸 위치에 만든다(몹 사망 드롭은 높이 보정이 0 이라 차이는 엔티티 추가 속성뿐).
6. 알레이 손·주머니, 피글린 주머니처럼 전리품을 모으며 이미 비운 것과 충전 크리퍼의 머리 떨굼 횟수는 취소해도 돌아오지 않는다(Paper 도 같다).
7. 소멸 저주 아이템을 `itemsToKeep` 에 넣으면 칸은 비우되 목록에서 지워지지 않아 남은 항목으로 다시 넣어진다(Paper processKeep 과 같다).
8. 이벤트를 들을 때 좀비가 주민을 감염시키면(`killedEntity` 가 false) 전리품 난수를 이미 굴린 뒤라 바닐라와 난수 소비가 다르다(감염된 주민은 사라지므로 관측 차이는 거의 없다).
9. `reviveHealth` 검사는 이벤트를 만들 때의 최대 체력 기준이다(Paper 는 부르는 순간의 속성값). 처리기가 최대 체력을 올린 뒤 그보다 큰 값을 주면 거부된다.
10. 처치 점수(`player_killed_entity` 등)는 미룬 장비 정리보다 먼저 돈다 — 조건이 떨군 장비를 본다(바닐라·Paper 순서, PR #166 검토에서 고침, 시험 `MobDeathKillCreditOrderTest`).
11. 플레이어 사망이 소유 스레드가 아닌 곳(명령 스레드의 `/kill` 등)에서 취소될 때, 그 사이 소유 틱이 체력 0 을 클라이언트에 보낼 수 있는 아주 좁은 틈이 있다.

## 시험

- `furnace-server` `PlayerVitalsTest#cancelledDeathRevivesWithoutAnyDeathEffects`: 취소 → reviveHealth, 메시지·사망 화면·이벤트 3·드롭 처리·사망 기록 없음, 다시 죽이면 평소처럼.
- `furnace-server` `EntityWorldApiTest#entityDeathEventCarriesDropsAndCancelKeepsMobAliveWithGear`: 돼지의 투구·안장이 목록에 실리고, 취소하면 살아서 장비 그대로, 아니면 바꾼 목록·경험치대로.
- `furnace-test` `PlayerDeathDropsTest`: itemsToKeep 칸 유지·새 아이템 추가·덜 찬 칸 먼저·keepInventory 면 무시, 취소, shouldDropExperience.
- `furnace-compat-paper` `BukkitDeathDropsTest`: 진짜 Bukkit 플러그인 jar 로 취소·setReviveHealth·소울바운드식 getItemsToKeep·몹 getDrops 편집·몹 취소.
- `furnace-test` `MobDeathKillCreditOrderTest`(습격 대장 처치 점수가 깃발을 본다), `PlayerDeathKeepCountTest`(같은 값 두 칸 중 하나만 남기기, 취소 뒤 재사망) — 독립 검토 재현 시험.
- 계약 표(`docs/api/event-cancel-contract.md`, `CancelContractTest`)에 두 이벤트를 `SERVER_DRIVEN` 으로 올렸다.
