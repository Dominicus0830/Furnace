# T-219 기본 세 월드 생성기 플러그인 — 함정 기록 (2026-11-17)

설계: `docs/feature-parity/compat-t219-default-world-generators-2026-11-17.md`

- **바꿔 끼울 수 있는 때는 "한 청크도 만들기 전"뿐이다.** `AnvilWorldSource#replaceBase` 는 `generateFallback`·`decodeChunk` 가 한 번이라도
  돌면 거절한다(읽은 청크도 바탕의 구조물 references 에 묶인다). 그래서 설정이 있는 차원은 강제 로드 청크 틱(`startTicking`)과 새 오버월드
  스폰 찾기를 미룬다 — 스폰 찾기는 높이를 얻으려고 바탕 생성기를 쓴다.
- **스폰 청크(prepareSpawnArea)는 모든 서버에서 STARTUP 플러그인 뒤로 옮겼다**(Paper prepareLevels 자리). 전에는 플러그인 앞이었다.
- **id 가 없으면 null 이다.** CraftServer#getGenerator 는 `name.split(":", 2)` 의 둘째 칸이 없으면 null 을 넘긴다(빈 문자열 아님).
  `플러그인:` 처럼 콜론 뒤가 비면 빈 문자열.
- **공급자는 생성기를 새로 만들어야 한다.** 노이즈 생성기의 바이옴 원천은 만들 때 정해지므로 `NoiseWorldSource#freshCopy(같은 시드, 공급자)` 로
  다시 만들고(생성 풀 공유), 그 사이 단 장식기는 옮긴 뒤 `ChunkDecorators.bind` 로 다시 묶는다. 평지도 같은 층·공급자로 다시 만든다.
- **공급자를 따로 주지 않으면 생성기의 `getDefaultBiomeProvider`**(MinecraftServer#loadWorld0). `CraftWorld#getBiomeProvider` 도 그것을 돌려준다.
- **POSTWORLD 플러그인은 "is not enabled yet (is it load:STARTUP?)"** 문구로 거절된다 — 그 플러그인의 `getDefaultWorldGenerator` 는 불리지 않는다.
  없는 플러그인은 Bukkit 쪽이 답하지 않고 서버가 `world.default-generator-missing`(Paper 와 같은 영어 문구)을 남긴다.
- **시험 서버(`FurnaceTestServer`)는 기본이 디스크 없는 생성기**라 `defaultWorldGeneration(...)` 을 주면 노이즈 생성기를 `AnvilWorldSource` 로 감싼다
  (바꿔 끼우는 자리가 그것). 노이즈 생성기가 아니면 IllegalArgumentException.
