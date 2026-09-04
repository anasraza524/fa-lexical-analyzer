import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Central, single-source-of-truth definition of the mini language exactly
 * as specified in the CCP problem statement:
 *   Keywords            : if, else, while, return
 *   Identifiers          : letter, then letters/digits
 *   Operators            : + - * / = ==
 *   Numbers               : integers and floating point (123, 3.14)
 *   Special characters : ( ) ; { }
 *
 * Both lexer implementations (DirectDfaLexer and TableDrivenLexer) read
 * from this one place so the two "different approaches" required by the
 * report are guaranteed to agree on what the language actually is -
 * they only differ in *how* they scan it.
 */
public final class LanguageSpec {

    public static final Set<String> KEYWORDS = new HashSet<>(
            Arrays.asList("if", "else", "while", "return"));

    public static final Set<Character> SYMBOLS = new HashSet<>(
            Arrays.asList('(', ')', ';', '{', '}'));

    // Single-character operators. '=' is handled specially because it can
    // extend into the two-character operator "==".
    public static final Set<Character> SINGLE_OPERATORS = new HashSet<>(
            Arrays.asList('+', '-', '*', '/'));

    private LanguageSpec() {
    }
}
