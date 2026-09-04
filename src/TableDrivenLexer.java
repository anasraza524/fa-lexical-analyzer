import java.util.ArrayList;
import java.util.List;

/**
 * APPROACH 2 - "Table-driven" finite automaton.
 *
 * Instead of encoding transitions as if/else and switch statements, the
 * automaton is expressed as DATA: a 2-D transition table
 * TRANSITION[state][charClass] -> nextState (or DEAD). The scanning loop
 * itself is a small, generic engine that just repeatedly looks up the
 * table; it never needs to change even if the language's alphabet grows.
 * This is the classic approach used by generated scanners (e.g. lex/flex
 * output, JFlex) and is what most compiler textbooks mean by
 * "table-driven lexical analysis".
 *
 * Trade-off vs. the direct DFA: adding a token type here means editing a
 * table, not control flow, which scales better to large grammars - at the
 * cost of an extra indirection (table lookup) per character.
 *
 * States:
 *   S_ID     - inside an identifier/keyword
 *   S_INT    - inside the integer part of a number
 *   S_FLOAT  - inside the fractional part of a number
 * Character classes:
 *   C_LETTER, C_DIGIT, C_OTHER
 * Table values:
 *   CONTINUE - stay in / re-enter the same state, consume the character
 *   STOP     - character does not extend the token; end it here
 */
public class TableDrivenLexer {

    private static final int S_ID = 0;
    private static final int S_INT = 1;
    private static final int S_FLOAT = 2;
    private static final int NUM_STATES = 3;

    private static final int C_LETTER = 0;
    private static final int C_DIGIT = 1;
    private static final int C_OTHER = 2;
    private static final int NUM_CLASSES = 3;

    private static final int STOP = 0;
    private static final int CONTINUE = 1;

    /** TRANSITION[state][charClass] = CONTINUE (stay, consume char) or STOP (end token). */
    private static final int[][] TRANSITION = new int[NUM_STATES][NUM_CLASSES];
    static {
        // S_ID: letters and digits keep extending an identifier, anything else ends it
        TRANSITION[S_ID][C_LETTER] = CONTINUE;
        TRANSITION[S_ID][C_DIGIT] = CONTINUE;
        TRANSITION[S_ID][C_OTHER] = STOP;

        // S_INT: digits keep extending the integer part
        TRANSITION[S_INT][C_LETTER] = STOP;  // handled as an error case by the caller
        TRANSITION[S_INT][C_DIGIT] = CONTINUE;
        TRANSITION[S_INT][C_OTHER] = STOP;

        // S_FLOAT: digits keep extending the fractional part
        TRANSITION[S_FLOAT][C_LETTER] = STOP; // handled as an error case by the caller
        TRANSITION[S_FLOAT][C_DIGIT] = CONTINUE;
        TRANSITION[S_FLOAT][C_OTHER] = STOP;
    }

    private static int classify(char c) {
        if (Character.isLetter(c)) return C_LETTER;
        if (Character.isDigit(c)) return C_DIGIT;
        return C_OTHER;
    }

    private final String src;
    private int pos;
    private int line;
    private int col;
    private final List<Token> tokens = new ArrayList<>();

    public TableDrivenLexer(String source) {
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
                runTable(S_ID, startLine, startCol);
            } else if (Character.isDigit(c)) {
                scanNumberViaTable(startLine, startCol);
            } else if (c == '=') {
                advance();
                if (pos < src.length() && src.charAt(pos) == '=') {
                    advance();
                    tokens.add(new Token(TokenType.OPERATOR, "==", startLine, startCol));
                } else {
                    tokens.add(new Token(TokenType.OPERATOR, "=", startLine, startCol));
                }
            } else if (LanguageSpec.SINGLE_OPERATORS.contains(c)) {
                tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c), startLine, startCol));
                advance();
            } else if (LanguageSpec.SYMBOLS.contains(c)) {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), startLine, startCol));
                advance();
            } else {
                tokens.add(new Token(TokenType.ERROR, String.valueOf(c), startLine, startCol));
                advance();
            }
        }
        return tokens;
    }

    /** Generic table-driven run used for identifiers/keywords (state S_ID). */
    private void runTable(int state, int startLine, int startCol) {
        int start = pos;
        while (pos < src.length()) {
            int cls = classify(src.charAt(pos));
            if (TRANSITION[state][cls] == STOP) break;
            advance();
        }
        String lexeme = src.substring(start, pos);
        if (state == S_ID) {
            TokenType type = LanguageSpec.KEYWORDS.contains(lexeme) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
            tokens.add(new Token(type, lexeme, startLine, startCol));
        }
    }

    /** Number scanning: table drives digit runs; a 1-char lookahead resolves the '.'. */
    private void scanNumberViaTable(int startLine, int startCol) {
        int start = pos;
        int state = S_INT;

        // integer part, driven by the table
        while (pos < src.length()) {
            int cls = classify(src.charAt(pos));
            if (TRANSITION[state][cls] == STOP) break;
            if (cls == C_LETTER) break; // let outer edge-case check below handle it
            advance();
        }

        // optional fractional part - '.' only consumed if a digit follows it
        if (pos < src.length() && src.charAt(pos) == '.'
                && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
            advance(); // consume '.'
            state = S_FLOAT;
            while (pos < src.length()) {
                int cls = classify(src.charAt(pos));
                if (TRANSITION[state][cls] == STOP) break;
                if (cls == C_LETTER) break;
                advance();
            }
        }

        // EDGE CASE: digit run immediately followed by a letter (e.g. "123abc")
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

    private void advance() {
        pos++;
        col++;
    }
}
