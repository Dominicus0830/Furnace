# 라이선스 정책 (2026-11-08, 도미니코 승인)

> 법률 자문이 아니다. 배포 전 중요한 판단은 전문가에게 확인할 것.

- 서버 본체와 `furnace-compat-paper`: **GPL-3.0-only**. 루트 `LICENSE`.
- `furnace-api`: **MIT**. `furnace-api/LICENSE`. 플러그인이 API 만 쓰면 어떤 라이선스든 가능하다.
- 모듈별 SPDX 식별자는 각 `build.gradle.kts` 첫 줄.
- **제외:** 바닐라 Mojang 에서 추출한 데이터·코드·자산은 이 라이선스 대상이 아니다(Mojang EULA 적용). 저장소에 커밋하지 않는 원칙은 그대로.
- **Spigot 이름 대응표(H7d):** Mojang 공식 매핑·Spigot BuildData 매핑은 저장소·서버 jar 에 싣지 않는다. Spigot 이름으로 컴파일된 NMS 플러그인이 `plugins/bukkit-plugins/` 에 있을 때만 운영자 컴퓨터가 원 출처에서 받아 캐시에 두고 메모리에서 대응표를 만든다(Paper 의 첫 실행 방식과 같은 구조). 기본 `plugins.compat.nms.spigot-mappings=download`(2026-11-12 도미니코 결정 — 넣으면 바로 돈다), 받지 않으려면 `off`. 코드에는 주소·체크섬만 있다.
- **예외 — Paper 1.21.1 멤버 이름표(2026-11-16, 코디네이터 결정, 도미니코가 되돌릴 수 있음):** `furnace-compat-paper/src/main/resources/furnace-compat-nms/paper-1.21.1-members.txt.gz` 는 실제 Paper 1.21.1 서버 jar 의 NMS 클래스별 **필드·메서드 이름만** 담는다(코드·본문·서명·값 없음, 호환을 위한 사실 정보). 플러그인이 리플렉션으로 찾다 실패한 이름이 "Paper 에도 없는 이름(옛 판 이름·기능 감지용)"이라 같은 예외가 나는 것인지, 우리 shim 의 진짜 구멍인지 가려 경고를 덜 거짓으로 만들려는 것이다. 이름만이라고 보는 이유: 이름 자체는 플러그인이 호출하는 공개 접점의 사실이고 구현·주석을 싣지 않는다. 다시 만드는 도구는 `tools/compat/gen-paper-member-names.py`(개발자가 PaperMC 원 출처 jar 로 다시 만든다. 이 표는 jar 에 포함되지만 Paper 코드는 포함하지 않는다). 빼면: 표가 없어 모든 실패를 구멍으로 보고 경고하므로 `nextTickTime`·`playerConnection` 같은 오탐 경고가 다시 나온다(동작은 같음).
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
| Guava, SnakeYAML (플러그인 호환용으로 jar 포함 — 2026-11-12 결정) | Apache-2.0 | 가능 |
| Brigadier, DataFixerUpper (Mojang 이 MIT 로 공개, 필요할 때 내려받기) | MIT | 가능 |
| Adventure, JOML (필요할 때 내려받기) | MIT | 가능 |
| bungeecord-chat (`compileOnly`, 배포물에 안 들어감) | BSD-3-Clause 계열 | 해당 없음 |
| authlib (Mojang, 컴파일용 stub 만 쓰고 실행 때 내려받기) | Mojang 라이선스(재배포 불가) | 저장소·jar 에 넣지 않으므로 충돌 없음. 계속 내려받기만 할 것 |
| JUnit (시험 전용) | EPL-2.0 | 배포물에 들어가지 않음 |
| scoreboard-library (`nmsSurvey`, 조사 전용) | 배포물에 포함되지 않음 | 해당 없음 |

`furnace-api` 는 MIT 라 GPLv3 서버에 포함돼도 문제없고, 플러그인은 API 만 쓰면 GPL 의무가 없다. 서버 내부(`Internals`)를 링크하는 플러그인은 GPLv3 의 결합 저작물 해석 대상일 수 있다.

2026-11-16 점검: 위 표에 GPLv3 와 충돌하는 의존성은 없다. 단 Guava·SnakeYAML·Brigadier·DFU·Adventure·JOML·authlib 행은 build.gradle.kts 가 아니라 호환 계층·내려받기 목록에서 확인한 것이므로 릴리스 전에 실제 jar 의 라이선스 파일로 재확인한다.
