// 소유 스레드가 아닌 곳에서 소유 스레드 전용 API 를 불렀을 때 던지는 예외.
// 운영 빌드에서도 예외로 거부한다.
package org.domi.furnace.api.concurrent;

public final class ThreadOwnershipException extends IllegalStateException {

    public ThreadOwnershipException(String message) { super(message); }
}
