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
