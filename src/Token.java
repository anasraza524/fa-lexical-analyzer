/**
 * Immutable representation of a single recognized token.
 * Stores the 1-based line and column of the token's first character so
 * error messages / reports can point back at the exact source location.
 */
public final class Token {
    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;

    public Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** Matches the exact "[TYPE: lexeme]" output format required by the assignment. */
    @Override
    public String toString() {
        return "[" + type + ": " + lexeme + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token t = (Token) o;
        return type == t.type && lexeme.equals(t.lexeme);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + lexeme.hashCode();
    }
}
