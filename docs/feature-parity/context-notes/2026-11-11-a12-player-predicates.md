## 2026-11-11 A12 플레이어 술어: looking_at·stats·모르는 필드 (A12-01·A12-11, 묶음 1)

보고서 `docs/audit/findings/A12-item-recipe-loot-advancement.md` 수정 묶음 1(술어 — 플레이어 필드). 아이템 하위 술어(`jukebox_playable` 등 A12-02)는 A13 묶음 1 이 한 번에 고치므로 건드리지 않았다.

**looking_at (A12-01).** 바닐라 `dp`(PlayerPredicate) 바이트코드를 따라 `LootEntities.PlayerView.lookingAt` 이 계산한다.
- 눈(`eyeY`)에서 시선 벡터 × 100 까지의 선분, 그 상자를 1.0 키운다. `Level.getEntities(player, box, !spectator)` 의 모든 엔티티(탈 것·아이템 포함, 플레이어 별도)를 `bb.clip(눈, 끝)`(여유 0)으로 맞춰 가장 가까운 것을 고른다. 엔더 드래곤을 잡으면 바로 뒤에 파트 8개가 조건만으로 붙는다(바닐라 `Level.getEntities`). 파트에 맞아도 종류는 `ender_dragon`.
- 그 엔티티까지 `hasLineOfSight`(눈에서 눈, 128칸 안, 충돌 모양에 안 막힘)도 참이어야 한다 — 벽 뒤 앵무새는 달성 안 됨. 이 검사는 `LootEntity.typeSpecific("looking_at")` 쪽이 해서 벽 뒤면 `null` 을 돌려주고, `EntityPredicate` 는 하위 조건을 **플레이어 위치를 원점으로** 평가한다(바닐라 `matches(player, target)`).
- 한계: 그 플레이어의 region 레벨이 가진 엔티티만 본다(투사체 `findHitEntity` 와 같은 범위). 기본 설정(`region-section-shift=4` 구역 256블록, `region-merge-radius=1`)에서는 101칸 안이 늘 같은 region 이라 드러나지 않는다. 드러나는 때는 합치기가 한두 틱 미뤄진 순간과 관리자가 `region-section-shift` 를 1~2 로 낮춘 경우뿐이다. 플레이어는 `forEachPlayerIn` 이 차원 전체를 훑는다(엔티티와 범위가 다름).
- **소유 스레드만 계산한다**(`PlayerView.lookingAt` 은 `onOwnerThread` 아니면 null). 처치자가 포털로 다른 region 에 간 뒤 죽는 쪽 스레드에서 스냅샷하면 남의 EntityStore 를 훑게 되기 때문이다. 발전 과제 player 조건은 `triggerAdvancement` → 소유 스레드의 살아 있는 보기로 평가되므로 using_item(망원경)은 그대로 맞는다. 처치·피해 문맥에서 **다른 플레이어**의 looking_at 을 보는 데이터 팩은 소유 스레드가 아닐 때 거짓이 된다(가정, 바닐라 데이터에는 없음).
- 비용: 스냅샷 경로의 미리 뜨기는 `EntityPredicate.lookingAtUsed`(전리품·술어 파일에서 looking_at 을 읽었을 때만 켜짐 — 발전 과제 player 조건은 `liveViewOnly` 로 읽어 켜지 않으므로 바닐라 데이터만으로는 꺼져 있다)일 때만 하고, `KillCredit.snapshot` 은 처음 물을 때 한 번 센다(독립 검토 R1). `ExecuteConditions` 는 소유 스레드에서 `KillCredit.snapshot(hit)` 으로 떠 둔다(R2).
- 알려진 기존 한계(이 PR 밖, 검토 R3): `ExecuteConditions.snapshot` 이 effects·nbt·vehicle 등을 안 떠서 `execute if predicate` 의 그 조건들은 늘 거짓이다.

**stats.** `{type, stat, value?}` 목록. 바닐라 stat_type 9종과 그 레지스트리 값이 아니면 파일을 거부(코덱 오류). 현재 값은 `PlayerStats` 에서 읽고(통계가 안 붙은 플레이어는 0), 키는 `stat:<종류>|<값>`. 통계 **카운터** 자체(걷기·점프 등 21종)가 안 오르는 문제는 A12-04(묶음 3)라 이 PR 범위가 아니다.

**is_flying 플래그.** 바닐라 `EntityFlagsPredicate`(`bt`)에는 `is_flying`(겉날개 비행 또는 플레이어 abilities.flying)이 있는데 Furnace 에 빠져 있어 늘 거짓이었다. 같은 자리라 함께 고쳤다.

**A12-11 — 보고서와 다르게 고친 점.** 보고서는 "모르는 필드는 바닐라가 파일을 거부하니 같게 거부"라고 제안했지만, 바닐라 `EntityPredicate`(`bv`)·`LocationPredicate`(`df`)·`EntityFlagsPredicate`(`bt`)·`PlayerPredicate`(`dp`)는 `RecordCodecBuilder` 로 만든 코덱이라 **모르는 키를 오류로 보지 않고 무시한다**(데이터 팩 오타가 조용히 빠지는 바로 그 동작). 거부하면 오히려 바닐라와 달라진다. 그래서: 이름이 바닐라 필드에 없으면 경고만 남기고 무시한다(조건 전체를 거짓으로 만들던 `unsupported` 필드는 없앴다). 반대로 **값이 틀린 것**(모르는 통계 종류·값 등)은 코덱 오류라 거부한다. 모르는 `type_specific.type` 은 dispatch 코덱 오류라 거부한다(바닐라 19종 `bx` 와 대조, 모르는 플래그는 값도 안 본다). 바닐라 필드 이름(`bv` 15 + 위치 3, `df` 9, `bt` 7, `dp` 6)은 바이트코드의 `ldc` 문자열로 하나하나 대조했고 Furnace 는 전부 읽는다.
