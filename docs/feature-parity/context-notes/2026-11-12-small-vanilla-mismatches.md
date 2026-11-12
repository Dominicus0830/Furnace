# 엔티티·AI 작은 바닐라 차이 묶음 대조 기록 (2026-11-12)

(독립 검토 R5~R10 반영분과 Activity 해시 C-B1 은 맨 아래 "검토 반영" 절.)

출처: `furnace/reviews/opt-review/O05-entities.md` 5장 C2·C4. 바닐라 1.21.1 Mojang 이름 소스는 extracted_minecraft_data `client1.21.1` 으로 읽었다(커밋 안 함).

## O05 C2 — 속성 값 계산 순서·수정자 순서
- 보고서의 "바닐라는 차례로 더하고 곱한다"는 맞지만 **"넣은 순서"는 틀렸다.** 1.21.1 `AttributeInstance.modifiersByOperation` 은 연산마다 `Object2ObjectOpenHashMap<ResourceLocation, AttributeModifier>`(해시 순서)이고, 넣은 순서 배열 맵 `Object2ObjectArrayMap` 은 `modifierById`(getModifiers·저장·패킷 순서)뿐이다.
- 그래서 `calculateValue` 는 ADD_VALUE 를 해시 맵 순서대로 `d += amount`, ADD_MULTIPLIED_BASE 를 `e += d * amount`(d = 더하기 뒤 값), ADD_MULTIPLIED_TOTAL 을 `e *= 1 + amount` 로 하나씩 한다. 이전 Furnace 는 합을 먼저 구해 `d + d*Σ`, `Π` 로 한 번에 곱해 마지막 비트가 달랐다.
- 고침: `ModifierOrder`(fastutil 열린 주소 해시 순회 순서 재현 — `ResourceLocation.hashCode` = 31×ns + path, `HashCommon.mix`, 선형 탐사·shiftKeys·rehash 증가/축소 포함)로 연산별 순서를 불변 값으로 들고 `value()` 가 그 순서로 계산한다. 실제 fastutil 8.5.12 `Object2ObjectOpenHashMap` 과 무작위 넣기·빼기 약 298만 단계 비교해 순회 순서가 모두 같았다(스크래치 도구, 커밋 안 함; 풀 120개·표 증가·축소 포함).
- `addOrReplaceModifier` 는 바닐라 `addOrReplacePermanentModifier` 처럼 지우고 다시 넣는다(`modifiers()` 순서에서 끝으로). 계산 순서는 해시 칸이 정하므로 같은 칸 충돌이 없으면 그대로이고, **충돌이 있으면 바뀐다**(예: 32칸 표에서 `test:m12`·`test:m13` 은 같은 칸 — 처음 [m13, m12], 지우고 다시 넣으면 [m12, m13], fastutil 실측과 같음). `addOrReplaceTransientModifier`(저장 안 함, 제자리 교체·같은 값이면 안 건드림)는 바닐라 `addOrUpdateTransientModifier` 에 해당한다. 바닐라와 다른 점(의도): 연산이 바뀌면 옛 연산 표에서 뺀다(바닐라는 안 빼 이중 계산 버그), 저장 목록은 transient 로 바뀐 만큼 따라간다.
- **알려진 한계(후속):** ① 플레이어 값은 `PlayerAttributes.compute` 가 계산 때마다 새 인스턴스에 "효과 → 장비 → 달리기 → 위치" 순서로 넣어, 같은 칸 충돌이 있으면 바닐라의 시간 순 삽입 이력과 순서가 다를 수 있다. ② 바닐라가 `remove` 뒤 `addTransientModifier` 를 쓰는 곳(예: `LivingEntity` 인챈트 속성)을 Furnace 가 제자리 교체로 쓰면 충돌 때 마지막 비트가 달라질 수 있다. 둘 다 평소엔 같은 연산에 같은 칸 충돌 수정자가 둘 이상일 때만 생긴다.

## O05 C3 · O06 C2 · O06 C3 (이 묶음에서 제외)
- O05 C3(RandomSource 씨앗)은 PR #272 로 병합됐다. O06 C2(LookAtPlayerGoal 타게팅 조건)·C3(Sensor·ShufflingList 난수)는 PR #274 가 같은 코드를 이미 고치고 있어 중복을 피하려고 이 묶음에서 뺐다(#274 의 `createThreadSafeFor`·`canBeSeenByAnyone` 이 그 몫).

## O05 C4 — B.isTag 캐시와 /reload (바뀐 것 없음)
- `B.TAGS` 는 `BlockTags.get()`(jar 의 `worldgen/1.21.1/block_tags.txt`, 불변)만으로 채워진다. `/reload` 는 `DataPackManager` 가 `TagSet`(레지스트리 숫자 id)을 `ItemContext.installTags` 에 걸 뿐 `BlockTags`·`B.TAGS` 를 안 건드린다. 캐시가 낡을 원본 변화가 없으므로 무효화할 것이 없다.
- 더 큰 차이: 데이터 팩 블록 태그가 `B.isTag`·`BlockTags` 호출처 어디에도 안 들어간다(`isInTag("minecraft:block", …)` 호출처 없음). → `docs/user-todo/entries/2026-11-12-small-vanilla-mismatches.md` 결정 1.

## 검토 반영 (PR #280 독립 검토 2건)
- **R5 `LegacyRandomSource.nextDouble`**: `long × 1.110223E-16F` 는 float 곱셈이라 정밀도를 잃고 최댓값이 1.0 이 됐다. 바닐라 `BitRandomSource.nextDouble` 은 `(double)l × 1.1102230246251565E-16`. 고침(JDK `Random.nextDouble` 과 같은 씨앗·같은 수열 시험). 이 난수를 쓰는 모든 곳(가우스 포함)의 값이 바닐라와 같아진다.
- **R6 효과 수정자**: 바닐라 `MobEffect.addAttributeModifiers` 는 `addPermanentModifier`(저장). Furnace `LivingEntity.applyEffectAttributes` 가 transient 라 청크를 다시 불러오면 수정자가 사라졌다 → `addOrReplaceModifier`(영구). 효과 자체는 효과 목록에서 되살아나고 속성 NBT 가 수정자를 되살린다(바닐라와 같다). 플레이어는 `PlayerAttributes` 가 효과 목록에서 매번 계산한다.
- **R7**: `State.replace` 주석을 "바닐라와 다름(의도)"로 고침(연산이 바뀌면 바닐라는 옛 연산 맵에서 빼지 않아 이중 계산, 영구 id 위에 쓰면 permanentModifiers 를 안 건드림).
- **R8 `AttributeMap.load`**: 저장된 수정자 id 가 이름공간 없이 `foo` 면 `minecraft:foo` 로 맞춘다(바닐라는 `ResourceLocation` 으로 파싱). 가정(바닐라와 다름): 바닐라 `load` 는 기존 수정자를 비우지 않지만 Furnace 는 비운다 — 저장 목록이 그 속성의 영구 수정자 전부를 담으므로 결과는 같고, 만들 때 단 기본 수정자가 저장에 없을 때만 다르다.
- **R9 계산 비용**: `PlayerAttributes.compute`(새 인스턴스 + 수정자 몇 개 + value)가 이 PR 로 약 2배 느려졌다(검토자 측정 255~318 → 497~594 ns). `idHash` 가 문자열을 자르지 않고 해시를 직접 합치고, `AttributeInstance.addAllModifiers`(상태·표 복사 한 번)로 플레이어 계산이 한 번에 단다(같은 id 가 겹치면 하나씩 단 것과 같은 결과로 되돌린다).
- **R10 가정**: 플레이어 값은 새 인스턴스에 "효과 → 장비 → 달리기 → 위치" 순서로 단다. 바닐라 표는 넣고 뺀 이력을 따르므로 같은 연산 표 안에 선형 탐사 충돌이 있으면 순서가 다를 수 있다(바닐라 수정자 id 의 실제 칸 충돌은 검토자가 확인한 범위에서 드물다).
- **R1~R4·R11**(Piglin 센서·복어 크리에이티브, 판다, 감싸개 `canBeSeenByAnyone`, 시험 모드 ShufflingList 같은 난수열)은 #274 몫이라 거기서 처리한다(#274 구현 스레드에 알림).

## Activity.hashCode (C-B1, 반론 B)
- 바닐라 `Activity` 는 `hashCode = name.hashCode()`, `equals` 는 이름 비교. Furnace 는 재정의가 없어 식별 해시라 두뇌의 `HashMap<Activity, …>`(`availableBehaviorsByPriority` 안쪽 맵) 순회 순서가 실행마다 달라, 같은 우선순위의 core·idle 행동 시작 순서와 난수 소비가 바닐라와 어긋났다. 이름 기반 `hashCode`·`equals` 로 맞췄다(바닐라 활동은 `play → core → idle → work` 순으로 돈다 — 시험으로 고정).
- 두뇌 컨테이너: 안쪽 맵은 이미 `new HashMap<>()`(기본 용량 16 = 바닐라 `Maps.newHashMap()`)·같은 넣는 순서. `activeActivities` 를 `LinkedHashSet` 에서 `HashSet`(바닐라 `Sets.newHashSet`)으로, `coreActivities` 는 `HashSet` 이던 것을 호출처의 `Set.of`(JVM 마다 순서가 다름) 대신 넣은 순서 `LinkedHashSet` 복사로(바닐라는 `ImmutableSet`, 넣은 순서) 맞췄다. 이 둘은 난수를 쓰지 않는 곳(`getActiveNonCoreActivity`, 지울 기억 순서)에서만 돈다. 플러그인 활동(`ns:name`)은 키 문자열의 해시.
