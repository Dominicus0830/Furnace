# H4: Bukkit SimplePluginManager — 2026-11-08

H4 묶음 E 뒤 helper 가 켜지다 멈추던 `org.bukkit.plugin.SimplePluginManager` 를 채웠다. helper 의 `CommandMapUtil` 은 정적 초기화에서
`SimplePluginManager.class.getDeclaredField("commandMap")`·`SimpleCommandMap.class.getDeclaredField("knownCommands")` 를 리플렉션으로 잡고,
명령을 올릴 때 별칭을 `knownCommands.put(...)` 으로 직접 넣는다. 둘 다 막혀 있었다(클래스 없음, `put` 은 `UnsupportedFeature`).

## 무엇을 넣었나

| Bukkit | 내용 |
|---|---|
| `SimplePluginManager` | `Bukkit.getPluginManager()` 가 주는 실제 구현(서버마다 하나, `Compat.pluginManager()`). Paper 1.21.1 처럼 `@Deprecated(forRemoval = true)`. 필드 `server`·`plugins`·`lookupNames`·`commandMap`·`permissions`·`defaultPerms`·`permSubs`·`defSubs`·`useTimings` 를 Bukkit 이름으로 둔다 |
| `PluginManager` 전체 | `loadPlugin(s)`·`disablePlugins`·`clearPlugins`·권한 메서드 13개·`useTimings`, Paper 의 `addPermissions`·`clearPermissions` |
| `Permission`, `PermissionDefault` | 생성자 8개, `getChildren`(바꿀 수 있음)·`setDefault`·`recalculatePermissibles`·`addParent`·`getPermissibles`, `loadPermissions`/`loadPermission`(plugin.yml 규칙) |
| `Permissible` | `hasPermission(Permission)`·`isPermissionSet(Permission)`·`recalculatePermissions()`(기본 메서드) |
| `UnknownDependencyException` | |
| `Server/Bukkit#broadcast(message, permission)` | 권한 구독자 가운데 권한이 참인 발신자에게 보낸다 |
| `SimpleCommandMap.knownCommands.put` | 그 이름을 그 명령으로 명령 트리에 붙인다(helper 가 별칭을 이렇게 올린다) |

Furnace API 에는 둘을 더했다: `PluginManager#enable(FurnacePlugin)`(불러왔지만 꺼진 플러그인을 켠다 — Bukkit `enablePlugin`), `PermissionManager#replace(Permission)`(노드를 빈 순간 없이 바꾼다 — Bukkit 권한 객체를 고친 뒤 반영).

## 구조 — 기존 Furnace 와 겹치지 않게

- **플러그인 목록·켜기·끄기**: 따로 목록을 두지 않고 Furnace 플러그인 등록소를 본다(`getPlugin`·`getPlugins`·`isPluginEnabled` 는 `BukkitPluginAdapter` 를 거른다). 끄기·켜기는 Furnace `disable`/`enable` 로 가서 Furnace 가 하던 자동 정리(이벤트·명령·작업·채널·권한 첨부·핫 리로드 훅)가 그대로 돈다. Paper 처럼 `plugins`·`lookupNames` 필드는 비어 있다(Paper 의 실제 목록은 `PaperPluginInstanceManager` 에 있다).
- **이벤트**: H1 의 등록·다리(`EventBridge.ensure`) 그대로 옮겼다. `CompatPluginManager` 는 껍데기 찾기·처리기 목록 찾기만 하는 내부 손잡이가 됐다.
- **권한**: 노드는 Furnace 권한 등록소에만 산다. `permissions` 필드는 그 등록소를 Bukkit `Permission` 으로 보이는 살아 있는 지도이고, 같은 Furnace 노드는 늘 같은 Bukkit 객체로 보인다(노드가 다른 객체로 바뀌면 새로 만든다). 플러그인이 고친 Bukkit 객체를 `recalculatePermissibles()`(또는 `setDefault`) 하면 `recalculatePermissionDefaults` 가 그 객체가 이 노드의 객체일 때 등록소에 `replace` 한다. 권한 값 계산은 Furnace `PermissibleBase` 가 등록소 버전으로 알아서 다시 한다.
- **구독**: `subscribeToPermission`·`subscribeToDefaultPerms` 는 Paper 처럼 `WeakHashMap` 에 기억하고, 돌려줄 때 "지금 접속한 플레이어·콘솔 가운데 그 권한 값이 정해진 대상"(기본값 구독은 OP 여부가 같은 대상)을 더한다 — Bukkit 의 `PermissibleBase` 가 권한을 계산할 때마다 스스로 구독하는 결과와 같다. 그래서 `Bukkit.broadcast(msg, 권한)` 은 **등록되지 않은 권한이면 OP·콘솔에게도 가지 않는다**(Bukkit 과 같다).
- **plugin.yml 권한**은 이전처럼 Furnace 가 켤 때 등록소에 올린다 — `getPermission` 으로 Bukkit 객체가 보인다.

## 바닐라·Paper 와 같게 맞춘 동작 (Paper 1.21.1 원문 대조: main 커밋 `33ff3dff` 의 `patches/{api,server}/Paper-Plugins.patch`, 동작만)

- `getPlugin(name)`: 공백 → 밑줄, 대소문자 무시, plugin.yml `provides` 로도 찾는다(진짜 이름이 먼저).
- `isPluginEnabled(Plugin)`: 이 서버가 불러온 플러그인일 때만 그 켜짐, 모르는 객체·null 은 false.
- `disablePlugin`: JavaPlugin 이 아니면 `IllegalArgumentException("Only expects java plugins.")`, 이미 꺼졌으면 아무 일 없음. `onEnable` 안에서 자기를 끄는 흔한 패턴(의존 없음)도 Paper 처럼 `onDisable` 이 그 자리에서 돌고 플러그인은 꺼진 채 남는다.
- `enablePlugin`: 켜져 있으면 아무 일 없음. 꺼진 플러그인은 plugin.yml 명령을 다시 올리고 `onEnable`·`PluginEnableEvent`.
- `callEvent`: 비동기 이벤트를 주 스레드에서 부르면 `IllegalStateException("<이름> may only be triggered asynchronously.")`. 처리기 예외는 서버 로거에 `Could not pass event <이름> to <플러그인 v버전>` 으로 남기고 다음 처리기로 간다. 꺼진 플러그인의 처리기는 건너뛴다.
- `registerEvents`: 리스너 클래스가 없는 클래스를 가리켜 `getMethods` 가 `NoClassDefFoundError` 면 Paper 처럼 그 리스너만 건너뛰고(`Failed to register events for ... because ... does not exist.`) 플러그인은 계속 켠다(이전에는 던져서 플러그인 켜기가 실패했다).
- 권한: 이름은 소문자 키, `addPermission` 중복은 `IllegalArgumentException("The permission <이름> is already defined!")`, `getPermission` 은 대소문자 무시, 등록된 객체의 `getName()` 은 넣은 대소문자 그대로. `getDefaultPermissions(op)` 는 `OP`·`TRUE` / `NOT_OP`·`TRUE` 묶음. `Permissible#hasPermission(Permission)` 은 값이 정해지지 않았으면 등록 기본값이 아니라 그 객체의 기본값.
- 콘솔: `isPermissionSet`·`hasPermission` 이 늘 true 이던 것을 Furnace 콘솔의 실제 계산(OP, 첨부)으로 바꿨다 — Bukkit 콘솔도 `PermissibleBase` 다.

## Bukkit·Paper 와 다른 점 (관측 가능한 것)

1. **동기 이벤트를 주 스레드 밖에서 불러도 막지 않는다.** Paper 는 `may only be triggered synchronously.` 로 던진다. Furnace 의 게임 이벤트는 region 스레드에서 오고 그때 `isPrimaryThread()` 가 false 라, 막으면 리스너 안에서 부르는 사용자 이벤트가 모두 실패한다(사용자 확인 항목 2).
2. **실행 중 불러오기 없음**: `loadPlugin` 은 `InvalidPluginException`, `loadPlugins` 는 경고 후 빈 배열, `clearPlugins` 는 `UnsupportedFeature`. `registerInterface`·`fileAssociations`·`dependencyGraph`·Paper 의 `paperPluginManager`·`isTransitiveDependency`·`overridePermissionManager` 는 없다(`PluginLoader`·`PluginMeta` 형이 아직 없다).
3. **권한 지도 바꿔치기(LuckPerms 식 주입)는 Furnace 계산에 닿지 않는다.** 필드를 다른 지도로 바꾸면 Bukkit API 로는 보이지만 `hasPermission` 계산은 Furnace 등록소·첨부로 한다. `defaultPerms` 는 읽기 전용 뷰.
4. **같은 이름의 다른 `Permission` 객체로 `recalculatePermissionDefaults`** 를 부르면 무시한다. Paper 는 그 객체의 기본값을 기본값 묶음에 더해 버린다(등록된 것과 둘 다 남는다).
5. `removePermission` 은 기본값 묶음에서도 빠진다(Paper 는 묶음에 남기는 버릇이 있다). `getDefaultPermissions` 순서는 등록소 순서(Paper 는 넣은 순).
6. **plugin.yml 권한은 플러그인이 꺼지면 빠진다**(이 묶음 전부터의 Furnace 동작). Paper 는 남긴다. 실행 중 `addPermission` 한 것은 Paper 처럼 남는다. 귀결: 끄고 다시 켜면 그 노드는 새 Bukkit 객체로 보이고, 끄기 전에 받아 둔 옛 객체를 고쳐도(`setDefault` 등) 무시된다. 다른 플러그인이 같은 이름을 `removePermission`+`addPermission` 으로 갈아 끼웠으면 그 노드는 남긴다(설명 파일의 그 노드일 때만 지운다).
7. 구독 결과에 들어가는 것은 접속한 플레이어·콘솔·직접 구독한 대상뿐이다. Bukkit 은 권한을 계산한 다른 대상(명령 블록 발신자 등)도 들어간다.
8. `broadcast(message, permission)` 은 Paper 의 `BroadcastMessageEvent` 를 아직 내지 않는다.
9. `getPlugins()` 는 Bukkit 플러그인만(Furnace 플러그인은 Bukkit `Plugin` 이 아니다). `isPluginEnabled(String)` 은 Furnace 플러그인 이름에도 답한다(이전과 같다).
10. **꺼진 플러그인을 다시 켜는 것이 된다.** Paper 는 끌 때 클래스로더를 닫아, 다시 켜면 아직 안 올린 클래스에서 실패할 수 있다.
11. `knownCommands.put("플러그인:이름", c)` 는 Furnace 명령 트리가 이름과 접두어 형태를 함께 붙이므로 `이름` 도 생긴다. **맨 이름 키(`put("give", c)`)도 그 이름을 바닐라·다른 플러그인이 잡고 있으면 덮어쓰지 않고 접두어 형태(`플러그인:give`)만 붙이고 플러그인 로그에 경고를 남긴다** — Paper 의 `BukkitBrigForwardingMap.put` 은 덮어쓴다. 다른 플러그인 접두어는 `UnsupportedFeature`. 이름 하나를 넣을 때마다 접속자 명령 트리를 다시 보낸다.
12. 처리기 예외 뒤 Paper 의 `ServerExceptionEvent` 재발화와 `AuthorNagException` 의 "Nag author(s)" 한 번 경고는 없다(해당 클래스가 아직 없다). 로그 메시지는 Paper 와 같다.
13. Furnace API `PluginManager#enable` 로 **Furnace 플러그인**을 다시 켜면 `onEnable`·설명 파일 권한·명령만 돌아온다. 부트스트랩·`onLoad` 의 등록(수명주기 처리기·레지스트리 정의·월드젠 훅)은 끌 때 지워져 되살아나지 않는다(javadoc 에 적음). Bukkit 플러그인은 그 등록소를 쓰지 않아 해당 없다.

## 측정 (2026-11-08, `liveProbe -PlibraryCache`)

| jar | 기동 | 없는 Bukkit 심볼 (전→후) | 다음 장벽 |
|---|---|---|---|
| helper 5.6.14 | **ENABLED**(처음), `/helper`·`/helper:helper` 실행됨 | 176 → 175 | 켜기 단계에는 없음. helper 를 쓰는 플러그인이 기능을 부를 때: `BossBar`(14)·`BarColor`·`BarStyle`, `ArmorStand`(18)·`Pig`, 인벤토리 이벤트(`InventoryClickEvent` 등 — 묶음 D), `DisplaySlot`(Scoreboard), `BukkitObjectInput/OutputStream`, `AsyncPlayerPreLoginEvent` |
| CommandAPI 10.1.2 | LOADED_NOT_ENABLED(그대로) | 94 → 91 | Spigot 이름 NMS `net.minecraft.world.level.IWorldReader`(H7d) |

라이브러리 캐시 없이 도는 CI 기준표(`realworld-baseline.properties`)의 helper 기동 상태는 여전히 Guava 없음(`LOADED_NOT_ENABLED`)이다 — 기준표 시험은 내려받기를 하지 않는다. 캐시는 Maven 배치(`그룹/이름/버전/이름-버전.jar`) 폴더다: `ServerLibraries` 의 Central 좌표 24개는 Maven Central(컨테이너에서는 구글 미러)에서, Mojang 3개(brigadier·authlib·datafixerupper)는 `main` 브랜치 `tools/vanilla-data/minecraft_server.1.21.1.jar` 의 `META-INF/libraries/` 에서 복사해 `-PlibraryCache=<폴더>` 로 준다.

- 개발용 `liveProbe` 에 `-Pcommands="helper;help helper"` 를 더했다 — 켠 뒤 그 명령들을 콘솔로 실행해 출력을 보인다.

독립 검토(`furnace/reviews/spm-pr148-review.md`, Paper 1.21.1 원문 대조)에서 막는 결함은 없었고, 지적 F1~F3·F5·F7 을 반영했다(위 6·11·13번, 노드 동일성 지우기, `Permission` 의 서버 밖 처리, `replace` javadoc). F6(플레이어 아닌 엔티티의 문자열·객체 권한 답이 다름)은 이 묶음 전부터의 것이라 후속 후보.

## 시험

`SimplePluginManagerTest`(헤드리스 서버, 시험 안에서 컴파일한 진짜 jar 둘):
helper 의 리플렉션 경로 그대로(생성자·`commandMap`·`knownCommands.put` 별칭 → 실행), 다른 접두어 거절, 없는 이벤트 클래스 리스너 건너뛰기, 찾기(대소문자·provides),
`onEnable` 안에서 자기 끄기 → `enablePlugin` 으로 다시 켜기(명령 다시 올림), 바닐라 이름 `put` 은 접두어 형태만, 갈아 끼운 plugin.yml 노드는 끌 때 남김, 비동기 이벤트 스레드 검사, plugin.yml 권한 객체·기본값 묶음·`permissions` 필드,
실행 중 권한 추가·자식 고치기·`setDefault`·`addParent`·지우기가 Furnace 등록소와 플레이어 권한에 반영, `hasPermission(Permission)`, 구독·권한 방송 수.
