// 연결이 끊겨서 기다리던 일(리소스 팩 응답·쿠키 응답)이 끝내 오지 않을 때 그 Future 가 이 예외로 끝난다.
// 구성 단계에서 Future 를 기다리는 처리기가 연결이 끊긴 뒤에도 매달려 있지 않게 하는 장치다.
package org.domi.furnace.api.net;

public final class ConnectionClosedException extends RuntimeException {

    public ConnectionClosedException(String message) {
        super(message, null, false, false);
    }
}
