# SeagrassFeature·KelpFeature vanilla 버그 수정

## 작업 요약
SeagrassFeature canSurvive 완전 제거 + KelpFeature loop/age 수정으로 vanilla 동작 일치

## 문제 / 목적
FeaturePipelineParityTest에서 cx=0 60, cx=-5 89, cx=7 663 mismatch. seagrass·kelp feature 구현이 vanilla와 달라 블록 배치 결과 상이.

## 해결 방법 / 구현 내용

### SeagrassFeature — canSurvive 완전 제거
vanilla SeagrassFeature.place()에 canSurvive 체크 없음. 단순히:
1. oceanFloor+1 위치에 water인지 확인
2. nextDouble() → isTall 결정
3. isTall이면 y+1도 water 확인 후 배치(fail이면 배치 없이 return true)
4. short이면 그냥 배치

이전 Furnace 구현에 canSurvive(floor != 0 && !isWater && != magma && != seagrass)가 있었음 → 배치 누락 버그.

### KelpFeature — 3가지 수정 (이전 세션 + 이번 확인)
- `offset <= height` → `offset < height` (height+1회 → height회)
- kelpTopStates age 20-23 → age 0-25 (26개)
- 복잡한 early-return → vanilla-style "water AND canGrowInto → place or continue"

## 핵심 코드

```java
// SeagrassFeature.java — vanilla 동작
@Override
public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
    int placeX = x + random.nextInt(8) - random.nextInt(8);
    int placeZ = z + random.nextInt(8) - random.nextInt(8);
    int placeY = view.getOceanFloorY(placeX, placeZ) + 1;
    if (placeY < ChunkColumn.MIN_Y) return false;
    if (!isWater(view.getBlock(placeX, placeY, placeZ))) return false;
    boolean isTall = random.nextDouble() < tallProbability;  // nextDouble BEFORE 배치
    if (isTall) {
        if (isWater(view.getBlock(placeX, placeY + 1, placeZ))) {
            view.setBlock(placeX, placeY, placeZ, tallLowerStateId);
            view.setBlock(placeX, placeY + 1, placeZ, tallUpperStateId);
        }
        // tall fail → 배치 없이 return true (not return false!)
    } else {
        view.setBlock(placeX, placeY, placeZ, seagrassStateId);
    }
    return true;
}

// isWater: 80-95 (source+flowing 모두 포함)
private static boolean isWater(int stateId) {
    return stateId >= 80 && stateId <= 95;
}
```

```java
// KelpFeature.java — offset < height (not <=)
int height = random.nextInt(10) + 1;
for (int offset = 0; offset < height; offset++) {  // vanilla: k iterations
    if (isSourceWater(current) && canGrowInto(below)) {
        if (offset == height - 1 || !isSourceWater(above)) {
            view.setBlock(placeX, currentY, placeZ, kelpTopState(random));
        } else {
            view.setBlock(placeX, currentY, placeZ, kelpPlantStateId);
        }
        placed = true;
    }
}

// BiomeFeatureData: kelpTopStates age 0-25
int[] kelpTopStates = new int[26];
for (int age = 0; age <= 25; age++) {
    kelpTopStates[age] = registry.stateIdOrAir("minecraft:kelp", Map.of("age", Integer.toString(age)));
}
```

## 분석 도구: oracle binary 파싱 (Python)

```python
import struct
MIN_Y = -64
path = 'featurefull_1234_0_0.bin'
with open(path,'rb') as f:
    data = f.read()
vals = struct.unpack('<' + 'H' * (len(data)//2), data)

def lookup(lx, y, lz):
    idx = 10 + (y - MIN_Y) * 256 + lz * 16 + lx  # +10 = 20 bytes header
    return vals[idx]
```
loop 순서: y-outer, lz-middle, lx-inner. 2 bytes little-endian per block. header 20 bytes skip 필수.

## 관련 파일
[[레이어-월드젠.md]]

## 태그
#Furnace #feature-pipeline #BugFix
