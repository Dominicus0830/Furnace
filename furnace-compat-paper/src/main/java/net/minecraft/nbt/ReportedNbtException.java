package net.minecraft.nbt;

/** 바닐라: NBT 를 읽다 난 입출력 오류(제목 "Loading NBT data"). */
public class ReportedNbtException extends net.minecraft.ReportedException {

    public ReportedNbtException(net.minecraft.CrashReport report) { super(report); }
}
