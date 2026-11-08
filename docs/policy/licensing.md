# 라이선스 정책 (2026-11-08, 도미니코 승인)

> 법률 자문이 아니다. 배포 전 중요한 판단은 전문가에게 확인할 것.

- 서버 본체와 `furnace-compat-paper`: **GPL-3.0-only**. 루트 `LICENSE`.
- `furnace-api`: **MIT**. `furnace-api/LICENSE`. 플러그인이 API 만 쓰면 어떤 라이선스든 가능하다.
- 모듈별 SPDX 식별자는 각 `build.gradle.kts` 첫 줄.
- **제외:** 바닐라 Mojang 에서 추출한 데이터·코드·자산은 이 라이선스 대상이 아니다(Mojang EULA 적용). 저장소에 커밋하지 않는 원칙은 그대로.
- `furnace-compat-paper` 의 `org.bukkit.*` 호환 클래스는 공개 이름·서명만 따라 새로 썼고 Bukkit 소스를 복사하지 않았다. 서버 모듈이 GPLv3 이므로 같은 jar 에 넣어도 GPLv3 조건을 지키면 된다.

## 의존 라이브러리와 GPLv3 호환 점검 (build.gradle.kts 기준, 라이선스는 각 프로젝트 공지에서 아는 대로 적음 — 릴리스 전 재확인)

| 라이브러리 | 라이선스 | GPLv3 결합 |
|---|---|---|
| netty (all, io_uring) | Apache-2.0 | 가능 (Apache-2.0 은 GPLv3 와 호환) |
| gson | Apache-2.0 | 가능 |
| aircompressor | Apache-2.0 | 가능 |
| jline | BSD-3-Clause | 가능 |
| asm / asm-commons | BSD-3-Clause | 가능 |
| slf4j-api | MIT | 가능 |
| logback-classic | EPL-1.0 / LGPL-2.1 이중 | LGPL 쪽을 택하면 GPL 로 전환 가능해 호환 (LGPL-2.1 §3) |
| JUnit (시험 전용) | EPL-2.0 | 배포물에 들어가지 않음 |
| scoreboard-library (`nmsSurvey`, 조사 전용) | 배포물에 포함되지 않음 | 해당 없음 |

`furnace-api` 는 MIT 라 GPLv3 서버에 포함돼도 문제없고, 플러그인은 API 만 쓰면 GPL 의무가 없다. 서버 내부(`Internals`)를 링크하는 플러그인은 GPLv3 의 결합 저작물 해석 대상일 수 있다.
