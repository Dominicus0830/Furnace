// 시험 JVM 에서는 엔티티 손잡이(T-122)를 바닐라 값으로 둔다. 운영 기본은 Paper 값(밀기 상한 8·추적 거리)이지만, 그대로 두면 바닐라 대조 시험과
// 장면 금 파일이 모르는 사이에 Paper 동작을 시험하게 된다(EntityActivationOffExtension 과 같은 이유). 손잡이 시험은 스스로 켠다.
package org.domi.furnace.server;

import org.domi.furnace.server.config.EntityHandleSettings;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class EntityHandlesVanillaExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) { EntityHandleSettings.install(EntityHandleSettings.VANILLA); }

    @Override
    public void afterAll(ExtensionContext context) { EntityHandleSettings.install(EntityHandleSettings.VANILLA); }
}
