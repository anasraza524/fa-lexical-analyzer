import java.util.ArrayList;
import java.util.List;

/**
 * APPROACH 1 - "Direct DFA" (hand-coded finite automaton).
 *
 * Each branch of the switch below corresponds to one state of an explicit
 * finite automaton (START -> IN_ID / IN_NUMBER / IN_EQ / ... -> ACCEPT).
 * Transitions are written directly as Java control flow rather than being
 * looked up in a table - this is the classic "direct-coded scanner" style
 * used by hand-written compilers (e.g. early C compilers, CPython's old
 * tokenizer). It is fast and easy to read, but every new token type means
 * editing code, not data.
 *
 * Maximal-munch (longest match) is used throughout, e.g. "==" is always
 * preferred over two separate "=" tokens, and "3.14" is preferred over
 * "3", ".", "14".
 */
public class DirectDfaLexer {

    private final String src;
    private int pos;
    private int line;
    private int col;
    private final List<Token> tokens = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public DirectDfaLexer(String source) {
        this.src = source == null ? "" : source;
    }

    public List<Token> getErrors() {
        List<Token> errTokens = new ArrayList<>();
        for (Token t : tokens) {
            if (t.getType() == TokenType.ERROR) errTokens.add(t);
        }
        return errTokens;
    }

    public List<Token> tokenize() {
        tokens.clear();
        pos = 0;
        line = 1;
        col = 1;

        while (pos < src.length()) {
            char c = src.charAt(pos);

            // --- STATE: START (whitespace is not a token; just skip it) ---
            if (c == '\n') {
                advance();
                line++;
                col = 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                advance();
                continue;
            }

            int startLine = line, startCol = col;

            if (Character.isLetter(c)) {
                scanIdentifierOrKeyword(startLine, startCol);
            } else if (Character.isDigit(c)) {
                scanNumber(startLine, startCol);
            } else if (c == '=') {
                scanEquals(startLine, startCol);
            } else if (LanguageSpec.SINGLE_OPERATORS.contains(c)) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c), startLine, startCol));
                advance();
            } else if (LanguageSpec.SYMBOLS.contains(c)) {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), startLine, startCol));
                advance();
            } else {
                // --- STATE: ERROR (unrecognized character, e.g. @ # $ ? ` ) ---
                tokens.add(new Token(TokenType.ERROR, String.valueOf(c), startLine, startCol));
                advance();
            }
        }
        return tokens;
    }

    // --- STATE: IN_ID  (letter (letter|digit)*) ---
    private void scanIdentifierOrKeyword(int startLine, int startCol) {
        int start = pos;
        while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) {
            advance();
        }
        String lexeme = src.substring(start, pos);
        if (LanguageSpec.KEYWORDS.contains(lexeme)) {
            tokens.add(new Token(TokenType.KEYWORD, lexeme, startLine, startCol));
        } else {
            tokens.add(new Token(TokenType.IDENTIFIER, lexeme, startLine, startCol));
        }
    }

    // --- STATE: IN_INT -> (optional) IN_DOT -> IN_FLOAT ---
    private void scanNumber(int startLine, int startCol) {
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            advance();
        }

        // Look ahead for a fractional part: '.' MUST be followed by at least
        // one digit to be consumed as part of the number (maximal munch with
        // 1-token lookahead). "3." or "3.x" leaves the '.' for the next scan.
        if (pos < src.length() && src.charAt(pos) == '.'
                && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
            advance(); // consume '.'
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                advance();
            }
        }

        // EDGE CASE: a number immediately followed by a letter with no
        // separator (e.g. "123abc") is not a valid NUMBER or IDENTIFIER
        // under this grammar (identifiers must start with a letter) -
        // report the whole run as a single ERROR token instead of silently
        // splitting it into "123" + "abc".
        if (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) {
                advance();
            }
            String bad = src.substring(start, pos);
            tokens.add(new Token(TokenType.ERROR, bad, startLine, startCol));
            return;
        }

        String lexeme = src.substring(start, pos);
        tokens.add(new Token(TokenType.NUMBER, lexeme, startLine, startCol));
    }

    // --- STATE: IN_EQ  ('=' -> '==' or '=') ---
    private void scanEquals(int startLine, int startCol) {
        advance(); // consume first '='
        if (pos < src.length() && src.charAt(pos) == '=') {
            advance(); // consume second '='
            tokens.add(new Token(TokenType.OPERATOR, "==", startLine, startCol));
        } else {
            tokens.add(new Token(TokenType.OPERATOR, "=", startLine, startCol));
        }
    }

    private void advance() {
        pos++;
        col++;
    }
}
