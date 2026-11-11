# A13-01 아이템 하위 술어 공용 코덱 (2026-11-11)

명령 인자(`item_predicate` 의 `종류~값`)와 전리품·발전 과제(`loot/ItemPredicate`)가 같은 코드 `item/ItemPredicates` 를 쓴다.
아이템은 `item/ItemView`(엔티티 ItemStack·LootStack·컴포넌트 속 정규 아이템), 레지스트리·태그는 `item/RegistryView`(명령=ItemContext, 전리품=LootRegistries)로 본다.

## 바닐라 대조(1.21.1 바이트코드)

- 레지스트리 `cu`: damage, enchantments, stored_enchantments, potion_contents, custom_data, container, bundle_contents, firework_explosion, fireworks, writable_book_content, written_book_content, attribute_modifiers, trim, jukebox_playable(14종). `count` 는 명령 인자만의 가짜 종류.
- 대부분 `dw`(SingleComponentItemPredicate): 컴포넌트가 없으면 거짓. **custom_data(`ck`→`dk`)만 예외**: `getOrDefault(CUSTOM_DATA, 빈 태그)` 에 `compareNbt(술어, 값, true)` 라 `custom_data~{}` 는 모든 아이템에 참.
- damage(`cl`): `durability.matches(getMaxDamage() - damage) && damage.matches(damage)`. `isDamageableItem`·unbreakable 은 보지 않는다(예전 전리품 쪽 구현은 봤다).
- EnchantmentPredicate(`bq`): 인챈트 집합이 있으면 그중 레벨≠0 이고 levels 를 만족하는 것이 하나라도; 없고 levels 가 `ANY`(참조 비교 — 필드가 없을 때만)가 아니면 아무 인챈트나 그 레벨; 둘 다 없으면 인챈트가 하나라도.
- CollectionPredicate(`bd`): contains(`bb`: 0개 참, 1개 하나라도, 여럿이면 원소마다 맞는 술어를 지워 다 지워지면 참), count(`bc`: `{test, count}` 둘 다 필수, 맞는 원소 수), size. container 는 빈 칸을 뺀 아이템(`nonEmptyItems`).
- written_book_content 쪽 술어 값은 `ComponentSerialization.CODEC` — **문자열은 그 글자 그대로의 텍스트**라 `pages:{contains:['"hi"']}` 는 쪽 `"hi"` 와 다르다(`"hi"` 나 `{text:"hi"}` 로 써야 맞는다). title 은 raw 문자열, author 문자열.
- trim 은 인라인(직접 정의) 재료·무늬면 어떤 집합에도 들지 않는다. jukebox_playable 은 키 비교.
- **MinMaxBounds(`dh`) 1.21.1 코덱은 min>max 를 거절하지 않는다**("Swapped values" 문자열이 jar 에 없다). 예전 명령 인자 `count` 의 거절은 틀린 것이라 뺐다. 명령 문법의 범위(`1..5`, `MinMaxBounds.fromReader`)의 `argument.range.swapped` 는 그대로.
- ItemPredicate(`cs`)의 predicates 는 `dispatchedMap` 이라 모르는 종류는 그 술어(=발전 과제·전리품 표)의 로드 오류. 14종을 모두 구현했으므로 전리품 쪽도 경고+항상 거짓 대신 오류로 바꿨다. 로컬 바닐라 데이터로 발전 과제 1,399개·블록/상자/엔티티 전리품 시험이 그대로 통과했다.
- 같은 종류를 네임스페이스 생략·포함으로 두 번 쓰면(`damage`·`minecraft:damage`) DFU DispatchedMapCodec 의 Duplicate entry 오류. components 도 같다.
- 전리품 쪽 `LootRegistries.exists` 는 동기화 레지스트리 밖(물약·속성·장식 재료/무늬·음반 등)도 내장·동적 이름표로 검사한다(검토 R1) — 없는 키는 로드 오류.
- 명령 SNBT 의 텍스트 스타일 불리언은 바이트(`bold:true`=1b)라 쓴 책 쪽 술어에서 불리언으로 바꿔 읽는다(NbtOps Codec.BOOL, 검토 R2).
- JSON 으로 쓴 custom_data 객체는 JsonOps→NbtOps 변환에서 수가 좁혀진다(`{"a":1}` → `{a:1b}`) — 바닐라도 그래서 int 1 과 다르다. SNBT 문자열(`"{a:1}"`)로 쓰면 int.

## 남긴 차이(가정)

- `components`(정확히 같음)에서 우리 코덱이 못 읽는 값은 바닐라처럼 로드 오류로 하지 않고 예전처럼 구조 비교로 둔다(우리 코덱 결함을 데이터 팩 탓으로 돌리지 않으려고). 모르는·저장 안 하는 컴포넌트 이름은 오류.
- 오류 문구는 DFU 문구를 흉내 냈지만 바이트 단위로 대조하지 않았다.
- 전리품 쪽 태그는 예전처럼 파싱 때 검사하지 않는다(없는 태그는 빈 태그). 명령 쪽은 서버 태그로 검사한다.
- 쓴 책 쪽 비교는 `Component.equals` 대신 정규 평면 문자열(FlatText) 비교.
- 전리품 스택의 패치 값이 정규화에 실패하면(검증 없이 들어온 set_components 원값 등) 판정에서 그 컴포넌트를 없는 것으로 본다(바닐라라면 그 전리품 표가 로드 오류 — set_components 로드 검증은 후속).
