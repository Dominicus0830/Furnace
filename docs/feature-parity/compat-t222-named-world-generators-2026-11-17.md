# T-222 이름별 월드 생성기 설정 (2026-11-17)

T-219(`compat-t219-default-world-generators-2026-11-17.md`)의 후속. Paper 의 bukkit.yml `worlds.<이름>.generator`·`biome-provider` 가
플러그인이 만드는 **추가 월드**에도 쓰이는 것을 맞춘다. 근거는 Paper 1.21.1 서버 jar(paperclip 패치 적용본, sha256 `020b6831…`)의
`CraftServer` 바이트코드(`javap -c`) — 추측이 아니다.

## 1. Paper 1.21.1 이 하는 일

`CraftServer#createWorld(WorldCreator)` 순서:
1. 서버 시작 중(월드가 아직 안 올라옴)이면 `IllegalStateException("Cannot create additional worlds on STARTUP")`, 생성기(`creator.generator()`)·공급자
   (`creator.biomeProvider()`)를 지역 변수로 읽는다.
2. 이름·키가 이미 있는 월드면(둘이 같은 월드일 때만) 그 월드를 돌려준다 — **이 경우 설정은 읽지 않는다**.
3. 폴더가 파일이면 `IllegalArgumentException`.
4. **`generator == null` 이면 `getGenerator(이름)`, `biomeProvider == null` 이면 `getBiomeProvider(이름)`** — 둘은 서로 독립이다
   (생성기를 직접 줘도 공급자는 설정에서 읽는다). 환경 해석·level.dat 읽기보다 앞이라 이미 폴더에 level.dat 이 있는 월드도 똑같이 읽는다.
5. 그 뒤 `generator != null` 이고 공급자가 아직 null 이면 `generator.getDefaultBiomeProvider(worldInfo)` (설정의 공급자가 이 기본값보다 앞선다).

`getGenerator(이름)`(공급자는 `biome-provider`·`getDefaultBiomeProvider`):
- 설정 `worlds` → `<이름>` → `generator` 문자열이 없거나 빈 문자열이면 null(로그 없음). 이름은 설정 경로라 대소문자를 구분한다.
- 값은 `split(":", 2)` — 앞이 플러그인 이름(`SimplePluginManager#getPlugin`, 대소문자 무시·빈칸 `_`), 뒤가 id(없으면 null).
- 플러그인이 없으면 서버 로거 SEVERE `Could not set generator for default world '<이름>': Plugin '<플러그인 이름>' does not exist`,
  켜지지 않았으면 `… Plugin '<fullName>' is not enabled yet (is it load:STARTUP?)`, null 을 주면 `… lacks a default world generator`,
  던지면 그 플러그인 로거 SEVERE `… Plugin '<fullName>'` + 예외. 모두 null → 그 월드는 바닐라 생성기.
  문구에 "default world" 가 있는 것은 추가 월드에도 같다(같은 메서드).

## 2. Furnace 설계

- 설정 키: `world-generator.name.<월드 이름>`·`world-biome-provider.name.<월드 이름>` = `플러그인[:id]`. 이름은 키의 접두 뒤 전부(점·빈칸 포함,
  대소문자 구분). `.properties` 형식이라 한글 이름은 `\uXXXX` 로 적는다(Paper 설정 가져오기는 자동으로 이스케이프한다).
- 서버 API: `Furnace#configuredWorldGenerator(이름)` → `WorldGeneratorSetting(generator, biomeProvider)`(비면 빈 문자열). 서버 본체가 시작 때
  `ServerCore#worldSettings` 에 건 `WorldSettings` 에서 답한다.
- 호환 계층: `CraftServer#getGenerator(String)`·`getBiomeProvider(String)`(Paper 와 같은 public 메서드, 플러그인 조회·문구는 T-219 의
  `BukkitDefaultWorlds` 를 공유 — 플러그인이 없으면 이제 Bukkit 쪽이 직접 Paper 문구를 남긴다)와 `createWorld` 의 위 4단계.
  `createWorld` 만 읽는다: Furnace 자체 API 의 `createWorld`(Bukkit 이 아닌 플러그인)는 설정을 보지 않는다 — Paper 에 대응이 없고,
  Furnace 플러그인은 `generationHooksObject`·`biomeProvider` 를 직접 준다.
- 기본 세 월드: 차원 키(`world-generator.overworld` 등)가 먼저, 없으면 그 월드의 Bukkit 식 이름(level-name, `_nether`, `_the_end`)으로 이름별 키를 본다
  (Paper 는 이름으로만 찾으므로 `world-generator.name.world` 도 기본 오버월드에 적용). 같은 차원에 `worldgen-hooks.<차원>` 이 있으면 훅 키가 이긴다.
- Paper 설정 가져오기: `bukkit.yml` 의 `worlds` 아래 모든 `<이름>.generator`·`<이름>.biome-provider` 를 옮긴다 — 기본 세 월드 이름(`server.properties`
  level-name 기준)은 차원 키로, 나머지는 이름별 키로. Bukkit `getString` 처럼 숫자·불리언 값도 글자로 읽고, 점이 든 이름은 중첩 칸으로 적혀도
  같은 이름이다(`MemorySection` 경로 규칙). 빈 값은 "비어 있다 — 바닐라"로 무시하고, 그 아래 다른 칸은 "읽는 곳이 없다"로 보고한다.

## 3. 같음의 기준과 받아들인 차이
- 같은 bukkit.yml 로 만든 추가 월드 = Paper 결과(생성기·공급자 고르기 순서, 호출 횟수, 로그 문구).
- 차이: 설정 값 앞뒤 빈칸을 자른다(Paper YAML 은 따옴표 값의 빈칸을 그대로 둬 플러그인을 못 찾는다 — Furnace 는 관대). 기본 세 월드 외 이름은 가져오기가
  `world-generator.name.<이름>` 으로 옮기며 플러그인이 `createWorld` 를 부를 때만 읽힌다(`worlds.autoload` 로 서버가 직접 여는 월드는 읽지 않는다 —
  생성기 표시가 있는 월드는 T-217 규칙대로 열지 않고, 그 플러그인이 다시 만들 때 열린다).

## 4. 시험
- 서버: 이름별 키 읽기(점·빈칸·대소문자·빈 값·접두만), 기본 월드 우선순위(`DefaultWorldGeneratorsTest`), 가져오기(`PaperConfigImportTest` — 추가 월드·숫자 값·중첩 이름·
  한글 이름 이스케이프 왕복).
- 호환(`BukkitNamedWorldGeneratorTest`, 실제 서버 `FurnaceTestServer.namedWorldGeneration`): 설정 생성기가 `createWorld` 월드의 청크·바이옴을 정함,
  이름 대소문자·이미 있는 월드는 묻지 않음, 생성기만 줘도 공급자는 설정에서(그리고 설정 공급자가 생성기의 기본 공급자보다 앞섬), 둘 다 주면 묻지 않음,
  없는 플러그인·null 답의 Paper 문구와 바닐라로 열림.
