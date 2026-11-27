# 2026-11-27 영역5: Paper LootGenerateEvent 이식

## 동작 (Paper 바이트코드 대조)
- 표를 굴린 **뒤**, 칸 섞기 **전**에 발사한다. 취소되면 그대로 돌아온다(칸 변화 없음).
- 이벤트의 loot 목록이 굴린 결과를 대체한다(`setLoot`).
- plugin 값: 자연 생성·`LootTable.fill` = false, `CraftLootTable.fillInventory` = true.
- `CraftLootTable.convertContext` 는 luck 을 넘기지 않는다(Paper 그대로 → 이벤트 luck 0.0).

## 구현
- API `event/world/LootGenerateEvent`(WorldEvent, Cancellable), 서버 `GameEvents.lootGenerate` → `PluginGameEvents`(플래그 `wLootGenerate`).
- 서버 `LootTable.fill(..., review)`: review 가 null 을 돌려주면 취소. 기존 `fill` 은 null 로 위임.
- 호환: `Mappings` 의 `EventBridge.reg`(create: 보관자·LootContext·CraftLootTable 조립, push: loot 되쓰기), `CraftLootTable`, `org.bukkit.loot.LootTable/LootContext`.
- 보관자는 `Supplier<Object>`(holder token)로 넘겨 이벤트 스레드에서 푼다.

## 남은 틈
- `convertContext` 의 TOOL 은 `ItemStack.EMPTY`(바닐라는 `player.getUseItem()`; 호환 Player 에 아직 없음).
- 피해 출처는 처음에 `NmsLootDamage` 로 두었다가, 엔티티 스레드 PR 665 의 DamageSource shim 병합 때 Paper 와 같은 `handle.damageSources().generic()/playerAttack()` 으로 바꿨다.
- `LootContext`/`RandomSource` 받는 굴리기, `Bukkit.getLootTable`, `LootTables` enum 은 아직 없다.

## 검토 필요 파일
- `craft-live-api-methods-1.21.1.txt` 재기록(CraftLootTable 살아있는 메서드 증가).
- `identity/1.21.1/scene-compat-asm-prefilter.txt` bless(commandapi jar 해시 변경) — 금 파일.
