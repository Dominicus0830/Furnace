// 특정 스레드가 소유한 값을 감싸서, 소유하지 않은 스레드의 직접 접근을 막는 래퍼.
package org.domi.furnace.api.concurrent;

public final class Owned<T> {

    private final T value;
    private final ThreadOwner owner;

    public Owned(T value, ThreadOwner owner) {
        this.value = value;
        this.owner = owner;
    }

    /**
     * 소유 스레드에서만 값을 돌려준다. 소유하지 않은 스레드가 접근하면 assertion 이 켜진 빌드에서 AssertionError 가 난다.
     * <p>
     * 운영 빌드에서 assertion 이 꺼지면 가드는 비용 0으로 사라진다. 디버그 전용 불변식이다.
     */
    public T get() {
        assert owner.isOwnerThread()
                : "Owned access violation: owner=" + owner.ownerName()
                + ", current thread=" + Thread.currentThread().getName();
        return value;
    }

    public ThreadOwner owner() {
        return owner;
    }
}
