# 명령 큐 즉시 처리 (commands.region-drain, O11 P-O11-01) — 2026-11-12
함수·명령이 region 의 칸·엔티티를 만질 때 한 줄이 한 틱(50ms)씩 걸려 `#minecraft:tick` 함수가 리전 접근 3번에 초당 6.7번만 돌던 문제를 고쳤다. 명령이 region 에 보낸 작업(명령 범위에서 넣은 것, 이어서 넣는 두 번째 홉 포함)은 다음 틱을 기다리지 않고 곧바로 처리한다. 헤드리스 서버 시험으로 확인: 접근 3번 함수 61틱에 61번 실행(끄면 22번).

- 키 `commands.region-drain`(기본 `true`). `false` 면 예전처럼 접근 한 줄이 한 틱씩 걸린다.
- 예산: 전역 틱마다 급한 작업(drain 차례 실행 시간 + 명령 블록이 기다린 시간)을 합쳐 20ms(`-Dfurnace.commandBlockWaitMicros`, 명령 블록과 같은 값)까지만 즉시 처리하고, 넘으면 다음 region 틱으로 물러난다(결과는 같고 시간만 늦어짐).
- **확정된 결정(도미니코, O11):** `#tick` 은 "건너뛰기·큐잉" 이 아니라 **전역 틱이 함수 완료까지 기다린다.** 이번 PR 은 즉시 처리까지만이고(앞 실행이 아직 안 끝났을 때 건너뛰는 `waitingTick` 은 그대로), "전역 틱이 `#tick` 함수 완료까지 대기"는 후속 TASK(P-O11-11)로 남긴다. 드레인 확장과 RegionBorrow 비교 측정도 같은 후속에서 한다(이 PR 은 지시대로 기존 드레인 문 확장을 택했다).
- 실제 클라이언트로 남은 것: 데이터 팩의 `#minecraft:tick` 함수에 `execute if block`·`setblock` 줄을 여러 개 넣고, `/function` 이 아니라 스코어보드 카운터(`scoreboard players add #runs o 1`)로 1분 동안 세어 `/tick rate` 기본에서 약 1200번(바닐라 20/s)인지. `/tick freeze`·`/tick step` 에서도 한 틱에 한 번인지.
