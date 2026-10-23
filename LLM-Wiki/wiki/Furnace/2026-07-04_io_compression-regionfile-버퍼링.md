# 2026-07-04 io — CompressionEncoder 스크래치 + RegionFile 벌크 읽기 (M-opt P1-2·3)

## 작업 요약
net 압축 프레임별 할당 제거(3b909d4) + RegionFile 헤더 읽기 syscall 4096→1(f685275).

## 문제 / 목적
1. CompressionEncoder 프레임마다 `new byte[dataLen]` — GC 압력.
2. RegionFile 생성자 `readInt()`×1024 = 단일바이트 read 4096회 syscall, readChunk 헤더도 5회.

## 해결 방법 / 구현
1. **CompressionEncoder**: 인스턴스 필드 성장형 스크래치 재사용. 안전 전제 = 인코더 접속마다 새 인스턴스(ConnectionHandler:131-132, @Sharable 아님) + Netty 이벤트루프 단일스레드 순차. `deflater.setInput(input, 0, dataLen)` 실제 길이만 전달 — 스크래치 잔여 바이트 혼입 방지. fuzz(0B~2MiB, 임계 64 경계 포함) 라운드트립 바이트 동일.
2. **RegionFile**: 헤더 4096B·청크 헤더 5B 벌크 read 후 빅엔디안 수동 파싱(DataInput.readInt 동일 바이트오더 — 포맷 불변). 1024청크×20라운드 재오픈+read 332ms→223~230ms(~30%).

## 주의
- CompressionDecoder `MAX = 2*1024*1024` 프로토콜 상한 — fuzz 최대 크기 맞춤.
- writeChunk 개별 write 미착수(별도 후보). RegionFileStore 락 세분화 = P2 보류.

## 관련 파일
[[2026-07-04_worldgen_worldSurfaceHeightAt-캐시]], [[2026-07-04_m-opt_병목-서베이-실측]]

## 태그
#Furnace #net #anvil #Feature #Performance