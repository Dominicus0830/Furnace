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

도구 목록(28개, `--list`): Java 22개 — 블록 10(`block-attrs`·`block-hardness`·`block-light`·`block-props`·`block-shapes`·`block-transforms`·`block-sound-types`·`map-colors`·`note-instruments`·`pathfindable`), 아이템 5(`item-props`·`item-blocks`·`item-default-attributes`·`craft-remainders`·`stew-effects`), 프로토콜 1(`protocol-ids`), 호환 1(`kind-classes` — 블록·아이템 종류 → 바닐라 클래스, shim 생성기 T-231 이 읽는다), 시험 정답표 4(`context-collision-shapes`·`section-oracle`·`raw-write-oracle`·`palette-oracle`), 시험 벡터 1(`shape-vectors`, 재료를 따로 받아 전체 실행에 안 들어감) / 파이썬 6개 — `tags`·`builtin-registries`·`block-tags`·`item-components`·`block-definitions`·`damage-types`.

## 서명표 (T-227)

`-Pmode=sig-table` 은 jar 안 클래스·멤버의 **선언만** 모은 표를 캐시(`<캐시>/<판>/`)에 만든다. 서명 관문(T-228)·차분 시험(T-235)·표류 보고(T-236)가 읽는 공통 입력이다(설계 `docs/roadmap/broad-support-version-scaling-2026-11-20.md` 4.2).

```
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=sig-table                          바닐라(Mojang 이름) → sig-table.tsv.gz
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=sig-table -PsigPaperApi=true       paper-api(repo.papermc.io, 스냅샷 jar 의 sha1 확인) → sig-table-paper-api.tsv.gz
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=sig-table -PsigJar=<jar> -PsigName=<이름> [-PsigPrefix=a/,b/]   임의 jar → sig-table-<이름>.tsv.gz
tools/compat/sig-tables.sh 1.21.1                                                                      위 셋 + Mojang 이름 Paper 서버 jar(paperweight 가 닿을 때만) 한 번에
```

- 형식: 첫 줄 `#furnace-sig-table<TAB>1<TAB><이름표>`, 이어서 `C 접근 클래스 상위 인터페이스(쉼표) 제네릭서명 안쪽접근`, `F|M 접근 소유자 이름 서술자 제네릭서명`(없는 칸 `-`, 접근은 10진 ACC_*). 합성 멤버·`<clinit>` 는 뺀다. 같은 입력은 같은 바이트(gzip 시각 0, 정렬 고정). 읽기는 `SigTable.read`.
- **법적 경계:** 클래스 파일을 `SKIP_CODE|SKIP_DEBUG|SKIP_FRAMES` 로만 읽고 방문자가 몸통·애노테이션을 받지 않는다(시험 `methodBodiesAreNeverRead` — 몸통이 깨진 클래스도 통과해야 한다). 표는 Mojang 대응표에서 나온 것이므로 **저장소·서버 jar·공유 폴더 어디에도 두지 않는다**(대응표 머리말: 완전·미수정 재배포 금지).
- Mojang 이름 Paper 서버 jar(`ServerLevel#captureBlockStates`·`CraftPlayer` 등 Paper·CraftBukkit 이 더한 멤버)는 `:worldgen-oracle:printPaperServerJar` 가 경로를 낸다. 이 클라우드 환경에서도 paperweight 가 dev-bundle 을 받아 동작했다(2026-11-20). 단 CI 는 모든 gradle 에 `-PskipOracle=true` 라서 CI 에서 이 칸을 만들려면 그 단계만 오라클을 켜야 한다(T-228 이 정한다).

## 본문 지문표와 판 사이 차이 (2026-11-23)

`-Pmode=body-table` 은 메서드마다 **명령 흐름의 해시 두 개(모양·참조, SHA-256 앞 12자리)** 와 `static final` 상수 값만 모은 표를 캐시(`<캐시>/<판>/body-table.tsv.gz`)에 만든다. 서명표(T-227)는 몸통을 읽지 않는다는 약속을 그대로 두고, 이 표는 `BodyTable` 이 따로 만든다. 몸통의 명령·상수 글자는 어디에도 저장하지 않고 해시만 남긴다. 서명표처럼 커밋·공유하지 않는다.

- 모양 해시: 명령 코드·정수·문자열 상수·분기 구조·지역 변수 번호(호출·필드·형식이 가리키는 이름은 뺌). 참조 해시: 가리키는 (소유자, 이름, 서술자).
- 두 판에서 모양이 다르면 "흐름이 바뀐" 동작 변경 후보, 모양은 같고 참조만 다르면 "다른 이름을 부르게 된"(이름 바뀜·옮김) 약한 후보다. 람다 몸통은 번호가 판마다 흔들리므로 클래스마다 정렬해 묶은 한 줄로 견준다.

```
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=<판> -Pmode=body-table      본문 지문표
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=<판> -Pmode=reports         데이터 생성기 보고서(패킷·레지스트리·블록·아이템·명령)
python3 tools/compat/version-diff.py <옛 판> <새 판> [--shim-table <shim 서명표>] [--port-map build/vanilla-port-map.tsv] [--safe <개수 요약.md>]
python3 tools/compat/version-diff.py <옛 판> <새 판> --table paper-api            paper-api 서명표끼리(선언만)
python3 tools/compat/paper-patch-diff.py <Paper 옛 체크아웃> <Paper 새 체크아웃>   Paper 패치 묶음 차이
```

`version-diff.py` 는 서명표·본문표·생성기 보고서·jar 자원(구조물 NBT 는 DataVersion 을 가리고 견줌)을 읽어 클래스·멤버·본문·상수·패킷·레지스트리·블록·아이템·명령·데이터 파일 차이를 영역별로 센다. `--port-map` 을 주면 `@VanillaPort` 대응표(V-06)로 "고칠 Furnace 파일" 후보를 등급(A 선언 삭제·서술자 / C 멤버 추가만 / B 흐름만 / D 참조만)으로 낸다. 전체 보고서·목록(`<캐시>/diffs/`)에는 Mojang 이름이 들어 있으므로 커밋하지 않고, `--safe` 요약(개수만)만 문서에 옮긴다. 1.21.1→1.21.2 결과와 버전업 시간 추정: `docs/roadmap/version-diff-1.21.2-2026-11-23.md`.

## 서명 관문 (T-228)

컴파일한 `furnace-compat-paper` 의 shim 클래스 선언(`net/minecraft/`·`com/mojang/`·`org/bukkit/craftbukkit/`)을 기준 서명표(Paper 서버 → 바닐라 → 라이브러리 순)와 맞댄다. 플러그인이 Paper 에 대고 컴파일한 호출이 **링크되는 모양**인지 본다.

```
tools/compat/sig-tables.sh 1.21.1                              기준 표를 캐시에 만든다(Paper 서버 칸은 paperweight 가 닿을 때)
./gradlew -PskipOracle=true :vanilla-extract:signatureGate     기준선과 견준다(없는 기준 표는 건너뛰지 않고 실패)
./gradlew -PskipOracle=true :vanilla-extract:signatureGate -PsigBaselineWrite=true   기준선 다시 쓰기(이미 적은 이유는 그대로, 새 줄은 코드별 기본 이유)
```

- **실패 대상(링크를 깨는 차이):** `class-kind`·`class-access`(덜 열림·shim 만 final/abstract)·`super`(상위 클래스 다름)·`type-missing`(바닐라·Paper 가 구현하는 인터페이스·상위가 shim 에 없음, 상위가 같을 때만·JDK 타입과 Paper 내부 패치 인터페이스 `ca/spottedleaf/`(moonrise — 잠금 파일도 뺀다, T-232) 제외)·`descriptor`(같은 이름 멤버인데 서술자가 다르고 기준의 같은 이름 서술자가 shim 에 없음)·`static`·`member-access`. 멤버는 JVM 처럼 상위 사슬을 따라 풀어 상위 멤버를 하위에 다시 선언한 것은 세지 않는다.
- **참고만(실패 아님):** shim 이 더 가진 `extra-class`·`extra-member`·`extra-overload`(Furnace 내부 도우미) — 플러그인이 컴파일한 호출을 깨지 않는다. 보고서에 건수만 나온다.
- **기준선** `furnace-compat-paper/src/test/resources/shim-signature-baseline.txt`: `코드<TAB>소유<TAB>멤버<TAB>자세히<TAB>이유`. **새 차이**(기준선에 없음)·**낡은 줄**(이제 같아졌는데 남음)·**이유 없는 줄** 은 실패다. 지금 값은 손 shim 의 알려진 구멍을 코드별 "미분류" 이유로 채운 것이고, 생성기(T-229~)가 바로잡을 때마다 줄을 지운다. 의도한 차이는 이유에 Furnace 가 그렇게 한 까닭을 적는다. 이 파일은 커밋되므로 shim 에 이미 있는 이름과 이유만 담는다 — **기준(바닐라·Paper) 쪽 이름·서술자는 `#<12자리 지문>`(SHA-256 앞부분)으로만** 적는다(Mojang 이름·바닐라 메서드 모양은 커밋하지 않는다). 지문의 원문은 보고서에만 나오고, 새 차이가 생기면 콘솔·보고서에 원문이 함께 찍힌다.
- 보고서(커밋 안 함): `tools/vanilla-extract/build/reports/signature-gate.md` — 차이 전체의 원문(기준 쪽 이름·서술자 포함). CI 는 실패할 때 올린다.

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
