// 꾸밈 값의 세 상태 — 정하지 않음(부모 스타일 상속)·끔·켬.
package org.domi.furnace.api.text;

public enum TriState {
    NOT_SET, FALSE, TRUE;

    public static TriState of(boolean v) { return v ? TRUE : FALSE; }

    public static TriState of(Boolean v) { return v == null ? NOT_SET : of(v.booleanValue()); }

    /** NOT_SET 이면 null. */
    public Boolean toBoolean() { return this == NOT_SET ? null : this == TRUE; }
}
