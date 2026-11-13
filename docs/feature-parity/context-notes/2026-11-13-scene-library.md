# 공용 장면 라이브러리 (T-003, 2026-11-13)

근거: `docs/roadmap/optimization-tasks.md` T-003, `furnace/reviews/opt-review/O15-tests-ci.md` 4.3절. 사용법·장면 목록은 `docs/testing/scenes.md`.

- 새 패키지 `org.domi.furnace.server.scene`(시험 소스): `LibraryScene`(한 장면, 증명·벤치 두 모드)·`TickScene`·`SceneLevel`(단일 스레드 시험 레벨)·`SceneKit`(엔티티 NBT 해시 등)·`SceneLibrary`(목록)·`SceneLibraryTest`(증명), bench 쪽 `SceneLibraryBench`(JSON).
- 장면 9개, 금 파일 8개 새로 추가(`scene-*.txt`; `noise-quick` 은 기존 `worldgen-noise-quick` 그대로). 기존 금 파일은 바꾸지 않았다.
- `NoiseIdentityBench`·`PristineHashBench` 를 `src/bench` 로 이동, 장면 부분은 `world/gen/NoiseIdentityScene`.
- 발견: 옛 `SharedPipelineBench hash`(O04 시제품)는 `String.valueOf(col.extraNbt())` 를 해시했는데 `NbtCompound` 에 `toString` 이 없어 정체 해시였다. 블록 부분 해시는 유효했으나 합계(`e4d2fdf35572ffdf` 등)는 NBT 몫이 무의미했다. 장면 라이브러리는 NBT 바이트로 해시한다.
- 운영 코드 변경 없음.
