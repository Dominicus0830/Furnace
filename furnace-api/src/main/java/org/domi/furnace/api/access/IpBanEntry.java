// banned-ips.json 한 줄(읽기 전용 사본).
package org.domi.furnace.api.access;

import java.time.Instant;

/** @param expires null 이면 영구. */
public record IpBanEntry(String ip, String source, Instant created, Instant expires, String reason) {}
