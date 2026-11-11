## 2026-11-11 시험 제한시간·재시도 정책 (A19-01·02)
- 시험 기본 제한시간 5분(`junit-platform.properties`, 서버·API·시험 서버·호환 모듈) + `SEPARATE_THREAD`. 근거·CI 단계 제한·재시도 규칙은 `docs/ci.md` 의 "시험 제한시간"·"재시도 정책".
- **함정**: 제한시간을 `-Djunit.jupiter.execution.timeout.default=3s` 로 gradle 에 넘겨도 시험 JVM 에는 안 간다(`furnace.*` 속성만 전달). 짧게 확인하려면 `junit-platform.properties` 값을 임시로 바꾼다.
- 바쁜 루프(`while(true)`)도 `SEPARATE_THREAD` 에서는 제한시간에 `TimeoutException` 으로 실패하고 JVM 은 정상 종료한다(실측). 같은 스레드 방식이면 인터럽트만 걸려 안 멈춘다.
- 확률 실패를 잡는 시험은 `@NoRetry` + `@RepeatedTest(n)`(`org.domi.furnace.server.NoRetry`). 재시도 단계(`tools/ci/retry-failed-tests.sh`)가 `NoRetryFailureListener` 가 적은 `build/no-retry-failures.txt` 를 보고 재시도를 건너뛴다.
- 새 시험이 5분 넘게 걸려야 하면 시험에 `@Timeout` 을 붙인다. 정상 최장은 67초(`TreeFeatureScanTest`).
