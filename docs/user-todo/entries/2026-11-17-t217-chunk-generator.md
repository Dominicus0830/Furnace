# T-217 Bukkit 청크 생성기로 만드는 월드 — 결정 두 가지 (2026-11-17)

근거: `docs/feature-parity/compat-t217-chunk-generator-worlds-2026-11-17.md`

플러그인이 자기 지형 생성 코드(Bukkit `ChunkGenerator`)를 넘겨 월드를 만들 수 있게 했다(Paper 와 같은 방식). 하늘섬·빈 월드·미로 같은 지형을
만드는 생성기 플러그인이 이제 Furnace 에서 돈다. 플러그인이 받는 난수까지 Paper 와 같게 맞췄다. 아래 G1 은 추천안으로 이미 적용했고, G2 도
추천안대로 T-219 에서 적용했다(11-17). 바꾸고 싶으면 알려 주면 된다.

## G1. 서버가 스스로 다시 열 때(자동 불러오기·`/worlds load`) 생성기 월드를 어떻게 할까

- 쉬운 말: 생성기는 플러그인 안의 코드라 파일에 저장할 수 없다. 서버를 다시 켜면 그 월드에는 "생성기가 필요하다"는 표시만 남는다.
  바이옴 공급자 월드(T-210, 결정 E1)와 같은 문제다.
- 예: 하늘섬 플러그인으로 만든 `skyblock` 월드를 `worlds.autoload=skyblock` 에 넣어 둔 경우. 자동 불러오기는 플러그인이 켜지기 전에 돈다.
- 추천(적용함): E1 과 같은 규칙. 표시가 있는 월드는 자동 불러오기·`/worlds load` 에서 열지 않고 콘솔에 "이 월드는 생성기 ○○ 로 만든 월드라
  건너뜁니다. 그 플러그인이 다시 만들 때 열립니다" 한 줄을 남긴다. 플러그인이 켜지면서 생성기와 함께 다시 만들면 정상으로 열린다.
- 이렇게 안 하면(바닐라로 연다): 서버가 플러그인보다 먼저 그 월드를 바닐라 지형으로 열어 버린다. 그 뒤 플러그인이 생성기를 주며 다시 만들어도
  "이미 열린 월드"가 돌아와 생성기가 영영 안 붙고, 하늘섬 옆에 바닐라 산이 생긴다(되돌릴 수 없다).
- 플러그인이 Bukkit `createWorld` 로 생성기 없이 다시 열면 Paper 와 똑같이 그냥 연다(콘솔 경고 한 번만 더함).

## G2. 기본 세 월드(`world`·`world_nether`·`world_the_end`)를 생성기 플러그인으로 만들기 — 추천안 적용함(T-219, 11-17)

- 쉬운 말: Paper 는 `bukkit.yml` 에 `worlds: world: generator: 플러그인이름` 을 적으면 기본 월드도 그 플러그인 생성기로 만든다.
  Furnace 는 기본 월드를 플러그인보다 먼저 만들고 `bukkit.yml` 파일도 없어서 이번에는 넣지 않았다. 추가 월드(`createWorld`)는 이번에 다 된다.
- 예: "서버 전체가 하늘섬" 같은 설정(`bukkit.yml` 의 generator)은 아직 안 된다. 추가 월드로 만들고 접속 위치를 그 월드로 보내는 방식은 된다.
- 추천: 지금처럼 두고, 필요해지면 T-219 로 `furnace.properties` 에 같은 뜻의 설정(예: `world-generator.overworld=플러그인[:id]`)을 더하고, 그 생성기를 가진
  플러그인이 켜질 때까지 기본 월드 만들기를 미루는 방식으로 한다(Paper 의 bukkit.yml 과 같은 결과).
- 안 하면: 기본 월드를 생성기 플러그인으로 바꾸려는 서버는 Furnace 에서 그 설정이 무시된다.
- **추천안 적용함(11-17, 도미니코 위임 — "취지에 맞게 알아서")**: `furnace.properties` 에 `world-generator.overworld|the_nether|the_end`·
  `world-biome-provider.<같은 셋>` = `플러그인[:id]` 를 더했다. `load: STARTUP` 플러그인을 켠 뒤 그 생성기·공급자를 붙이고, 스폰 위치·스폰 청크도
  그 뒤에 정한다(Paper 순서). Paper 설정 가져오기는 `bukkit.yml` 의 `worlds.<level-name>[_nether|_the_end].generator·biome-provider` 를 이 키로 옮긴다.
  플러그인이 없거나 켜지지 않았으면 Paper 와 같은 문구를 남기고 바닐라로 연다. 설계 `docs/feature-parity/compat-t219-default-world-generators-2026-11-17.md`.

## 남은 것(T-218)

- 옛 방식 `BlockPopulator#populate(World, Random, Chunk)`(오래된 플러그인)는 아직 불리지 않는다. 그런 플러그인을 쓰면 콘솔에 클래스마다 경고 한 번이 뜬다.
- 장식 단계 영역(`LimitedRegion`)에서 엔티티·블록 상태·나무 만들기·바이옴 바꾸기는 아직 없다(부르면 이유를 적은 오류).
