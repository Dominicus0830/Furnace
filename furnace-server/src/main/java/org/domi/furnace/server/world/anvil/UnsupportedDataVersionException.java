// 이 서버(1.21.1, DataVersion 3955)가 안전하게 읽고 쓸 수 없는 판의 데이터를 열지 않는다는 표시(A05-03·04).
// 새 판(NewerDataVersionException)은 모르는 블록·아이템이 지워지고, 옛 판(OlderDataVersionException)은 데이터 픽서가 없어
// 개수·마법 부여·블록이 조용히 사라진다. 어느 쪽이든 월드를 새로 생성해 덮지 않는다.
package org.domi.furnace.server.world.anvil;

public abstract class UnsupportedDataVersionException extends RuntimeException {
    private final int dataVersion;

    protected UnsupportedDataVersionException(String message, int dataVersion) {
        super(message);
        this.dataVersion = dataVersion;
    }

    public int dataVersion() { return dataVersion; }
}
