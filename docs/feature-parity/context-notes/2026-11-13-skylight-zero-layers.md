## 2026-11-13 모두 0 인 하늘빛 층 저장 (T-012 후속 R7)

**결론: 문제 맞음(좁은 장면), 고침.** Furnace 는 모두 0 인 하늘빛 층을 저장에서 뺐다. 바닐라 1.21.1 은 빠진 하늘빛 층을 늘 0 으로 보지 않는다. 그래서 바닐라가 Furnace 월드를 열면 어떤 어두운 곳이 하늘빛 15 가 됐다. 이제 하늘이 있는 열(어느 층이든 0 이 아닌 하늘빛)은 모두 0 인 하늘빛 층도 쓴다. 블록빛과 하늘 없는 열(네더·엔드)은 예전처럼 뺀다.

### 바닐라 규칙 (extracted_minecraft_data client1.21.1 원문)
- 쓰기 `ChunkSerializer.write`: 층이 `null` 이 아니고 `!isEmpty()` 면 쓴다. `DataLayer.isEmpty()` = `data == null && defaultValue == 0`. 그래서 바닐라도 **한 번도 값을 쓰지 않은 0 층은 빼고**, 빛이 닿았다 0 이 된 층은 0 배열(2048바이트)로 쓴다. 바닐라 월드 땅속 섹션에도 SkyLight 가 없는 일이 흔하다.
- 읽기 `ChunkSerializer.read`: 있는 층만 `queueSectionData`. 빠진 층은 `SkyLightSectionStorage.createDataLayer` 가 만든다. 섹션을 그 열의 지금 맨 위(`topSections`)보다 **아래에** 새로 만들면 가장 가까운 위쪽 층의 맨 아래 줄을 16번 되풀이한다(`repeatFirstLayer`). 맨 위 이상이면 0(빛 켜기 전) 또는 15(빛 켠 뒤)다. `getLightValue` 도 층이 없으면 위쪽 층의 맨 아래 줄을 쓴다.
- `ThreadedLevelLightEngine.initializeLight` 는 비지 않은 섹션을 아래에서 위로 등록한다. 그래서 보통의 땅속 0 층은 맨 위에 만들어져 0 이 된다. 자연 지형에서는 문제가 거의 드러나지 않는 이유다.
- 드러나는 꼴: 섹션 S 가 모두 공기이고 S 바로 아래 섹션도 공기라서, S 가 위 섹션(S+1, 비지 않음)을 등록할 때 이웃으로 **나중에** 만들어진다. 이때 S+1 의 맨 아래 줄이 밝으면 S 전체가 그 값이 된다. 반 블록 지붕(맨 아래 줄은 하늘빛 15, 아래 면이 막혀 그 아래는 0) 밑의 밀폐된 어두운 방이 그렇다.

### 바닐라 서버 jar 로 확인 (시드 12345)
- 하네스: main 브랜치 `tools/vanilla-data/minecraft_server.1.21.1.jar`(번들러, 라이브러리 포함)를 오프라인으로 띄운다. 빛은 데이터 팩 술어 `location_check` + `light.light.min`(=`getMaxLocalRawBrightness`, 정오라 하늘빛 그대로)을 `execute positioned … if predicate` 로 묻는다. 스크립트·기록은 공유 폴더 `furnace/skylight-zero-layers/`.
- 자연 지형 3×3 청크, 7,533곳(빛 ≥1·≥8·15): 바닐라 생성 → 바닐라 다시 열기 = 완전히 같음. Furnace 생성 → 바닐라로 열기에서 밝아진 곳은 0. 다른 5곳은 모두 Furnace 쪽이 어둡고, 고치기 전과 뒤에 같은 5곳이다. 이 3×3 청크는 블록부터 바닐라와 1~2천 칸 다르다(예: 점토 ↔ 심층암 다이아몬드 광석, 발광 이끼 2→1). 빛 저장과는 상관없다(별도 확인 거리).
- 장면: `fill 319 191 319 336 224 336 stone hollow` 뒤 `fill 320 224 320 335 224 335 smooth_stone_slab[type=bottom]`. 청크 (20,20) 섹션 12·13 이 밀폐된 어두운 방이 되고, 섹션 14 맨 아래 줄은 반 블록이라 15 다.
  - 바닐라가 만들고 바닐라로 다시 열기: 방 안 하늘빛 0. 바닐라는 섹션 12·13 을 0 배열로 썼다.
  - 고치기 전 Furnace 가 만들고 바닐라로 열기: 방 안 21곳(그리고 지붕·위 6곳)이 모두 15. Furnace 는 섹션 12·13 SkyLight 를 뺐다.
  - 고친 뒤: 54개 판정이 바닐라와 하나하나 같다.
- 크기: 고친 뒤 r.0.0 의 같은 청크 23개가 압축 뒤 평균 +101바이트(+1.07%)다. 0 배열은 거의 다 압축된다.

### T-012 표시와의 관계
- `furnace:light_version` 은 올리지 않는다. Furnace 읽기는 빠진 층을 0 으로 보고, 새로 쓰는 0 층도 0 이라 뜻이 같다. 옛 Furnace 청크도 그대로 믿어도 된다.
- 고치기 전에 저장된 Furnace 청크는 다시 저장될 때까지 옛 꼴이다. 바닐라로 열 때만 위 장면에서 차이가 난다. Furnace 자신에게는 영향이 없다.

### 시험
`StoredLightCodecTest.zeroSkyLayer_underLitSlabRow_isWritten_soVanillaKeepsItDark`: 위 바닐라 읽기 규칙을 본뜬 모형으로 섹션마다 비교한다. 고치기 전에는 실패하는 것을 확인했다. `columnWithoutSky_writesNoSkyLight` 는 하늘 없는 열에 하늘빛을 쓰지 않는지 본다.
