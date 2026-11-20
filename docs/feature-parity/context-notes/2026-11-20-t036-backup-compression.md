# T-036 백업 zip 압축 옵션 (2026-11-20)

- 결정 D3(도미니코 "추천대로"): zip 안 파일을 다시 압축하지 않는 것(STORED)이 기본. `backup.compression=stored|fast`.
- STORED 항목은 zip 규격상 크기·CRC 를 항목을 쓰기 전에 알아야 한다(ZipOutputStream 은 STORED 에 데이터 기술자를 못 쓴다). 그래서 파일을 한 번 더 읽어 CRC32 만 센다(CRC 는 deflate 보다 훨씬 싸다). 스테이징 폴더는 복사가 끝난 뒤라 바뀌지 않으며, 쓰는 동안 크기가 달라지면 IOException 으로 백업을 실패시킨다.
- 로드맵 표의 "parallel 선택지"(항목별 병렬 deflate)는 넣지 않았다: 기본이 STORED 라 이득이 크지 않고, 쓰려면 zip 직접 쓰기가 필요하다. 필요해지면 후속.
- 시험: `BackupZipTest`(STORED·FAST 왕복, 빈 파일, 64KiB 넘는 파일, CRC 검사), `IdlePauseTest`(옵션 읽기·검증), 기존 `WorldBackupTest`(기본 STORED 로 왕복).
