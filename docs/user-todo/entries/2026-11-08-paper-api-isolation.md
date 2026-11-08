# Paper API 클래스 격리 (호환 모듈 지연 로딩) — 2026-11-08

결정(Paper API 클래스 0.6 MB 는 서버 jar 에 두되, Bukkit·Paper 플러그인이 없으면 서버 핵심과 얽히지 않는다)을 구현했다. 서버는 호환 어댑터를 Bukkit·Paper 플러그인 jar 를 처음 만났을 때에야 만들고, 그 전에는 호환 모듈 클래스가 JVM 에 하나도 올라오지 않는다(실제 `fatJar` 로 기동해 `-Xlog:class+load` 7,730줄 중 0건 확인). 설명: `docs/api/compat-paper.md` "격리 보장".

- **직접 확인(선택):** 서버 jar 로 플러그인 없이 기동해 보세요 — `java -Xlog:class+load=info:file=cl.log -jar furnace-server.jar --nogui` 후 `grep -E "org\.bukkit\.|compat\.paper|objectweb" cl.log` 가 비어 있으면 됩니다. `plugins/bukkit-plugins/` 에 Bukkit 플러그인을 넣으면 `CompatFormat` 이 나타납니다(정상).
- **확정(도미니코, 2026-11-08):** Furnace 네이티브 플러그인에는 호환 클래스를 숨긴다(지향점 = 네이티브 플러그인은 Furnace API 만 쓴다). 구현 완료: 네이티브 플러그인 클래스로더가 `org.bukkit.*`·`io.papermc.*`·`com.destroystokyo.*`·호환 코어·ASM·`net.minecraft.*` 호환 shim 을 서버 jar 에서 찾지 않는다. Bukkit·Paper 플러그인은 영향 없음. 이미 Bukkit 클래스를 몰래 쓰던 네이티브 플러그인이 있다면 `NoClassDefFoundError` 가 나므로(없을 것으로 봄) 알려 주세요.
