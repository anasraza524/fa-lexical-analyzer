import java.util.List;
import java.util.Scanner;

public class Main {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("interactive")) {
            runInteractive();
            return;
        }

        printAssignmentExample();
        runEdgeCaseSuite();
        runBenchmark();

        System.out.println("\n================================================================");
        System.out.printf("TEST SUMMARY: %d passed, %d failed%n", passCount, failCount);
        System.out.println("================================================================");

        if (failCount > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------
    // 1. Reproduce the exact example from the problem statement
    // ---------------------------------------------------------------
    private static void printAssignmentExample() {
        String line = "----------------------------------------------------------------";
        System.out.println(line);
        System.out.println("ASSIGNMENT WORKED EXAMPLE");
        System.out.println(line);
        String input = "if (x == 10) return y + z;";
        System.out.println("INPUT:  " + input);
        System.out.println("OUTPUT (Direct DFA):");
        for (Token t : new DirectDfaLexer(input).tokenize()) {
            System.out.println("  " + t);
        }
        System.out.println();
    }

    // ---------------------------------------------------------------
    // 2. Edge-case regression suite.
    //    Every input is run through BOTH lexer implementations; a case
    //    passes only if (a) the two implementations agree with each
    //    other, and (b) they match the hand-verified expected output.
    // ---------------------------------------------------------------
    private static void runEdgeCaseSuite() {
        String line = "----------------------------------------------------------------";
        System.out.println(line);
        System.out.println("EDGE CASE REGRESSION SUITE");
        System.out.println(line);

        // --- Normal / happy-path cases -------------------------------------------------
        check("Simple if-statement",
                "if (x == 10) return y + z;",
                "[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [OPERATOR: +] [IDENTIFIER: z] [SYMBOL: ;]");

        check("While loop with float, no relational operator",
                "while (rate == 3.14) { total = total + 1; }",
                "[KEYWORD: while] [SYMBOL: (] [IDENTIFIER: rate] [OPERATOR: ==] [NUMBER: 3.14] [SYMBOL: )] [SYMBOL: {] [IDENTIFIER: total] [OPERATOR: =] [IDENTIFIER: total] [OPERATOR: +] [NUMBER: 1] [SYMBOL: ;] [SYMBOL: }]");

        // Note: '<' is intentionally NOT in the operator set from the problem
        // statement ({+,-,*,/,=,==}), so it must be reported as ERROR, not
        // silently accepted. This double-checks the spec is followed exactly.
        check("While loop with an operator outside the spec ('<') is flagged",
                "while (rate < 3.14) total = total + 1;",
                "[KEYWORD: while] [SYMBOL: (] [IDENTIFIER: rate] [ERROR: <] [NUMBER: 3.14] [SYMBOL: )] [IDENTIFIER: total] [OPERATOR: =] [IDENTIFIER: total] [OPERATOR: +] [NUMBER: 1] [SYMBOL: ;]");

        // --- Empty / whitespace-only input ----------------------------------------------
        check("Empty string", "", "");
        check("Whitespace only", "   \n\t  \n  ", "");

        // --- Identifier vs keyword boundary ----------------------------------------------
        check("Keyword-prefixed identifier is NOT split ('returnValue')",
                "returnValue = 1;",
                "[IDENTIFIER: returnValue] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]");
        check("Exact keyword match",
                "else",
                "[KEYWORD: else]");
        check("Identifier with digits ('a1b2')",
                "a1b2 = 3;",
                "[IDENTIFIER: a1b2] [OPERATOR: =] [NUMBER: 3] [SYMBOL: ;]");
        check("Single-letter identifier",
                "a = b;",
                "[IDENTIFIER: a] [OPERATOR: =] [IDENTIFIER: b] [SYMBOL: ;]");

        // --- Operator boundary cases -------------------------------------------------
        check("'==' takes priority over two '=' (maximal munch)",
                "a==b",
                "[IDENTIFIER: a] [OPERATOR: ==] [IDENTIFIER: b]");
        check("Single '=' at end of input (no second '=' to look ahead to)",
                "a=",
                "[IDENTIFIER: a] [OPERATOR: =]");
        check("Chained '=' ('a===b') greedily takes '==' first, then '=' (maximal munch)",
                "a===b",
                "[IDENTIFIER: a] [OPERATOR: ==] [OPERATOR: =] [IDENTIFIER: b]");
        check("Adjacent operators with no whitespace ('a=-b')",
                "a=-b",
                "[IDENTIFIER: a] [OPERATOR: =] [OPERATOR: -] [IDENTIFIER: b]");

        // --- Number edge cases ---------------------------------------------------------
        check("Plain integer",
                "42;",
                "[NUMBER: 42] [SYMBOL: ;]");
        check("Plain float",
                "3.14;",
                "[NUMBER: 3.14] [SYMBOL: ;]");
        check("Trailing dot with no following digit ('5.') splits into NUMBER + ERROR",
                "5.;",
                "[NUMBER: 5] [ERROR: .] [SYMBOL: ;]");
        check("Two decimal points ('3.14.15') splits into NUMBER + ERROR + NUMBER",
                "3.14.15;",
                "[NUMBER: 3.14] [ERROR: .] [NUMBER: 15] [SYMBOL: ;]");
        check("Number immediately followed by letters ('123abc') is a single ERROR token",
                "123abc = 1;",
                "[ERROR: 123abc] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]");
        check("Leading dot with no digit before it ('.5') - dot alone is an ERROR, then NUMBER",
                ".5;",
                "[ERROR: .] [NUMBER: 5] [SYMBOL: ;]");

        // --- Unknown / illegal characters ------------------------------------------------
        check("Unknown character '@' is reported, not silently dropped",
                "a = @;",
                "[IDENTIFIER: a] [OPERATOR: =] [ERROR: @] [SYMBOL: ;]");
        check("Multiple unknown characters in a row ('#$?')",
                "#$?",
                "[ERROR: #] [ERROR: $] [ERROR: ?]");
        check("Underscore is not a letter or digit per spec, so it errors",
                "my_var = 1;",
                "[IDENTIFIER: my] [ERROR: _] [IDENTIFIER: var] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]");

        // --- Whitespace / formatting robustness -----------------------------------------
        check("No spaces at all between tokens still tokenizes correctly",
                "if(x==10)return y+z;",
                "[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [OPERATOR: +] [IDENTIFIER: z] [SYMBOL: ;]");
        check("Tabs, newlines and extra spaces are all treated as separators",
                "if\t(x\n==\n   10)\treturn y;",
                "[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [SYMBOL: ;]");

        // --- All symbols / operators individually -----------------------------------------
        check("All special characters recognized",
                "(){};",
                "[SYMBOL: (] [SYMBOL: )] [SYMBOL: {] [SYMBOL: }] [SYMBOL: ;]");
        check("All arithmetic operators recognized",
                "+ - * / =",
                "[OPERATOR: +] [OPERATOR: -] [OPERATOR: *] [OPERATOR: /] [OPERATOR: =]");

        // --- Longer, realistic program -----------------------------------------------------
        check("Nested block with mixed tokens",
                "if (count == 3) { while (sum == 0) { sum = sum + 42.5; } } else return sum;",
                "[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: count] [OPERATOR: ==] [NUMBER: 3] [SYMBOL: )] [SYMBOL: {] [KEYWORD: while] [SYMBOL: (] [IDENTIFIER: sum] [OPERATOR: ==] [NUMBER: 0] [SYMBOL: )] [SYMBOL: {] [IDENTIFIER: sum] [OPERATOR: =] [IDENTIFIER: sum] [OPERATOR: +] [NUMBER: 42.5] [SYMBOL: ;] [SYMBOL: }] [SYMBOL: }] [KEYWORD: else] [KEYWORD: return] [IDENTIFIER: sum] [SYMBOL: ;]");
    }

    /**
     * Runs an input through both lexer implementations, verifies they agree
     * with each other (cross-validation between the two report "approaches"),
     * and checks the result against a hand-computed expected token stream.
     */
    private static void check(String description, String input, String expected) {
        List<Token> directTokens = new DirectDfaLexer(input).tokenize();
        List<Token> tableTokens = new TableDrivenLexer(input).tokenize();

        String directStr = joinTokens(directTokens);
        String tableStr = joinTokens(tableTokens);

        boolean implementationsAgree = directStr.equals(tableStr);
        boolean matchesExpected = directStr.equals(expected.trim());

        boolean pass = implementationsAgree && matchesExpected;
        if (pass) {
            passCount++;
        } else {
            failCount++;
        }

        System.out.println((pass ? "PASS" : "FAIL") + " - " + description);
        if (!pass) {
            System.out.println("    input:            " + input);
            System.out.println("    expected:         " + expected.trim());
            System.out.println("    direct DFA got:   " + directStr);
            System.out.println("    table-driven got: " + tableStr);
        }
    }

    private static String joinTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // 3. Performance analysis across increasing input sizes
    //    (required deliverable: "Performance analysis for different
    //    sizes of input programs")
    // ---------------------------------------------------------------
    private static void runBenchmark() {
        String line = "----------------------------------------------------------------";
        System.out.println("\n" + line);
        System.out.println("PERFORMANCE ANALYSIS (Direct DFA vs Table-Driven)");
        System.out.println(line);

        int[] statementCounts = {100, 1_000, 10_000, 100_000};
        System.out.printf("%-18s %-14s %-16s %-16s %-16s%n",
                "Statements", "Tokens", "Direct DFA (ms)", "Table-Driven(ms)", "Chars");

        final int WARMUP_RUNS = 5;
        final int TIMED_RUNS = 7;

        for (int n : statementCounts) {
            String code = generateSyntheticProgram(n);

            // Warm up the JIT so timings reflect steady-state compiled code,
            // not interpreter/compilation overhead on the first pass.
            for (int w = 0; w < WARMUP_RUNS; w++) {
                new DirectDfaLexer(code).tokenize();
                new TableDrivenLexer(code).tokenize();
            }

            double bestDirectMs = Double.MAX_VALUE;
            double bestTableMs = Double.MAX_VALUE;
            List<Token> directTokens = null;
            List<Token> tableTokens = null;

            for (int r = 0; r < TIMED_RUNS; r++) {
                long t0 = System.nanoTime();
                directTokens = new DirectDfaLexer(code).tokenize();
                long t1 = System.nanoTime();
                tableTokens = new TableDrivenLexer(code).tokenize();
                long t2 = System.nanoTime();

                bestDirectMs = Math.min(bestDirectMs, (t1 - t0) / 1_000_000.0);
                bestTableMs = Math.min(bestTableMs, (t2 - t1) / 1_000_000.0);
            }

            boolean agree = joinTokens(directTokens).equals(joinTokens(tableTokens));

            System.out.printf("%-18d %-14d %-16.3f %-16.3f %-16d %s%n",
                    n, directTokens.size(), bestDirectMs, bestTableMs, code.length(),
                    agree ? "" : "  <== MISMATCH!");
        }
    }

    /** Builds a synthetic but syntactically representative program of n statements. */
    private static String generateSyntheticProgram(int statements) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statements; i++) {
            switch (i % 4) {
                case 0:
                    sb.append("if (count").append(i).append(" == 10) return value").append(i).append(";\n");
                    break;
                case 1:
                    sb.append("while (sum < 3.14) { sum = sum + 1; }\n");
                    break;
                case 2:
                    sb.append("total").append(i).append(" = total").append(i).append(" + 42.5;\n");
                    break;
                default:
                    sb.append("else return x").append(i).append(";\n");
                    break;
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Optional interactive mode (run: java Main interactive)
    // ---------------------------------------------------------------
    private static void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter code to tokenize ('quit' to exit):");
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            if (input.trim().equalsIgnoreCase("quit")) break;
            for (Token t : new DirectDfaLexer(input).tokenize()) {
                System.out.println("  " + t);
            }
        }
        scanner.close();
    }
}
