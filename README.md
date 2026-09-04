# Lexical Analyzer for a Mini Programming Language

Java (JDK 17+) implementation of the CCP lexical analyzer, built two
different ways (Direct DFA and Table-Driven DFA) so the two approaches
can be compared directly, as required by the report.

## Files
- `src/TokenType.java`      - token category enum
- `src/Token.java`          - token record (type, lexeme, line, column)
- `src/LanguageSpec.java`   - single source of truth for keywords/operators/symbols
- `src/DirectDfaLexer.java` - Approach 1: hand-coded / direct DFA
- `src/TableDrivenLexer.java` - Approach 2: table-driven DFA
- `src/Main.java`           - worked example, 27-case regression suite, performance benchmark
- `run_output.txt`          - captured output of a full run (used for the report's Results section)

## Build & run
```bash
cd src
javac -d ../out *.java
cd ../out
java Main                 # worked example + regression suite + benchmark
java Main interactive     # type your own code, 'quit' to exit
```

## What it recognizes
| Category   | Rule                                   |
|------------|-----------------------------------------|
| KEYWORD    | if, else, while, return                |
| IDENTIFIER | letter (letter\|digit)*                |
| OPERATOR   | + - * / = ==                           |
| NUMBER     | digit+ ('.' digit+)?                   |
| SYMBOL     | ( ) { } ;                              |
| ERROR      | anything else (reported, never dropped)|

All 27 regression cases pass on both implementations (see `run_output.txt`).
