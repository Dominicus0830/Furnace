package net.minecraft.commands;

import com.mojang.brigadier.exceptions.BuiltInExceptionProvider;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import org.domi.furnace.compat.nms.NmsBrigadier;

/**
 * 바닐라 Brigadier 기본 오류(Mojang 이름 shim, NMS shim 5e-3c). 바닐라·Paper 는 서버가 뜰 때({@code SharedConstants} 정적 초기화) Brigadier 의
 * {@code CommandSyntaxException.BUILT_IN_EXCEPTIONS} 를 이것으로 바꾸고 스택 추적을 끈다 — 그래서 플러그인이 보는 글 읽기 오류가 Brigadier 원래 문구가 아니라
 * 게임 번역 문구다(예 따옴표 안 잘못된 이스케이프: Brigadier "'i'", 바닐라 "'\i'"). 키·인자 차례는 Paper 1.21.1 바이트코드대로. Furnace 는 Brigadier 묶음을 붙일 때
 * {@link #furnaceInstall()} 로 같은 일을 한다.
 */
class BrigadierExceptions {

    private static final Dynamic2CommandExceptionType DOUBLE_TOO_SMALL = range("argument.double.low");
    private static final Dynamic2CommandExceptionType DOUBLE_TOO_BIG = range("argument.double.big");
    private static final Dynamic2CommandExceptionType FLOAT_TOO_SMALL = range("argument.float.low");
    private static final Dynamic2CommandExceptionType FLOAT_TOO_BIG = range("argument.float.big");
    private static final Dynamic2CommandExceptionType INTEGER_TOO_SMALL = range("argument.integer.low");
    private static final Dynamic2CommandExceptionType INTEGER_TOO_BIG = range("argument.integer.big");
    private static final Dynamic2CommandExceptionType LONG_TOO_SMALL = range("argument.long.low");
    private static final Dynamic2CommandExceptionType LONG_TOO_BIG = range("argument.long.big");
    private static final DynamicCommandExceptionType LITERAL_INCORRECT = dynamic("argument.literal.incorrect");
    private static final SimpleCommandExceptionType READER_EXPECTED_START_OF_QUOTE = simple("parsing.quote.expected.start");
    private static final SimpleCommandExceptionType READER_EXPECTED_END_OF_QUOTE = simple("parsing.quote.expected.end");
    private static final DynamicCommandExceptionType READER_INVALID_ESCAPE = dynamic("parsing.quote.escape");
    private static final DynamicCommandExceptionType READER_INVALID_BOOL = dynamic("parsing.bool.invalid");
    private static final DynamicCommandExceptionType READER_INVALID_INT = dynamic("parsing.int.invalid");
    private static final SimpleCommandExceptionType READER_EXPECTED_INT = simple("parsing.int.expected");
    private static final DynamicCommandExceptionType READER_INVALID_LONG = dynamic("parsing.long.invalid");
    private static final SimpleCommandExceptionType READER_EXPECTED_LONG = simple("parsing.long.expected");
    private static final DynamicCommandExceptionType READER_INVALID_DOUBLE = dynamic("parsing.double.invalid");
    private static final SimpleCommandExceptionType READER_EXPECTED_DOUBLE = simple("parsing.double.expected");
    private static final DynamicCommandExceptionType READER_INVALID_FLOAT = dynamic("parsing.float.invalid");
    private static final SimpleCommandExceptionType READER_EXPECTED_FLOAT = simple("parsing.float.expected");
    private static final SimpleCommandExceptionType READER_EXPECTED_BOOL = simple("parsing.bool.expected");
    private static final DynamicCommandExceptionType READER_EXPECTED_SYMBOL = dynamic("parsing.expected");
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_COMMAND = simple("command.unknown.command");
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_ARGUMENT = simple("command.unknown.argument");
    private static final SimpleCommandExceptionType DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR = simple("command.expected.separator");
    private static final DynamicCommandExceptionType DISPATCHER_PARSE_EXCEPTION = dynamic("command.exception");

    private static volatile boolean installed;

    public BrigadierExceptions() {}

    /** 바닐라 SharedConstants 정적 초기화와 같은 일(한 번만): 스택 추적을 끄고 이 기본 오류를 넣는다. Brigadier 묶음이 붙은 뒤에만 부른다. */
    public static synchronized void furnaceInstall() {
        if (installed) return;
        com.mojang.brigadier.exceptions.CommandSyntaxException.ENABLE_COMMAND_STACK_TRACES = false;
        com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS = new BrigadierExceptions();
        installed = true;
    }

    private static SimpleCommandExceptionType simple(String key) { return new SimpleCommandExceptionType(NmsBrigadier.message(net.minecraft.network.chat.Component.translatable(key))); }

    private static DynamicCommandExceptionType dynamic(String key) { return new DynamicCommandExceptionType(a -> NmsBrigadier.translatable(key, a)); }

    /** 바닐라 람다: (찾은 값, 한계) → 번역(한계, 찾은 값). */
    private static Dynamic2CommandExceptionType range(String key) { return new Dynamic2CommandExceptionType((found, limit) -> NmsBrigadier.translatable(key, limit, found)); }

    @Override public Dynamic2CommandExceptionType doubleTooLow() { return DOUBLE_TOO_SMALL; }
    @Override public Dynamic2CommandExceptionType doubleTooHigh() { return DOUBLE_TOO_BIG; }
    @Override public Dynamic2CommandExceptionType floatTooLow() { return FLOAT_TOO_SMALL; }
    @Override public Dynamic2CommandExceptionType floatTooHigh() { return FLOAT_TOO_BIG; }
    @Override public Dynamic2CommandExceptionType integerTooLow() { return INTEGER_TOO_SMALL; }
    @Override public Dynamic2CommandExceptionType integerTooHigh() { return INTEGER_TOO_BIG; }
    @Override public Dynamic2CommandExceptionType longTooLow() { return LONG_TOO_SMALL; }
    @Override public Dynamic2CommandExceptionType longTooHigh() { return LONG_TOO_BIG; }
    @Override public DynamicCommandExceptionType literalIncorrect() { return LITERAL_INCORRECT; }
    @Override public SimpleCommandExceptionType readerExpectedStartOfQuote() { return READER_EXPECTED_START_OF_QUOTE; }
    @Override public SimpleCommandExceptionType readerExpectedEndOfQuote() { return READER_EXPECTED_END_OF_QUOTE; }
    @Override public DynamicCommandExceptionType readerInvalidEscape() { return READER_INVALID_ESCAPE; }
    @Override public DynamicCommandExceptionType readerInvalidBool() { return READER_INVALID_BOOL; }
    @Override public DynamicCommandExceptionType readerInvalidInt() { return READER_INVALID_INT; }
    @Override public SimpleCommandExceptionType readerExpectedInt() { return READER_EXPECTED_INT; }
    @Override public DynamicCommandExceptionType readerInvalidLong() { return READER_INVALID_LONG; }
    @Override public SimpleCommandExceptionType readerExpectedLong() { return READER_EXPECTED_LONG; }
    @Override public DynamicCommandExceptionType readerInvalidDouble() { return READER_INVALID_DOUBLE; }
    @Override public SimpleCommandExceptionType readerExpectedDouble() { return READER_EXPECTED_DOUBLE; }
    @Override public DynamicCommandExceptionType readerInvalidFloat() { return READER_INVALID_FLOAT; }
    @Override public SimpleCommandExceptionType readerExpectedFloat() { return READER_EXPECTED_FLOAT; }
    @Override public SimpleCommandExceptionType readerExpectedBool() { return READER_EXPECTED_BOOL; }
    @Override public DynamicCommandExceptionType readerExpectedSymbol() { return READER_EXPECTED_SYMBOL; }
    @Override public SimpleCommandExceptionType dispatcherUnknownCommand() { return DISPATCHER_UNKNOWN_COMMAND; }
    @Override public SimpleCommandExceptionType dispatcherUnknownArgument() { return DISPATCHER_UNKNOWN_ARGUMENT; }
    @Override public SimpleCommandExceptionType dispatcherExpectedArgumentSeparator() { return DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR; }
    @Override public DynamicCommandExceptionType dispatcherParseException() { return DISPATCHER_PARSE_EXCEPTION; }
}
