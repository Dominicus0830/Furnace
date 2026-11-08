# 플레이어 사망 드롭 (PlayerDeathEvent#getDrops 등) — 2026-11-08

PR #161 검토에서 나온 후속: 무덤·keep-inventory 류 플러그인은 `getDrops().clear()` 를 부르는데 호환 계층이 `UnsupportedFeature` 를 던져 죽을 때마다 오류 로그가 났다. 이제 플레이어 사망은 Paper 1.21.1 처럼 드롭 목록을 싣고, 바꾼 값이 실제 드롭에 반영된다. Paper 코드를 옮기지 않고 관측 가능한 동작만 따랐다(1.21.1 패치 `Restore-vanilla-entity-drops-behavior`·`Improve-death-events`, 최신 main `CraftEventFactory` 로 동작 확인).

## 지원하는 것

Furnace API `PlayerDeathEvent`: `drops()`(바꿀 수 있는 목록), `droppedExp()`, `newExp()`·`newLevel()`·`newTotalExp()` 를 더했다. 기존 `deathMessage`·`keepInventory`·`keepLevel` 은 그대로. Bukkit `PlayerDeathEvent` 는 `getDrops`·`getDroppedExp`·`setDroppedExp`·`getNewExp`·`getNewLevel`·`getNewTotalExp`(와 setter)가 이어진다.

- **드롭 목록의 기본값**: 죽는 순간 인벤토리를 `Inventory.getContents` 순서(주 인벤토리 → 갑옷 발·다리·가슴·머리 → 왼손)로 모은 비어 있지 않은 칸. 소멸 저주 아이템은 뺀다. `keepInventory` 게임룰이 켜졌거나 관전자면 빈 목록.
- **목록과 keepInventory 는 독립(Paper 와 같다)**: 이벤트가 끝나면 목록에 남은 아이템을 순서대로 던지고(keepInventory 와 무관), `keepInventory` 가 false 면 그와 따로 인벤토리를 전부 비운다. 그래서 `setKeepInventory(true)` 만 하고 목록을 비우지 않으면 아이템이 떨어지면서 인벤토리에도 남고(복제 — Paper 도 그렇다), 흔한 `setKeepInventory(true); getDrops().clear();` 는 아무것도 안 떨어지고 그대로 남는다.
- 목록의 비었거나 null 인 칸·개수 0 이하는 건너뛴다. Bukkit 쪽은 목록에서 스택을 직접 고쳐도(`setAmount` 등) 이벤트 뒤 변환해 되돌려 쓴다.
- **던지는 방식**: 이전과 같은 `Inventory.dropAll` 식(아무 쪽으로, 던진 이 소유권 없음, 같은 난수 순서). 다만 이전엔 `PlayerDropItemEvent` 를 불러 취소하면 아이템이 인벤토리로 돌아왔는데, Paper 는 사망 드롭에서 부르지 않으므로 이제 부르지 않는다(바닐라 차이 하나 줄임).
- **경험치**: `droppedExp` 기본은 바닐라 `getBaseExperienceReward`(레벨×7, 최대 100, keepInventory·관전자는 0). 플러그인이 정한 양이 그대로 구슬로 나온다(keepInventory 여도, 0 이하면 없음). 스컬크 촉매가 먼저 가져가면 구슬이 없는 것은 그대로.
- **리스폰 경험치**(`keepLevel` 이 false 일 때): Paper `ServerPlayer.reset` 처럼 레벨 = `newLevel`, 총량 = `newTotalExp`, 진행도 0, 그 뒤 `newExp` 를 더한다(기본 모두 0 이라 이전과 같다). `keepLevel` 이 true 면 레벨·경험치를 그대로 둔다.
- 관전자는 이제 이벤트에 `keepInventory=true`·`keepLevel=true` 로 전달된다(Paper 와 같다). 죽을 때 열린 창은 관전자도 닫는다.
- 플러그인이 목록에 넣은 알 수 없는 아이템·컴포넌트는 경고 로그를 남기고 버린다(죽음 처리는 계속).
- 손대지 않은 아이템은 API 값으로 변환했다 돌려놓지 않고 원래 스택을 그대로 쓴다(컴포넌트 손실 방지).

## Paper 1.21.1 과 아직 다른 점

1. 사망 취소(`PlayerDeathEvent#setCancelled(true)`)는 `UnsupportedFeature` 다. 몹 `EntityDeathEvent` 의 드롭·경험치·취소도 아직 없다(몹 전리품 후속).
2. Paper 전용 `getItemsToKeep()`·`shouldDropExperience`·`getReviveHealth`·사망 소리 설정은 없다.
3. `ENTITY_DIE` 게임 이벤트(스컬크 촉매)는 Paper 의 취소 가능 사망 패치처럼 이벤트 뒤가 아니라 이벤트 전에 불린다(취소가 없어 관측 차이는 없다).
4. 이벤트에서 본 목록은 죽는 순간 인벤토리의 사본이다. 이벤트 뒤 인벤토리를 비우는 일은 연결 스레드에서 하므로, 그 사이(같은 틱 안)에 바뀐 칸은 목록에 비치지 않고 비울 때 함께 사라질 수 있다(Paper 는 같은 스레드라 사이가 없다).
5. 같은 Furnace 이벤트를 여러 플러그인이 받을 때 Bukkit 목록은 이벤트당 하나를 공유한다(Paper 와 같다). Furnace 고유 처리기와 Bukkit 처리기가 섞이면 처리기가 고친 값이 차례로 이어진다.

## 시험

- `furnace-test` `PlayerDeathDropsTest`(Furnace 고유 이벤트): 기본 드롭·순서·소멸 저주 제외, 목록 수정이 실제 드롭에 반영, keepInventory 와 목록이 독립, 게임룰 keepInventory 의 빈 목록, 경험치 기본·변경, newLevel/newExp 리스폰, `PlayerDropItemEvent` 미호출, 알 수 없는 아이템.
- `furnace-compat-paper` `BukkitDeathDropsTest`: 진짜 Bukkit 플러그인 jar(무덤식·목록 편집·취소 요청)로 같은 것을 확인한다.
