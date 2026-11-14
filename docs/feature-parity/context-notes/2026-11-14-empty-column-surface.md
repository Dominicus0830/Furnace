# 빈 열 WORLD_SURFACE 하이트맵 갱신 (2026-11-14)

- 증상: `ChunkColumn.updateSurface`/`updateSurfaceWg` 가 `surface[열]==0` 이면 "프라임 전"이라며 갱신을 건너뛰었다. 하이트맵을 만든 뒤 열 전체가 공기인 경우도 0 이라, 공허 월드·스카이블록·엔드 공허·바닥까지 파낸 열에 블록을 놓아도 WORLD_SURFACE 가 0(= `surfaceBlockY` −65)으로 남았다. 바닐라 `Heightmap.update` 는 이 경우도 갱신한다(`y <= first-2` 가 아니면 `!air` 일 때 `y+1`).
- 수정: 두 가드를 제거. 미프라임 열의 값은 이후 `loadGenerated`/`rebuildHeightmapsAfterSurface` 가 덮어쓰므로 안전하다. `wgOceanFloor` 는 처음부터 `UNSET_HEIGHT` 센티넬이라 같은 문제가 없었다.
- 영향: 청크 패킷·저장 WORLD_SURFACE, `Region.surfaceHeight`(자연 스폰 높이), `SpawnLocation`, `/execute positioned over world_surface`, 지도 렌더. 월드젠 중 `updateSurfaceWg` 는 비공기만 놓는 흐름에서 값이 이미 > 0 이라 결과 동일(월드젠 골든·IdentityKit 시험 통과로 확인).
- 시험: `ChunkColumnTest.worldSurface_updatesOnEmptyColumn*`·`worldSurfaceWg_updatesOnEmptyColumnBeforeFreeze`.
- N2b-0 의 바닐라 jar 정답기(`VanillaSectionOracleTest`)는 이 브랜치 기준에 아직 병합돼 있지 않아 `knownEmptyColumn` 허용 제거는 그 시험이 병합될 때 함께 해야 한다.
