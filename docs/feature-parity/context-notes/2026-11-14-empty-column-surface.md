# 빈 열 WORLD_SURFACE 하이트맵 갱신 (2026-11-14)

- 증상: `ChunkColumn.updateSurface`/`updateSurfaceWg` 가 `surface[열]==0` 이면 "프라임 전"이라며 갱신을 건너뛰었다. 하이트맵을 만든 뒤 열 전체가 공기인 경우도 0 이라, 공허 월드·스카이블록·엔드 공허·바닥까지 파낸 열에 블록을 놓아도 WORLD_SURFACE 가 0(= `surfaceBlockY` −65)으로 남았다. 바닐라 `Heightmap.update` 는 이 경우도 갱신한다(`y <= first-2` 가 아니면 `!air` 일 때 `y+1`).
- 수정: 두 가드를 제거. 미프라임 열의 값은 이후 `loadGenerated`/`rebuildHeightmapsAfterSurface` 가 덮어쓰므로 안전하다. `wgOceanFloor` 는 처음부터 `UNSET_HEIGHT` 센티넬이라 같은 문제가 없었다.
- 영향: 청크 패킷·저장 WORLD_SURFACE, `Region.surfaceHeight`(자연 스폰 높이), `SpawnLocation`, `/execute positioned over world_surface`, 지도 렌더.
- 월드젠 영향(독립 검토 확인): 기존 월드젠 골든·IdentityKit 은 노이즈+표면 단계의 블록만 해시해 이 변경을 구조적으로 못 잡는다. 전체 파이프라인(장식 포함, End 285청크) 차분에서 블록·WG·바다바닥·모션 해시는 기준과 동일하고, WORLD_SURFACE 만 End 외곽 섬 열(공허에서 시작해 장식이 비공기를 놓는 열) 281개가 바닐라와 같아졌다. WG 는 카버 직후 동결이라 불변.
- 시험: `EndOuterIslandSurfaceTest`(장식까지 간 End 외곽 청크 (64,-3)·(69,0) 의 WORLD_SURFACE = 블록 최상단, 기준에서 실패)·`ChunkColumnTest.worldSurface_updatesOnEmptyColumn*`·`worldSurfaceWg_updatesOnEmptyColumnBeforeFreeze`.
- N2b-0 정답기(`VanillaSectionOracleTest`, PR #355)의 `knownEmptyColumn` 허용(44칸)을 지워 네 하이트맵 엄격 비교로 바꿨다(같은 PR).
- 후속 후보(이 PR 밖): `SuperflatWorldSource` 의 `setSurface(x,z,column.length)` 는 이제 중복이고 맨 위가 공기인 프리셋에선 부정확할 수 있다(실행 확인 안 함). 디스크 로드·슈퍼플랫 청크는 `freezeWgHeightmaps()` 가 안 걸려 setBlock 마다 WG 미러를 갱신한다(성능, CSP 후보). 정답기를 늘릴 때(N2b-2) WORLD_SURFACE_WG 도 비교할 것.
