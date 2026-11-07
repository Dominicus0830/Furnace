# 호환 계층 JavaPlugin 명령·스케줄러·서비스 연결 (H4 다음 묶음 A) — 2026-11-07

서버 쪽은 시험으로 확인(시험 안에서 컴파일한 플러그인 jar 를 헤드리스 서버에 올림). 설계·한계: `docs/feature-parity/compat-javaplugin-wiring-2026-11-07.md`. 직접 확인·결정할 것:

- **직접 확인(접속 시험, 권장):** 실제 클라이언트로 옛 방식 명령 플러그인 하나(`plugin.yml` 의 `commands:` + `onCommand` + 탭 완성)가 입력·탭·`usage` 에서 Bukkit 서버와 같게 보이는지, 서비스(Vault 류) 등록·조회가 되는지. 자동 시험은 가짜 플레이어까지만 봤다.
- **결정:** 옛 "동기 작업"(`BukkitScheduler#runTask*`)은 전역 스레드에서 돈다. 그 안에서 블록·엔티티 같은 region 상태를 읽으면 `ThreadOwnershipException` 이 난다(메인 스레드가 모든 월드를 만진다고 믿는 옛 코드). 대안: ① 지금처럼 두고 Paper 스케줄러 묶음(H)·플러그인 쪽 수정으로 푼다(추천 — 실제 플러그인이 켜지는 것부터 보고 정한다), ② 전역 스레드에서의 월드 읽기를 소유 region 으로 넘겨 기다리게 한다(교착 위험이 커서 비추), ③ 작업을 만든 플레이어/위치를 알 때만 그 region 스레드에서 돌린다. 답이 없으면 ①.
- **직접 확인(Bukkit 원문 대조):** 이 컨테이너는 Bukkit 소스를 못 받아 아래는 기억 기준이다. ① `SimpleServicesManager#register` 가 같은 우선순위에서 "나중 것이 앞"인지(Bukkit 은 `Collections.binarySearch` 결과 자리에 넣는다 — 지금 같은 알고리즘), ② `Bukkit.dispatchCommand` 가 실행기가 false 를 돌려줘도 true 인지, ③ 권한 없는 플레이어가 명령을 칠 때의 문장(H2 항목과 같은 질문).
