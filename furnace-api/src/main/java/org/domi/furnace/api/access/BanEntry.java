// banned-players.json 한 줄(읽기 전용 사본).
package org.domi.furnace.api.access;

import java.time.Instant;
import java.util.UUID;

/** @param name    밴할 때 알던 이름. @param expires null 이면 영구. */
public record BanEntry(UUID uniqueId, String name, String source, Instant created, Instant expires, String reason) {}
