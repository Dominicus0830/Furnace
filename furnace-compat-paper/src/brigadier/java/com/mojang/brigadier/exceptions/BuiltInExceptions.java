package com.mojang.brigadier.exceptions;

import com.mojang.brigadier.LiteralMessage;

/** Brigadier 1.3.10 대역(컴파일·시험 전용): 진짜와 같은 문구(바닐라 서버는 이것 대신 BrigadierExceptions 를 넣는다). */
public class BuiltInExceptions implements BuiltInExceptionProvider {
    private static final Dynamic2CommandExceptionType DOUBLE_TOO_SMALL = low("Double");
    private static final Dynamic2CommandExceptionType DOUBLE_TOO_BIG = high("Double");
    private static final Dynamic2CommandExceptionType FLOAT_TOO_SMALL = low("Float");
    private static final Dynamic2CommandExceptionType FLOAT_TOO_BIG = high("Float");
    private static final Dynamic2CommandExceptionType INTEGER_TOO_SMALL = low("Integer");
    private static final Dynamic2CommandExceptionType INTEGER_TOO_BIG = high("Integer");
    private static final Dynamic2CommandExceptionType LONG_TOO_SMALL = low("Long");
    private static final Dynamic2CommandExceptionType LONG_TOO_BIG = high("Long");
    private static final DynamicCommandExceptionType LITERAL_INCORRECT = dynamic("Expected literal ", "");
    private static final SimpleCommandExceptionType READER_EXPECTED_START_OF_QUOTE = simple("Expected quote to start a string");
    private static final SimpleCommandExceptionType READER_EXPECTED_END_OF_QUOTE = simple("Unclosed quoted string");
    private static final DynamicCommandExceptionType READER_INVALID_ESCAPE = dynamic("Invalid escape sequence '", "' in quoted string");
    private static final DynamicCommandExceptionType READER_INVALID_BOOL = dynamic("Invalid bool, expected true or false but found '", "'");
    private static final DynamicCommandExceptionType READER_INVALID_INT = dynamic("Invalid integer '", "'");
    private static final SimpleCommandExceptionType READER_EXPECTED_INT = simple("Expected integer");
    private static final DynamicCommandExceptionType READER_INVALID_LONG = dynamic("Invalid long '", "'");
    private static final SimpleCommandExceptionType READER_EXPECTED_LONG = simple("Expected long");
    private static final DynamicCommandExceptionType READER_INVALID_DOUBLE = dynamic("Invalid double '", "'");
    private static final SimpleCommandExceptionType READER_EXPECTED_DOUBLE = simple("Expected double");
    private static final DynamicCommandExceptionType READER_INVALID_FLOAT = dynamic("Invalid float '", "'");
    private static final SimpleCommandExceptionType READER_EXPECTED_FLOAT = simple("Expected float");
    private static final SimpleCommandExceptionType READER_EXPECTED_BOOL = simple("Expected bool");
    private static final DynamicCommandExceptionType READER_EXPECTED_SYMBOL = dynamic("Expected '", "'");
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_COMMAND = simple("Unknown command");
    private static final SimpleCommandExceptionType DISPATCHER_UNKNOWN_ARGUMENT = simple("Incorrect argument for command");
    private static final SimpleCommandExceptionType DISPATCHER_EXPECTED_ARGUMENT_SEPARATOR = simple("Expected whitespace to end one argument, but found trailing data");
    private static final DynamicCommandExceptionType DISPATCHER_PARSE_EXCEPTION = dynamic("Could not parse command: ", "");

    private static SimpleCommandExceptionType simple(String s) { return new SimpleCommandExceptionType(new LiteralMessage(s)); }

    private static DynamicCommandExceptionType dynamic(String before, String after) { return new DynamicCommandExceptionType(a -> new LiteralMessage(before + a + after)); }

    private static Dynamic2CommandExceptionType low(String type) { return new Dynamic2CommandExceptionType((found, min) -> new LiteralMessage(type + " must not be less than " + min + ", found " + found)); }

    private static Dynamic2CommandExceptionType high(String type) { return new Dynamic2CommandExceptionType((found, max) -> new LiteralMessage(type + " must not be more than " + max + ", found " + found)); }

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
