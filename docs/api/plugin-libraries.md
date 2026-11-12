# 플러그인 라이브러리 자동 로딩 (B5)

플러그인이 설명 파일(`furnace-plugin.yml/.toml/.json`)에 Maven 좌표를 적으면, 서버가 Maven Central 에서 내려받아 그 플러그인의 클래스로더에 붙인다. jar 에 라이브러리를 통째로 넣지 않아도 된다. **서버 설정으로 켜야만 동작하고 기본은 꺼짐이다**(결정 R1).

## 설명 파일

```yaml
libraries:
  - com.google.code.gson:gson:2.11.0                 # 그룹:아티팩트:버전
  - coordinate: org.xerial:sqlite-jdbc:3.46.0.0       # 체크섬을 고정하려면 이 모양
    sha256: 9f0c...64자리 소문자 16진수
  - io.netty:netty-transport-native-epoll:4.1.100.Final:linux-x86_64   # 끝에 :분류자
```

- YAML 에서는 `sha256` 값을 따옴표로 감싸라. 숫자만(또는 `1e5` 꼴)으로 된 해시를 YAML 이 숫자로 읽어 "16진수 64자리여야 한다" 오류가 난다.
- 좌표 글자는 `A-Z a-z 0-9 _ . + -` 만, `..` 는 안 된다. 틀리면 그 플러그인이 설명 파일 오류로 빠진다.
- API: `PluginDescription.libraries()` → `List<PluginDescription.Library>`(`coordinate()`, `group()`, `artifact()`, `version()`, `classifier()`, `sha256()`).
- 클래스는 플러그인 jar → 라이브러리 순으로 찾는다(플러그인 자기 클래스가 먼저). 서버가 이미 가진 클래스는 부모 로더가 먼저 답한다.

## 서버 설정 (furnace.properties)

| 키 | 기본 | 뜻 |
|---|---|---|
| `plugins.libraries.enabled` | false | 켜야 `libraries` 를 받는다. 꺼져 있으면 플러그인은 그대로 뜨되 라이브러리는 안 붙고 경고 한 줄(좌표 목록 포함)이 남는다 |
| `plugins.libraries.offline` | false | 켜면 캐시에 있는 것만 쓰고 네트워크에 나가지 않는다(운영 서버에서 캐시를 미리 채워 둘 때) |
| `plugins.libraries.transitive` | true | 받은 라이브러리의 pom 이 선언한 compile/runtime 의존까지 함께 받는다 |
| `plugins.libraries.require-checksum` | false | 켜면 설명 파일 `sha256` 이나 잠금 파일 기록이 없는 jar 는(전이 의존 포함) 받지도 쓰지도 않는다 |
| `plugins.libraries.cache` | (빈 값 → `plugins/.libraries`) | 받은 파일과 `libraries.lock` 을 두는 폴더 |
| `plugins.libraries.timeout-seconds` | 30 | 요청 하나의 제한 시간(1~600) |
| `plugins.libraries.max-size-mb` | 64 | 파일 하나의 크기 상한(1~1024) |

잘못된 값은 경고 후 기본값을 쓴다. `enabled=false` 일 때 다른 키를 적어 두면 무시한다는 경고가 나온다. 켜면 시작 로그에 "Maven Central 에서 내려받는다" 경고가 한 번 나온다(`require-checksum` 이나 `offline` 이면 생략).

## 무엇을 믿는가 (체크섬)

받아 온 jar 는 아래 순서로 검증한다. 하나라도 다르면 **그 플러그인만** 불러오지 않는다(`plugins.fail-fast=true` 면 서버 시작 중단).

1. 설명 파일의 `sha256` — 있으면 이 값과 같아야 한다(Central 의 `.sha1` 은 건너뛴다).
2. 잠금 파일 `libraries.lock`(`좌표=sha256`, 캐시 폴더 안) — 처음 받을 때 서버가 기록하고, 이후엔 이 값과 같은지만 확인한다. 의도한 업데이트라면 해당 줄을 지운다. 설명 파일 `sha256` 과 잠금 기록이 서로 다르면 거부한다.
3. 둘 다 없는 처음 받는 jar — Central 이 주는 `<jar>.sha1` 과 맞는지 확인한 뒤 잠금 파일에 기록한다(`.sha1` 이 없으면 거부). 이 단계는 전송 오류 정도만 막는다. 공급망 공격을 막으려면 `sha256` 을 적거나 `require-checksum=true` 로 잠금 파일을 미리 채워 두는 쪽이 안전하다.

캐시에 있는 jar 도 매번 해시를 다시 확인한다. 기록과 다르면 지우고 다시 받아(offline 이면 거부) 기록과 맞는지 본다. 내려받기는 https 만, 응답이 429/5xx 면 몇 번 다시 시도하고, 크기 상한을 넘으면 중단한다. pom 의 외부 엔티티는 풀지 않는다.

## 전이 의존

- pom 의 `parent`·`properties`·`dependencyManagement`(BOM `import` 포함)·`dependencies` 를 읽는다. scope 가 compile/runtime(없으면 compile)이고 `optional` 이 아니며 type 이 jar 인 것만 따라간다. `exclusions` 를 지킨다.
- 같은 `그룹:아티팩트[:분류자]` 가 여럿이면 설명 파일에 직접 적은 것 → 가까운 쪽이 이긴다(Maven 과 같다). 버전을 알 수 없거나 `(,2.0]` 처럼 하한이 없는 범위는 경고 후 건너뛴다 — 필요하면 설명 파일 `libraries` 에 직접 적는다.
- 직접 적은 라이브러리의 jar 가 Central 에 없으면 그 플러그인이 빠진다. 전이 의존의 jar 만 없으면 경고 후 건너뛴다. `packaging=pom` 은 jar 없이 의존만 따라간다.
- 전이 의존은 설명 파일에 `sha256` 을 적을 자리가 없다. `require-checksum` 을 쓰려면 필요한 전이 의존도 `libraries` 에 직접 적거나 잠금 파일에 한 줄씩 넣는다. 끄려면 `plugins.libraries.transitive=false`.

## 핫 리로드·부트스트랩

해석은 플러그인 부트스트랩 단계(클래스로더를 만들기 직전, 부트스트래퍼가 불리기 전)에 한 번 한다. 그래서 `bootstrapper` 클래스도 라이브러리를 쓸 수 있다. 개발 모드 핫 리로드(`/plugins reload`)도 같은 길을 타서 캐시에서 다시 붙인다. 내려받기는 서버 시작을 막는 동기 작업이다(플러그인 하나당 jar 수 × 네트워크 시간).

## Paper/Bukkit 플러그인용 서버 제공 라이브러리 (H4 묶음 B)

Paper 는 Guava·Adventure·SnakeYAML·Brigadier 같은 라이브러리를 서버 jar 에 싣고 있어서, Paper/Bukkit 플러그인은 `libraries:` 선언 없이 그것들을 그냥 쓴다. Furnace 는 **`plugins/bukkit-plugins/` 의 플러그인이 쓸 때만** 이 라이브러리를 준비해 붙인다(폴더가 비면 아무것도 올리지 않는다). 그중 **Guava(+failureaccess)·SnakeYAML 은 서버 jar 에 jar 그대로 실려 있어**(약 3.4 MB, 사용자 결정 5 정정 2026-11-12) 내려받지 않고 꺼내 쓴다. 나머지(Adventure 포함)는 쓸 때 받는다(사용자 결정 4·5). Furnace 플러그인(`furnace-plugin.yml`)과 위의 `plugins.libraries.*` 기능에는 영향이 없다.

| 키 | 기본 | 뜻 |
|---|---|---|
| `plugins.compat.libraries` | needed | `needed`: 호환 플러그인 jar 의 클래스 파일에 그 라이브러리의 패키지 이름(슬래시·점 표기)이 보이는 묶음만 받는다. `all`: 호환 플러그인이 하나라도 있으면 목록 전부(jar-in-jar 처럼 훑기로 못 보는 경우). `off`: 받지 않는다(그것을 쓰는 플러그인은 `NoClassDefFoundError`) |
| `plugins.compat.libraries.mojang` | true | Brigadier·authlib·DataFixerUpper 는 Maven Central 에 없어 Mojang 저장소(`libraries.minecraft.net`, 바닐라 런처·Paper 가 쓰는 곳)에서 받는다. false 면 그 셋은 빼고 경고 |
| `plugins.compat.libraries.declared` | true | 호환 플러그인 `plugin.yml` 의 `libraries:` 를 Paper·Spigot 처럼 받는다(`plugins.libraries.enabled` 가 꺼져 있어도). 체크섬·잠금 파일·전이 의존 규칙은 위와 같다 |

`plugins.libraries.offline`·`cache`·`timeout-seconds`·`max-size-mb` 는 이 기능에도 그대로 쓰인다.

### 무엇을 받나 (버전·sha256 은 코드 `ServerLibraries` 에 고정)

"서버 jar" 는 fat jar(`furnace-server.jar`) 의 `META-INF/furnace/libraries/` 에 jar 그대로 실려 있다는 뜻이다. 클래스를 풀어 넣지 않았고 패키지 이름도 바꾸지 않았다(재배치 없음 — 플러그인이 `com.google.common`·`org.yaml.snakeyaml` 을 그대로 쓴다). 필요할 때 캐시(아래 경로와 같은 자리)로 꺼내 공유 로더에 붙이므로 서버 로더·Furnace 플러그인은 이 클래스를 보지 않는다. `fatJar` 는 실은 jar 의 sha256 이 `ServerLibraries` 고정값과 같은지, bundled 로 적힌 것이 모두 실렸는지 확인한다. `start.sh`/`start.bat`(installDist 의 `lib/*` 실행)에도 같은 자원을 담은 `lib/furnace-bundled-libraries.jar` 를 넣었다(2026-11-12 기준 그 실행은 호환 어댑터 자체가 클래스패스에 없어 Bukkit 플러그인을 안 올린다는 경고만 낸다 — 어댑터가 실리면 같은 자원으로 꺼낸다). gradle 개발 실행(`run`)·시험처럼 그 jar 가 없을 때만 Central 에서 받는다. 라이선스 고지는 저장소 `NOTICE`·`licenses/` 와 서버 jar 의 `META-INF/furnace/libraries/NOTICE`.

| 묶음 | jar | 저장소 | 표지(이 패키지가 보이면 필요) |
|---|---|---|---|
| guava | guava 32.1.2-jre, failureaccess 1.0.1 | **서버 jar** (없으면 Central) | `com/google/common/`, `com/google/thirdparty/` |
| adventure | adventure-api·key·text-minimessage·text-serializer-json/gson/legacy/plain/ansi·text-logger-slf4j 4.17.0, examination-api/string 1.3.0, option 1.0.0, ansi 1.0.3 | Central | `net/kyori/adventure/`, `net/kyori/examination/` |
| snakeyaml | snakeyaml 2.2 | **서버 jar** (없으면 Central) | `org/yaml/snakeyaml/` |
| bungeecord-chat | bungeecord-chat 1.20-R0.2 (+ guava) | Central | `net/md_5/bungee/api/`(ChatColor 포함), `net/md_5/bungee/chat/` |
| json-simple | json-simple 1.1.1 | Central | `org/json/simple/` |
| joml | joml 1.10.5 | Central | `org/joml/` |
| fastutil | fastutil 8.5.12 (23 MB) | Central | `it/unimi/dsi/fastutil/` |
| commons-lang3 | commons-lang3 3.14.0 | Central | `org/apache/commons/lang3/` |
| commons-io | commons-io 2.15.1 | Central | `org/apache/commons/io/` |
| log4j | log4j-api 2.22.1 + log4j-to-slf4j 2.22.1(플러그인의 log4j 로그를 서버 로그로) | Central | `org/apache/logging/log4j/` |
| brigadier | brigadier 1.3.10 | Mojang | `com/mojang/brigadier/` |
| authlib | authlib 6.0.54 (+ guava·commons-io·commons-lang3) | Mojang | `com/mojang/authlib/` |
| datafixerupper | datafixerupper 8.0.16 (+ guava·fastutil) | Mojang | `com/mojang/serialization/`, `com/mojang/datafixers/` |

- 바닐라 1.21.1 서버 jar 가 싣는 것(Guava·failureaccess·JOML·fastutil·commons-lang3·commons-io·log4j-api·Brigadier·authlib·DataFixerUpper)은 바닐라 jar 의 `META-INF/libraries.list` 에 Mojang 이 적은 버전·sha256 을 그대로 썼다. 나머지(Adventure·SnakeYAML·BungeeChat·json-simple·log4j-to-slf4j)는 Maven Central 의 jar 를 받아 잰 값이다. Paper 1.21.1 이 정확히 어느 버전을 싣는지는 Paper 저장소에 못 닿아 대조하지 못했다(기억 기준: Adventure 4.17.0, SnakeYAML 2.2).
- 서버 클래스패스에 이미 있는 Gson·slf4j·netty 는 받지 않는다(두 벌이면 플러그인과 서버가 다른 Class 를 본다).
- 받은(꺼낸) jar 는 고정 sha256 과 같은 바이트만 쓴다. 서버 jar 에 실린 바이트가 다르면 쓰지 않고 Central 에서 받는다. 캐시 파일이 다르면 지우고 다시 받는다. 잠금 파일(`libraries.lock`)은 보지도 쓰지도 않는다 — 코드의 고정값이 기준이다.

### 어떻게 붙나

- 모든 호환 플러그인은 서버 제공 라이브러리 **공유 로더 하나**를 부모로 둔다(Paper 처럼 서버 쪽 한 벌). 그래서 플러그인끼리 Adventure `Component`·Guava `ImmutableList` 를 주고받아도 같은 Class 다. 부모 우선이라 플러그인이 같은 이름 클래스를 리로케이션 없이 품고 와도 서버 쪽이 이긴다(Paper 와 같다).
- 묶음은 처음 필요한 플러그인을 준비할 때 받는다(서버 시작 중, 동기). 개발 모드 핫 리로드로 새 묶음이 필요해지면 그때 더 붙인다.

### 끄거나 오프라인일 때

- Guava·SnakeYAML 은 서버 jar 에서 꺼내므로 `offline=true` 이거나 인터넷이 없어도 준비된다. `plugins.compat.libraries=off` 면 이 둘도 주지 않는다.
- 받지 못한 묶음(오프라인·캐시 없음·404·sha256 불일치·Mojang 금지)은 **경고 한 줄만 남기고 플러그인은 그대로 올린다.** 그 클래스를 쓰는 순간 `NoClassDefFoundError` 가 나고 서버는 평소처럼 그 플러그인의 오류를 남긴다(옆 플러그인은 영향 없음). 경고에는 받을 주소, 캐시 경로, sha256 이 들어 있다.
- 인터넷이 없는 서버: 다른 곳에서 받은 jar 를 경고에 나온 경로(`plugins/.libraries/<그룹 경로>/<아티팩트>/<버전>/<아티팩트>-<버전>.jar`)에 두고 `plugins.libraries.offline=true` 로 켠다. 고정 sha256 과 같으면 그대로 쓴다. Mojang 라이브러리 셋은 바닐라 1.21.1 서버 jar 안 `META-INF/libraries/com/mojang/...` 에 있는 jar 와 바이트가 같다.
- 응답 없는 네트워크: 한 저장소 호스트에 연결(전송)이 한 번 실패하면 그 시작 동안 같은 호스트의 남은 받기는 시도하지 않고 묶음 목록을 한 번에 경고한다(묶음마다 `timeout-seconds` 를 기다리지 않게). HTTP 오류·404·sha256 불일치는 호스트를 막지 않는다. Furnace 플러그인 `libraries` 받기도 같은 규칙이다.
- `off` 면 시작할 때 한 번 경고한다.
- 개발 모드 핫 리로드에서 새 묶음이 필요해지면 받는 동안 전역 스레드가 기다린다(개발 모드에서만).
- 한계: log4j 묶음은 `log4j-api` 와 slf4j 다리만 준다. `org.apache.logging.log4j.core.*`(예: 콘솔 비밀번호 필터를 다는 플러그인)는 없어서 그 경로는 `NoClassDefFoundError` 다 — Furnace 로그는 logback 이라 core 를 넣어도 그런 필터는 동작하지 않는다.

### 시험 서버

`FurnaceTestServer` 는 기본으로 이 기능과 `declared` 를 끈다(시험이 네트워크에 기대지 않게 — 실제 서버 기본과 다르다). Paper 플러그인을 Guava 등과 함께 시험하려면 캐시를 채워 두고 `builder.libraries(LibrarySettings.DEFAULT.withOffline(true).withCache(폴더))` 로 켠다.
