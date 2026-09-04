/**
 * Enumerates every category of token the lexical analyzer can produce.
 * ERROR is included so that invalid/unrecognized input is reported as a
 * token rather than silently dropped or crashing the analyzer (edge-case
 * handling requirement).
 */
public enum TokenType {
    KEYWORD,
    IDENTIFIER,
    OPERATOR,
    NUMBER,
    SYMBOL,
    ERROR
}
