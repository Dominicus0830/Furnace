# 몹 AI 플러그인 API (작업 묶음 E1, 2026-11-06)

불만 표 17행(몹 목표 API)·19행(바닐라 목표 재사용)·20행(경로 찾기 노드 평가 훅). API 는 `furnace-api` 의 `org.domi.furnace.api.entity.Mob`, `org.domi.furnace.api.entity.ai.*`, 서버 구현은 `furnace-server/.../plugin/{MobAiImpl,PluginGoal,VanillaGoals,GoalKeys}.java`.

## 1. 원칙

- **기본 동작은 바닐라 그대로.** 플러그인이 아무것도 걸지 않으면 목표 표·길찾기는 바뀌지 않는다(훅 자리는 `null` 검사 하나).
- **버전 중립.** 내부 클래스(`Goal`, `PathType`, `GoalSelector`)를 노출하지 않는다. 목표는 `GoalKey`("minecraft:melee_attack"), 바닐라 목표는 `VanillaGoal`(이름표 + 값), 길 종류는 `PathNodeType`(문자열 키), 엔티티·아이템은 레지스트리 키 문자열. 판이 바뀌면 서버의 변환 계층만 고친다.
- **스레드.** 목표는 몹 틱 안(그 몹을 가진 region 스레드)에서 돈다. `MobAi` 는 그 스레드에서만 쓰고, 다른 스레드에서 부르면 `ThreadOwnershipException`(엔티티·월드 API 의 `*Now` 규칙과 같다).

## 2. 얻는 법

| 방법 | 스레드 | 언제 |
|---|---|---|
| `Mob.editAi(ai -> …)` | 아무 스레드에서 호출 → 다음 틱 소유 스레드에서 실행 | 명령 처리기 등 다른 스레드에서. 몹이 region 을 옮겨도 따라간다. 몹이 사라지면 `IllegalStateException` 으로 완료 |
| `Mob.aiNow()` | 소유 스레드에서만 | 그 몹 이벤트 처리기 안(예: `EntitySpawnEvent`/`CreatureSpawnEvent` — 첫 틱 전에 목표를 바꾼다) |
| `MobGoal` 콜백 인자 | 소유 스레드 | 목표 안에서 몹을 움직일 때 |

몹 핸들(`EntityHandle.of`)은 이제 `Mob` 이다(주민·떠돌이 상인 핸들은 `Merchant` 이면서 `Mob`).

## 3. 목표

```java
mob.editAi(ai -> {
    ai.removeGoals(GoalSet.GOALS, GoalKey.minecraft("panic"));                 // 바닐라가 넣은 목표도 이름표로 뺀다
    ai.addGoal(GoalSet.GOALS, 2, VanillaGoal.meleeAttack(1.2, false));         // 바닐라 목표 재사용
    ai.addGoal(GoalSet.TARGETS, 1, VanillaGoal.nearestAttackableTarget("minecraft:player", true));
    ai.addGoal(plugin, GoalSet.GOALS, 0, new GuardGoal());                       // 플러그인 목표
    return null;
});
```

- 우선순위·제어 칸(`GoalControl` MOVE/LOOK/JUMP/TARGET)·끼어들기는 바닐라 `GoalSelector` 규칙 그대로다. 시작 판정은 2틱에 한 번, `tick` 은 `tickEveryTick()` 이 아니면 2틱에 한 번.
- 플러그인이 꺼지면 그 플러그인 목표는 다시 불리지 않고 스스로 빠진다. 콜백이 예외를 던지면 로그 한 번 남기고 그 목표는 빠진다.
- 콜백 안에서 목표를 넣고 빼도 된다(`GoalSelector` 가 돌기 도중 변경을 부른 순서대로 미뤄 적용 — 전에는 `LinkedHashSet` CME 위험이 있었다. 해골 무기 바꾸기처럼 "빼고 넣기"도 순서가 지켜진다).
- 돌던 목표를 빼면 먼저 멈춘다(잠근 제어 칸이 남지 않는다).
- **한계:** 플러그인 목표는 저장되지 않는다. 몹이 청크와 함께 내려갔다 올라오면 바닐라 목표만 있다(Paper 와 같다). 엔티티 불러오기 이벤트가 생기면(이벤트 묶음) 거기서 다시 넣으면 된다.

### 바닐라 목표 팩토리(`VanillaGoal`)

`float`, `melee_attack`, `random_stroll`, `water_avoiding_random_stroll`, `random_swimming`, `water_avoiding_random_flying`, `look_at_player`, `random_look_around`, `panic`, `tempt`, `avoid_entity`, `leap_at_target`, `move_towards_target`, `move_towards_restriction`, `open_door`, `restrict_sun`, `flee_sun`, `breath_air`, `breed`, `follow_parent`(행동), `nearest_attackable_target`, `hurt_by_target`(대상).
몹 종류가 맞지 않으면(길찾지 않는 몹에 근접 공격, 동물이 아닌 몹에 번식, 땅 길찾기가 아닌 몹에 문 열기) 넣을 때 `IllegalArgumentException`. 엔티티 종류 조건은 바닐라의 클래스 조건을 키 비교로 바꿔 건다(찾는 범위·순서·조건은 바닐라 그 목표와 같다).

### 이름표 규칙(`GoalKeys`)

바닐라 목표는 종류 이름에서 `Goal` 을 떼고 snake_case: `MeleeAttackGoal` → `minecraft:melee_attack`, 몹 안쪽 클래스 `GhastShootFireballGoal` → `minecraft:ghast_shoot_fireball`, 이름 없는 클래스는 부모 이름. Paper `VanillaGoal` 키와 같은 방식이다.

## 4. 길찾기

- `pathCost(type)`/`setPathCost(type, cost)`: 몹별 칸 종류 비용(바닐라 `Mob.setPathfindingMalus`). 음수면 못 간다.
- `setPathTypeHook(plugin, (x, y, z, vanilla) -> …)`: 노드 칸 판정 훅. 바닐라가 그 몹 기준으로 정한 칸 종류를 받아 바꿀 종류를 돌려준다(`null` 은 그대로). 걷기·날기·양서·헤엄 평가자 모두 노드 칸 기억(`pathTypesByPosCache`)에 넣기 직전 한 번 부른다 — 한 번 길을 찾는 동안 같은 칸은 다시 묻지 않는다. 비용은 종류별 표로 정해지므로 "이 칸은 못 감"(BLOCKED)·"돌아가라"(DANGER_OTHER + 비용)를 둘 다 만든다.
- 훅은 길찾기 도중 region 스레드에서 아주 자주 불린다. 가볍게. 예외·모르는 종류·꺼진 플러그인이면 바닐라 값을 쓰고 훅을 뗀다.
- 무작위 목적지 고르기(`RandomPos`/`GoalUtils`, 바닐라 정적 판정)는 훅을 보지 않는다. 막힌 칸이 목적지로 뽑혀도 길찾기가 거기로 가지 않을 뿐이다.

## 5. 움직임·대상

`moveTo(x,y,z,speed)`, `moveTo(uuid,speed)`, `stopMoving()`, `isMoving()`, `lookAt`, `jump`, `target()`, `setTarget(uuid|null)`(같은 region 의 엔티티·플레이어, `EntityTargetEvent` 를 거친다).

## 6. 시험

- `plugin/MobAiApiTest`(스레드 없는 시험 레벨): 목표 표 키, 플러그인 목표 시작·멈춤·제어 칸, 돌던 목표 빼기, 콜백 안 변경, 꺼진 플러그인·예외 정리, 팩토리 검사, 좀비가 소를 노리게, 훅으로 우회·비용, 훅 예외 떼기, 남의 스레드 거부.
- `plugin/MobAiHandleTest`(region 스레드): `editAi` 가 소유 스레드에서 돈다, `aiNow` 거부/허용, 스폰 이벤트 안에서 바로 바꾸기, 주민 핸들이 `Mob`.

## 7. 다음(E2 와 맞출 것)

- E2(두뇌 API)는 같은 규약: `MobAi` 에 두뇌 쪽 손잡이를 더하고, 기억·센서·활동도 레지스트리 키 문자열로.
- 엔티티 불러오기 이벤트(플러그인 목표 다시 넣기용)는 이벤트 묶음에서.
