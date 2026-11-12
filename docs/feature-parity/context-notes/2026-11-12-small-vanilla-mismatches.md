# 엔티티·AI 작은 바닐라 차이 묶음 대조 기록 (2026-11-12)

출처: `furnace/reviews/opt-review/O05-entities.md` 5장 C2·C4. 바닐라 1.21.1 Mojang 이름 소스는 extracted_minecraft_data `client1.21.1` 으로 읽었다(커밋 안 함).

## O05 C2 — 속성 값 계산 순서·수정자 순서
- 보고서의 "바닐라는 차례로 더하고 곱한다"는 맞지만 **"넣은 순서"는 틀렸다.** 1.21.1 `AttributeInstance.modifiersByOperation` 은 연산마다 `Object2ObjectOpenHashMap<ResourceLocation, AttributeModifier>`(해시 순서)이고, 넣은 순서 배열 맵 `Object2ObjectArrayMap` 은 `modifierById`(getModifiers·저장·패킷 순서)뿐이다.
- 그래서 `calculateValue` 는 ADD_VALUE 를 해시 맵 순서대로 `d += amount`, ADD_MULTIPLIED_BASE 를 `e += d * amount`(d = 더하기 뒤 값), ADD_MULTIPLIED_TOTAL 을 `e *= 1 + amount` 로 하나씩 한다. 이전 Furnace 는 합을 먼저 구해 `d + d*Σ`, `Π` 로 한 번에 곱해 마지막 비트가 달랐다.
- 고침: `ModifierOrder`(fastutil 열린 주소 해시 순회 순서 재현 — `ResourceLocation.hashCode` = 31×ns + path, `HashCommon.mix`, 선형 탐사·shiftKeys·rehash 증가/축소 포함)로 연산별 순서를 불변 값으로 들고 `value()` 가 그 순서로 계산한다. 실제 fastutil 8.5.12 `Object2ObjectOpenHashMap` 과 무작위 넣기·빼기 약 298만 단계 비교해 순회 순서가 모두 같았다(스크래치 도구, 커밋 안 함; 풀 120개·표 증가·축소 포함).
- `addOrReplaceModifier` 는 바닐라 `addOrReplacePermanentModifier` 처럼 지우고 다시 넣는다(`modifiers()` 순서에서 끝으로). 해시 맵이라 계산 순서는 그대로(재삽입 순서 효과는 `modifiers()` 순서에만). `addOrUpdateTransientModifier`(제자리 교체, 같은 값이면 안 건드림)를 새로 두었다 — 호출처를 바닐라가 쓰는 쪽으로 바꾸는 일은 이번에 안 했다(순서가 `modifiers()` 에만 보인다).

## O05 C3 · O06 C2 · O06 C3 (이 묶음에서 제외)
- O05 C3(RandomSource 씨앗)은 PR #272 로 병합됐다. O06 C2(LookAtPlayerGoal 타게팅 조건)·C3(Sensor·ShufflingList 난수)는 PR #274 가 같은 코드를 이미 고치고 있어 중복을 피하려고 이 묶음에서 뺐다(#274 의 `createThreadSafeFor`·`canBeSeenByAnyone` 이 그 몫).

## O05 C4 — B.isTag 캐시와 /reload (바뀐 것 없음)
- `B.TAGS` 는 `BlockTags.get()`(jar 의 `worldgen/1.21.1/block_tags.txt`, 불변)만으로 채워진다. `/reload` 는 `DataPackManager` 가 `TagSet`(레지스트리 숫자 id)을 `ItemContext.installTags` 에 걸 뿐 `BlockTags`·`B.TAGS` 를 안 건드린다. 캐시가 낡을 원본 변화가 없으므로 무효화할 것이 없다.
- 더 큰 차이: 데이터 팩 블록 태그가 `B.isTag`·`BlockTags` 호출처 어디에도 안 들어간다(`isInTag("minecraft:block", …)` 호출처 없음). → `docs/user-todo/entries/2026-11-12-small-vanilla-mismatches.md` 결정 1.
