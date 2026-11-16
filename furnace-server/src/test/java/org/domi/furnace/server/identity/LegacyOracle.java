// 옛 구현 사본(레거시 오라클) 표시. 차분 시험이 "옛 구현과 같다"를 증명하려고 기준 커밋에서 복사해 얼려 둔 코드에 붙인다.
//
// 규칙(LegacyOracleRulesTest 가 검사한다, docs/testing/identitykit.md "옛 구현 보관"):
//  1. 파일 첫 줄: "// LEGACY-ORACLE since <커밋>: <무엇의 사본인지>" — since 는 이 어노테이션의 since 와 같아야 한다.
//  2. since 는 복사해 온 커밋(7~40자리 16진). 계산은 그 커밋의 운영 코드 그대로다(고치지 않는다 — 고치면 오라클이 아니다).
//     클래스 밖에서 돌게 하는 각색(필드 → 인자, private 판정 → 넘겨받는 함수)만 허용하고, 바꾼 것을 파일 머리에 적는다.
//  3. 차분 시험 하나 이상이 이 클래스를 써야 한다. 아무도 안 쓰면 지운다.
//  4. coveredBy 에 적은 장면 금 파일(src/test/resources/identity/<이름>.txt)이 생기면 그 영역은 장면 해시가 지키므로 지운다.
//  5. 위치: 공개 API 만 쓰면 identity/legacy/<파트>/, 패키지 비공개 접근이 필요하면 운영 코드와 같은 패키지에 Legacy<이름> 으로.
// 사본이 오래 남아 운영 코드와 함께 진화하지 못하면 "거짓 오라클"이 된다 — 그래서 3·4 로 수명을 둔다.
package org.domi.furnace.server.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LegacyOracle {

    /** 복사해 온 기준 커밋(짧은 해시 이상). */
    String since();

    /** 무엇의 사본인지: 운영 클래스#메서드. */
    String copyOf();

    /** 이 오라클을 대신할 금 파일 장면 이름(있으면). 그 금 파일이 생기면 이 오라클을 지운다. 비우면 차분 시험만이 쓴다. */
    String coveredBy() default "";
}
