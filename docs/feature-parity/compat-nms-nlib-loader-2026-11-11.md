# 범용 NMS 어댑터 N-lib — 호환 계층이 라이브러리 타입을 보게 하기 (설계안, 2026-11-11)

## 1. 문제

N-lib 은 계획(`compat-nms-adapter-plan-2026-11-11.md` 3장)에서 "authlib·DFU·Brigadier 를 결정 5(필요할 때 내려받기)에 넣는다"였다. 받는 쪽은 H4 묶음 B(PR #145)로 이미 끝났다: Brigadier·authlib·DataFixerUpper 는 바닐라 1.21.1 `META-INF/libraries.list` 의 sha256 으로 libraries.minecraft.net 에서, Adventure·Guava·SnakeYAML 등은 Central 에서 받는다. 받은 jar 는 공유 로더(`SharedLibraryLoader`)에 붙는다.

막힌 것은 **누가 그 타입을 볼 수 있느냐**다.

```
서버 로더 (서버 jar = furnace-server + furnace-compat-paper 를 합친 fat jar)
  └─ SharedLibraryLoader (authlib·DFU·Brigadier·Adventure·Guava …)
       └─ 호환 플러그인 로더 (Bukkit·Paper 플러그인)
```

- 플러그인은 공유 로더를 통해 라이브러리를 본다.
- 호환 계층(`org.bukkit.*`·`net.minecraft.*` shim·CraftBukkit shim)은 **서버 로더**에 있다. 부모는 자식을 볼 수 없다. 그래서 shim 은 `com.mojang.authlib.GameProfile`·`com.mojang.serialization.Codec`·`net.kyori.adventure.text.Component` 를 서명에도 본문에도 쓸 수 없다.

측정에서 이 타입이 얼마나 쓰이는지(소스 10개, `$S/nmsurvey`):

| 타입 | import 수 | 대표 쓰임 |
|---|---|---|
| authlib `GameProfile` | 229 | `ServerPlayer.getGameProfile()`, 가짜 플레이어 생성자(N4a), 머리 텍스처 |
| authlib `Property`·`PropertyMap` | 114·43 | 스킨 텍스처 |
| DFU `Pair`·`Either` | 42·3 | `Registry.getTags()`, `HolderSet.unwrap()` |
| DFU `Codec`·`DynamicOps`·`Dynamic`·`JsonOps`·`MapCodec` | 16·11·10·9·8 | `BlockState.CODEC`, `ItemStack.CODEC`, NBT↔JSON |
| Brigadier `CommandDispatcher`·`CommandContext` 등 | 42·19… | `Commands.getDispatcher()`, Paper `io.papermc.paper.command.brigadier` |

Paper API 쪽도 같은 벽에 막혀 있다. 지금 호환 계층에는 Adventure 서명이 하나도 없다. 그래서 `Player.sendMessage(Component)`·`displayName()` 같은 Paper 메서드가 통째로 빠져 있다(H4b 기억의 "호환 API 에 Adventure 서명을 넣으려면 먼저 설계" 항목). 따라서 N-lib 은 NMS 만의 문제가 아니라 **호환 계층 전체의 로더 배치 문제**다.

## 2. 후보

### A. 호환 계층을 공유 로더 아래로 내린다 (추천)

```
서버 로더 (furnace-server 만)
  └─ SharedLibraryLoader
       └─ CompatLoader (furnace-compat-paper[+ 나중에 furnace-compat-nms])
            └─ 호환 플러그인 로더
```

1. **jar 배치:** fat jar 에 호환 모듈 클래스를 풀어 넣지 않는다. 대신 jar 하나를 통째로 리소스(`META-INF/furnace/compat/furnace-compat-paper.jar`)로 싣는다. 크기는 같다.
2. **로더 만들기:** 서버는 Bukkit 호환이 켜져 있을 때만 `CompatLoader(urls=그 jar, parent=SharedLibraryLoader)` 를 만든다. 그리고 `PluginFormats.discover(compatLoader)` 로 ServiceLoader 를 돈다. 서버는 지금도 호환 클래스를 import 하지 않으므로(ServiceLoader `PluginFormat` 만 씀) 코드 결합은 바뀌지 않는다.
3. **로더 순서:** 공유 로더는 비어 있는 채로 먼저 만든다. jar 는 지금처럼 플러그인을 훑은 뒤 뒤에 붙인다(`URLClassLoader` 는 붙인 뒤에도 찾는다).
4. **호환 계층이 늘 기대는 묶음:** Adventure·Guava 처럼 호환 계층이 서명에 쓰는 묶음은 "Bukkit 플러그인이 하나라도 있으면 받는다"로 바꾼다. 결정 4("Bukkit·Paper 플러그인이 있을 때만")와 같은 조건이라 사용자 결정은 그대로다. 표지(marker)로 고르는 것은 그 밖의 묶음(authlib·DFU·Brigadier·SnakeYAML…)만 남긴다.
5. **라이브러리가 없을 때:** authlib·DFU 를 서명에 쓰는 shim 은 그 타입이 없을 수 있다. 플러그인이 그 메서드를 부를 때는 이미 그 플러그인의 표지 때문에 받아져 있다. 다만 검증기(verifier)가 클래스 로드 때 대입 가능 검사로 타입을 미리 읽는 경우가 있다. 그래서 라이브러리 타입을 쓰는 본문은 따로 작은 클래스(`…$Lib` 도우미)로 떼어 둔다. 그러면 바깥 클래스 로드가 라이브러리를 끌어오지 않는다.
6. **시험:** 호환 모듈 시험은 지금처럼 앱 클래스패스에서 돈다. 라이브러리는 `testImplementation` 으로 준다. libraries.minecraft.net 이 막혀 있으므로 바닐라 번들러 jar 안의 실물을 쓰는 기존 H4b 방식을 따른다. 로더 배치 자체는 fat jar 를 띄우는 시험(H4 liveProbe 식) 하나로 확인한다.

- **장점:** Paper 와 같은 그림이다. Paper 는 라이브러리와 서버 구현이 같은 로더에 있다. 한 번 바꾸면 Adventure·authlib·DFU·Brigadier 서명이 모두 풀린다. 계획의 `furnace-compat-nms`(서버에 기대는 shim)도 같은 CompatLoader 에 함께 싣는다.
- **위험:**
  - 서버 로더 쪽 코드가 호환 클래스를 이름으로 찾는 곳이 있으면 깨진다(`CompatPackages`·`hideCompat` 은 오히려 단순해진다).
  - 핫 리로드(B8)가 호환 플러그인 로더를 다시 만들 때 부모가 바뀌지 않는지 확인해야 한다.
  - 시험 서버(`FurnaceTestServer`)는 라이브러리 내려받기가 기본 꺼짐이고 호환 모듈이 앱 클래스패스에 있다. 이 경로와 실제 fat jar 경로가 갈라진다.

### B. 라이브러리 API 를 호환 계층에 새로 쓴다(clean-room)

`GameProfile`·`Property`·`PropertyMap`·`Pair` 같은 작은 값 클래스를 서버 jar 에 Furnace 가 직접 쓴다.

- 부모 우선 탓에 서버 것이 받은 jar 의 같은 이름 클래스를 가린다. 그러면 받은 authlib 의 나머지(`YggdrasilMinecraftSessionService` 등)가 우리 `GameProfile` 에 링크된다. 바이트코드 수준까지 같지 않으면 깨진다.
- `Codec`·`RecordCodecBuilder`·Brigadier 처럼 큰 라이브러리를 다시 쓰는 것은 범용 원칙에도, 비용에도 맞지 않는다.
- Adventure 는 표면이 너무 커서 이 길로는 풀 수 없다.

### C. 라이브러리를 서버 로더에 넣는다

시작기(launcher)가 플러그인 폴더를 먼저 훑어 라이브러리를 받는다. 그다음 서버를 새 로더(또는 새 JVM)에서 띄운다. Paper 의 paperclip 과 비슷한 구조다.

- 그림은 A 와 같다.
- 시작 경로를 새로 만들어야 한다. 서버 jar 를 그냥 `java -jar` 로 띄우는 지금의 사용 방식과 시험 하네스가 모두 바뀐다.
- 핫 리로드로 새 묶음이 필요해지면 이미 뜬 서버 로더에는 붙일 수 없다.

## 3. 추천과 순서

A 를 추천한다. 순서는 다음과 같다.

1. **N-lib-1 로더 배치:** fat jar 에 호환 jar 를 내포하고 CompatLoader 를 둔다. Adventure·Guava 를 "Bukkit 플러그인이 있으면" 묶음으로 바꾼다. fat jar 기동 시험을 넣는다. 행동 변화가 없는 구조 PR 이다.
2. **N-lib-2 authlib 다리:** `ServerPlayer.getGameProfile()`, CraftPlayer 의 `PlayerProfile` ↔ `GameProfile`, `Property`(텍스처) 를 둔다. Furnace API 의 프로필 값에서 만든다.
3. **N-lib-3 DFU 다리:** `Pair`·`Either` 를 쓰는 `Registry.getTags()`·`HolderSet.unwrap()` 를 둔다. `BlockState.CODEC`·`ItemStack.CODEC` 는 NBT 경로(`NbtOps`)가 필요해 N1 뒤로 미룬다.
4. Paper Adventure 서명(`sendMessage(Component)` 등)은 같은 길로 풀린다. 이것은 H 묶음(호환 API) 쪽 작업으로 넘긴다.

## 4. 판단을 부탁할 것 (독립 검토)

1. 후보 A·B·C 중 A 가 맞는가. A 의 위험(시험 서버 경로가 갈라짐, 핫 리로드, 검증기 선로드)을 더 싸게 막는 방법이 있는가.
2. 1-5 의 "라이브러리 타입 본문을 도우미 클래스로 뗀다"가 HotSpot 검증기에서 실제로 충분한가. 서명에만 나오는 타입은 해석이 늦게 일어나는가.
3. Adventure·Guava 를 "Bukkit 플러그인이 있으면 늘" 받는 것이 결정 4·5 와 어긋나지 않는가.

## 5. 검토 반영과 구현 (N-lib-1, 2026-11-11)

독립 검토(`furnace/reviews/nlib-loader-review.md`)는 A 방향에 동의하면서 고칠 점을 내놓았다. 반영한 것은 다음과 같다.

- **A' — 새 로더 대신 공유 로더의 첫 항목으로 붙인다.** Adventure 는 제공자를 자기 로더로 찾는다. `adventure-api-4.17.0` 의 `Services0.loader` 는 `ServiceLoader.load(type, type.getClassLoader())` 이고, 바이트코드에서 확인했다. 호환 계층을 자식 로더에 두면 Adventure 가 호환 계층의 제공자(`ClickCallback.Provider` 등)를 못 본다. 시제품(자식 로더판)으로 fat jar 를 띄워 로더 사슬이 `PluginClassLoader > furnace-compat > furnace-server-libraries > app` 인 것을 확인한 뒤 A' 로 바꿨다. 지금 사슬은 `PluginClassLoader > furnace-server-libraries > app` 이고, 호환 jar 가 공유 로더의 첫 URL 이다.
- **jar 안의 jar 는 꺼내 붙인다.** `CompatJars` 가 `META-INF/furnace/compat/index.txt` 순서대로 jar 를 `plugins/.libraries/compat/<이름>-<sha256 16자>.jar` 로 꺼낸다. 내용이 같으면 다시 쓰지 않는다. 판이 바뀌면 새 파일을 쓰고 열린 옛 파일은 덮어쓰지 않는다. 호환 모듈의 런타임 의존(ASM)도 함께 꺼낸다.
- **경로 선택은 한 곳에서 한다(`PluginBootstrapper.bootstrap(dir, settings, source)`).** 색인이 없으면 예전처럼 클래스패스 방식(시험·개발 실행)이다. 색인이 있고 클래스패스에도 호환 서비스 자원이 있으면 시작을 거부한다(같은 클래스가 두 로더에 정의되는 일을 막는다).
- **호환 계층이 기대는 묶음.** 호환 jar 를 라이브러리 표지로 훑어 자동으로 정한다(`compatCore`). needed 모드에서도 Bukkit 플러그인이 있으면 늘 준비한다. 받지 못하면 ERROR 한 줄을 남긴다. 반사(`getMethod` 하나)도 그 클래스의 서명을 모두 풀기 때문에, 그 플러그인이 그 라이브러리를 쓰지 않아도 있어야 한다. 지금은 비어 있다.
- **시험:**
  - server `CompatJarsTest` 6개: 색인, 꺼내기, 첫 항목, 두 경로 거부, Bukkit 플러그인 없으면 꺼내지 않음.
  - compat `CompatLinkTest`: 선택 라이브러리 없이 호환 클래스 전부를 링크해 본다. 없는 이름으로 `findStatic` 을 하면 초기화 없이 검증이 일어난다. JDK 21 실험에서 라이브러리가 없으면 원인이 `NoClassDefFoundError` 인 `IllegalAccessException`, 있으면 `NoSuchMethodException` 이었다. `$Lib` 도우미만 실패를 허용한다.
  - fatJar 태스크: 최상위에 호환 패키지 클래스가 하나라도 있으면 빌드 실패.
  - fat jar 기동(로컬): Bukkit 플러그인 하나가 호환 계층 클래스를 공유 로더에서 받고, 시스템 로더는 `org.bukkit.Bukkit` 을 못 본다.
- **사용자 알림과 결정:** `docs/user-todo/entries/2026-11-11-nlib-loader.md`. needed 의 뜻이 넓어졌고, Adventure 를 받지 못할 때 어떻게 할지 정해야 한다(추천: 내려받기 유지). 검토 지적대로 이 PR 은 "행동 변화 없는 구조 PR" 이 아니다. 받는 묶음 규칙이 바뀌었다(지금은 호환 계층이 라이브러리를 안 써서 실제로 더 받는 것은 없다).
- **남은 것:** Guava 는 authlib·DFU 의 의존으로 따라온다(닫힘). 서버 jar 의 gson 2.11 을 받은 라이브러리들이 함께 쓰므로 gson 판을 바꿀 때 확인할 것.

### PR #229 독립 검토 반영 (`furnace/reviews/pr229-nlib1-review.md`)

검토 결론은 승인이었다. 반영한 것은 다음과 같다.

- **R1:** `sharedLoader()` 는 지역 변수로 로더를 다 만든 뒤에야 필드에 넣는다. 꺼내기나 훑기가 실패하면 원인을 기억해 두고, 다시 부를 때 같은 예외를 던진다. 예전에는 실패 뒤 다음 호출이 호환 jar 없는 로더를 돌려주고, 어댑터는 빈 목록을 캐시했다.
- **N1:** 이미 있는 꺼낸 파일은 크기만 보지 않고 내용 sha256 을 다시 재서 확인한다.
- **N2:** 같은 jar 의 옛 판 파일을 지운다.
- **N4·N5:** fatJar 검사는 이제 접두 목록을 쓰지 않는다. 내포 jar 의 클래스 이름이 최상위에 있으면 실패한다. 최상위에 `PluginFormat` 서비스 자원이 있어도 실패한다.
- **N7:** `CompatLinkTest` 의 `Class.forName` 을 try 안으로 옮겼다.
- **낡은 주석 세 곳**을 고쳤다.

남은 것은 N-lib-2 에서 다룬다.
- **N6:** authlib 를 `testImplementation` 으로 넣으면 `CompatLinkTest` 가 헛돈다. 라이브러리 패키지를 가리는 전용 로더에서 링크하도록 바꾼다.
- **N9:** authlib·DFU 가 compatCore 에 들어오면 받는 양(fastutil 포함)을 user-todo 에 적는다.
- **N8:** fat jar 기동을 자동 시험으로 만든다.

## 6. N-lib-2 authlib 다리 (2026-11-11)

- **Furnace API:** `Player.profileProperties()` — 접속 때 받은 프로필 속성(서명 포함). 오프라인 접속이면 비어 있다. 서버는 `entity.Player.profileProperties()`(로그인 때 세션 서버 응답으로 정해짐)를 옮겨 준다.
- **호환 계층:**
  - `net.minecraft.world.entity.player.Player.getGameProfile()`: 바닐라처럼 플레이어마다 하나인 `GameProfile`. 핸들을 만들 때 UUID·이름·속성(`Property(name, value, signature)`)으로 채운다. authlib 을 본문에 쓰는 부분은 `Player$Lib` 로 뗐다. authlib 이 없으면 생성자는 `LinkageError` 를 삼키고, `getGameProfile()` 을 부를 때 다시 만들어 원인 오류를 그대로 낸다.
  - `CraftPlayer.getProfile()`: CraftBukkit 과 같이 `getHandle().getGameProfile()`.
- **빌드:** 호환 모듈이 `com.mojang:authlib:6.0.54` 와 `guava:32.1.2-jre` 를 compileOnly 로 쓴다(바닐라 1.21.1 판). authlib 은 Central 에 없어 `libraries.minecraft.net` 저장소를 `com.mojang` 묶음에만 연다. 배포 jar 에는 싣지 않는다 — 서버가 H4b 경로로 sha256 을 맞춰 받는다. 이 컨테이너처럼 그 호스트에 닿지 않는 곳에서는 바닐라 번들러 jar 안의 같은 jar(sha256 같음)로 만든 로컬 저장소를 init 스크립트로 가리킨다(커밋하지 않음).
- **compatCore:** 호환 jar 가 `com/mojang/authlib/` 를 가리키므로 훑기가 authlib 묶음을 자동으로 넣는다. 받는 양은 `docs/user-todo/entries/2026-11-11-nlib-authlib.md`(N9).
- **N6 반영:** `CompatLinkTest` 는 시험 클래스패스에서 선택 라이브러리 jar(authlib·Guava·DFU·Brigadier·Adventure)를 뺀 전용 로더(부모 = 플랫폼 로더)로 호환 클래스를 다시 올려 링크한다. 그 로더가 정말 라이브러리를 가리는지, 검증기가 라이브러리 클래스를 읽어야 하는 본문(`LinkProbe`: `PropertyMap` 을 `ForwardingMultimap` 으로 돌려줌)이 실패로 잡히는지 먼저 확인한다.
- **가정·남은 구멍:**
  - 플러그인이 `GameProfile` 의 속성을 바꿔도(스킨 바꾸기) 서버의 탭 목록·스킨에는 반영되지 않는다. Paper 는 다음 플레이어 정보 패킷이 바뀐 프로필을 읽는다. 쓰기 방향 다리는 API(`Player` 의 프로필 속성 쓰기)와 다시 보내기 규칙이 필요해 따로 다룬다.
  - 로그인 뒤에 서버 속성이 바뀌어도 이미 만든 프로필은 그대로다(지금 서버는 로그인 때만 정한다).
  - Bukkit `org.bukkit.profile.PlayerProfile`·Paper `PlayerProfile` 과의 변환은 그 API 를 호환 계층에 넣을 때(H 묶음) 함께 한다.
  - 가짜 플레이어 생성자(`new ServerPlayer(server, level, GameProfile, ClientInformation)`)는 N4a.

### PR #231 독립 검토 반영 (`furnace/reviews/pr231-nlib2-review.md`)

결론은 조건부 승인이었다. 코드 결함은 없었고, 병합 조건은 문서 하나였다.

- **R1:** Mojang 저장소에 닿지 못하는 서버에서는 흔한 반사(`CraftPlayer.getMethod("getHandle")` 등)가 이제 실패한다. 검토자가 fat jar 기동으로 직접 재현했다. user-todo `2026-11-11-nlib-authlib.md` 를 알림에서 결정 항목으로 바꿨다. 추천은 내려받기 유지다.
- **N3:** `CompatLinkTest` 의 전용 로더는 이제 jar 이름 접두가 아니라 `ServerLibraries` 표지 패키지를 `loadClass` 에서 막는다. N-lib-3 에서 fastutil 같은 묶음이 따라와도 목록을 따로 고칠 필요가 없다.
- **N4:** `CompatLinkTest.compatCoreIsOnlyAuthlib` 를 넣었다. 호환 클래스를 jar 로 묶어 서버와 같은 훑기를 돌리고, 결과가 `[authlib]` 인지 본다.
- **N5:** `docs/api/compat-paper.md` 에 "스킨 바꾸기는 아직 반영되지 않는다" 를 적었다.
- **N6:** `tools/compat/local-mojang-repo/` 를 넣었다. 번들러 jar 에서 sha256 을 맞춰 꺼내는 스크립트와 init 스크립트, 사용법이 들어 있다. 출력은 저장소 밖에만 둔다.
- **N7:** 빌드 저장소를 `exclusiveContent` 로 묶었다. 이제 `com.mojang` 은 Mojang 저장소에서만 찾는다.
