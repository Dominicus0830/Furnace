# A20 문서 정합 — 조사 보고서

- 기준 커밋: `origin/claude/quirky-wright-lra834` (8f8b188f, PR #170 병합 뒤)
- 조사 범위: `docs/api/*.md`(27개), `docs/policy/*.md`(3개), `docs/user-todo.md`·`docs/user-todo/README.md`·`docs/user-todo/entries/*`(이미 끝난 항목 남김 확인), `docs/handoff/2026-10-31-session-handoff.md`, `CLAUDE.md`·`AGENTS.md`·`README.md`, `docs/ci.md`(CI 설명과 `.github/workflows/ci.yml` 대조).
- 조사하지 못한 범위: 예시 코드 컴파일(`furnace-plugin-test` 에 임시로 넣는 작업)은 하지 않았다. 이름 수준 대조로 끝냈다. 사용자 접속 시험에서만 확인되는 user-todo 1장 항목의 기대 동작은 문서 내용의 옳음 판단이라 범위 밖(A20 방법 3절 "제외"). 바닐라 대응 이름은 이 문서가 맡지 않았다.
- 재현: `A20-repro/` 의 셸 스크립트 세 개(`.txt` 확장자, 실행 안 함). 결과는 아래 "확인한 것"에 적었다.

## 확인한 것 (어긋나지 않음)

| 검사 | 대상 | 결과 |
|---|---|---|
| 백틱 대문자 식별자 | `docs/api`·`policy`·README·CLAUDE·AGENTS 의 클래스 이름 632개 | 9개만 소스에 단어로 없음. 전부 의도된 것: `ClientboundXxxPacket`·`ServerboundXxxPacket`(자리표시자), `MAJOR.MINOR.PATCH`·`LICENSE`(파일·표기), `BiomeProvider`(문서가 "이번 묶음에 없다"고 명시), `EntityChangeBlockEvent`·`RecipeRegisterEvent`(문서가 "만들지 않았다"고 명시), `DedicatedServer.isSpawningAnimals`·`MinecraftServer.updateMobSpawningFlags`·`ServerLevel.setSpawnSettings`(바닐라 1.21.1 바이트코드 이름, `spawn-and-villages.md` 3절이 바닐라 대조라고 밝힘) |
| 백틱 메서드 호출 `이름(` | 같은 문서들의 409개 | 6개만 없음. 위와 같은 바닐라 참조 3개 + `feat`(설명문 글자 조각) + `areNpcsEnabled`·`isSpawningMonsters`·`getEquipment`(바닐라 쪽 설명). Furnace 쪽 메서드 이름은 빠짐없이 있다 |
| api-version 규칙 | `docs/api/plugin-foundation.md` 2장 ↔ `ApiVersion.CURRENT = 1.0` · `PluginBootstrapper` | 일치. `compat-paper.md` 의 "1.21 초과 거절"은 `CompatFormat` 의 Bukkit 쪽 검사와 일치(`api-version` 1.21 이하만 받음) |
| 취소 계약·원인 사슬 설명의 클래스 | `cause-chain.md`(`Causes.java`), `event-cancel-contract.md`(`ClientResync.java`), `internals.md`(`InternalsImpl.java`, `ApiNbt.java`) | 실제 파일은 `furnace-server/src/main/java/org/domi/furnace/server/plugin/` 아래에 있다. 문서의 `.../plugin/` 생략 표기는 맞다 |
| 문서 안 파일 경로 | 백틱 파일 이름 108개 | 없는 것은 전부 설정 파일 예시(`server.properties`·`bukkit.yml` 등 서버 폴더 산출물), 가져오기 보고서 이름, `plugin.yml` 류 매니페스트 예시, `m21-followup` 예시 이름(규칙 설명용). 저장소 파일이어야 할 것은 없다 |
| CI 도구 경로 | `docs/ci.md` ↔ `tools/ci/shard-balance.py`, `tools/user-todo-combined.sh`, `furnace-server/build.gradle.kts` 의 `testShard`·`shardSalt` | 전부 있고 `ci.md` 설명과 맞다 |
| CI 브랜치 이름 | `docs/ci.md` "기준 브랜치 이름 바꿀 때 두 곳" ↔ `ci.yml` | `on.push.branches` 와 `BASE_BRANCH` 둘 다 `claude/quirky-wright-lra834`. 일치 |
| 결정 기록 | `docs/user-todo.md` 2장 "지금 결정을 기다리는 항목은 없습니다" ↔ 2장 본문 | 일치(2026-11-07 R1~R5 답변 반영) |

## 어긋난 것

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A20-01 | 중간 | 문서 | `docs/handoff/2026-10-31-session-handoff.md` 4장 7번: "CLAUDE.md 의 커밋 전 규칙에 `:furnace-api:test` 추가 … 사용자 결정 대기"라고 적었다. 그러나 `CLAUDE.md` 12행에 이미 `:furnace-api:test :furnace-server:test` 가 들어 있다. 같은 4장 3번 "플러그인 API 2단계 끝(2026-11-03)·접속 시험 대기"와 2번 "2026-11-06 6차"도 날짜가 10-31 인 파일에 적혀 있어 스냅샷인지 현재 상태인지 헷갈린다 | `grep -n ":furnace-api:test" CLAUDE.md` 로 확인. 파일 날짜 vs 본문 날짜 비교 | 4장 7번을 "해결됨(CLAUDE.md 반영)"으로 바꾸고, 파일 머리에 "이 인수인계서는 10-31 기준이다. 이후 상태는 `docs/user-todo.md` 와 `CLAUDE.md` 가 기준"이라는 한 줄을 넣는다. 작음. 문서만 — 권장 모델 Haiku(low) |
| A20-02 | 낮음 | 문서 | `CLAUDE.md` 12행 "CI 도 같은 두 모듈을 돌린다"는 사실과 다르다. `ci.yml` 은 `:furnace-api:test` 와 서버 3조각 외에 API 테스트, 플러그인 샘플 컴파일, 시험 서버 모듈 테스트, 호환 계층 모듈 테스트, fatJar 빌드를 더 돌린다 | `.github/workflows/ci.yml` 99~120행 | "커밋 전 최소 검사. CI 는 이 외에 샘플 플러그인 컴파일·호환 계층·fatJar 도 돌린다(`docs/ci.md`)"로 고친다. 작음. Haiku(low) |

## 사용자 확인·결정 필요

없다. 위 두 건은 문서 고침이고, 도미니코 결정과 관계없다. `docs/user-todo/entries/` 새 항목은 만들지 않았다.

## 심각도별 항목 수

- 치명: 0 / 높음: 0 / 중간: 1 / 낮음: 1 / 참고: 0
