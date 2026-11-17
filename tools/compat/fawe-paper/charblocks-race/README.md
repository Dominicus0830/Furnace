# FAWE CharBlocks 겹친 첫 읽기 경쟁 재현

T-204 후속: `//fixblocks` 뒤 레드스톤 가루 판 한 칸(`-35,101,4` 의 `north`)이 실행마다 갈리던 원인. 분석은 `docs/feature-parity/context-notes/2026-11-17-t204-redstone-flaky.md`.

서버 없이 FAWE 2.16.2 의 진짜 `CharBlocks`(worldedit-core) 를 돌려, 같은 GET 층을 두 스레드가 동시에 처음 읽을 때 둘째가 공개된 배열을 제자리에서 다시 채우는 동안
읽는 쪽이 팔레트 번호를 보는지 센다. 첫 읽기 시간(서버 섹션 읽기)을 바꿔 가며 잰다.

```sh
D=$(mktemp -d); M=https://repo.maven.apache.org/maven2   # 429 가 나면 https://maven-central.storage-download.googleapis.com/maven2
curl -sSo $D/core.jar  $M/com/fastasyncworldedit/FastAsyncWorldEdit-Core/2.16.2/FastAsyncWorldEdit-Core-2.16.2.jar
curl -sSo $D/log4j.jar $M/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar
curl -sSo $D/guava.jar $M/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar
javac -cp $D/core.jar -d $D RaceHarness.java
java -cp $D/core.jar:$D/log4j.jar:$D/guava.jar:$D RaceHarness 5,20,50,140,300 3000
```

인자: 첫 읽기 시간(µs, 쉼표), 시도 수. FAWE jar 는 커밋하지 않는다. log4j 구현이 없다는 `StatusLogger` 한 줄은 무시해도 된다.
수치는 최악 장면(읽는 쪽이 1.5 ms 내내 그 칸을 읽음)의 상대 비교이지 실서버 확률이 아니다.

T-209(첫 섹션 읽기 가속) 뒤 Furnace 의 첫 읽기 시간은 `furnace-compat-paper` 벤치 `FirstDataReadBench` 로 잰다(묶음 읽기 섹션당 약 2 µs, 틱 직후 한 섹션 15~29 µs).
그 값을 이 도구의 첫 인자로 넣어 비율을 본다 — 결과는 `docs/feature-parity/context-notes/2026-11-17-t209-first-section-read.md`. 실서버 확정 실험
(`2026-11-17-t204-fawe-threads-confirm.md`)에서는 갈림이 FAWE 다중 스레드에서 나오고 Paper 도 같거나 더 자주 갈렸다 — 이 도구의 비율은 상대 비교로만 쓴다.
