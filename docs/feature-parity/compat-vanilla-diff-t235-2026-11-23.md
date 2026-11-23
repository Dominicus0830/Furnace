# 바닐라 차분 시험 틀 (T-235, 2026-11-23)

shim 연결마다 손으로 시험을 쓰지 않고, **이름 푼 바닐라 서버 jar 와 shim 에 같은 입력을 넣어 결과를 프로그램이 견준다.**
설계 근거: `docs/roadmap/faster-shim-and-version-up-research-2026-11-23.md` 5.1(1a·1b·1c), `docs/roadmap/broad-support-version-scaling-2026-11-20.md` 5.2·5.5.
결정 F1(11-23, 도미니코): 이 시험을 통과한 **읽기** 연결은 검토 없이 병합할 수 있다(쓰기는 검토 유지).
**적용 범위**: "통과"는 ① 대상 파일의 관문 클래스 멤버, ② 연결 명세 `pure`·`read=any` 줄 가운데 보고서에서 **같음**으로 증명된 멤버만이다. 증명 없음으로 남은 명세 줄은 이 시험을 통과한 것이 아니므로 검토 생략 대상이 아니다.

## 쓰는 법

```
./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest                                 # 전부(처음엔 바닐라 jar 를 받아 이름을 푼다)
./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest -PvdiffClasses=net.minecraft.util.Mth,net.minecraft.nbt.NbtIo
./gradlew -PskipOracle=true :furnace-compat-paper:vanillaDiffTest -PvdiffIterations=200000         # 모든 클래스 입력 수를 덮어쓴다(깊게 돌릴 때)
```

- 보고서: `furnace-compat-paper/build/reports/vanilla-diff/shim-proof-report.md` — 묶음별 같음·다름·증명 없음·뺌·Paper 와 같게·shim 에 없음 개수와 증명 비율, 다름은 입력·양쪽 결과 앞 3개, 클래스별 입력 수와 걸린 시간.
- 대상: `furnace-compat-paper/src/vanillaDiff/resources/vanilla-diff-targets.txt`. 형식은 파일 머리 주석.
- 바닐라 jar 준비: `:vanilla-extract:prepareVanillaJars`(새 `--mode jars`) — `piston-meta.mojang.com` 에서 받아 캐시(`~/.cache/furnace-vanilla-extract/<판>/`, `-Pcache`·`FURNACE_VANILLA_CACHE`)에서 이름을 풀고, 서버 jar·라이브러리 경로를 `vanilla-classpath.txt` 에 적는다. 저장소·서버 jar 에는 아무것도 들어가지 않는다(EULA). 목록이 없으면 건너뛰지 않고 실패한다(규칙 5).
- CI: `vanilla-gates` 작업의 "바닐라 차분 시험 (T-235)" 단계. **바뀐 영역 선택**: `changes` 작업이 `vdiff` 를 낸다 — 호환 계층·API main·서버 main·`tools/vanilla-extract`·`gradle/`·빌드 스크립트·`ci.yml` 이 바뀐 PR 과 기준 브랜치 push 에서만 돈다(시험만·문서만·다른 도구만 바뀐 PR 은 안 돈다). 보고서 머리 표는 단계 요약에 붙는다.

## 어떻게 견주나

| 조각 | 파일 | 하는 일 |
|---|---|---|
| 격리 로더 | `VanillaRuntime` | 캐시의 Mojang 이름 서버 jar + 라이브러리를 부모가 플랫폼 로더인 별도 로더에 올리고 `SharedConstants.tryDetectVersion()`·`Bootstrap.bootStrap()`(vanilla-extract `Vanilla.boot` 와 같은 두 호출). shim 은 시험 클래스패스에서 — 두 쪽이 섞이지 않는다 |
| 형식 꾸러미 | `TypeKit`·`NbtKit` | 형식마다 한 번: **씨앗**(고정 시드 난수·경계값 → 중립 값), **올리기**(씨앗 → 한쪽 로더의 객체, 생성자·정적 공장·열거형 이름), **내리기**(객체 → 숫자·글·목록). 형식 하나를 더하면 그 형식을 쓰는 멤버가 모두 시험된다 |
| 실행기 | `DiffRunner` | 바닐라 클래스의 공개 정적 필드·공개 생성자·공개 메서드(물려받은 `net.minecraft` 멤버 포함)를 훑어 같은 이름·인자 형식의 shim 멤버를 찾고, 입력마다 [반환값 또는 던진 예외 종류, 부른 뒤 받는 쪽 상태, 부른 뒤 인자 상태]를 양쪽에서 내려 견준다. 처음 400개 입력은 경계값 위주(80%), 나머지는 난수 위주(경계값 10%) |
| 시험·보고 | `VanillaDiffTest` | 대상 파일의 클래스마다 동적 시험 하나, 보고서 쓰기. 연결 명세(`src/shimspec/*.spec`)의 `pure`·`read=any` 줄도 자동으로 시도한다(1c) — 다름·오류는 실패, 증명 없음만 보고. shim 클래스가 호환 계층 main 출력 폴더에서 왔는지 단언한다 |
| 틀 점검 | `HarnessCanaryTest` | 일부러 틀린 구현은 "다름", 같은 구현은 "같음" — 반환값(`Mth.floor`), 예외 종류(`ResourceLocation.parse`), 인자 상태(`NbtIo.write` 바이트), NBT 내리기(`NbtIo.read`), paper 허용 범위(허용 쌍이 있어도 다른 차이는 다름) 경로를 하나씩. 틀이 늘 "같음"을 내는 고장을 막는다 |

판정 규칙:
- 대상 파일의 클래스는 **관문**이다. 다름이 하나라도 있거나, 증명 없음(꾸러미에 없는 형식)이 `exclude <열쇠>  # 이유` 없이 남으면 실패.
- `paper <열쇠> vanilla=<예외> shim=<예외> [vanilla=… shim=…]…  # 근거` — 바닐라와 다르지만 Paper 와 같게 둔 차이(호환 원칙 #505·#449·#222). **적은 예외 쌍인 입력만 허용**하고, 같은 멤버여도 그 밖의 차이는 다름(실패). 허용한 차이가 한 번도 안 나오면 낡은 줄이라 실패. 근거 입력은 고정 훑기에 넣는다.
- 고정 훑기: 멤버마다 처음 16개 입력(`DiffRunner.SWEEP`)은 모든 자리가 경계값 0~15번이라 입력 수·씨앗과 무관하게 늘 같다. paper 줄의 근거 입력(길이 1<<24 배열 태그, 한도 무제한·1MiB)이 여기 있어 입력 16·400·3000개, 씨앗 0~3 에서 같은 판정을 확인했다.
- 리플렉션 실패(`harness …`)는 양쪽이 같아도 다름으로 센다.
- 바닐라에만 있는 멤버(shim 에 없음)는 서명 관문 몫이라 보고만.
- 메모리 모자람(OutOfMemoryError)은 입력의 관측값이 아니라 힙 크기라 그 입력은 건너뛴다(센다). 모든 입력에서 양쪽이 같은 예외를 던진 멤버는 같음이지만 "주의"로 따로 적는다(값을 본 적 없음).
- 멤버 하나가 120초를 넘으면 다름(시간 초과)으로 본다.

## 첫 실행 결과 (이 PR, 입력 값 클래스 20,000·NBT 3,000)

- 대상 30클래스 820멤버: **같음 814, 다름 0, 증명 없음 0, Paper 와 같게 6**(증명 비율 100%). shim 에 없는 바닐라 멤버 202개는 보고만. 이 환경에서 약 93초(NbtIo 가 절반 넘게 — 깨진 길이로 큰 배열을 잡는 입력).
- 연결 명세 pure·read=any 9개는 받는 쪽(엔티티·블록·아이템·레벨)이 꾸러미에 없어 전부 증명 없음 — 이 형식은 값 차분이 아니라 장면 차분(T-253 후보)의 몫이다.

### 이 틀이 찾아 고친 차이

| 클래스 | 멤버 | 차이 | 고침 |
|---|---|---|---|
| `Direction` | `fromYRot(double)` | 아주 큰 음수에서 `(int) Math.floor` 와 `Mth.floor` 가 다르다(넘침) | `Mth.floor` |
| `Direction` | `getNearest(double,double,double)` | 바닐라는 인자를 float 로 바꾼 뒤 float 셈 — 큰 값에서 방향이 달랐다 | float 판 `getNearest(float,float,float)`(바닐라 공개 멤버)을 더하고 double 판은 그것을 부름 |
| `FloatTag`·`DoubleTag` | `getAsInt`·`getAsShort`·`getAsByte` | 무한대·아주 큰 값에서 `Mth.floor` 와 다름(`CompoundTag.getByte`·`getBoolean`, 배열 태그 `setTag` 도 같은 뿌리) | `Mth.floor` |
| `ByteArrayTag`·`IntArrayTag`·`LongArrayTag` | `add(int, …)`·`addTag` | 범위 밖에서 바닐라는 `IndexOutOfBoundsException`(ArrayUtils.add), shim 은 `ArrayIndexOutOfBoundsException` | 범위를 먼저 보고 바닐라 예외 |
| `Tag` | `TAG_ANY_NUMERIC` | 바닐라는 `byte`, shim 은 `int`(서명 관문 기준선에 "알려진 차이"로 있던 줄) | `byte`, 기준선 줄 지움 |

### Paper 와 같게 둔 차이 (바닐라와 다름)

`NbtIo` 의 읽기 6개(`read(DataInput)`·`read(DataInput, NbtAccounter)`·`read(Path)`·`readAnyTag`·`readCompressed(InputStream, …)`·`readCompressed(Path, …)`): 허용 쌍은 바닐라 `ReportedNbtException`·`NbtAccounterException` ↔ shim `IllegalArgumentException` 뿐. Paper 는 바이트·정수 배열 태그의 길이를 할당량 계산 **전에** `Preconditions.checkArgument(n < 1<<24)` 로 막는다(바닐라는 할당량 예외 또는 끝까지 읽다 `ReportedNbtException`). shim 은 Paper 와 같다. Mojang 이름 Paper 서버 jar(paperweight 개발 묶음)의 `ByteArrayTag$1`·`IntArrayTag$1` 바이트코드로 확인. 기록: `docs/user-todo/entries/2026-11-23-t235-vanilla-diff.md`.

## 형식 꾸러미에 있는 것

원시값·글(자원 위치 꼴·깨진 꼴·열거형 이름 섞음)·`UUID`, `Vec3i`·`BlockPos`·`MutableBlockPos`·`SectionPos`·`ChunkPos`·`Vec3`·`Vec2`·`AABB`·`ResourceLocation`·`RandomSource`(시드)·`StringReader`·`NbtAccounter`, 모든 열거형(바닐라 상수 이름), `Position`→`Vec3`, `Object` 자리→받는 쪽 형식(가끔 null), `List<원소>`(원소가 꾸러미에 있으면), NBT 태그 전 종류(깊이 3 나무), `DataOutput`·`OutputStream`(바이트 비교), `DataInput`·`InputStream`·`Path`(바닐라가 쓴 태그 바이트, 일부 gzip, 4분의 1은 한 바이트 깨기·자르기).
내리기: 위 값 형식, 태그(종류·내용, 이름표는 이름 순), `TagType`, 목록·흐름·반복자(앞 64개), 배열, `Optional`, 상태 없는 객체.

## 남은 것 (후속 — 범위를 나눈 이유: 형식마다 따로 큰 일)

- **`Component` 직렬화·레지스트리 키·블록 상태·아이템 스택**: 바닐라 쪽은 부트스트랩이 됐으므로 씨앗·올리기·내리기를 더하면 된다. 블록 상태는 26,684개 전부를 데이터에서, 아이템은 기본 구성 요소 + 무작위 조합(설계 5.1).
- **코덱 퍼징(1d)**: NBT·구성 요소·패킷 버퍼를 적용 범위 유도 입력으로(JQF 식).
- **Paper 를 정답기로 같이 돌리기**: paperweight 개발 묶음의 Mojang 이름 Paper 서버 jar 가 CI(`sig-tables.sh`)에 이미 생긴다. Paper 쪽도 격리 로더에 올려 "바닐라와 다르면 Paper 와 같은가"를 기계가 보면 `paper` 줄의 근거를 사람이 바이트코드로 찾지 않아도 된다(가정 — Paper 부트스트랩이 Bukkit 없이 되는지 확인 필요).
- 연결 명세의 받는 쪽이 엔티티·레벨인 줄(현재 9개)은 장면 차분기(T-253 후보)에서.
- 버전업: 새 판 jar 로 같은 시험을 돌리면 "이름은 같고 동작이 바뀐 shim 멤버"가 자동으로 나온다(설계 5.1 마지막 줄) — 판 이름은 `gradle/minecraft.properties` 의 `mc.version` 에서 읽으므로 판을 올리면 이 시험도 새 jar 로 돈다.

## 후속 1 (11-23): Paper 허용을 입력 조건으로 좁힘

재검토가 남긴 점: paper 줄이 예외 쌍(바닐라 `ReportedNbtException` ↔ shim `IllegalArgumentException`)만 보면, 깨진 입력에서 보고 예외를 전부 IAE 로 바꾸는 틀린 구현도 통과한다.

- paper 줄에 `when=<입력 조건>` 을 더했다. 조건 이름은 `DiffRunner.CONDITIONS` 에만 있고, 모르는 이름은 읽을 때 오류.
- `nbt-array-limit`(`NbtKit.tripsArrayLimit`): 멤버가 실제로 읽을 바이트(readCompressed 는 gzip 을 푼 것)를 NBT 바이너리 사양대로 구조만 훑어, 끝·형식 오류·깊이 초과보다 먼저 바이트·정수 배열 길이 1<<24 이상을 만나는 입력일 때만 참. 할당량은 보지 않는다(할당량이 먼저 걸리면 shim 도 할당량 예외라 쌍이 맞지 않는다). 판정 못 하면 거짓(허용을 넓히지 않는 쪽).
- NbtIo 의 paper 6줄 모두 `when=nbt-array-limit`. 카나리 `paperAllowanceIsLimitedToItsInputs`: 그 틀린 구현이 조건 없이는 "Paper 와 같게"로 통과하고(대조군), 조건을 걸면 다름(입력 500개 중 180개)으로 잡힌다.
- 전체 실행 결과는 그대로: 같음 814, 다름 0, 증명 없음 0, Paper 와 같게 6.

## 2단계 (11-23): 게임 값 — 채팅 글·레지스트리 키·블록 상태·아이템

- 새 형식 꾸러미 `GameKit`: 레지스트리 칸(블록·아이템·Holder), 레지스트리 키, 글 색·스타일·클릭·마우스 올림, 글(Component — 글자·번역·대체 글 번역·키 이름·빈 글, 스타일·자식·번역 인자 섞기, 깊이 2), 블록 상태(블록 + 속성 값 고르기), 아이템 더미(종류·개수), 블록 속성(BlockStateProperties 상수), 데이터 컴포넌트 종류, 블록 속성 값 자리(Comparable). 경계값은 고정 목록, 난수는 **바닐라 레지스트리 전체**에서 고른다 — shim 에 없는 블록·아이템이 나오면 "shim 이 입력을 못 만듦" 으로 다름(전에는 건너뜀)이다.
- 내리기: 레지스트리 값은 열쇠, 블록 상태는 속성 이름=값, 글은 getString + `Component.Serializer.toJson` 의 JSON 나무(열쇠 순서 무시). 맵은 열쇠 글 순, Holder 는 값, 배열 인자(번역 인자·서식 목록)도 씨앗을 만든다.
- shim 의 블록·아이템 레지스트리는 Furnace 서버 데이터를 읽으므로 시험이 Furnace 시험 서버를 하나 띄운다.
- 새 관문 8클래스: ResourceKey, TextColor, Style, Component, MutableComponent, BlockState, Item, ItemStack.
- 고친 shim 차이 11건: 키 이름 글의 평문(Furnace API 까지 — 바닐라는 키 글자 그대로), 빈 글 붙이기(append("") 는 자식 안 더함), applyFormat 의 이름 있는 색, applyFormats 의 RESET·새 Style, getString(n≤0), Style·글 toString 꼴(바닐라 꼴), 빈 아이템 더미의 컴포넌트 읽기(최대 개수 1·내구도 0), set·remove 의 이전 값, 공기의 기본 최대 개수 64.
- Paper 와 같게 1건: 부술 수 없는 블록 10개의 피스톤 반응(when=paper-indestructible-block, 값 쌍 `value:` 꼴 허용 — Paper Mojang 이름 jar 바이트코드로 확인).
- 뺀 것(이유는 대상 파일): 해시 값, 함수 인자, 코덱 상수, 태그 질문(바닐라 실행기에 태그 없음), 월드가 필요한 동작, 컴포넌트 값 넣기·마법 부여, 서식 없는 withStyle()(알려진 JSON 꼴 차이 — user-todo).
- 결과(이 환경): 38클래스 같음 952, 다름 0, 증명 없음 0, Paper 와 같게 7 — 증명 비율 100% 는 뺀 36개(태그·데이터 팩·월드 필요, 컴포넌트 값 입력 없음 등)를 빼고 센 값이다. 연결 명세 줄은 새 형식 덕에 6개가 증명됨(전엔 0).
- 내리기마다 Class.forName 을 부르면 값 클래스 시험이 3배 느려져 로더별로 기억한다.
