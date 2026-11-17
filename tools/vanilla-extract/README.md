# vanilla-extract — 바닐라 데이터 추출 도구 (V-05)

바닐라 서버 jar 에서 Furnace 가 쓰는 표(블록·아이템·태그·프로토콜 번호)와 시험 정답표를 뽑는 도구 모듈이다.
**판 이름을 인자로 받는다.** 난독화 이름(`akt`, `dfy`…)을 쓰지 않고 Mojang 이름으로 쓴 도구를, 실행기가 판마다 공개되는
공식 대응표(`server_mappings`)로 이름을 풀어 둔 jar 위에서 돌린다. Paper dev bundle 이 없는 판(1.21.2 등)에서도 돈다.
26.x 처럼 난독화가 없는 판은 version.json 에 대응표가 없으므로 이름 풀기를 건너뛰고 jar 를 그대로 쓴다.

서버 클래스·플러그인 API 와 이어진 곳이 없다(서버 실행 경로에 영향 0). 바닐라 jar·대응표·내려받은 결과는 캐시 폴더에만 두며 커밋하지 않는다(EULA, 저장소 규칙 7).

## 쓰는 법

```
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1                      전 도구 실행 → tools/vanilla-extract/build/out/<판>/ 에 저장소 경로 그대로 쓴다
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=verify        같은 결과를 저장소에 커밋된 파일과 견준다(.gz 는 푼 내용 비교)
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.2 -Pmode=install       저장소 resources 에 바로 덮어쓴다(새 판으로 올릴 때, 파일 이름의 판 번호가 따라 바뀐다)
./gradlew -PskipOracle=true :vanilla-extract:run -Plist=true                       도구 목록
옵션: -Ptools=block-attrs,block-light  -Pcache=<폴더>  -PjavaHome=<JAVA_HOME>  -PserverJar=<번들러 jar> -Pmappings=<대응표>  -Pprotocol=<번호>
```

- 캐시 기본 위치 `~/.cache/furnace-vanilla-extract`(환경변수 `FURNACE_VANILLA_CACHE` 로 바꿈). 판마다 `server.jar`(sha1 확인), `server_mappings.txt`, 이름 푼 jar, 바닐라 데이터 생성기 출력(`generated/`)이 쌓이고, 입력이 같으면 다시 만들지 않는다.
- 내려받기는 `piston-meta.mojang.com` 에서 한다(version_manifest_v2 → 판 version.json → 서버 jar·대응표). 오프라인이면 `-PserverJar` 와 `-Pmappings` 로 파일을 직접 준다.
- 26.x 는 Java 25 가 필요하다고 알려져 있으므로(가정) `-PjavaHome=<JDK 25 위치>` 를 준다. 도구마다 새 JVM 으로 돈다.
- 파이썬 도구는 `python3`(또는 `python`, `py`)가 PATH 에 있어야 한다(`--python` 으로 지정).

## 구성

| 부분 | 하는 일 |
|---|---|
| `Preparer` | version.json → jar·대응표 내려받기 → `ServerBundle`(번들러 풀기) → `JarRemapper`(이름 풀기) → 바닐라 데이터 생성기(`net.minecraft.data.Main --all`) |
| `MojangMappings`·`ClassHierarchy`·`JarRemapper` | ProGuard 형식 대응표 읽기, 하위 클래스 이름으로 부른 멤버를 부모에서 찾기, ASM 으로 클래스·필드·메서드(람다 invokedynamic 포함) 이름 풀기, 서명 파일 제거 |
| `Tools` | 도구 목록: 소스, 결과 저장소 경로(`{mc}`·`{protocol}` 자리), 인자 틀 |
| `Runner` | Java 도구 소스를 도구마다 컴파일(`javac`)하고 도구마다 새 JVM 으로 실행(실패는 도구별로 기록), 파이썬 도구 실행, `.gz` 묶기 |
| `Verifier` | 새 결과와 커밋된 파일을 견줌(`.gz` 는 푼 내용, `soundtypes` 는 소리 내용) |
| `Legacy`·`PortCheck` | 옛(난독화 이름) 도구를 git 기록에서 꺼내 같은 jar 에 돌려 전후 비교, 옛 바이트코드를 이름 풀어 새 도구와 호출 단위로 견줌 |
| `src/main/resources/exporters/furnacetools/` | **Mojang 이름으로 쓴 도구 소스**(`Vanilla.java` 가 부트스트랩·판 이름·저장소 위치를 한곳에서 줌) |
| `scripts/compare-without-column.py` | 열 하나만 빼고 두 결과를 견준다(`block-attrs` destroy 열 차이 확인용, user-todo 참고) |
| `src/main/resources/python/` | 데이터 생성기 JSON 을 읽는 파이썬 도구(`<판>` 을 마지막 인자로 받음) |

도구 목록(27개, `--list`): Java 21개 — 블록 10(`block-attrs`·`block-hardness`·`block-light`·`block-props`·`block-shapes`·`block-transforms`·`block-sound-types`·`map-colors`·`note-instruments`·`pathfindable`), 아이템 5(`item-props`·`item-blocks`·`item-default-attributes`·`craft-remainders`·`stew-effects`), 프로토콜 1(`protocol-ids`), 시험 정답표 4(`context-collision-shapes`·`section-oracle`·`raw-write-oracle`·`palette-oracle`), 시험 벡터 1(`shape-vectors`, 재료를 따로 받아 전체 실행에 안 들어감) / 파이썬 6개 — `tags`·`builtin-registries`·`block-tags`·`item-components`·`block-definitions`·`damage-types`.

## 바이트 동일 증명 (V-05 수용 조건)

수용 조건은 "1.21.1 결과가 이전 도구 결과와 같다"이다. 옛 도구는 `tools/vanilla-data/Export*.java` 와 파이썬 둘이었고 git 기록에 남아 있다.

```
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=proof -PlegacyRef=<옛 도구가 있던 커밋>
```

1. 옛 도구를 `legacyRef` 에서 꺼내 컴파일하고 난독화 원본 jar 에 돌려 `build/out/1.21.1-legacy/` 에 쓴다(전).
2. 새 도구를 **도구마다 따로** 컴파일한다(기본 선택 밖인 `shape-vectors` 포함 — 증명·포팅 점검에서는 모든 Java 도구를 컴파일). 깨진 도구가 있어도 나머지는 돌고, 끝에 도구별 `성공`/`컴파일 실패`/`실행 실패` 표가 나온다(`build/out/1.21.1/` 에 후 결과).
3. 도구·파일마다 비교한다. 옛 Java 도구가 있던 것은 전↔후가 합격 판정, 옛 Java 도구가 없던 파이썬 6개는 커밋된 파일이 정답이라 `커밋(합격)` 으로 판정한다. 옛 도구 결과가 있어야 하는데 없으면 불합격이다. 커밋 파일과의 차이는 `커밋(참고)` 로만 알린다(커밋된 파일이 옛 도구의 옛 버전 결과일 수 있다).
4. `shape-vectors` 는 `-Ptools=shape-vectors -PtoolArgs="shape-vectors=<블록들>;shape-vectors=<이웃 블록들>;shape-vectors=<시도 수>;shape-vectors=<시드>"` 로 고정 시드 인자를 주면 옛 도구도 같은 인자로 돌려 출력을 견준다.
5. 포팅 점검: 옛 클래스 파일을 대응표로 이름만 풀고, 새 도구가 메서드마다 같은 호출(소유 클래스·이름·서술자, 필드, 상수)을 하는지 목록으로 보인다. 다르면 이름을 잘못 짚었거나 일부러 바꾼 곳이다. 준비 단계와 옛 도구 컴파일을 매번 하므로 `-Pmode=port-check` 의 "빠름"은 첫 캐시 이후에만 맞다.

한 도구가 깨졌을 때의 오류는 화면에는 앞 40줄만, 전체는 `<캐시>/<판>/exporter-compile-<도구>.log` 에 쓴다(`javac -Xmaxerrs 1000`). 새 판 시험(U-02)은 이 표를 그대로 재료로 쓴다.

비교 규칙: `.gz` 는 gzip 헤더에 시각이 들어가 압축 바이트가 매번 다르므로 푼 내용을 견준다. `block-sound-types` 는 이전에 서버 바이트코드를 읽는 파이썬이라 종류 번호가 난독화 필드 이름순이었는데(이름 풀기로 되살릴 수 없는 우연), 지금은 SoundType 선언 순서이므로 번호를 소리 내용으로 풀어 블록마다 견준다.
`.gz` 를 만드는 도구는 파이썬 `item-components` 외에는 실행기가 헤더 시각 없이 같은 내용 → 같은 파일이 되게 묶는다.

## 결과 머리말의 도구 경로

결과 파일 머리말에는 `tools/vanilla-data/ExportXxx.java` 가 적혀 있다. 이미 커밋된 리소스와 글자까지 같게 하려고 이전 위치를 그대로 적는 것이며(`Vanilla.generator`, 파이썬은 각 스크립트), 다음 판으로 올리면서 결과를 새로 쓸 때(U-02) 한 곳에서 바꾼다.

## 새 판을 도구에 맞추기

1. `-Pmc=<판> -Pmode=extract` 로 돌린다. 컴파일 오류가 나면 그 도구만 바닐라 쪽 이름이 바뀐 것이다(Mojang 이름으로 읽어 고친다 — 난독화 이름을 다시 찾을 필요가 없다).
2. 도구 소스를 고치고, 같은 판 번호가 쓰이는 도구는 `Vanilla.java` 한 곳만 바꾸면 된다.
3. `-Pmode=install` 로 저장소 리소스에 쓴다.
