## 2026-11-07 JavaPlugin 명령·스케줄러·서비스 연결 — 함정 (H4 다음 묶음 A)

- **시험 jar 의 클래스는 부모 클래스로더가 먼저 찾는다.** `PluginClassLoader` 의 부모는 서버(시험에서는 시험 클래스패스)라서, 시험 클래스패스에서 `Jars.write` 로 복사한 픽스처 클래스는 jar 가 아니라 **앱 클래스로더**가 올린다 — `getProvidingPlugin`·"클래스로더로 주인 찾기"를 확인할 수 없다. 해결: `fixtures/JarCompiler` 가 `javax.tools` 로 소스를 그 자리에서 컴파일해 jar 에 넣는다(시험 클래스패스에 없는 클래스만 들어간다). 시험과 플러그인 사이의 값은 시험 클래스패스에 있는 `WiringProbe` 칠판으로 주고받는다.
- **바닐라 명령 이름과 겹치는 별칭을 시험에 쓰지 마라.** `w`(바닐라 `/msg` 별칭)는 트리에 이미 있어 플러그인 별칭이 접두어 형태(`플러그인:w`)로만 올라간다 — 실제 Bukkit 도 같은 결과라 시험이 `wi` 로 바꿨다.
- **서버 등록소는 plugin.yml 별칭을 스스로 더한다.** `CommandRegistryImpl.register` 가 `info.aliases()` 를 인자의 별칭에 더하므로, 같은 별칭을 인자로도 주면 두 번 처리돼 "이미 있어 접두어로만 등록한다" 경고가 헛으로 났다. 이름 목록을 `LinkedHashSet` 으로 바꿔 한 번만 처리한다.
- **`knownCommands` 는 필드 이름이 `knownCommands` 인 `Map` 이어야 한다**(플러그인이 리플렉션으로 필드를 읽는다). 안쪽 저장소와 따로 두고 밖에는 지우기를 트리에 반영하는 뷰를 내보낸다. `AbstractMap` 의 `values().removeIf`/`keySet().removeIf` 는 `entrySet().iterator().remove()` 를 거치므로 반복자의 `remove` 를 이어야 한다.
- **`BukkitScheduler` 의 `Consumer<BukkitTask>` 변형**은 핸들이 예약 뒤에야 생기는데, 비동기 호출자와 박동은 다른 스레드라 박동이 먼저 도착할 수 있다. 핸들 `CompletableFuture` 를 몸통 맨 앞에서 `join` 한다(호출자는 예약 직후 채우므로 기다림은 마이크로초).
- **서비스 정렬은 일부러 Bukkit 의 이진 탐색을 그대로 쓴다.** 같은 우선순위는 "나중 것이 앞"이 되는 자리에 들어간다(작은 목록에서) — 정렬 안정성을 가정한 코드로 바꾸면 `load` 결과가 달라진다.
- **JavaPlugin 하위 클래스 안의 호출은 owner 가 자기 클래스다**(javac). `Coverage.attribute` 가 상속 사슬을 따라 올라가 API 클래스로 귀속한다(`ClassRefs.declared` 로 자기 선언 멤버를 걸러 냄). 이 변경 뒤 기준표 숫자가 바뀌므로(늘어난 jar 도 있음) 이전 묶음의 기준표와 직접 비교하지 않는다.
- 서버를 다시 띄우는 시험에서 `Compat.State` 는 Furnace 인스턴스가 바뀌면 옛 `LegacyScheduler` 박동을 닫는다(닫지 않아도 죽은 서버의 박동이지만 스레드 이름이 시험 로그에 남지 않게).
