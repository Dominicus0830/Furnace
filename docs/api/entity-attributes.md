# 엔티티 속성 API (E6)

이동 속도·최대 체력·공격력 같은 **엔티티 속성**을 `Internals` 로 내부 `AttributeMap` 을 꺼내지 않고 다룬다. 의미는 Bukkit/Paper 의 `Attributable`·`AttributeInstance` 와 같다(바닐라 `AttributeInstance` 를 그대로 만지므로 `/attribute`, 상태 효과, 장비 수정자와 같은 상태를 본다). 패키지 `org.domi.furnace.api.entity.attribute`.

## 빠른 예

```java
// 읽기: 어느 스레드에서나 — 핸들을 만든 시점 값(플레이어는 최근 값)
var speed = player.attribute(Attribute.MOVEMENT_SPEED).orElseThrow();
double now = speed.value();

// 쓰기: 그 엔티티를 가진 region 스레드로 넘어가고 Future 로 끝을 알린다
speed.setBaseValue(0.2);
speed.addModifier(new AttributeModifier("my_plugin:sprint_boost", 0.3, Operation.ADD_MULTIPLIED_BASE));
speed.removeModifier("my_plugin:sprint_boost");

// 읽은 뒤 쓰기를 한꺼번에(지금 값): edit 콜백은 소유 스레드에서 돈다
zombie.attribute(Attribute.ATTACK_DAMAGE).ifPresent(a -> a.edit(i -> {
    if (i.value() < 8) i.setBaseValue(8);
    return null;
}));

// 이미 소유 스레드 안(entityScheduler 작업·이벤트 핸들러)이면 editState 의 손잡이를 바로 쓴다
entity.editState(st -> ((LivingEntityState) st).attribute(Attribute.MAX_HEALTH).map(i -> { i.setBaseValue(40); return i.value(); }));
```

## 구성

| 타입 | 뜻 |
|---|---|
| `Attribute` | 1.21.1 속성 31종(`MAX_HEALTH`·`MOVEMENT_SPEED`·`ATTACK_DAMAGE` …): `key()`·`defaultValue()`·`minValue()`·`maxValue()`·`sanitize`. `Attribute.of/find(이름)` |
| `AttributeModifier` | `(id, amount, Operation)` 불변 값. `Operation` 은 아이템 `AttributeModifiers.Operation` 을 재사용. `toEntry(attribute, slot)` 로 아이템 항목으로, `of(Entry)` 로 가져온다 |
| `AttributeHandle` | `LivingEntity#attribute(a)` — **읽기=만든 시점**, 쓰기=`edit`/`setBaseValue`/`addModifier`/`addTransientModifier`/`setModifier`/`removeModifier`(Future) |
| `AttributeInstance` | 지금 값 손잡이(Bukkit `AttributeInstance`) — 소유 스레드 안에서만. `LivingEntityState#attribute(a)`, `AttributeHandle#edit` 콜백 |

`LivingEntity` 에는 `attribute(a)`(엔티티가 그 속성을 가지지 않으면 `Optional.empty` — Bukkit 의 `getAttribute` 가 null 인 경우), `attributes()`(가진 속성 목록), `attributeValue(a, fallback)`, `registerAttribute(a)`(Paper) 가 있다.

## 이름

키는 1.21.1 이름이다(`minecraft:generic.movement_speed`). `Attribute.find/of` 는 다음을 같은 속성으로 받는다 — 코드가 어느 버전 이름을 써도 된다.

- `generic.movement_speed`, `movement_speed` (접두사 없는 이름)
- `minecraft:movement_speed` (1.21.2 이후 이름)
- Paper 가 바꿔 주는 옛 Bukkit 이름: `generic.movementSpeed`, `Movement Speed`, `horse.jump_strength` …

## Bukkit/Paper 와 같은 점 (원문 CraftAttributeInstance·CraftAttributeMap 대조)

- `getDefaultValue` = 속성 기본값(엔티티 종류가 정한 시작 값이 아니다. 플레이어 이동 속도 기본값 0.1, `defaultValue()` 는 0.7).
- `setBaseValue` 는 넣은 값을 **그대로** 둔다. `value()` 가 `[최소, 최대]` 로 자른다(NaN 은 최소). 최대 체력을 줄이면 체력이 따라 잘린다.
- `addModifier` = 바닐라 `addPermanentModifier` — **저장된다**. 같은 id 가 이미 있으면 `IllegalArgumentException("Modifier is already applied on this attribute!")`.
- `addTransientModifier`(Paper) — 같은 검사, **저장하지 않는다**(서버를 다시 켜면 사라진다). 값 계산에는 든다.
- `removeModifier` — id 로 뗀다. 장비·상태 효과·달리기가 단 수정자도 `modifiers()` 에 보인다.
- `registerAttribute` — 없는 속성을 속성 기본값으로 단다(아래 "다른 점" 참고).
- 리스폰하면 /attribute·플러그인이 단 수정자는 사라지고 기본값만 남는다(바닐라 `ServerPlayer.restoreFrom` 이 `assignBaseValues` 만 하는 것과 같다). 플러그인은 리스폰 뒤에 다시 단다.

## 다른 점

- Bukkit 은 모든 호출이 동기다. 여기서는 읽기가 만든 시점 값이고 쓰기가 Future 다(스레드 소유권 규칙). 소유 스레드 안에서는 `editState`·`AttributeHandle#edit` 의 `AttributeInstance` 로 Bukkit 과 똑같이 동기로 쓴다.
- `setModifier`(있으면 바꾸고 없으면 단다)는 Bukkit 에 없는 편의다(바닐라 `addOrReplacePermanentModifier` — transient 수정자에 쓰면 저장되는 수정자가 된다).
- `registerAttribute` 는 이미 있으면 그대로 두고 false 다. Paper 는 이미 있어도 새 인스턴스로 바꿔 끼워 기본값·수정자를 초기화한다(Furnace 쪽이 안전해 유지). 등록하는 순간 클라이언트로는 보내지 않지만, 그 뒤 값이 바뀌면 일반 속성처럼 동기에 실려 간다 — 클라이언트가 모르는 속성이면 클라이언트 로그에 경고가 찍힐 수 있다.
- 수정자 id 는 `minecraft:` 네임스페이스를 붙인 소문자 키여야 한다(`AttributeModifier` 가 검사). 옛 UUID 수정자는 받지 않는다. NaN·무한대 양은 Bukkit 처럼 막지 않는다.
- 호환 계층(`CraftLivingEntity#getAttribute` → 이 API)은 이 PR 범위가 아니다(후속).

## 서버 구조

- `AttributeMap#snapshot()`: 버전이 같으면 이전 스냅샷을 그대로 돌려주는 불변 보기. 몹 핸들은 만들 때 한 번 받고, 플레이어는 region 이 매 틱 `syncAttributes` 끝에 다시 떠서 `Player#attributeView()` 로 다른 스레드가 읽는다.
- `AttributeInstance`(서버)에 transient id 집합을 두고 `AttributeMap#save` 가 뺀다. 플레이어의 저장 경로(`refreshAttributesSave`)도 같은 `save` 를 쓴다.
- 쓰기: 몹은 `EntityHandle#onEntity`(엔티티를 따라가는 대기열), 플레이어는 `Player#runOnOwner` — `/attribute` 와 같은 `AttributeMap` 을 직접 만진다.
