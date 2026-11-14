// 시험 JVM 에서는 엔티티 활성 범위(D11)를 끈 채로 둔다. 운영 기본은 켜짐이지만, 그대로 두면 "먼 몹을 10초 넘게 돌려 바닐라 동작을 보는" 시험이
// 모르는 사이에 Paper 동작을 시험하게 된다(PR #326 검토 R4). 활성 범위 시험(EntityActivationTest·장면 activation 등)은 스스로 켠다.
package org.domi.furnace.server;

import org.domi.furnace.server.config.EntityActivationSettings;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class EntityActivationOffExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) { EntityActivationSettings.install(EntityActivationSettings.OFF); }

    @Override
    public void afterAll(ExtensionContext context) { EntityActivationSettings.install(EntityActivationSettings.OFF); }
}
