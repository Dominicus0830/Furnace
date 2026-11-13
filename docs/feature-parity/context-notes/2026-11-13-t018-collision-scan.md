# T-018 (P-O05-07) 블록 충돌 수집 재작성 — 2026-11-13

O05 보고서 P-O05-07, TASK 표 T-018. PR #291(엔티티·보더 충돌 모양) 위에서. 결과(관측 동작)는 바꾸지 않는 최적화다.

## 바꾼 것
- `ChunkLevel.scanCollisions(box, entity, CollisionSink)`: 바닐라 `BlockCollisions`(Cursor3D — x 가 가장 빨리, y, z) 순서 그대로 칸을 돈다.
  - 청크 열은 z 청크마다 처음 닿을 때 한 번 찾고(예전: 같은 행을 다시 돌 때마다 `chunk()` 다시), 구역(섹션)은 행마다 한 번. `hasOnlyAir()` 구역은 행째 건너뛴다.
  - 상태별 표 `ChunkLevel.CollisionTable`: 블록 동작이 `getCollisionShape`·`getEntityCollisionShape` 를 덮어쓰지 않으면 모양은 `BlockInfo.collisionShape(state)` 하나로 정해진다.
    빈 모양(공기·동굴 공기·빈 공간 공기·풀·물·횃불 …)은 칸을 읽자마자 건너뛰고, 아니면 미리 든 모양을 쓴다. 덮어쓴 동작은 지금 둘(움직이는 피스톤, 가루눈) —
    리플렉션으로 고르므로 새 동작이 덮어쓰면 자동으로 "문맥" 쪽으로 간다(`CollisionScanParityTest.contextualBehaviorsAreDynamic` 이 개수를 지킨다).
  - 칸 객체 `Pos`·옮긴 모양(`Shape.move`)은 문맥 동작에서만 만든다. 받는 쪽에는 (칸, 옮기지 않은 모양)을 넘긴다.
- `Colliders`: `Entity.collectColliders` 의 목록을 (모양, 옮길 거리)로 든다. `Shape.collide(axis, box, d, ex, ey, ez)`·`exactYCoords(ex, ey, ez)` 는
  `move(ex, ey, ez)` 한 모양의 값과 비트 단위로 같다 — `move` 가 세 값이 모두 0 이면 자기 자신을 돌려주는 것(부호 있는 0)까지 따라 한다.
- 오르기 후보: `TreeSet<Float>` → `float[]` 정렬 + `Float.compare` 로 같은 값 빼기(같은 순서·같은 −0.0/0.0 구분).
- `findSupportingBlock`·`noBlockCollision` 은 칸 객체 없이.

## 증명
- 차분(IdentityKit `Differential`): 옛 수집기 사본 `block/LegacyBlockCollisions`·`entity/LegacyEntityCollide`(7b2d6b77) 대 새 구현,
  `blockCollisions` 목록(상자 값 비트·오르기 격자), `noBlockCollision`, `findSupportingBlock`, `collideMove`, `collideBoundingBox` 각 2만 건(고정 씨앗).
  무작위 블록(모양 블록 57종 상태 전부 + 아무 상태 + 세 가지 공기), 움직이는 피스톤 블록 엔티티, 배·셜커 몸, 질의 근처 보더, 로드 안 된 청크 옆, 공기뿐인 구역,
  정수 경계 ±1e-7·1/16 격자 상자, 얇은 상자. 변이 점검 3개(목록 순서 뒤집기, 가장 가까운 칸 대신 첫 칸, 오르기 빼기).
  생성기 범위 점검(`generatorCoversInterestingCases`): 2만 건 중 충돌 있음 11484, 문맥 블록 292, 엔티티 몸 158, 보더 1595, 오르기로 결과가 바뀜 737.
  수동: `-Dfurnace.identity.scale=10 -Dfurnace.identity.randomSeeds=2` 로 종류마다 60만 건 넘게 불일치 0.
- 장면 금 파일 `entity-collision-scene`(새 JVM, 씨앗 켜짐): 장애물 18종·동굴 공기 구덩이·배·셜커·보더가 있는 평지에서 몹 150마리 300틱, 20틱마다 엔티티 상태 해시.
  금 파일은 **재작성 전 커밋(7b2d6b77)에서** 만들었고 새 코드가 그대로 통과한다.

- 엔티티 충돌 후보(`entityCollisions`)는 몹 등(단단한 엔티티만 보는 쪽)에서 결과 목록을 찾았을 때만 만든다(예전: 호출마다 `ArrayList` 둘).
  같은 순서·조건 — 옛 사본(`LegacyBlockCollisions.entityCollisions`) 차분 `t018-entity-collisions`.
- 바닐라 jar 대조: PR #291 하네스(`furnace/reviews/o05-c1-pr291-repro/`)를 새 코드로 다시 돌렸다(옮긴 `collideWithShapes`·오르기 후보가 `Colliders` 를 거친다).
  `1 300000`, `4242 300000 29999970`, `99 300000 0 flat` 세 가지 6 항목 모두 불일치 0.

## 측정 (4코어 컨테이너, 옛 = 7b2d6b77 worktree, 새 = 이 브랜치, 교대 실행, `-Dfurnace.test.seeded=true`)
- 충돌 마이크로 벤치 `CollisionScanBench`(같은 JVM 에서 옛 사본과 교대, 예열 뒤 6회): 평지에서 걷는 몹(collide + 디딘 칸) 2.8~3.3µs → 1.26~1.53µs(약 −55%),
  모양 블록 밭 무작위 질의 7.0~8.4µs → 4.7~5.4µs(약 −30%).
- `RegionTickBench` 몹 1000·600틱:
  - 노이즈 지형: 15.18 / 15.21ms → 14.58 / 14.05ms(약 −5.5%), 틱당 할당 2.53 / 2.42MB → 2.26 / 2.13MB.
  - 평지: 18.67 / 19.95ms → 18.96 / 19.12ms(잡음 안), 할당 3.29 / 2.99MB → 2.56 / 2.59MB(약 −18%).
- 소 3000(`-Dfurnace.bench.types=minecraft:cow`, 400틱): 54.5 / 57.1ms → 55.5 / 49.3ms(잡음이 커서 판단 못 함), 할당 11.5 / 10.2MB → 6.9 / 7.6MB(약 −33%).
- `EntityPerfBench.manyMobsTickCost` 1000(시험 레벨, 평지): 13.4 / 11.7 / 12.3ms → 12.1 / 12.6 / 12.3ms — 차이 없음(평지 풀밭은 예전에도 `air` 빠른 길이 있었다).
- JFR(노이즈, 몹 1000): 블록 충돌 수집 포함 비율 12.4% → 8.1%. 예전에는 그 안의 `RegionLevel.chunk` → `Regionizer.owns` 가 9.1% 였는데
  (칸 행을 돌 때마다 청크를 다시 찾음) 새 수집기에서는 사라졌다. `Entity.collide` 포함 비율은 13.9% → 13.3% 로 덜 줄었다 — 남은 몫은 엔티티 후보 찾기·`collideWithShapes`.
- O05 가설(충돌 몫 −50%, 틱 −8%)보다 작다: 충돌 코드 자체는 30~55% 줄었지만 틱 전체에서는 노이즈 지형 −5% 안팎, 평지는 잡음 안.

## 손대지 않은 것(TASK 표의 나머지 생각)
- `collide` 와 `checkSupportingBlock` 이 같은 칸을 두 번 모으는 것(⑤): 발밑 상자(1e-6 두께)와 이동 상자가 다르고, 재사용하려면 덮는 범위 증명이 필요해 이번에는 뺐다.
- 변 칸(edges == 2)도 지금은 블록을 읽고 나서 움직이는 피스톤인지 본다(바닐라와 같은 판정). 구역 팔레트에 피스톤이 없으면 읽지 않는 길은 측정 뒤 판단.

## 독립 검토(PR #305, 공유 폴더 `furnace/reviews/t018-pr305.md`) R 항목 반영
| R | 내용 | 처리 |
|---|---|---|
| R1 | `Shape.exactYCoords(ex, ey, ez)` 가 `move` 의 "빈 모양이면 this" 를 안 따름 | 고침 — 빈 모양도 옮기지 않음(`collide` 6인자도 대칭으로) |
| R2 | `CollisionTable` 초기화가 상태마다 리플렉션(약 0.17초, 첫 충돌 질의 틱에 걸림) | 고침 — 블록 단위 + 동작 클래스 캐시, `FurnaceMain` 시작 때 `ChunkLevel.warmCollisionTable()` |
| R3 | scratch 열린 섹션의 `hasOnlyAir` 는 낡을 수 있음 | 고침 — `ChunkSection.scratchOpen()` 이면 건너뛰지 않고 칸을 읽는다(주석 포함) |
| R4 | 차분 생성기가 청크 열 셋 이상(열 배열 길)을 거의 안 만듦 | 고침 — 넓은 상자(x 폭 20~50) 경우, 범위 점검 `wide > 500`(2만 건 중 1400) |
| R5 | 금 장면이 문맥 충돌 길(가루눈·움직이는 피스톤)을 못 지킴 | 고침 — 새 장면 `entity-collision-scene-powder`(가루눈 밭·떨어지는 몹·토끼·움직이는 피스톤 칸), 금 파일은 7b2d6b77 에서 bless. 변이(DYNAMIC 칸 건너뛰기)로 이 장면이 실패함을 확인 |
| R6 | 바닐라 문맥 충돌 블록 중 Furnace 동작이 없는 것: 비계(위/아래·웅크림), 액체(스트라이더 `canStandOnFluid`) | 이 PR 범위 밖(기존 공백, 회귀 아님) — 별도 TASK 로 요청. 고치면 `contextualBehaviorsAreDynamic` 개수와 SHAPED 목록을 같이 고친다 |
