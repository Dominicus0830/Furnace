// 특정 스레드가 소유한 값을 감싸 비소유 스레드의 직접 접근을 막는 래퍼
package org.domi.furnace.api.concurrent;

public final class Owned<T> {

    private final T value;
    private final ThreadOwner owner;

    public Owned(T value, ThreadOwner owner) {
        this.value = value;
        this.owner = owner;
    }

    /**
     * 소유 스레드에서만 값 반환. 비소유 스레드 접근은 assertion 활성 빌드에서 AssertionError.
     * (운영 빌드에서 assertion 비활성 시 가드는 비용 0으로 사라진다 — 디버그 전용 불변식.)
     */
    public T get() {
        assert owner.isOwnerThread()
                : "Owned 접근 위반: 소유자=" + owner.ownerName()
                + ", 현재 스레드=" + Thread.currentThread().getName();
        return value;
    }

    public ThreadOwner owner() {
        return owner;
    }
}
