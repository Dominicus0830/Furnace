// 바닐라 BrigadierExceptions — Brigadier 기본 오류를 번역 컴포넌트로.
package org.domi.furnace.server.command.brigadier;

import org.domi.furnace.server.net.protocol.Text;

public final class BuiltInExceptions {

    private static final Dynamic2CommandExceptionType DOUBLE_TOO_SMALL = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.double.low", m, f));
    private static final Dynamic2CommandExceptionType DOUBLE_TOO_BIG = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.double.big", m, f));
    private static final Dynamic2CommandExceptionType FLOAT_TOO_SMALL = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.float.low", m, f));
    private static final Dynamic2CommandExceptionType FLOAT_TOO_BIG = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.float.big", m, f));
    private static final Dynamic2CommandExceptionType INTEGER_TOO_SMALL = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.integer.low", m, f));
    private static final Dynamic2CommandExceptionType INTEGER_TOO_BIG = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.integer.big", m, f));
    private static final Dynamic2CommandExceptionType LONG_TOO_SMALL = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.long.low", m, f));
    private static final Dynamic2CommandExceptionType LONG_TOO_BIG = new Dynamic2CommandExceptionType((f, m) -> Text.translatable("argument.long.big", m, f));
    private static final DynamicCommandExceptionType LITERAL_INCORRECT = new DynamicCommandExceptionType(e -> Text.translatable("argument.literal.incorrect", e));
    private static final SimpleCommandExceptionType READER_EXPECTED_START_OF_QUOTE = new SimpleCommandExceptionType(Text.translatable("parsing.quote.expected.start"));
    private static final SimpleCommandExceptionType READER_EXPECTED_END_OF_QUOTE = new SimpleCommandExceptionType(Text.translatable("parsing.quote.expected.end"));
    private static final DynamicCommandExceptionType READER_INVALID_ESCAPE = new DynamicCommandExceptionType(c -> Text.translatable("parsing.quote.escape", c));
    private static final DynamicCommandExceptionType READER_INVALID_BOOL = new DynamicCommandExceptionType(v -> Text.translatable("parsing.bool.invalid", v));
    private static final DynamicCommandExceptionType READER_INVALID_INT = new DynamicCommandExceptionType(v -> Text.translatable("parsing.int.invalid", v));
    private static final SimpleCommandExceptionType READER_EXPECTED_INT = new SimpleCommandExceptionType(Text.translatable("parsing.int.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_LONG = new DynamicCommandExceptionType(v -> Text.translatable("parsing.long.invalid", v));
    private static final SimpleCommandExceptionType READER_EXPECTED_LONG = new SimpleCommandExceptionType(Text.translatable("parsing.long.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_DOUBLE = new DynamicCommandExceptionType(v -> Text.translatable("parsing.double.invalid", v));
    private static final SimpleCommandExceptionType READER_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Text.translatable("parsing.double.expected"));
    private static final DynamicCommandExceptionType READER_INVALID_FLOAT = new DynamicCommandExceptionType(v -> Text.translatable("parsing.float.invalid", v));
    private static final SimpleCommandExceptionType READER_EXPECTED_FLOAT = new SimpleCommandExceptionType(Text.translatable("parsing.float.expected"));
    private static final SimpleCommandExceptionType READER_EXPECTED_BOOL = new SimpleCommandExceptionType(Text.translatable("parsing.bool.expected"));
    private static final DynamicCommandExceptionType READER_EXPECTED_SYMBOL = new DynamicCommandExceptionType(s -> Text.translatable("parsing.expected", s));
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_COMMAND = new SimpleCommandExceptionType(Text.translatable("command.unknown.command"));
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_ARGUMENT = new SimpleCommandExceptionType(Text.translatable("command.unknown.argument"));
    private static final SimpleCommandExceptionType DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR = new SimpleCommandExceptionType(Text.translatable("command.expected.separator"));
    private static final DynamicCommandExceptionType DISPATCHER_PARSE_EXCEPTION = new DynamicCommandExceptionType(m -> Text.translatable("command.exception", m));

    public Dynamic2CommandExceptionType doubleTooLow() { return DOUBLE_TOO_SMALL; }
    public Dynamic2CommandExceptionType doubleTooHigh() { return DOUBLE_TOO_BIG; }
    public Dynamic2CommandExceptionType floatTooLow() { return FLOAT_TOO_SMALL; }
    public Dynamic2CommandExceptionType floatTooHigh() { return FLOAT_TOO_BIG; }
    public Dynamic2CommandExceptionType integerTooLow() { return INTEGER_TOO_SMALL; }
    public Dynamic2CommandExceptionType integerTooHigh() { return INTEGER_TOO_BIG; }
    public Dynamic2CommandExceptionType longTooLow() { return LONG_TOO_SMALL; }
    public Dynamic2CommandExceptionType longTooHigh() { return LONG_TOO_BIG; }
    public DynamicCommandExceptionType literalIncorrect() { return LITERAL_INCORRECT; }
    public SimpleCommandExceptionType readerExpectedStartOfQuote() { return READER_EXPECTED_START_OF_QUOTE; }
    public SimpleCommandExceptionType readerExpectedEndOfQuote() { return READER_EXPECTED_END_OF_QUOTE; }
    public DynamicCommandExceptionType readerInvalidEscape() { return READER_INVALID_ESCAPE; }
    public DynamicCommandExceptionType readerInvalidBool() { return READER_INVALID_BOOL; }
    public DynamicCommandExceptionType readerInvalidInt() { return READER_INVALID_INT; }
    public SimpleCommandExceptionType readerExpectedInt() { return READER_EXPECTED_INT; }
    public DynamicCommandExceptionType readerInvalidLong() { return READER_INVALID_LONG; }
    public SimpleCommandExceptionType readerExpectedLong() { return READER_EXPECTED_LONG; }
    public DynamicCommandExceptionType readerInvalidDouble() { return READER_INVALID_DOUBLE; }
    public SimpleCommandExceptionType readerExpectedDouble() { return READER_EXPECTED_DOUBLE; }
    public DynamicCommandExceptionType readerInvalidFloat() { return READER_INVALID_FLOAT; }
    public SimpleCommandExceptionType readerExpectedFloat() { return READER_EXPECTED_FLOAT; }
    public SimpleCommandExceptionType readerExpectedBool() { return READER_EXPECTED_BOOL; }
    public DynamicCommandExceptionType readerExpectedSymbol() { return READER_EXPECTED_SYMBOL; }
    public SimpleCommandExceptionType dispatcherUnknownCommand() { return DISPATCHER_UNKNOWN_COMMAND; }
    public SimpleCommandExceptionType dispatcherUnknownArgument() { return DISPATCHER_UNKNOWN_ARGUMENT; }
    public SimpleCommandExceptionType dispatcherExpectedArgumentSeparator() { return DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR; }
    public DynamicCommandExceptionType dispatcherParseException() { return DISPATCHER_PARSE_EXCEPTION; }
}
