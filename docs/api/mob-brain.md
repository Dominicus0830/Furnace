# 몹 두뇌(Brain) 플러그인 API (작업 묶음 E2, 2026-11-07)

불만 표 18행(두뇌 몹 기억·센서·활동 API, Paper #6862 열림). API 는 `furnace-api` 의 `org.domi.furnace.api.entity.ai.brain.*`, 얻는 곳은 `MobAi.brain()`.
서버 구현은 `furnace-server/.../plugin/{MobBrainImpl,BrainValues,PluginBrainSensor,PluginBrainBehavior}.java` 와 `entity/ai/brain/Brain.java` 의 플러그인 자리.

## 1. 원칙 (E1 `docs/api/mob-ai.md` 와 같다)

- **기본 동작은 바닐라 그대로.** 플러그인이 아무것도 하지 않으면 두뇌 틱은 null 검사 몇 개만 더 한다(붙잡은 활동·플러그인 기억·플러그인 센서 자리가 모두 null). 저장 형식도 바뀌지 않는다.
- **버전 중립.** 기억 종류 객체(`MemoryModuleType`)·활동 객체·행동 클래스는 드러내지 않는다. 기억은 `MemoryKey`("minecraft:home" + 값 모양 `MemoryValueType`), 활동은 `ActivityKey`, 센서는 `SensorKey`. 엔티티를 가리키는 기억은 UUID, 위치는 `BlockPos`/`GlobalBlockPos`, 걸어갈·바라볼 대상은 `WalkTarget`/`BrainTarget`.
- **스레드.** `MobBrain` 은 그 몹을 가진 region 스레드에서만 쓴다(`Mob.editAi`, `Mob.aiNow`, 목표·행동·센서 콜백 안). 다른 스레드에서 부르면 `ThreadOwnershipException`.

```java
mob.editAi(ai -> {
    MobBrain brain = ai.brain().orElseThrow();              // 두뇌 몹이 아니면 빈 값
    brain.setMemory(MemoryKey.HOME, GlobalBlockPos.of("minecraft:overworld", 10, 64, -3));
    brain.setMemory(MemoryKey.ANGRY_AT, player.uniqueId()); // 피글린
    brain.forceActivity(ActivityKey.of("myplugin", "guard"));
    brain.addBehavior(plugin, ActivityKey.of("myplugin", "guard"), 0, new StandGuard());
    return null;
});
```

## 2. 기억

| 호출 | 뜻 |
|---|---|
| `memories()` | 등록된 기억 전부(바닐라 + 플러그인): 이름·값 모양·값·남은 틱. 길·피해 기록처럼 API 로 다루지 않는 내부 값은 모양 `null` |
| `memory(key)` / `memoryExpiry(key)` | 값 / 남은 틱. 비었거나 등록 안 됐으면 빈 값 |
| `setMemory(key, v)` / `setMemory(key, v, ttl)` | 영구 / ttl 틱 뒤 지워짐. 등록 안 된 기억은 false(바닐라처럼 무시). null·빈 목록은 지우기 |
| `eraseMemory(key)`, `registerMemory(key)` | 지우기, 등록 |

- 값 모양: `FLAG`(표지 — true 로 읽고, true 쓰기=켜기, false=지우기), `BOOLEAN`, `INTEGER`, `LONG`, `UUID_VALUE`, `UUID_LIST`, `ENTITY`, `ENTITY_LIST`, `BLOCK_POS`, `GLOBAL_POS`, `GLOBAL_POS_LIST`, `WALK_TARGET`, `TARGET`, 플러그인용 `object(클래스)`.
- 바닐라 기억은 모양이 서버가 아는 것과 다르면 `IllegalArgumentException`. 서버는 기억마다 모양을 `MemoryModuleType` 상수의 제네릭 형식에서 한 번 읽어 표로 만든다(`BrainValues`) — 판이 바뀌어 기억이 늘어도 형식만 맞으면 따라온다. 시험이 "내부 값은 dummy·path·hurt_by·ram_target 뿐"을 지킨다.
- 엔티티 모양 쓰기는 같은 region 에서 살아 있는 그 엔티티(플레이어 포함)를 찾는다. 못 찾거나 그 기억이 받는 종류가 아니면(예: 플레이어만 받는 기억에 소) false.
- 걸어갈 엔티티는 발 높이, 바라볼 엔티티는 눈높이를 따라간다(바닐라 `EntityTracker` 쓰임새와 같다).
- **플러그인 기억**(`MemoryKey.plugin("ns", "name", 클래스)`)은 두뇌 안에서 바닐라 기억과 같은 만료 규칙으로 돌지만 **저장되지 않는다**(바닐라 결과 그대로). 플러그인 센서·행동끼리 값을 주고받는 데 쓴다.
- 바닐라 기억을 원래 쓰지 않는 몹에 `registerMemory` 하면 바닐라 행동은 그 값을 보지 않는다. 저장 방식이 있는 기억이면 저장은 되지만 다시 불러올 때는 그 몹이 원래 쓰는 기억만 읽는다(바닐라 규칙).

## 3. 활동

- 읽기: `activities()`(행동·조건이 걸린 활동), `activeActivities()`, `coreActivities()`, `defaultActivity()`.
- `trySetActivity(a)`: 바닐라가 그 활동에 거는 기억 조건이 맞으면 바로 바꾸고 true. 몹은 매 틱 스스로 활동을 다시 고르므로(주민 시간표, 피글린·아홀로틀·워든 상황 판단) 다음 틱 되돌아갈 수 있다.
- `forceActivity(a)`: 조건 없이 켜고 **붙잡는다**. 붙잡힌 동안 몹이 활동을 바꾸려는 모든 호출(`setActiveActivityIfPossible`·`setActiveActivityToFirstValid`·시간표·기본 활동)이 그 활동에 머문다 — 핵심 활동은 계속 돈다. 바꿀 때 꺼지는 활동이 지우는 기억은 바닐라처럼 지운다(예: 포효 → 다른 활동이면 `roar_target` 지움). 행동이 없는 플러그인 활동(`myplugin:guard`)도 된다.
- `releaseActivity()`: 놓고 기본 활동으로 돌아간다. 다음 틱부터 몹이 스스로 고른다.
- 바닐라 이름공간의 모르는 활동은 `IllegalArgumentException`. 플러그인 활동은 처음 쓸 때 만들어 같은 객체를 돌려준다.

## 4. 센서

- `sensors()`: 바닐라 센서(종류 이름) 다음 플러그인 센서, 도는 순서.
- `removeSensor(key)`: 바닐라 센서도 뺄 수 있다. 그 센서가 채우던 기억은 등록된 채 남고 더는 갱신되지 않는다.
- `addSensor(plugin, BrainSensor)`: `scanRate` 틱마다 `sense`. 첫 감지는 바닐라 센서처럼 0~scanRate-1 틱 무작위로 어긋난다(바닐라 센서와 같은 별도 난수 — 월드 난수 순서는 그대로). `memories()` 의 기억은 넣을 때 등록된다. 같은 이름표가 있으면 거부.

## 5. 행동

- `addBehavior(plugin, activity, priority, BrainBehavior)`: 그 활동이 켜져 있을 때만 시작을 시도한다. 규칙은 바닐라 행동과 같다: 매 틱 기억 만료 → 센서 → 멈춘 행동 중 켜진 활동의 것을 우선순위 순서로 시작 시도(`requiredMemories` 조건 → `shouldStart`) → 돌고 있는 행동마다 `shouldContinue` 면 `tick`, 아니면 `stop`. 시작한 그 틱에 바로 한 번 돈다. 활동이 꺼져도 돌던 행동은 `shouldContinue` 가 false 가 될 때까지 이어진다.
- `behaviors()`: 붙은 행동 전부(활동·우선순위·이름·돌고 있는지·플러그인인지). 바닐라 행동 이름은 표시용이다(이름 없는 행동은 `anonymous` — 판마다 바뀔 수 있어 찾기에 쓰지 않는다).
- `removeBehavior(행동 객체)`, `clearBehaviors(activity)`(바닐라 것까지 그 활동의 행동을 모두 — 활동과 조건은 남는다). 돌던 행동은 멈춘 뒤 빠진다.
- 콜백 안에서 센서·행동을 넣고 빼도 된다 — 두뇌 틱이 끝난 뒤 부른 순서대로 적용한다(돌던 표를 건드리지 않게).
- 플러그인이 꺼지면 그 행동·센서는 다시 불리지 않고 스스로 빠진다. 콜백 예외는 로그 한 번 남기고 그 행동·센서를 뗀다.

## 6. 두뇌를 새로 만들 때 · 저장

- 주민은 직업·나이가 바뀌면 두뇌를 새로 만든다(`refreshBrain` — 바닐라: 돌던 행동 멈춤 → 기억만 옮긴 새 두뇌에 활동 다시 걸기). 이때 **플러그인 행동·센서·플러그인 기억·붙잡은 활동은 새 두뇌로 옮겨 간다**(뺀 바닐라 센서도 빠진 채로 옮겨 간다 — 바닐라 `copyWithoutBehaviors` 가 지금 센서 목록을 그대로 쓰기 때문). `MobBrain` 손잡이는 부를 때마다 몹의 지금 두뇌를 읽으므로 그대로 써도 된다.
- 플러그인이 꺼지면 그 행동·센서는 몹의 다음 두뇌 틱에 빠진다. 플러그인 기억은 주인 플러그인이 없어(이름공간만 있다) 몹이 내려갈 때까지 남는다 — 핫 리로드(개발 모드)를 쓰면 기억 값에 플러그인 클래스 객체 대신 기본 자바 값(문자열·숫자·UUID·목록)을 넣는 편이 옛 클래스로더를 붙잡지 않는다.
- 플러그인 센서·행동·기억·붙잡은 활동은 **저장되지 않는다**. 몹이 청크와 함께 내려갔다 올라오면 바닐라 두뇌만 있다(E1 플러그인 목표와 같다) — 엔티티 불러오기 이벤트가 생기면 거기서 다시 건다.

## 7. 시험

- `plugin/MobBrainApiTest`(스레드 없는 시험 레벨, 18개): 주민 기억 모양·등록·지우기·저장, 엔티티 UUID 기억·종류 검사·걸어갈/바라볼 대상·보이는 생물 목록, 만료·플러그인 기억 미저장, 표지 기억(워든), 모든 바닐라 기억 모양 표, 피글린 활동 읽기·시도·붙잡기·놓기, 붙잡기의 바닐라 기억 지우기(워든 포효), 플러그인 행동(활동·시작·이어 가기·멈춤·되풀이), 조건(바닐라·플러그인 기억, 아홀로틀), 콜백 안 변경, 꺼진 플러그인·예외 정리, 주민 두뇌 새로 만들기, 센서 목록·빼기·추가·주기, 손대지 않은 두뇌가 그대로인지, 남의 스레드 거부.
- `plugin/MobAiHandleTest`(region 스레드): 주민 `editAi` 안에서 붙잡은 활동이 region 틱 뒤에도 남고 밖으로 가져간 손잡이는 거부.
- 봇: `tools/bot-test/round13/brain.js`(시험 플러그인 `round13/probe/BrainProbe`) 12항목 — 실제 서버(region 스레드·플러그인 로더)에서 주민·피글린·아홀로틀·소 손잡이와 활동 목록, 집 기억 쓰기 + `/data` 로 바닐라 저장 형식, 플러그인 활동 80틱 유지·놓기, 플러그인 행동 매 틱·센서, 피글린 피하기 고정, 모르는 활동 거부, 피글린 분노·공격 대상. 2026-11-07 12/12 PASS.

실제 클라이언트로 볼 것은 없다(두뇌는 서버 안 상태이고, 몹 움직임은 기존 패킷 그대로 간다).
