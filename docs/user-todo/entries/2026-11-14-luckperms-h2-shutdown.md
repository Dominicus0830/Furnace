# LuckPerms H2 종료 오류 (2026-11-14)

결정할 것은 없다. 확인만 부탁한다.

| # | 확인할 것 | 어떻게 |
|---|---|---|
| 1 | `/stop` 때 `NoClassDefFoundError: org/h2/api/ErrorCode` 와 크래시 리포트가 더 이상 안 나오는지 | 이 PR 이 들어간 빌드로 LuckPerms(H2) 를 넣고 켠 뒤 콘솔에서 `stop`. 로그의 종료 줄이 `[Server Shutdown Thread/INFO]` 로 찍히면 새 빌드다 |

참고: Ctrl+C(창 닫기)로 끄면 Paper 와 같게 플러그인 훅과 동시에 돌아 드물게 같은 오류가 날 수 있다(Paper 도 같은 구조다). 서버는 `stop` 명령으로 끄는 것이 안전하다.
