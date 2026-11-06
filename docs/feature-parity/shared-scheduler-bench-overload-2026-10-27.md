# 공유 스케줄러 과부하 벤치 (2026-10-27)

`shared-scheduler-review-2026-10-27.md` 3.5(공정성)·R3(EDF 기아)·7장 검증용. 운영 코드는 건드리지 않았고, 테스트 소스셋의
`MultiDimensionTickBench.overloadScenarios` 만 추가했다(기본 꺼짐).

실행: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests '*MultiDimensionTickBench' -Dfurnace.bench=true -Dfurnace.bench.overload=true`
(`-Dfurnace.bench.ticks=N` 로 틱 수 조절, 기본 60).

## 방법
- 차원 = 평지 월드 하나, region 은 서로 멀리 둬서 분리. region 마다 매 틱 바쁜 대기(부하 ms)를 하는 반복 작업을 건다.
- 지표: 간격 - 50ms = 늦음. 첫 5 간격 제외. **지연 틱** = 늦음 > 10ms. **maxRun** = 한 region 의 최장 연속 지연 틱 수(기아 지표).
  단위는 전부 ms. 차원마다 표본은 region 수 x 55.
- `perDim` = 현행(차원마다 풀, region-threads = 표의 threads). `shared` = 한 월드에 모든 region 을 넣은 **공유 큐 근사**(풀 하나, 데드라인 큐 하나, 같은 threads).
  근사의 한계: 차원별 최소 보장 같은 정책이 없는 순수 EDF 이고, 월드 하나라 합치기·나누기 잠금도 한 개다. 실제 B안 구현이 아니다.
- 시나리오: (1) 3차원 x 8 region, 합계 부하 = 코어 x 50ms x {1.0,1.5,2.0} 균등. (2) 차원0 무거움(region 4 또는 12개, 틱당 40/60/80ms), 차원1·2 가벼움(region 2개, 1ms) - 가벼운 차원만 측정.
  (3) N=3/6/12 차원 x region 4, 총 부하 약 150%(차원0 은 2배), threads = 2/4/8.

## 측정 환경과 한계
- 샌드박스 **4코어**(nproc=4), 리눅스 VM. 다른 프로세스·gradle 데몬과 CPU 를 나눈다. 시나리오당 1회, 60틱(약 3~10초)만 쟀다 - **반복·분산 없음**, p95/max 는 표본이 적고 노이즈가 크다
  (예: 같은 조건 근처에서도 max 가 수배 흔들림). 수치는 방향성 증거로만 쓰고 절대값을 SLA 로 삼지 않는다.
- 부하는 CPU 바쁜 대기라 실제 틱(메모리 접근·GC·IO)과 다르다. 바쁜 대기는 OS 가 스레드를 선점하면 시간이 늘지 않고 벽시계 기준이라 코어 경쟁이 있으면 부하가 실제보다 가볍게 보일 수 있다.
- 윈도·거친 타이머 플랫폼은 측정하지 않았다. 마지막 N=12 결과는 스레드가 48~96 개라 OS 스케줄링 노이즈가 지배적이다.
- 벤치 스레드(스핀 대기 포함)와 JIT 예열이 섞여 있다. 앞 5틱만 제외했다.

## 원자료 (d0.. = 차원별 지연, ms)
```
cores=4 ticks=60 lateThreshold=10ms
load100%                 perDim  threads=4 | d0 p50=32.1 p95=128.4 max=423.8 late=66% maxRun=13 | d1 p50=11.9 p95=98.0 max=170.9 late=52% maxRun=14 | d2 p50=27.0 p95=137.9 max=786.0 late=62% maxRun=22
load100%                 shared  threads=4 | d0 p50=38.0 p95=86.9 max=206.2 late=92% maxRun=31 | d1 p50=39.1 p95=87.0 max=277.6 late=94% maxRun=49 | d2 p50=38.7 p95=85.9 max=127.1 late=95% maxRun=55
load150%                 perDim  threads=4 | d0 p50=20.1 p95=89.8 max=962.0 late=61% maxRun=17 | d1 p50=27.1 p95=87.4 max=845.9 late=67% maxRun=19 | d2 p50=28.2 p95=100.2 max=1013.5 late=68% maxRun=10
load150%                 shared  threads=4 | d0 p50=63.9 p95=118.3 max=290.6 late=96% maxRun=41 | d1 p50=64.4 p95=117.5 max=866.0 late=97% maxRun=55 | d2 p50=65.1 p95=121.9 max=727.3 late=98% maxRun=55
load200%                 perDim  threads=4 | d0 p50=9.5 p95=82.0 max=432.1 late=48% maxRun=11 | d1 p50=11.7 p95=114.6 max=440.0 late=52% maxRun=8 | d2 p50=26.1 p95=181.9 max=1001.6 late=66% maxRun=19
load200%                 shared  threads=4 | d0 p50=94.0 p95=138.0 max=971.4 late=100% maxRun=55 | d1 p50=94.6 p95=134.7 max=274.9 late=100% maxRun=55 | d2 p50=93.8 p95=144.7 max=667.6 late=100% maxRun=55
asym heavy=40ms x4       perDim  threads=4 | d1 p50=-0.0 p95=12.7 max=17.7 late=7% maxRun=1 | d2 p50=-0.0 p95=4.1 max=52.6 late=2% maxRun=1
asym heavy=40ms x4       shared  threads=4 | d1 p50=0.1 p95=18.0 max=33.4 late=18% maxRun=2 | d2 p50=0.8 p95=27.7 max=140.4 late=28% maxRun=3
asym heavy=40ms x12      perDim  threads=4 | d1 p50=0.0 p95=12.9 max=28.7 late=6% maxRun=1 | d2 p50=0.0 p95=10.2 max=29.4 late=6% maxRun=1
asym heavy=40ms x12      shared  threads=4 | d1 p50=53.4 p95=99.0 max=204.4 late=83% maxRun=34 | d2 p50=56.7 p95=103.9 max=271.1 late=90% maxRun=21
asym heavy=60ms x4       perDim  threads=4 | d1 p50=0.0 p95=12.7 max=39.6 late=5% maxRun=1 | d2 p50=0.0 p95=5.4 max=129.6 late=2% maxRun=1
asym heavy=60ms x4       shared  threads=4 | d1 p50=0.5 p95=36.6 max=54.0 late=26% maxRun=1 | d2 p50=0.0 p95=54.9 max=57.9 late=31% maxRun=2
asym heavy=60ms x12      perDim  threads=4 | d1 p50=-0.0 p95=32.9 max=78.0 late=16% maxRun=1 | d2 p50=-0.0 p95=17.3 max=34.3 late=8% maxRun=3
asym heavy=60ms x12      shared  threads=4 | d1 p50=102.0 p95=143.4 max=186.0 late=100% maxRun=55 | d2 p50=103.3 p95=146.0 max=162.1 late=100% maxRun=55
asym heavy=80ms x4       perDim  threads=4 | d1 p50=0.0 p95=7.5 max=16.5 late=5% maxRun=1 | d2 p50=0.0 p95=3.8 max=15.9 late=2% maxRun=1
asym heavy=80ms x4       shared  threads=4 | d1 p50=0.3 p95=53.2 max=62.6 late=16% maxRun=1 | d2 p50=1.0 p95=47.7 max=73.0 late=20% maxRun=2
asym heavy=80ms x12      perDim  threads=4 | d1 p50=0.0 p95=4.1 max=22.4 late=4% maxRun=1 | d2 p50=-0.0 p95=9.4 max=25.1 late=5% maxRun=1
asym heavy=80ms x12      shared  threads=4 | d1 p50=152.0 p95=197.6 max=209.0 late=100% maxRun=55 | d2 p50=150.4 p95=197.5 max=206.0 late=100% maxRun=55
N=3 th=2                 perDim  threads=2 | d0 p50=50.0 p95=95.9 max=294.0 late=93% maxRun=26 | d1 p50=2.1 p95=54.1 max=78.2 late=33% maxRun=6 | d2 p50=6.5 p95=51.0 max=73.1 late=44% maxRun=7
N=3 th=2                 shared  threads=2 | d0 p50=123.0 p95=180.6 max=222.2 late=100% maxRun=55 | d1 p50=108.5 p95=145.2 max=201.0 late=100% maxRun=55 | d2 p50=109.3 p95=140.4 max=177.6 late=100% maxRun=55
N=3 th=4                 perDim  threads=4 | d0 p50=0.0 p95=59.5 max=241.4 late=20% maxRun=4 | d1 p50=0.0 p95=127.1 max=401.9 late=26% maxRun=7 | d2 p50=0.0 p95=45.9 max=60.7 late=5% maxRun=1
N=3 th=4                 shared  threads=4 | d0 p50=59.4 p95=107.8 max=329.3 late=98% maxRun=52 | d1 p50=32.7 p95=70.9 max=127.8 late=83% maxRun=22 | d2 p50=30.1 p95=69.7 max=187.2 late=82% maxRun=21
N=3 th=8                 perDim  threads=8 | d0 p50=44.1 p95=203.7 max=751.9 late=55% maxRun=9 | d1 p50=0.0 p95=54.2 max=354.2 late=10% maxRun=2 | d2 p50=1.7 p95=103.9 max=231.9 late=33% maxRun=8
N=3 th=8                 shared  threads=8 | d0 p50=9.1 p95=127.0 max=463.4 late=50% maxRun=15 | d1 p50=2.2 p95=102.9 max=238.1 late=27% maxRun=6 | d2 p50=1.8 p95=83.4 max=384.4 late=25% maxRun=4
N=6 th=2                 perDim  threads=2 | d0 p50=44.0 p95=207.9 max=397.3 late=80% maxRun=19 | d1 p50=46.4 p95=204.4 max=450.0 late=77% maxRun=22 | d2 p50=29.6 p95=241.5 max=576.3 late=67% maxRun=18 | d3 p50=35.7 p95=225.6 max=1471.9 late=70% maxRun=22 | d4 p50=31.9 p95=202.0 max=410.0 late=70% maxRun=7 | d5 p50=23.5 p95=161.4 max=546.0 late=64% maxRun=9
N=6 th=2                 shared  threads=2 | d0 p50=113.6 p95=147.7 max=176.0 late=100% maxRun=55 | d1 p50=110.8 p95=141.3 max=182.2 late=100% maxRun=55 | d2 p50=110.9 p95=141.4 max=182.0 late=100% maxRun=55 | d3 p50=110.8 p95=143.3 max=158.2 late=100% maxRun=55 | d4 p50=110.6 p95=143.0 max=166.1 late=100% maxRun=55 | d5 p50=110.9 p95=144.6 max=154.9 late=100% maxRun=55
N=6 th=4                 perDim  threads=4 | d0 p50=6.5 p95=87.7 max=149.7 late=45% maxRun=5 | d1 p50=2.0 p95=68.6 max=108.0 late=30% maxRun=8 | d2 p50=3.6 p95=90.5 max=129.8 late=40% maxRun=6 | d3 p50=3.9 p95=80.6 max=157.5 late=40% maxRun=6 | d4 p50=1.9 p95=83.6 max=348.1 late=29% maxRun=4 | d5 p50=1.9 p95=97.9 max=138.6 late=30% maxRun=4
N=6 th=4                 shared  threads=4 | d0 p50=52.0 p95=89.9 max=117.0 late=95% maxRun=55 | d1 p50=39.1 p95=71.0 max=101.8 late=91% maxRun=38 | d2 p50=35.6 p95=63.1 max=82.6 late=93% maxRun=33 | d3 p50=35.5 p95=68.9 max=84.8 late=93% maxRun=30 | d4 p50=36.2 p95=64.7 max=90.2 late=90% maxRun=18 | d5 p50=36.0 p95=63.8 max=95.3 late=95% maxRun=27
N=6 th=8                 perDim  threads=8 | d0 p50=1.9 p95=90.6 max=144.9 late=32% maxRun=6 | d1 p50=1.2 p95=74.6 max=120.0 late=28% maxRun=5 | d2 p50=0.1 p95=66.2 max=133.9 late=30% maxRun=5 | d3 p50=0.1 p95=58.4 max=284.0 late=25% maxRun=4 | d4 p50=2.1 p95=91.0 max=176.2 late=38% maxRun=5 | d5 p50=0.5 p95=86.1 max=158.0 late=32% maxRun=4
N=6 th=8                 shared  threads=8 | d0 p50=8.7 p95=83.1 max=130.9 late=47% maxRun=19 | d1 p50=3.3 p95=80.8 max=106.2 late=35% maxRun=8 | d2 p50=4.6 p95=71.7 max=263.5 late=37% maxRun=9 | d3 p50=4.6 p95=80.2 max=104.8 late=39% maxRun=14 | d4 p50=4.6 p95=70.5 max=150.0 late=38% maxRun=15 | d5 p50=3.4 p95=74.0 max=103.3 late=37% maxRun=10
N=12 th=2                perDim  threads=2 | d0 p50=52.2 p95=180.3 max=379.9 late=79% maxRun=22 | d1 p50=43.5 p95=154.6 max=297.6 late=74% maxRun=16 | d2 p50=31.8 p95=134.0 max=354.0 late=67% maxRun=18 | d3 p50=55.8 p95=191.2 max=803.6 late=78% maxRun=25 | d4 p50=42.1 p95=206.0 max=310.7 late=67% maxRun=23 | d5 p50=41.4 p95=230.0 max=548.6 late=69% maxRun=17 | d6 p50=66.1 p95=243.7 max=810.0 late=79% maxRun=22 | d7 p50=48.4 p95=201.5 max=371.6 late=75% maxRun=13 | d8 p50=30.9 p95=203.2 max=306.9 late=70% maxRun=12 | d9 p50=53.6 p95=208.9 max=455.7 late=81% maxRun=30 | d10 p50=51.3 p95=245.3 max=427.9 late=79% maxRun=24 | d11 p50=38.3 p95=191.2 max=325.6 late=60% maxRun=12
N=12 th=2                shared  threads=2 | d0 p50=104.8 p95=136.4 max=156.9 late=100% maxRun=55 | d1 p50=100.8 p95=136.4 max=156.9 late=100% maxRun=55 | d2 p50=102.5 p95=135.1 max=156.2 late=100% maxRun=55 | d3 p50=102.5 p95=131.3 max=159.6 late=100% maxRun=55 | d4 p50=102.5 p95=131.3 max=148.9 late=100% maxRun=55 | d5 p50=102.8 p95=131.3 max=151.8 late=100% maxRun=55 | d6 p50=101.9 p95=131.3 max=142.9 late=100% maxRun=55 | d7 p50=101.1 p95=132.0 max=150.5 late=100% maxRun=55 | d8 p50=100.8 p95=132.4 max=152.9 late=100% maxRun=55 | d9 p50=100.4 p95=132.4 max=153.0 late=100% maxRun=55 | d10 p50=100.3 p95=132.7 max=153.0 late=100% maxRun=55 | d11 p50=100.4 p95=133.3 max=150.5 late=100% maxRun=55
N=12 th=4                perDim  threads=4 | d0 p50=59.5 p95=160.5 max=281.5 late=83% maxRun=29 | d1 p50=50.1 p95=174.7 max=310.3 late=77% maxRun=22 | d2 p50=40.3 p95=154.0 max=221.9 late=70% maxRun=14 | d3 p50=55.1 p95=182.2 max=270.0 late=77% maxRun=25 | d4 p50=46.7 p95=178.5 max=269.1 late=69% maxRun=10 | d5 p50=52.1 p95=186.2 max=252.2 late=72% maxRun=23 | d6 p50=46.0 p95=198.3 max=345.6 late=70% maxRun=13 | d7 p50=46.3 p95=174.0 max=442.1 late=73% maxRun=14 | d8 p50=50.0 p95=162.1 max=250.0 late=71% maxRun=11 | d9 p50=50.0 p95=182.7 max=254.7 late=73% maxRun=16 | d10 p50=62.5 p95=161.9 max=266.8 late=81% maxRun=24 | d11 p50=50.3 p95=175.9 max=226.0 late=75% maxRun=20
N=12 th=4                shared  threads=4 | d0 p50=61.9 p95=94.0 max=124.7 late=97% maxRun=55 | d1 p50=53.9 p95=88.2 max=110.3 late=97% maxRun=55 | d2 p50=49.8 p95=82.1 max=101.3 late=96% maxRun=45 | d3 p50=50.5 p95=85.1 max=104.0 late=94% maxRun=44 | d4 p50=53.6 p95=84.4 max=109.5 late=97% maxRun=46 | d5 p50=53.7 p95=92.9 max=108.0 late=96% maxRun=54 | d6 p50=54.1 p95=87.4 max=106.4 late=96% maxRun=54 | d7 p50=54.0 p95=88.6 max=106.8 late=98% maxRun=50 | d8 p50=53.2 p95=85.5 max=103.7 late=95% maxRun=31 | d9 p50=54.1 p95=90.8 max=104.6 late=98% maxRun=55 | d10 p50=53.3 p95=89.2 max=110.7 late=97% maxRun=55 | d11 p50=54.0 p95=89.0 max=111.5 late=95% maxRun=31
N=12 th=8                perDim  threads=8 | d0 p50=65.7 p95=244.2 max=795.2 late=75% maxRun=17 | d1 p50=63.8 p95=196.6 max=719.4 late=79% maxRun=19 | d2 p50=60.2 p95=178.0 max=534.0 late=76% maxRun=23 | d3 p50=69.5 p95=178.0 max=406.1 late=78% maxRun=25 | d4 p50=51.8 p95=192.4 max=313.4 late=73% maxRun=19 | d5 p50=35.7 p95=190.1 max=495.0 late=64% maxRun=10 | d6 p50=62.0 p95=207.0 max=693.3 late=80% maxRun=21 | d7 p50=65.1 p95=196.2 max=677.9 late=75% maxRun=15 | d8 p50=66.7 p95=206.1 max=347.8 late=86% maxRun=34 | d9 p50=74.1 p95=205.3 max=583.7 late=87% maxRun=30 | d10 p50=69.8 p95=181.9 max=303.7 late=80% maxRun=27 | d11 p50=47.8 p95=188.1 max=394.0 late=72% maxRun=22
N=12 th=8                shared  threads=8 | d0 p50=67.9 p95=114.9 max=234.8 late=97% maxRun=55 | d1 p50=60.3 p95=115.9 max=158.2 late=91% maxRun=30 | d2 p50=63.6 p95=119.7 max=271.5 late=96% maxRun=45 | d3 p50=64.7 p95=114.0 max=170.4 late=95% maxRun=30 | d4 p50=62.2 p95=123.1 max=231.7 late=96% maxRun=55 | d5 p50=59.6 p95=115.3 max=289.9 late=91% maxRun=34 | d6 p50=68.0 p95=119.8 max=186.3 late=95% maxRun=55 | d7 p50=62.0 p95=111.8 max=901.9 late=95% maxRun=39 | d8 p50=66.0 p95=114.0 max=157.7 late=97% maxRun=52 | d9 p50=67.6 p95=110.6 max=171.9 late=95% maxRun=50 | d10 p50=61.9 p95=116.2 max=171.3 late=95% maxRun=37 | d11 p50=68.5 p95=109.1 max=614.3 late=96% maxRun=35
```

## 해석
1. **합계 부하 (시나리오 1)**: 두 번 돌려 봤고(첫 실행은 gradle 결과 파일 오류로 중간에 끊겨 표에는 두 번째 실행만 실음) **같은 조건의 결과가 크게 다르다**
   (150% 현행 지연 틱: 1회차 12~21%, 2회차 61~68%). 즉 균등 과부하에서 현행/공유의 우열은 이 샌드박스 노이즈 안에 있다.
   그나마 일관된 경향: 공유 큐는 지연이 **균등하고 상시적**(두 번 모두 150%·200% 에서 지연 틱 96~100%, 세 차원 p50 이 거의 같음, 200% 에서 약 94ms),
   현행은 차원별로 들쭉날쭉하고 p50 이 더 낮다(2회차 150%: 20~28ms vs 64ms). 이유 추정(미검증): 현행은 차원 수 x 코어 수 스레드를 OS 가 선점 배분해 일부 region 이 앞서가고,
   공유는 모든 region 이 재동기되어 같이 밀린다. 균등 과부하에서 공유가 공정하게 다 같이 늦는 것은 설계 의도에 가깝다.
2. **비대칭 (시나리오 2, R3)**: 핵심 결과. 무거운 차원의 region 수가 코어 수(4)일 때는 가벼운 차원이 공유에서도 대체로 버틴다(지연 틱 16~31%, maxRun 1~2, p95 36~55ms).
   **region 수가 코어의 3배(12)이면 공유 큐에서 가벼운 차원이 완전히 굶는다**: heavy 60ms/80ms x12 에서 가벼운 차원 지연 틱 100%, maxRun 55(측정 전 구간),
   p50 100~150ms. heavy 40ms x12 도 76~90% 지연, maxRun 13~34. 같은 조건의 현행(차원별 풀)은 지연 틱 0~16%, maxRun 1~3, p95 3~33ms.
   => R3 가 실제로 재현된다. 1ms 짜리 작업이 무거운 차원 region 들에 계속 밀려 한 번도 제때 못 돈다(maxRun = 측정한 틱 수 전부).
   공유 큐는 "정책 없는 EDF" 를 그대로 채택하면 안 되고, 3.5-1 의 차원별 최소 보장이 4단계와 같은 커밋이어야 한다는 리뷰 결론을 수치로 뒷받침한다.
3. **차원 수 / threads (시나리오 3)**: threads=2 에서 공유는 N 과 무관하게 전 차원 100% 지연(maxRun=55), 현행은 N=3 에서 차원0 만 93%, 나머지 33~44% 로 부하가 무거운 쪽에 격리된다.
   N=3 th=8 에서는 공유가 현행보다 오히려 낫다(차원0 지연 50% vs 55%, 나머지 25~27% vs 10~33%, max 도 낮음) - 스레드가 충분하면 공유 큐가 코어를 더 잘 채운다.
   N=6·12 에서 threads=4 이하는 공유가 전 차원 90%+ 지연으로 균등 붕괴, 현행도 70~80% 로 나쁘다(차원 수 x threads 개의 스레드가 4코어에서 경쟁).
   현행은 차원 수가 늘면 스레드 수가 N x threads 로 늘어 OS 경합이 커지고, 공유는 스레드 수가 고정이라 대기열 지연이 커진다 - 서로 다른 방식의 열화.
4. **일반적 관찰**: 가벼운 차원(1ms)은 현행에서 무거운 차원이 얼마나 무겁든 거의 영향이 없다(CPU 경쟁이 있을 때만 약간). 이것이 지금 구조의 장점(격리)이다.

## 기아 임계값 제안 (3.5-1 의 "늦음 > 임계")
- 단위 틱 기준. 한 region 이 **연속 2틱 이상 늦음 > 10ms** 이거나 한 번이라도 **늦음 > 1틱(50ms)** 이면 "기아 후보"로 본다.
  근거: 현행 정상 범위(가벼운 차원)는 지연 틱 0~5%, maxRun 1 이고 단발이다. 과부하 비대칭의 공유 큐에서는 maxRun 이 13~55 로 크게 갈린다. 연속 2틱은 정상 노이즈(maxRun 1)와 기아(>=13)를 모두 구분하는 가장 낮은 값이다.
- 선택기: `(차원 늦음 > 50ms ? 0 : 1, nextTickNanos)` 를 시작점으로 하고, 한 차원이 워커 점유 한도를 넘지 못하게 **차원당 동시 점유 상한 = max(1, threads - 1)** 도 후보(가벼운 차원에 항상 워커 하나를 남긴다).
  asym x12 에서 가벼운 차원은 region 2개 x 1ms 라 워커 1개면 충분하다.
- aging 파라미터 후보(튜닝 대상, 이번 측정으로 확정 아님): `agingThreshold = 1틱(50ms)`, `agingBoost = 늦음/틱 만큼 데드라인을 앞당김(상한 4틱)`, `reservedWorkers = 1`.
  재현 시나리오: asym heavy=60/80ms x12 (현행 p95 4~33ms 이하 유지가 합격선), 균등 150%(현행 대비 p95 악화 없음). 합격선 미달이면 채택 보류(리뷰 7장 2항).
- 틱을 건너뛰지 않는다는 원칙(3.5-2)은 유지: aging 은 순서만 바꾼다.

## 남은 일
- 정책 구현 후 같은 시나리오·같은 코어 수로 재측정(반복 5회 이상, 중앙값·최악 보고).
- 실제 B안 선택기로 근사를 대체. 현재 "공유"는 한 월드 근사라 잠금 경합이 실제와 다르다.
- 코어 수 8 이상/윈도 환경에서의 재측정(미측정, 가정).
