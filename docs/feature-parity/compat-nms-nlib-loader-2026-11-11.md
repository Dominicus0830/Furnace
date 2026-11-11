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
