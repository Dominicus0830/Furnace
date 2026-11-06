# 청크 저장·로드 접속 시험 (2026-11-06)

노이즈 월드에서 디스크 저장·퇴출 후 재로드·재시작 뒤 재로드가 같은 지형·블록·상자 내용을 주는지 본다(`docs/feature-parity/perf-chunkio-2026-11-06.md`).

```
D=$(../srv/newsrv.sh chunkio 'level-type=minecraft\:normal' 'level-seed=12345')
../srv/run.sh <furnace-server.jar> $D && ../srv/waitup.sh $D
SRV=$D node persist.js 1      # 지형 표본 저장, 표시 블록·상자, 3000칸 갔다 와서 비교
../srv/stop.sh $D
../srv/run.sh <furnace-server.jar> $D && ../srv/waitup.sh $D
SRV=$D node persist.js 2      # 재시작 뒤 표본·표시 블록·상자 내용 비교
../srv/stop.sh $D
```
- 재접속 직후 원점 청크가 오기 전에 표본을 뜨면 전부 null 로 다르게 나온다 — 스크립트가 표본 범위 네 귀퉁이 청크를 기다린다.
- `tprepro.js park|jump`: 재접속 직후 `/tp` 가 "대상을 가진 region 을 찾지 못했다" 로 한 번 실패한 것을 다시 보려던 스크립트.
