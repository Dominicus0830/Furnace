// CI 의 "실패한 시험 클래스 한 번 재시도"(.github/workflows/ci.yml, docs/ci.md)를 받지 않는 시험 표시.
// 확률로 실패하는 버그의 회귀 시험은 재시도가 실패를 가려 회귀가 들어와도 초록이 된다(실패율 p 면 두 번 다 실패할 확률은 p²).
// 그런 시험은 @RepeatedTest(n) 으로 한 번의 실행 안에서 여러 번 돌리고, 이 표시를 붙여 한 번이라도 실패하면 바로 빨강으로 둔다.
// 클래스에 붙이면 그 안의 모든 시험, 메서드에 붙이면 그 시험(반복 호출 포함)이 대상이다.
//   @NoRetry @RepeatedTest(50) void racyPathNeverDropsItems() { ... }
// 이 표시를 붙인 시험이 CI 에서 흔들리면 그 시험을 고친다(재시도로 가리지 않는다) — AGENTS.md 규칙 5 와 같은 방향이다.
package org.domi.furnace.server;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag(NoRetry.TAG)
public @interface NoRetry {
    String TAG = "no-retry";
}
