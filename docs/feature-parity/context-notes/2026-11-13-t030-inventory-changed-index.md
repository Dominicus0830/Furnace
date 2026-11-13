# T-030 inventory_changed 색인 (2026-11-13)

- 증거: `furnace/reviews/opt-review/O10-recipes-loot-advancements.md` P-O10-01 (슬롯 변경당 370µs, 리스너 1,419개 × 41칸 세기).
- 바꾼 것: ① 칸 세기는 사건당 한 번, 필요한 수만 센다(구간 조건이 ANY 면 안 센다; 찬/빈 칸은 스택 변환 없이 센다).
  ② 매니저 단위 색인(`AdvancementManager.TriggerIndex`): items 술어가 하나뿐이고 id 목록·태그로 제한한 기준은 바뀐 칸 아이템으로 바로 후보를 찾는다.
  태그는 쓰는 `LootRegistries` 로 처음 쓸 때 풀고(객체가 바뀌면 다시), 나머지 모양(술어 여러 개·items 없음·구간만)은 항상 후보.
  ③ `fireInventoryChanged` 가 41칸 `LootStack` 변환을 미루고(`Criterion.InventoryEvent` 지연 꼴), 대기 조건이 없으면 스냅숏도 안 뜬다.
- 결과 동일: 달성 **순서**는 대기 표(LinkedHashSet) 순서 그대로 — 맞은 것이 둘 이상일 때만 표를 한 번 훑어 정렬한다(취소·재등록으로 표 순서가 흔들려도 같음).
  시험 `InventoryChangedIndexTest`: 옛 매처 사본과 무작위 대조(지연 사건 포함), 색인 키가 필요 조건인지, 바닐라 1,399개 플레이어에서 색인 경로 ≡ 전부 훑는 경로(달성·레시피 보상 순서·저장 파일, 취소로 표 순서 흔들기).
- 측정(4코어 VM, 인벤토리 변환 제외한 trigger 호출, 바닐라 리스너 1,419개): 0.3~0.5µs(옛 370µs). 단 froglights·netherite_armor(술어 여러 개)는 아직 못 달성한 동안 사건마다 41칸 변환을 한다(수 µs) — 필요하면 이름 수준 사전 거르기를 후속으로.
- 보고서와 다른 점: 태그 제한 기준이 3개가 아니라 약 45개(`#minecraft:logs` 등)라 태그도 색인에 넣었다.
