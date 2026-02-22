package io.testsmith.plugin.llm.internal;

import io.testsmith.plugin.llm.api.LlmProtocolException;

import java.util.*;

/**
 * Small dependency-free JSON codec used for strict protocol parsing and payload generation.
 */
public final class JsonCodec {
    private JsonCodec() {
    }

    public static Object parse(String json) {
        if (json == null) {
            throw new LlmProtocolException("JSON payload must not be null");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEof()) {
            throw new LlmProtocolException("Unexpected trailing content after JSON value");
        }
        return value;
    }

    public static String toJsonString(String value) {
        Objects.requireNonNull(value, "JSON string value must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(ch);
                        for (int k = hex.length(); k < 4; k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
            this.pos = 0;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isEof()) {
                throw new LlmProtocolException("Unexpected end of JSON input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new LlmProtocolException("Unexpected token at position " + pos);
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (peek('}')) {
                expect('}');
                return result;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new LlmProtocolException("Object key must be a JSON string at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (peek(']')) {
                expect(']');
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isEof()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isEof()) {
                        throw new LlmProtocolException("Unterminated escape sequence in JSON string");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(parseUnicodeEscape());
                        default -> throw new LlmProtocolException("Invalid JSON escape sequence: \\" + esc);
                    }
                } else {
                    if (c < 0x20) {
                        throw new LlmProtocolException("Control character in JSON string at position " + pos);
                    }
                    sb.append(c);
                }
            }
            throw new LlmProtocolException("Unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw new LlmProtocolException("Incomplete unicode escape sequence");
            }
            int codePoint = 0;
            for (int i = 0; i < 4; i++) {
                char ch = text.charAt(pos++);
                int hex = Character.digit(ch, 16);
                if (hex < 0) {
                    throw new LlmProtocolException("Invalid unicode escape sequence");
                }
                codePoint = (codePoint << 4) + hex;
            }
            return (char) codePoint;
        }

        private Number parseNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            if (peek('0')) {
                pos++;
            } else {
                parseDigits();
            }
            if (peek('.')) {
                pos++;
                parseDigits();
            }
            if (peek('e') || peek('E')) {
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                parseDigits();
            }
            String numberText = text.substring(start, pos);
            try {
                if (numberText.contains(".") || numberText.contains("e") || numberText.contains("E")) {
                    return Double.parseDouble(numberText);
                }
                return Long.parseLong(numberText);
            } catch (NumberFormatException ex) {
                throw new LlmProtocolException("Invalid JSON number at position " + start, ex);
            }
        }

        private void parseDigits() {
            if (isEof() || !Character.isDigit(text.charAt(pos))) {
                throw new LlmProtocolException("Expected digit at position " + pos);
            }
            while (!isEof() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw new LlmProtocolException("Invalid literal at position " + pos);
            }
            pos += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (!isEof()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        private void expect(char ch) {
            if (isEof() || text.charAt(pos) != ch) {
                throw new LlmProtocolException("Expected '" + ch + "' at position " + pos);
            }
            pos++;
        }

        private boolean peek(char ch) {
            return !isEof() && text.charAt(pos) == ch;
        }

        private boolean isEof() {
            return pos >= text.length();
        }
    }
}
