# 엔티티·AI 작은 바닐라 차이 묶음 대조 기록 (2026-11-12)

출처: `furnace/reviews/opt-review/O05-entities.md` 5장 C2~C4, `O06-mob-ai-spawning.md` 3장 C2·C3. 바닐라 1.21.1 Mojang 이름 소스는 extracted_minecraft_data `client1.21.1` 으로 읽었다(커밋 안 함).

## O05 C2 — 속성 값 계산 순서·수정자 순서
- 보고서의 "바닐라는 차례로 더하고 곱한다"는 맞지만 **"넣은 순서"는 틀렸다.** 1.21.1 `AttributeInstance.modifiersByOperation` 은 연산마다 `Object2ObjectOpenHashMap<ResourceLocation, AttributeModifier>`(해시 순서)이고, 넣은 순서 배열 맵 `Object2ObjectArrayMap` 은 `modifierById`(getModifiers·저장·패킷 순서)뿐이다.
- 그래서 `calculateValue` 는 ADD_VALUE 를 해시 맵 순서대로 `d += amount`, ADD_MULTIPLIED_BASE 를 `e += d * amount`(d = 더하기 뒤 값), ADD_MULTIPLIED_TOTAL 을 `e *= 1 + amount` 로 하나씩 한다. 이전 Furnace 는 합을 먼저 구해 `d + d*Σ`, `Π` 로 한 번에 곱해 마지막 비트가 달랐다.
- 고침: `ModifierOrder`(fastutil 열린 주소 해시 순회 순서 재현 — `ResourceLocation.hashCode` = 31×ns + path, `HashCommon.mix`, 선형 탐사·shiftKeys·rehash 증가/축소 포함)로 연산별 순서를 불변 값으로 들고 `value()` 가 그 순서로 계산한다. 실제 fastutil 8.5.12 `Object2ObjectOpenHashMap` 과 무작위 넣기·빼기 약 298만 단계 비교해 순회 순서가 모두 같았다(스크래치 도구, 커밋 안 함; 풀 120개·표 증가·축소 포함).
- `addOrReplaceModifier` 는 바닐라 `addOrReplacePermanentModifier` 처럼 지우고 다시 넣는다(`modifiers()` 순서에서 끝으로). 해시 맵이라 계산 순서는 그대로(재삽입 순서 효과는 `modifiers()` 순서에만). `addOrUpdateTransientModifier`(제자리 교체, 같은 값이면 안 건드림)를 새로 두었다 — 호출처를 바닐라가 쓰는 쪽으로 바꾸는 일은 이번에 안 했다(순서가 `modifiers()` 에만 보인다).

## O05 C3 — RandomSource.create 씨앗 (이 묶음에서 제외)
- PR #272(주인 열쇠 이음매, `RandomSource.createFor`)가 맡아 병합됐다. 이 묶음은 그 위에서 `createFor`/`createThreadSafeFor` 만 쓴다.

## O05 C4 — B.isTag 캐시와 /reload (바뀐 것 없음)
- `B.TAGS` 는 `BlockTags.get()`(jar 의 `worldgen/1.21.1/block_tags.txt`, 불변)만으로 채워진다. `/reload` 는 `DataPackManager` 가 `TagSet`(레지스트리 숫자 id)을 `ItemContext.installTags` 에 걸 뿐 `BlockTags`·`B.TAGS` 를 안 건드린다. 캐시가 낡을 원본 변화가 없으므로 무효화할 것이 없다.
- 더 큰 차이: 데이터 팩 블록 태그가 `B.isTag`·`BlockTags` 호출처 어디에도 안 들어간다(`isInTag("minecraft:block", …)` 호출처 없음). → `docs/user-todo/entries/2026-11-12-small-vanilla-mismatches.md` 결정 1.

## O06 C3 — Sensor·ShufflingList 정적 난수
- 바닐라: `Sensor.RANDOM = RandomSource.createThreadSafe()`, `ShufflingList.random = RandomSource.create()`(목록마다). `ThreadSafeLegacyRandomSource`(CAS 48비트 LCG, 한 스레드에서 `LegacyRandomSource` 와 수열 같음)와 `RandomSource.createThreadSafeFor(열쇠, 용도)`(= `createFor` 의 스레드 안전판)를 추가해 Sensor 에 적용(열쇠 `Sensor.RANDOM`·용도 SENSOR_SCAN 유지), ShufflingList 는 인스턴스 필드 `createFor(key("ShufflingList"), SHUFFLING_LIST)`.
- 켜짐(시험 JVM 결정적 씨앗) 한계: Sensor 난수는 여러 region 스레드가 공유하므로 호출 순서가 스레드 일정에 달려 장면 해시가 흔들릴 수 있다(예전 정적 난수와 같은 성격). ShufflingList 는 목록마다 같은 (열쇠, 용도)라 켜짐일 때 모든 목록이 같은 난수열로 시작한다 — 각 목록의 첫 shuffle 결과는 항목 수·가중치에만 달린다.
- 시험: 8 스레드가 같이 뽑아도 한 스레드가 뽑은 것과 같은 집합(겹침·누락 없음), 한 스레드에서는 `LegacyRandomSource` 와 수열 동일.

## O06 C2 — LookAtPlayerGoal TargetingConditions
- 바닐라: `forNonCombat().range(거리).selector(EntitySelector.notRiding(mob))`, `getNearestPlayer(조건, mob, x, eyeY, z)`; `canContinueToUse` 는 `isAlive`·거리·남은 시간만(관전자 전환 검사 없음).
- 같은 곳에서 발견: Furnace `TargetingConditions.test` 가 첫 검사로 `canBeSeenAsEnemy`(무적 제외)를 써서 **비전투 조건도 크리에이티브 플레이어를 걸렀다**. 바닐라 첫 검사는 `canBeSeenByAnyone`(관전자·사망만 제외)이다. `Targetable.canBeSeenByAnyone`(Player: 살아 있고 관전자 아님)을 두고 첫 검사를 바꿨다. 전투 조건은 뒤의 `canBeSeenAsEnemy` 검사가 그대로라 같다. 유혹·박쥐·돌고래 등 다른 비전투 호출처도 같이 바닐라가 된다.
- 비전투가 아닌 `source == null && isCombat` 의 평화 난이도 TODO 는 이번에 안 건드렸다.
- LookAtPlayerGoal 의 다른 대상 종류(`lookAtType != Player`)는 Furnace 에 없다(생성자가 플레이어 전용).
