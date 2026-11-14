# 다인원 새 땅 탐험 측정 도구 (w60, T-173)

N 명이 새 땅을 걸어 다닐 때 생성 처리량·발밑 채움 공평성·TPS 를 재는 공통 장면이다(60명 조사 `W60-explore.md` 에서 나온 도구).
T-170(생성 대기열 재설계)·T-171·T-172·T-175·T-176 같은 다인원 새 땅 PR 은 이 도구로 전/후를 잰다.

| 파일 | 역할 |
|---|---|
| `w60.js` | 봇. 위치 패킷(20Hz)으로 직접 걷는다(콘솔 `/tp` 걷기 금지 — 명령이 틱당 1개만 처리돼 실제로 덜 걷는 함정). 결과 JSON 한 줄 |
| `w60-run.js` | 한 판 자동화: 새 월드 → 서버 → 표본 추출기 → 봇 → 종료 → `runs/w60.jsonl` 에 덧붙임. 리눅스·Windows 공용 |
| `w60-sum.py` | `w60.jsonl` + 표본 + 서버 `[CHUNKTRACE]` 로 판마다 한 줄 요약 |
| `w60-sampler.py` | 1초마다 서버 CPU·RSS·Prometheus 메트릭(TPS·mspt) 기록(리눅스는 스레드 그룹별 CPU 도) |

## 준비
1. 서버 jar: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew -PskipOracle=true :furnace-server:fatJar`(`furnace-server/build/libs/furnace-server.jar`).
2. 바닐라 데이터 폴더(`data/`·`version.json` 이 든 곳, `tools/bot-test/README.md` 준비 2항 참고)를 `VANILLA_DATA` 로 지정.
3. 봇 의존성: `tools/bot-test` 에서 `npm install`. 파이썬 3 (`python3`, Windows 는 `python`; 다르면 `PYTHON` 환경변수).
4. (선택) Windows 에서 CPU·RSS 표본을 얻으려면 `pip install psutil`. 없어도 TPS·mspt 메트릭은 나온다.

## 실행

클라우드(리눅스, 4코어 기준):
```
export VANILLA_DATA=<바닐라 데이터> W60_ROOT=/tmp/w60
useradd -m w60            # root 로 돌릴 땐 서버를 이 사용자로 띄운다(RUNAS 로 변경)
cd tools/bot-test/chunkrate
node w60-run.js --tag base --jar <서버 jar> --layout lines  --n 60 --speed 5.6 --secs 90 --warm 20
node w60-run.js --tag base-r --jar <서버 jar> --layout radial --n 60 --speed 5.6 --secs 90 --warm 20
python3 -I w60-sum.py            # 전부, 또는 w60-sum.py '^base'  (꼬리표 정규식)
```
노트북(Windows, PowerShell):
```
$env:VANILLA_DATA="C:\...\vanilla-data-1.21.1"; $env:W60_ROOT="C:\Users\<나>\furnace-r0\w60"
cd tools\bot-test\chunkrate
node w60-run.js --tag base --jar C:\...\furnace-server.jar --n 60 --xmx 8G
python w60-sum.py
```
- `--layout lines`: 사람마다 3000블록 간격 줄에 두고 +z 로 걷는다(서로 안 만남, 생성 처리량 장면). `radial`: 스폰에서 사람마다 각도로 퍼진다(처음엔 region 하나 → 점점 갈라짐, TPS 장면).
- `--ymode ground`(기본): 받은 청크의 `MOTION_BLOCKING` 높이맵을 읽어 **땅 높이를 따라 난다**(몹 스폰이 실제와 같다). `--ymode fixed --y 200`: 옛 방식 — y200 에 떠 있으면 몹이 거의 안 붙어 스폰 비용이 빠진다(옛 수치와 비교할 때만 쓴다). 땅 높이 모드는 처음에 y200 에서 천천히 내려오므로 `--warm` 은 10초 이상.
- `--from <이전 꼬리표>`: 그 판의 월드를 복사해 시작(미리 만든 땅 장면, T-175 효과 비교). `--props "view-distance=12;..."` server.properties, `--fprops "k=v;..."` furnace.properties 추가, `--` 뒤는 JVM 옵션(예: `-Dfurnace.proto.genPrio`).
- `serverCorrections`(되돌림 수)가 수백이어도 `walk_ok` 가 참이면 괜찮다(언덕 모서리 충돌 등 일부 보정). 거짓일 때만 그 판을 버린다.
- `--person`: 사람별 링 0~2 평균 전체를 결과에 넣는다(`ring02Person`).
- 서버는 `-Dfurnace.chunktrace=true -Dfurnace.tickstats=true` 로 자동 시작, 시드 20261112, 월드는 판마다 새로 만든다(`--from` 제외).
- 다른 컴퓨터에서 재현할 때 판 순서는 판마다 번갈아 뒤집을 것(컨테이너 속도가 날마다 다름 — 같은 날·같은 컴퓨터에서 전/후 비교). 4코어 한계 약 원형 180개/초, 노트북 10코어는 달라진다.

## 읽는 법
**먼저 `walk_ok`.** 서버가 받아들인 이동(`srvMovedMin`)이 기대(`speed×secs`)의 90% 미만이면 되돌림·접속 끊김으로 덜 걸은 판이다 — 수치를 버린다(`moved` 열에 `보낸 최소/서버 최소 (기대)`).

| 열 | 뜻 |
|---|---|
| `recv_s`·`need_s` | 받은 청크/초 합계, 필요량(전원이 시야 띠를 채우는 데 드는 양) |
| `ring02`·`r02min`·`r02p10`·`r02pct`·`r02zero` | **발밑(링 0~2) 채움**: 사람별 평균의 전체 평균·최저·p10·분포(p10·25·50·75·90)·0%대인 사람 수. 평균만 보면 "일부만 촘촘히" 불공평이 가려진다(W-4). 통과 조건: `r02zero` 0 |
| `ring35`·`cover` | 링 3~5·원형 시야 전체 채움 |
| `gen_s`·`prist_per_full`·`drop_per_full`·`cancel_s` | 완성 청크/초, 완성 1개당 원형(pristine) 수·버린 수, 취소/초 |
| `genq_ms`·`genrun_ms`·`hopgen_ms`·`srcgen_ms` | 생성 대기·실행·빛 풀 홉·생성 원본 평균. **`gen.queue` 는 "시작한 요청"만 잰다 — 시작 못 하고 큐에 남은(굶는) 요청은 안 들어가는 생존자 편향** |
| `gqlen_avg`·`gqstale_avg/max/last` | 큐 안 생성 요청 수·그중 가장 가까운 플레이어 시야를 넘게 먼 것(`batch.gqstale`). T-170 통과 조건 0 |
| `cpu`·`tps_avg`·`tps_min`·`mspt`·`regions`·`chunks`·`heap`·`grp` | 표본(걷는 동안). `grp` = 스레드 그룹별 CPU%(리눅스만) |
| `ground` | 높이맵 해석 성공/실패 횟수(`miss` 가 크면 높이 모드가 틀어진 것) |

`gen.queue`·`gen.run`·`gen.pristine`·`gen.drop`·`gen.cancel`·`batch.gqlen`·`batch.gqstale` 는 기준 브랜치 서버에는 없고 시제품(`GenPriorityExecutor`)·T-170 이후 서버가 `ChunkTrace` 로 찍는다. 없으면 `-1`/`0` 으로 나온다.

## 함정(조사에서 얻은 것)
- 콘솔 `tp` 로 걷기 금지(위). 봇이 직접 위치 패킷을 보내고, 멈춘 뒤 제자리 `tp` 로 서버 위치를 되읽어 검증한다(`walk_ok`).
- y200 에 떠 있으면 몹이 안 붙는다 → 기본이 땅 높이 모드. 옛 y200 수치와 직접 비교하지 말 것(장면 R 의 자연 스폰 52%·`owns/ownerOf` 20% 는 y200 값이라 TASK 근거로 쓰지 않는다 — T-019·T-176).
- 봇(node)이 60명에서 CPU 약 40% 를 쓴다. 청크 본문은 해석하지 않고 높이맵만 떼어 낸다. 4코어에선 봇이 서버를 방해하므로 인원을 키울 땐 CPU 열을 같이 볼 것.
- JFR 인라인 잎 착시: 프로파일 비율은 `-XX:CompileCommand=dontinline,...` 판으로 확인.
- 서버 로그 시각과 같은 컴퓨터의 현지 시각으로 맞추므로 서버·요약은 같은 컴퓨터에서 돌린다.
