# Paper API 클래스 격리 (호환 모듈 지연 로딩) — 2026-11-08

결정(Paper API 클래스 0.6 MB 는 서버 jar 에 두되, Bukkit·Paper 플러그인이 없으면 서버 핵심과 얽히지 않는다)을 구현했다. 서버는 호환 어댑터를 Bukkit·Paper 플러그인 jar 를 처음 만났을 때에야 만들고, 그 전에는 호환 모듈 클래스가 JVM 에 하나도 올라오지 않는다(실제 `fatJar` 로 기동해 `-Xlog:class+load` 7,730줄 중 0건 확인). 설명: `docs/api/compat-paper.md` "격리 보장".

- **직접 확인(선택):** 서버 jar 로 플러그인 없이 기동해 보세요 — `java -Xlog:class+load=info:file=cl.log -jar furnace-server.jar --nogui` 후 `grep -E "org\.bukkit\.|compat\.paper|objectweb" cl.log` 가 비어 있으면 됩니다. `plugins/bukkit-plugins/` 에 Bukkit 플러그인을 넣으면 `CompatFormat` 이 나타납니다(정상).
- **결정(작음):** Furnace 네이티브 플러그인에게도 호환 클래스를 숨길까요? 지금은 서버 `fatJar` 클래스패스에 `org.bukkit.*`·ASM 이 있어 네이티브 플러그인의 클래스로더(부모 우선)에서 보입니다. 네이티브 플러그인이 자기 jar 에 다른 버전 ASM 을 넣어도 서버의 ASM 9.7.1 이 먼저 잡힙니다(드문 충돌). 호환 jar 의 `net.minecraft.*` NMS shim 11개도 같은 식으로 보입니다. 후보: ① 그대로 둔다(추천 — 서버 핵심이 호환에 기대는 곳은 없고 충돌은 드물다), ② 네이티브 플러그인 로더가 호환 패키지를 부모에서 찾지 않게 막는다(작은 작업, 호환 시험의 네이티브 시험 플러그인 몇 개를 고쳐야 함), ③ 호환 모듈을 전용 클래스로더로 완전히 분리한다(가장 확실하지만 `fatJar` 구조를 바꾸는 큰 작업). 답이 없으면 ①.
