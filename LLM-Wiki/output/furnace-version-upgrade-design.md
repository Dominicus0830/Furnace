# Furnace 버전업 대비 설계 (1.21.1 → 이후)

작성: 2026-07-05. 목표: 버전업을 "코드 재작성"이 아니라 "데이터 교체 + parity 재검증"으로 만들기.

## 현재 자산 (이미 잘 돼 있는 것)

- 리소스가 버전 네임스페이스: `resources/blocks/1.21.1.txt`(+1.21.4.txt 존재), `resources/worldgen/1.21.1/`, registries, items.
- oracle parity 파이프라인: paperweight-userdev 고정 버전 → fixture 생성 → parity 테스트. 버전업 검증 절차가 이미 존재하는 셈.
- 디버깅 도구 재사용 가능: BLOCK_DEBUG 3x3 delta, per-draw RNG 트레이스, per-column write SET diff.

## 현재 부채 (버전업을 막는 것)

**P1 — raw state id 하드코딩: 11개 파일 78곳.**
ChunkColumn(27곳: 잎 범위·water plants·glow lichen 판정), TreeFeature(20곳: 잎 237~516, distance 인코딩, vine 6837~6868), SeagrassFeature(9), GlowLichenFeature(8: stateId=6996−flags 공식), KelpFeature(5), UnderwaterMagmaFeature(3), GeodeFeature(2) 등. state id 공간은 버전마다 밀림 → 전부 침묵 오작동. 이번 gridview 작업에서도 isVine 범위가 실제론 hanging_sign이었던 버그 전례.

**P2 — 패킷 id/포맷 산재.** ConnectionHandler 등에 1.21.1 프로토콜 상수. 1.21.2가 격변(청크·엔티티 패킷 다수 변경).

**P3 — 버전 상수 산재 가능성.** DataVersion, 프로토콜 번호, 월드 높이 등.

## 설계: 3층 대응

### 1층 — StateIds 코드젠 (핵심, 최우선)

`blockstates_<ver>.txt`를 입력으로 빌드타임 코드젠(Gradle task) → `StateIds.java` 상수 클래스 생성:

```java
// 자동 생성 예시 — 이름 기반으로 뽑아서 버전 독립
public final class StateIds {
    public static final int OAK_LEAVES_MIN = ...;   // "minecraft:oak_leaves" 첫 state
    public static final Range LEAVES_ALL = ...;      // 전체 잎 범위 (연속 보장은 검증으로)
    public static final int VINE_MIN = ..., VINE_MAX = ...;
    public static final int GLOW_LICHEN_BASE = ...;  // 6996 등 공식 기점
    ...
}
```

- 원칙: **소스코드에 3자리 이상 raw state id 금지** (lint 스크립트로 강제 — grep 기반이면 충분).
- property 인코딩 공식(예: glow_lichen flags, 잎 distance `(id-min)%28/4+1`)은 코드젠이 property 순서에서 **도출**하거나, 최소한 도출 결과를 assert하는 생성 테스트 포함. 바닐라 property 오름차순 규칙이 정본.
- 마이그레이션: 기존 78곳을 StateIds 참조로 치환. 기능 변화 0 — 기존 parity suite green이 그대로 회귀 증명. **버전업과 무관하게 지금 해도 순수 이득** (이번 isVine류 버그 재발 차단).

### 2층 — 프로토콜 버전 격리

- 패킷 id·포맷 상수를 `protocol/v1_21_1/` 한 곳으로 모음. 정본은 각 버전 vanilla datagen `packets.json` (paperweight 캐시 jar 버전 불일치 함정 — datagen 출력만 신뢰, m4c-3 교훈).
- packets.json → 패킷 id enum 코드젠까지 가면 이상적. 최소로는 "산재 금지, 한 파일" 원칙만 지켜도 버전업 diff가 한 파일로 수렴.
- 멀티버전 동시지원(ViaVersion류)은 **비목표**. 서버는 항상 단일 버전, 스위치는 컴파일 타임.

### 3층 — 버전업 실행 플레이북 (버전당 반복 절차)

1. paperweight-userdev를 새 버전으로 → oracle 컴파일 에러 = 바닐라 API 변경 목록 (공짜 diff).
2. datagen 재실행: blockstates_<ver>.txt, packets.json, registries, worldgen JSON 추출 → 리소스 폴더에 버전 추가.
3. StateIds·패킷 코드젠 재실행 (1·2층 덕에 자동).
4. oracle로 fixture 전량 재생성 → parity suite 실행 → **깨지는 테스트 = 바닐라 알고리즘이 그 버전에서 바뀐 지점 목록** (탐색 불필요, 실패가 곧 작업 목록).
5. 발산은 기존 3종 트레이스 기법으로 각개 격파.
6. DataVersion·Anvil 포맷 변경분 반영.

## 권장 순서

1. **지금 진행 중 작업 완료 후**: StateIds 코드젠 + 78곳 치환 (별도 task, 위험 낮음, parity로 검증 가능).
2. 패킷 상수 한 곳으로 모으기 (M5 전후, 패킷 작업할 때 겸사).
3. 실제 버전업은 1.21.2/3 건너뛰고 **1.21.4 직행** 권장 — 프로토콜 격변을 한 번에 흡수, blocks/1.21.4.txt 이미 확보돼 있음.

## 비목표

- 멀티버전 런타임 지원.
- 26.x 등 미래 메이저 선제 대응 — 구조만 지키면 그때 플레이북 재실행.
