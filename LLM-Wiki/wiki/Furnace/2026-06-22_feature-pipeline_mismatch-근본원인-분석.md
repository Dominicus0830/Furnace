# FeaturePipelineParityTest mismatch 근본원인 분석

## 작업 요약
seagrass/kelp DIVERGE 원인 = noise+carver 차이 → neighbor chunk ocean floor 다름 → cascade

## 문제 / 목적
Feature 구현 수정 후에도 seagrass DIVERGE 지속. cx=0: 19→60, cx=-5: 24→89, cx=7: 477→663(kelp). 원인 규명 필요.

## 해결 방법 / 구현 내용

### 원인 체인

```
noise+carver 차이 (mismatchStart=5006)
    ↓
neighbor chunk 블록 상태 Furnace ≠ oracle
    ↓
seagrass place() 시 getOceanFloorY(neighbor chunk) 다름
    ↓
배치 위치 달라짐 (Furnace 46개 vs oracle 5개 in center chunk)
    ↓
seagrass 배치 후 ocean floor 상태 달라짐
    ↓
kelp place() 시 getOceanFloorY도 달라짐 → cascade mismatch
```

### 증거

**oracle binary에서 세아그라스 (cx=0)**:
- 총 5개 short seagrass (tall seagrass 없음)
- 위치: (3,35,15), (1,36,13), (1,37,8), (2,37,10), (4,38,9)
- 쌓이는 패턴 (y=35~38)

**Furnace 세아그라스 (cx=0)**:
- 총 46개 배치 (oracle의 9배)
- center chunk + neighbor chunk 모두 배치
- neighbor chunk ocean floor가 낮아서 더 많이 water hit

### 결론

seagrass/kelp feature 자체 구현은 vanilla와 일치. 남은 mismatch는 **noise+carver 레벨 차이**에서 기인. Feature 수준에서 수정 불가. noise+carver를 먼저 byte-exact로 만들어야 함.

### underwater_magma feature (미구현)

cx=0 baseline 19 mismatch 중 7개: oracle에 magma_block(y=0 근처), Furnace에 deepslate. underwater_magma feature type = `minecraft:underwater_magma`. BiomeFeatureData에 handler 없어서 NoopFeature 반환.

구현 시 19→12 baseline 감소 가능하지만 seagrass cascade 문제 해결 안 됨.

### 현재 mismatch 현황 (2026-06-22)
| chunk | baseline | seagrass | kelp | 최종 |
|-------|----------|----------|------|------|
| cx=0,cz=0 | 19 | +41 | - | 60 |
| cx=-5,cz=3 | 24 | +65 | - | 89 |
| cx=7,cz=-2 | 431 | +46 | +186 | 663 |

cx=7 baseline 431 = geode 차이가 큼. geode가 ocean floor 근처 블록 일부 변경 → kelp floor Y 차이.

## 관련 파일
[[2026-06-22_feature-pipeline_SeagrassFeature-KelpFeature-vanilla-bugfix.md]]
[[레이어-월드젠.md]]

## 태그
#Furnace #feature-pipeline #Design
