// 서비스 제공 등록 한 건.
package org.domi.furnace.api.service;

import org.domi.furnace.api.plugin.FurnacePlugin;

public record ServiceRegistration<T>(Class<T> service, T provider, FurnacePlugin owner, ServicePriority priority) {}
