## 2026-11-08 Bukkit SimplePluginManager — 함정

- **helper 는 `CommandMapUtil` 정적 초기화에서 막힌다.** `SimplePluginManager.class` 를 `ldc` 로 집어 `getDeclaredField("commandMap")` — 클래스가 없으면 `NoClassDefFoundError`, 필드 이름이 다르면 `NoSuchFieldException` 이 `RuntimeException` 으로 싸여 onEnable 이 실패한다. 정적 대조표(`compatReport`)는 이 `ldc` 클래스 참조를 '없는 클래스' 표에 올리지 않았다 — 기동 측정(`liveProbe`)으로만 보였다.
- **`knownCommands.put` 은 helper 가 별칭을 올리는 길이다.** `register(이름, cmd)` 뒤에 별칭마다 `put("플러그인:별칭")`·`put("별칭")`. H4A 의 뷰는 `put` 을 막아 두었었다 — 첫 별칭(=이름)은 이미 있어 통과하고 두 번째 별칭에서 터진다(helper 는 예외를 printStackTrace 로 삼켜 조용히 별칭만 빠진다).
- **Bukkit `Permission` 생성자는 `recalculatePermissibles()` 를 부른다** → `getPermissionSubscriptions`·`recalculatePermissionDefaults`. 등록소 노드를 Bukkit 객체로 감쌀 때도 생성자를 거치므로, `recalculatePermissionDefaults` 는 "지금 이 노드를 보이는 바로 그 객체"일 때만 등록소에 쓴다. 이름만 같으면 쓰면, 플러그인이 아무 데서나 `new Permission("이미.있는.이름")` 을 만들 때 plugin.yml 권한의 기본값·자식이 지워진다.
- **같은 노드 = 같은 Bukkit 객체**는 Furnace 노드(record)의 객체 정체성으로 판단한다(`equals` 로 하면 플러그인이 자식을 고치고 아직 recalculate 하지 않은 객체를 새 객체로 바꿔 버린다).
- **콘솔 권한**: `CompatConsole.isPermissionSet` 이 늘 true 면 권한 방송이 등록되지 않은 권한에도 콘솔에 간다(Bukkit 은 안 간다). Furnace 콘솔(`ConsoleSender`, OP 고정 `PermissibleBase`)에 맡긴다.
- **Paper 원문 대조**: `Paper-Plugins.patch`(api·server, main `33ff3dff`)에 `PaperPluginInstanceManager`·`PaperEventManager`·`PaperPermissionManager`·`StupidSPMPermissionManagerWrapper` 가 들어 있다. Paper 의 `SimplePluginManager` 는 모든 메서드를 `paperPluginManager` 로 넘기고, 권한 지도는 SPM 의 public 필드를 그대로 쓴다. `disablePlugin` 은 클래스로더를 닫는다.
