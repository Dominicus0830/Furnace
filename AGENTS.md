# Furnace

Java 21로 바닥부터 만드는 멀티스레드 Minecraft **1.21.1** 서버. 패키지 루트 `org.domi.furnace`.
목표는 Paper 한계 돌파와 바닐라 parity(같은 시드·같은 좌표에서 같은 블록).

## 모듈
| 모듈 | 역할 |
|---|---|
| `furnace-api` | 스케줄러 인터페이스, `Owned`/`ThreadOwner` |
| `furnace-server` | 서버 본체 (`net/`, `world/`, `world/gen/`, `sched/`, `entity/`, `config/`) |
| `furnace-plugin-test` | 플러그인 API 테스트베드 |
| `worldgen-oracle` | paperweight-userdev로 바닐라 값을 덤프해 테스트 fixture 생성 |
| `tools/` | `parity-audit`(바닐라 실서버 .mca 대조), `vanilla-data`(바닐라 데이터 추출) |

## 명령
- 전체 테스트: `./gradlew :furnace-server:test`
- Paper 저장소에 못 닿는 환경(샌드박스·오프라인 CI): 모든 gradle 명령에 `-PskipOracle=true`. 이때 `:worldgen-oracle:*`는 쓸 수 없다.
- oracle 실행은 `-Doracle.*` 시스템 프로퍼티로 모드를 고른다(`worldgen-oracle/build.gradle`). 설정은 `settings.gradle.kts` 주석 참고.
- 서버 JVM 기본 `-Xmx6G`, 테스트는 assertion 활성(`Owned` 가드가 AssertionError로 동작).

## 절대 규칙
1. **버전은 1.21.1 고정.** 리소스는 `*/1.21.1*` 네임스페이스로만 둔다. 1.21.4 데이터를 섞지 않는다(과거에 혼입돼 교체한 이력 있음).
2. **바닐라가 정답이다.** 추측하지 말고 바닐라 서버 jar·oracle·실서버 `.mca`로 확인한다. 확인 못 한 것은 "가정"이라고 적는다.
3. **parity를 거짓으로 만들지 않는다.** 구현하지 못한 feature는 `NoopFeature` placeholder로 둔다. RNG 소비가 다른 근사 구현을 활성화하지 않는다.
4. **스레드 소유권을 지킨다.** region/global 스레드 소유 상태는 해당 owner 스레드에서만 건드린다. 상세는 `.claude/rules/scheduler.md`.
5. **테스트를 스킵·비활성화해서 초록을 만들지 않는다.** 실패는 원인을 고친다.
6. **raw block state id 숫자 리터럴을 새로 늘리지 않는다.** 이름 기반 조회(`BlockRegistry`, `BlockStateKey`)를 쓴다. 기존 78곳은 버전업 부채다(`LLM-Wiki/output/furnace-version-upgrade-design.md`).
7. **바닐라 jar·생성 결과물은 커밋하지 않는다**(Mojang EULA). 커밋 대상은 추출된 리소스와 도구뿐이다(`tools/vanilla-data/README.md`).

## 영역별 상세 규칙
`.claude/rules/`에서 해당 경로 파일을 열 때 자동 로드된다.
- `worldgen-parity.md` — `world/gen/**`, `worldgen-oracle/**`, 리소스
- `network-packets.md` — `net/**`
- `scheduler.md` — `sched/**`, `furnace-api/**`

## 작업 기록 위치
- `docs/feature-parity/` — parity 계획·체크리스트·컨텍스트 노트·바닐라 대조 감사(2026-10-07). 시작 전에 `vanilla-audit-2026-10-07.md`의 "후속 과제"를 읽는다.
- `docs/superpowers/{specs,plans}/` — 마일스톤 설계서와 구현 계획(M0~M4g-d).
- `LLM-Wiki/wiki/Furnace/` — 작업별 지식 노트. `아키텍처-개요.md`, `레이어-*.md`가 진입점.
- 새 교훈·함정은 `docs/feature-parity/context-notes.md`에 날짜와 함께 추가한다.

## 커밋·문서 스타일
- 커밋 메시지는 `feat(worldgen): …`, `fix: …` 형식, 한국어.
- 주석·문서·노트는 한국어. 식별자는 영어.
- 검증 숫자(테스트 개수, mismatch 수)는 실제로 돌려본 것만 쓴다.
