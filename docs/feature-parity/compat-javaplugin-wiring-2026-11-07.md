# JavaPlugin 에 명령·스케줄러·서비스 연결 (H4 다음 묶음 A) — 2026-11-07

H4 측정이 낸 1순위(없는 클래스 1위 `BukkitScheduler`, 2위 `Command`, helper 의 첫 장애물 `JavaPlugin.getProvidingPlugin`)를 `furnace-compat-paper` 에 연결했다.
H2 코어(`org.domi.furnace.compat.command·sched`)는 그대로 쓰고, 그 위에 Bukkit 이름의 얇은 껍데기를 얹었다. 새 시험: `JavaPluginWiringTest`(진짜로 컴파일한 플러그인 jar 로 11개).

## 들어온 것

| 영역 | 클래스·메서드 |
|---|---|
| 플러그인 | `JavaPlugin#getCommand`·`getProvidingPlugin`·`getPlugin(Class)`·`onCommand`·`onTabComplete`, `PluginBase`(이름으로 같음), `Plugin extends TabExecutor` |
| 명령 | `Command`(전부)·`PluginCommand`·`CommandExecutor`·`TabCompleter`·`TabExecutor`·`CommandMap`·`SimpleCommandMap`·`CommandException`·`PluginIdentifiableCommand`·`PluginCommandYamlParser`·`BlockCommandSender`(형만) |
| 스케줄러 | `BukkitScheduler`(기억 기준 Bukkit 1.21 의 메서드 전부 — 원문 대조 못 함, `Consumer<BukkitTask>` 변형 포함)·`BukkitRunnable`·`BukkitTask`·`BukkitWorker` |
| 서비스 | `ServicesManager`·`SimpleServicesManager`·`RegisteredServiceProvider`·`ServicePriority`·`ServiceRegisterEvent`·`ServiceUnregisterEvent` |
| 서버 | `Server`/`Bukkit` 에 `getScheduler`·`getServicesManager`·`isPrimaryThread`·`getCommandMap`·`getPluginCommand`. `dispatchCommand` 는 옛 명령을 먼저 찾는다 |

## 어떻게 이어졌나

- **명령 선언 시점.** Bukkit 처럼 `onEnable` 직전에 `plugin.yml` 의 `commands:` 를 `PluginCommandYamlParser` 로 `PluginCommand` 로 만들어 명령 지도에 올린다. 그래서 `onLoad` 에서 `getCommand` 는 null. H2 가 넘긴 D2(기본 실행기는 플러그인 본체)는 `PluginCommand` 가 만들 때 `executor = owner` 를 받는 Bukkit 규칙 그대로라 따로 어댑터가 필요 없다.
- **명령 지도 = Furnace 트리.** `SimpleCommandMap#register` → `CompatCommands.attach` → H2 `LegacyCommands.register`(서버 명령 등록소). 이름·별칭·`플러그인:이름` 이 트리에 붙고, 실제로 올라간 이름만 `getKnownCommands()` 에 보인다(별칭이 다른 명령에 막히면 활성 별칭에서 빠진다). `knownCommands.remove(이름)`·`values().removeIf(...)` 같은 플러그인의 흔한 해제는 트리에서도 이름을 뺀다. `put` 은 `UnsupportedFeature`(register 로만).
- **통과 모드.** 직접 상속한 `Command` 는 권한·사용법을 스스로 처리하므로 H2 `LegacyCommand` 에 `setPassthrough(visible)` 를 더했다 — 통과 모드면 코어가 검사·사용법을 하지 않고 `Command#execute` 만 부르며, 트리에 보일지는 `Command#testPermissionSilent` 가 정한다. `PluginCommand#execute` 가 Bukkit 규칙(꺼짐·권한·사용법·`CommandException`)을 직접 갖는다.
- **소유 플러그인.** Furnace 트리의 모든 명령은 주인이 있어야 꺼질 때 치운다. `PluginIdentifiableCommand` 면 그 플러그인, 아니면 명령 클래스를 올린 **플러그인 클래스로더**(`PluginLoaders`), 둘 다 아니면 `UnsupportedFeature`.
- **getProvidingPlugin.** 서버의 플러그인 클래스로더는 호환 모듈을 모르므로 `CompatFormat.create` 가 클래스로더 → 어댑터를 `PluginLoaders` 에 묶는다(생성자가 도는 동안은 `javaPlugin()` 이 null → `IllegalStateException`, Bukkit 의 "정적 초기화 중"). 지도는 키·값 모두 약한 참조라 버려진 플러그인(켜진 적 없음 포함)의 클래스로더를 붙잡지 않고, 생성자가 터지면 바로 푼다. 꺼진 플러그인도 서버 목록에 있는 동안은 Bukkit 처럼 돌려준다.
- **스케줄러.** `CompatScheduler` 가 `LegacyScheduler`(H2, CraftScheduler 의미)를 부른다. 꺼진 플러그인의 예약은 `IllegalPluginAccessException`. `Consumer<BukkitTask>` 변형은 예약이 끝나 핸들이 생긴 뒤 몸통을 부른다. 서버(Furnace 인스턴스)마다 첫 사용 때 만들어 전역에 박동을 건다.
- **서비스.** `SimpleServicesManager` 는 Bukkit 과 같은 이진 탐색 자리에 넣어(같은 우선순위는 나중 것이 앞) 정렬하고, 등록·해제 때 이벤트를 `callEvent` 한다. 서비스를 등록한 플러그인이 꺼지면 `unregisterAll`.

## Bukkit 과 다른 점·한계

0. **이름 겹침(이 지도 안의 명령끼리)은 Bukkit 규칙대로**: 본 이름은 다른 명령이 별칭으로만 잡은 이름을 가져가고, `접두어:이름` 은 늘 덮어쓰며, 본 이름끼리는 먼저 온 쪽이 지킨다(기억 기준).
1. **`fallbackPrefix` 는 소유 플러그인 이름이 대신한다.** 트리의 `접두어:이름` 은 서버 등록소가 플러그인 이름으로 만든다. `register("x", cmd)` 의 `x` 가 플러그인 이름과 다르면 `x:이름` 이 아니라 `플러그인:이름` 이 생기고, `command.getLabel()` 도 그렇게 된다.
2. **바닐라·Furnace 가 직접 올린 명령은 지도에 없다.** `getCommand("tp")` 는 null(Bukkit 은 래퍼를 준다). 겹치는 이름은 접두어 형태로만 올라가고 `register` 가 false. 예: 별칭 `w` 는 바닐라 `/w` 에 막혀 `플러그인:w` 만 생긴다.
3. **권한 없는 사람은 트리에서 명령이 숨는다**(H2 알려진 차이 1 그대로). 직접 `PluginCommand#execute` 를 부르면 Bukkit 의 거절 문장이 나간다.
4. **`Bukkit.dispatchCommand`** 는 옛 명령이면 주 스레드에서 바로 실행하고 실행기가 false 여도 "처리됨" true 를 돌려준다(Bukkit `SimpleCommandMap#dispatch` 와 같음; H2 의 `LegacyCommands#dispatch` 는 false). 주 스레드가 아니면 전역으로 넘기고 접수 true. 주 스레드에서 터진 예외는 호출자에게 `CommandException`.
5. **`Command.broadcastCommandMessage`** 는 `UnsupportedFeature`(권한 구독자 목록이 없다). `BlockCommandSender` 는 형만 있고 명령 블록 발신자는 아직 그 형으로 오지 않는다.
6. **동기 작업은 전역 스레드에서 돈다.** ~~블록·엔티티 같은 region 상태를 동기 작업에서 읽으면 `ThreadOwnershipException`.~~ 2026-11-08 부터는 동기 작업·옛 명령이 만진 region 만 틱 사이에 빌려 와 곧바로 읽고 쓴다 — `compat-legacy-sync-region-access-2026-11-08.md`.
7. **`BukkitTask` 의 같음은 예약 번호**(Bukkit 은 객체 동일성). `getPendingTasks` 가 새 핸들을 줘도 `equals` 는 맞다.
8. 서비스 이벤트·`PluginCommand` 의 `getPlugin` 은 Furnace 이벤트 버스와 이어져 있지 않다(Bukkit 안에서만).
9. `getProvidingPlugin` 은 서버가 그 플러그인을 목록에서 버린 뒤(약한 참조가 풀린 뒤)에는 `IllegalArgumentException`.
10. Paper 의 `GlobalRegionScheduler` 등 Paper 스케줄러 넷과 `io.papermc.paper.command.brigadier.*` 는 이번에 넣지 않았다(제안 H).

## 측정 변화 (H4 도구 재실행)

`Coverage` 가 자기 jar 클래스를 owner 로 적은 호출(`class MyPlugin extends JavaPlugin` 안의 `getCommand()`)을 상속 사슬로 API 에 귀속하도록 고쳤다(H4 검토 권고 1) — 상위 클래스가 JDK(`Enum#name`)이거나 검사 대상이 아닌 서드파티 클래스(adventure 의 `FacetAudienceProvider`)면 Bukkit "없음"으로 세지 않는다(독립 검토 F1 — 처음 구현은 이를 잘못 세어 adventure 가 97→101 로 보였다). adventure-platform-bukkit 은 97 그대로.
기준표 `missing`: helper 228→184, commandapi-bukkit-plugin 131→105, cloud-bukkit 19→5, bstats-bukkit 4→1, commodore 15→8, IF 98→94, commandapi-bukkit-core 62→49, cloud-paper 72→67, XSeries 699→694.
실제 기동: helper 의 첫 장애물이 `JavaPlugin.getProvidingPlugin` 에서 **`com.google.common.base.Supplier`(Guava, 묶음 B — 서버 제공 라이브러리, 사용자 결정)** 로 옮겨 갔다. 여전히 `LOADED_NOT_ENABLED`.
