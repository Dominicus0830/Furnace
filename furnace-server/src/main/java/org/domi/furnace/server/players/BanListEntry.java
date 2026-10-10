// 바닐라 BanListEntry — created/source/expires/reason. 날짜 형식 "yyyy-MM-dd HH:mm:ss Z"(ROOT 로캘, 기본 시간대).
package org.domi.furnace.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {

    public static final String EXPIRES_NEVER = "forever";
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT));

    protected final Date created;
    protected final String source;
    protected final Date expires;
    protected final String reason;

    protected BanListEntry(T user, Date created, String source, Date expires, String reason) {
        super(user);
        this.created = created == null ? new Date() : created;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expires;
        this.reason = reason == null ? "Banned by an operator." : reason;
    }

    protected BanListEntry(T user, JsonObject json) {
        super(user);
        Date c;
        try {
            c = json.has("created") ? DATE_FORMAT.get().parse(json.get("created").getAsString()) : new Date();
        } catch (ParseException e) {
            c = new Date();
        }
        this.created = c;
        this.source = json.has("source") ? json.get("source").getAsString() : "(Unknown)";
        Date x;
        try {
            x = json.has("expires") ? DATE_FORMAT.get().parse(json.get("expires").getAsString()) : null;
        } catch (ParseException e) {
            x = null;
        }
        this.expires = x;
        this.reason = json.has("reason") ? json.get("reason").getAsString() : "Banned by an operator.";
    }

    public static String formatDate(Date d) { return DATE_FORMAT.get().format(d); }

    public Date getCreated() { return created; }

    public String getSource() { return source; }

    public Date getExpires() { return expires; }

    public String getReason() { return reason; }

    /** 밴 목록 표시용 이름(사용자 이름 또는 IP). */
    public abstract String getDisplayName();

    @Override
    boolean hasExpired() { return expires != null && expires.before(new Date()); }

    @Override
    protected void serialize(JsonObject json) {
        json.addProperty("created", formatDate(created));
        json.addProperty("source", source);
        json.addProperty("expires", expires == null ? EXPIRES_NEVER : formatDate(expires));
        json.addProperty("reason", reason);
    }
}
