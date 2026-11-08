# 호환 플러그인용 서버 제공 라이브러리 필요 시 내려받기 (H4 묶음 B) — 2026-11-08

H4 측정에서 실제 Paper 플러그인이 Adventure 80개·Guava 67개·Brigadier 30개·SnakeYAML 4개 클래스를 요구하는데 Furnace 서버 클래스패스에는 Gson 만 있었다. Paper 는 이것들을 서버 jar 에 싣고 있어 플러그인이 선언 없이 기댄다. 사용자 결정(2026-11-08, 결정 4·5 → ②+③): **서버 jar 에 넣지 않고, Bukkit·Paper 플러그인이 있을 때만 자동으로 내려받는다. Mojang 저장소(Brigadier 등)도 허용한다.**

- 설명서(키·목록·동작): [`docs/api/plugin-libraries.md`](../api/plugin-libraries.md) "Paper/Bukkit 플러그인용 서버 제공 라이브러리"
- 코드: `furnace-server` 의 `plugin/library/ServerLibraries`(목록·고정 sha256·표지 훑기), `SharedLibraryLoader`(공유 로더), `LibraryResolver#ensurePinned`(고정값 내려받기), `PluginBootstrapper#sharedLibrariesFor`(감지·준비·경고)
- 사용자 확인: `docs/user-todo/entries/2026-11-08-h4b-libraries.md`

## 설계 결정

| 물음 | 정한 것 | 이유 |
|---|---|---|
| 언제 받나 | `plugins/bukkit-plugins/` 의 호환 형식 플러그인을 준비할 때, 그 jar 의 클래스 파일에 묶음 표지(패키지 이름, 슬래시·점 표기)가 보이면(`needed`, 기본). `all`·`off` 로 바꿀 수 있다 | "있을 때만"의 가장 좁은 해석. fastutil(23 MB)처럼 큰 묶음은 쓰는 플러그인이 있을 때만 받는다. 점 표기도 세서 `Class.forName("com.google...")` 리플렉션도 잡는다 |
| 어디에 붙나 | 공유 로더 하나(부모 = 서버 클래스로더), 모든 호환 플러그인 로더의 부모 | Paper 에서 이 라이브러리는 서버 로더에 한 벌 — 플러그인끼리 `Component` 를 주고받아도 같은 Class. 플러그인마다 붙이면(R1 방식) ClassCastException |
| 무엇을 믿나 | 코드에 고정한 sha256 만. 잠금 파일·`.sha1` 은 안 쓴다 | 목록이 코드에 닫혀 있으니 처음 받기부터 공급망을 막을 수 있다. Mojang 이 바닐라 jar 에 적은 sha256 을 그대로 쓴 것이 10개 |
| 전이 의존 | 안 따라간다. 목록 자체를 닫았다(pom 으로 확인: adventure → option·ansi·examination, authlib → guava·commons-io·commons-lang3, DFU → guava·fastutil) | 고정값을 쓰려면 받는 jar 가 미리 정해져야 한다 |
| 못 받으면 | 그 묶음만 경고(주소·캐시 경로·sha256 안내) 후 건너뛰고, 플러그인은 올린다 | Paper 라면 있었을 클래스라 플러그인을 미리 빼는 것은 과하다. 쓰는 순간의 `NoClassDefFoundError` 는 서버가 플러그인 오류로 남긴다 |
| 오프라인 | `plugins.libraries.offline=true` 면 캐시만. 캐시에 손으로 둔 jar 도 sha256 이 맞으면 쓴다 | R1 과 같은 키·같은 캐시 폴더 |
| plugin.yml `libraries:` | 호환 플러그인은 `plugins.compat.libraries.declared`(기본 켜짐)로 받는다 | Paper·Spigot 은 기본으로 받는다(관측 동작 맞춤). R1 의 체크섬·잠금 파일 규칙 그대로 |
| 시험 서버 | `FurnaceTestServer` 는 기본 꺼짐 | 시험이 네트워크에 기대지 않게. `builder.libraries(...)` 로 켠다 |

## 실제 jar 측정 (helper 5.6.14·CommandAPI 10.1.2)

캐시를 미리 채우고(Central jar 는 구글 미러, Mojang jar 3개는 바닐라 1.21.1 서버 jar 안의 것 — 둘 다 고정 sha256 과 같음) `offline` 으로 `liveProbe` 를 돌렸다(`-PlibraryCache=<폴더>`, 이번에 더한 옵션).

| 플러그인 | 받은 묶음(needed 판정) | 전 첫 장애물 | 후 첫 장애물 | 그다음(임시로 빈칸을 메워 본 것, 커밋 안 함) |
|---|---|---|---|---|
| helper | guava, snakeyaml, bungeecord-chat | `com.google.common.base.Supplier` 없음 | `org.bukkit.plugin.messaging.PluginMessageListener` 없음(묶음 E) | `org.bukkit.plugin.SimplePluginManager` 없음 |
| CommandAPI | guava, adventure, bungeecord-chat, joml, fastutil, commons-lang3, commons-io, log4j, brigadier, authlib, datafixerupper | `com.mojang.brigadier.context.CommandContext` 없음 | `PluginMessageListener` 없음(NMS 훅 적재 중) | `net.minecraft.world.level.IWorldReader` 없음(Spigot 이름 NMS — H7d 변환표) |

**결론: 라이브러리 장벽은 둘 다 넘었다. 둘 다 아직 켜지지는 않는다** — 다음 장벽은 Bukkit API 빈칸(묶음 E 플러그인 메시징, `SimplePluginManager`)과 Spigot 이름 NMS(H7d)다. 기준표 시험(`RealWorldCompatTest`)은 시험 서버 기본이 꺼짐이라 기준값이 바뀌지 않는다.

## Paper API 클래스(결정 4의 나머지)는 내려받지 않았다

`org.bukkit.*`·Paper API 클래스는 Furnace 가 H1~H7 에서 새로 쓴 호환 계층(`furnace-compat-paper`)이 구현한다 — 진짜 paper-api jar 는 인터페이스 묶음이라 CraftBukkit 구현 없이는 돌지 않고, 우리 클래스와 이름이 겹쳐 함께 둘 수 없다(그리고 `repo.papermc.io` 는 이 컨테이너에서 막혀 있다). 그래서 "받는" 대상이 아니라 우리 jar 의 일부다. 크기는 호환 jar 0.39 MB + ASM 약 0.2 MB 로 서버 fat jar(21.7 MB)의 3% 정도라, 따로 떼어 받게 하는 이득이 작다. 사용자 확인 항목으로 남겼다.

## 독립 검토 반영 (furnace/reviews/h4b-pr145-review.md)

- BungeeChat 표지를 `net/md_5/bungee/api/` 로 넓혔다(`ChatColor` 만 쓰는 헥스 색 플러그인), Guava 를 요구 묶음으로(ChatColor·ComponentBuilder 가 `Preconditions` 를 씀).
- 응답 없는 네트워크: 연결이 한 번 실패한 저장소 호스트는 그 시작 동안 다시 시도하지 않는다(전에는 all 모드에서 13번 × 30초). HTTP 오류는 호스트를 막지 않는다.
- 설명서에 log4j-core 없음·개발 모드 핫 리로드가 받는 동안 전역 스레드를 기다리게 함을 적었다. `closeSharedLibraries` 가 시도 기록도 비운다.

## 한계

- 표지 훑기는 jar-in-jar(플러그인 jar 안의 jar 를 스스로 여는 경우)를 못 본다 → `all` 로.
- 실제 Maven Central·Mojang 저장소에서 받는 길은 이 컨테이너에서 확인하지 못했다(Central 은 429, `libraries.minecraft.net` 은 연결 거부). 받기·검증 경로는 메모리 저장소 시험, 캐시·로더 경로는 실제 jar 로 확인했다.
- Paper 1.21.1 이 싣는 정확한 버전은 대조 못 했다. 바닐라와 겹치는 것은 바닐라 버전, 나머지는 기억 기준(Adventure 4.17.0·SnakeYAML 2.2·BungeeChat 1.20-R0.2). Paper 가 따로 싣는 다른 것(예: `commons-lang` 2.6 이 아직 있는지)은 확인하지 못해 넣지 않았다.
- 호환 계층 클래스(서버 클래스로더)는 공유 로더의 Adventure 를 볼 수 없다. 나중에 `Player#sendMessage(Component)` 같은 Adventure 서명을 호환 API 에 넣으려면 호환 모듈 자체를 공유 로더 아래로 옮기는 설계가 필요하다(묶음 F 와 함께).
