// 표준 서비스: 경제 제공자. 금액은 BigDecimal(부동소수점 오차 없이), 계정은 플레이어 UUID.
// 구현은 파일·DB 를 쓸 수 있다 — region 스레드에서 부를 때는 메모리에 둔 구현이 아니면 asyncExecutor 로 넘긴다.
package org.domi.furnace.api.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyService {

    /** 결과. success 가 false 면 error 에 사유(사람이 읽는 문장). balance 는 처리 뒤 잔액. */
    record Result(boolean success, BigDecimal amount, BigDecimal balance, String error) {
        public static Result ok(BigDecimal amount, BigDecimal balance) { return new Result(true, amount, balance, null); }

        public static Result fail(BigDecimal amount, BigDecimal balance, String error) { return new Result(false, amount, balance, error); }
    }

    String name();

    /** 단위 이름(예: "coin", "coins"). */
    String currencyNameSingular();

    String currencyNamePlural();

    /** 소수 자릿수(0 이면 정수). */
    int fractionalDigits();

    /** 표시용 문자열(단위 포함). */
    String format(BigDecimal amount);

    boolean hasAccount(UUID player);

    /** 계정이 없으면 만든다. 이미 있으면 false. */
    boolean createAccount(UUID player);

    /** 계정이 없으면 0. */
    BigDecimal balance(UUID player);

    default boolean has(UUID player, BigDecimal amount) { return balance(player).compareTo(amount) >= 0; }

    /** 음수 금액은 IllegalArgumentException. 잔액이 모자라면 실패 결과(예외 아님). */
    Result withdraw(UUID player, BigDecimal amount);

    Result deposit(UUID player, BigDecimal amount);

    /** 한 쪽이 실패하면 아무것도 옮기지 않는 이체를 구현이 지원하면 그것을, 아니면 인출 뒤 입금(입금이 실패하면 되돌린다). */
    default Result transfer(UUID from, UUID to, BigDecimal amount) {
        Result out = withdraw(from, amount);
        if (!out.success()) return out;
        Result in = deposit(to, amount);
        if (!in.success()) {
            deposit(from, amount);
            return in;
        }
        return out;
    }
}
