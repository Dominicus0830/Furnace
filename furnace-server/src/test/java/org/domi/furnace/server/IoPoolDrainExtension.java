// 모든 테스트 뒤에 TestServers 가 만든 IO 풀을 비운다. 퇴장 시 플레이어 저장이 IO 풀에서 비동기로 돌아
// @TempDir 삭제와 겹치면 "Failed to delete temp directory" 로 간헐 실패했다. 콜백은 @TempDir 정리보다 먼저 돈다.
package org.domi.furnace.server;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class IoPoolDrainExtension implements AfterEachCallback, AfterAllCallback {

    @Override
    public void afterEach(ExtensionContext context) throws Exception { TestServers.drainIoPools(); }

    @Override
    public void afterAll(ExtensionContext context) throws Exception { TestServers.drainIoPools(); }
}
