# Furnace

바닐라 마인크래프트(1.21.1)와 같은 결과를 내는 Java 서버. 플러그인 API(`furnace-api`)와 Bukkit·Paper 호환 계층(`furnace-compat-paper`)을 갖는다.

## 라이선스

| 모듈 | 라이선스 |
|---|---|
| 서버 본체(`furnace-server`), `furnace-compat-paper`, 시험·도구 모듈 | GPL-3.0-only (저장소 루트 `LICENSE`) |
| `furnace-api` | MIT (`furnace-api/LICENSE`) — 플러그인이 이 API 에만 의존하면 플러그인 라이선스는 자유 |

각 모듈의 `build.gradle.kts` 첫 줄에 SPDX 식별자를 적었다.

**제외:** 바닐라 Mojang 서버/클라이언트에서 추출한 데이터(블록 상태 표, 레지스트리, 월드젠 데이터 등)와 Mojang 코드·자산은 이 저장소의 라이선스 대상이 아니며 Mojang 의 약관(EULA)을 따른다.

의존 라이브러리 호환 점검은 `docs/policy/licensing.md`. 이 문서는 법률 자문이 아니다.
