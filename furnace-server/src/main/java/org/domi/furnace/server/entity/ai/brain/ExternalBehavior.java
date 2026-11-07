// 브레인 밖(플러그인 API)에서 붙인 행동 표지. 브레인을 새로 만들 때(copyWithoutBehaviors — 주민 직업·나이 변화) 바닐라 행동과 달리 새 브레인으로 옮겨 간다.
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.behavior.BehaviorControl;

public interface ExternalBehavior extends BehaviorControl<Mob> {}
