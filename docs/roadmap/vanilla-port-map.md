# 바닐라 ↔ Furnace 대응표 (V-06, 2026-11-16)

버전업 때 "바뀐 바닐라 클래스 → 고칠 Furnace 파일"을 바로 찾으려고, 바닐라에서 옮겨 온 Furnace 서버 클래스 머리에 `@VanillaPort` 소스 주석을 붙인다(설계: `version-upgrade-plan-2026-11-16.md` 3.4 장).

## 형식
```java
@VanillaPort("net.minecraft.world.scores.Scoreboard")                       // 클래스 하나
@VanillaPort({"net.minecraft.world.effect.WeavingMobEffect", "…OozingMobEffect"}) // 여러 개를 합친 클래스
public final class Scoreboard { … }
```
- 주석 클래스는 `org.domi.furnace.server.VanillaPort`(`@Retention(SOURCE)`). 클래스 파일에 남지 않아 실행·jar 크기에 영향이 없다.
- 값은 바닐라(Mojang 이름) 클래스 전체 이름. 안쪽 클래스는 `$`. Brigadier 는 바닐라 jar 에 들어 있는 `com.mojang.brigadier.*` 이름을 쓴다.
- **바닐라에 없는 Furnace 전용 클래스에는 붙이지 않는다**(스케줄러·플러그인·설정·메트릭·콘솔·Furnace 전용 명령 등).
- 한 Furnace 클래스가 바닐라 한 클래스의 일부만 옮겼어도(예: `/execute` 단계별로 나눈 `ExecuteCommands`·`ExecuteConditions`) 그 클래스를 적는다.

## 대응표 만들기
`python3 -I tools/ci/vanilla-port-map.py` → `build/vanilla-port-map.tsv`(Furnace 클래스 ⇥ 바닐라 클래스 ⇥ 소스 경로, 커밋 안 함). `--by-vanilla` 는 바닐라 이름 순.
`--verify <목록>` 은 바닐라 jar 에서 뽑은 클래스 이름 목록과 대조해 없는 이름을 알려 준다(jar 는 커밋하지 않는다).

## 붙인 범위(command·기타 조각)
`command/**`(실행 큐·인자 타입·내장 명령·Brigadier), `function`, `scores`, `advancement`, `attribute`, `bossbar`, `chat`, `effect`, `trade`, `map`, `players`, `menu`, `light`, `data`, `datafix`(일부), `config`(바닐라 설정 이식분), `remote`, `stats`, `sound`, `profiling`, `maintenance/ServerSave`, 최상위(`CrashReport`·`ServerCore`·`ServerWatchdog`·`ConsoleInput`).
`block`·`item`·`loot`·`recipe`·`world`·`net`·`entity` 는 다른 조각이 붙인다.

## 가정(확인 필요)
- 이 조각의 이름들은 각 파일 머리 주석("바닐라 X")과 Mojang 이름 기억으로 적었고, 이 세션에서는 바닐라 jar 를 열 수 없어 `--verify` 로 대조하지 못했다. 패키지 경로가 틀린 항목이 있을 수 있으니 U-03 도구(또는 jar 가 있는 환경)에서 `--verify` 를 한 번 돌려 고친다.
