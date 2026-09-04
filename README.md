# Lexical Analyzer for a Mini Programming Language

Java (JDK 17+) implementation of the CCP lexical analyzer for the Theory of Automata assignment
(FEST, BS Computer Science). Built two different ways — Direct DFA and Table-Driven DFA — so the
two approaches can be compared directly, as required by the report.

---

## Files

| File | Purpose |
|------|---------|
| `src/TokenType.java` | Token category enum (KEYWORD, IDENTIFIER, OPERATOR, NUMBER, SYMBOL, ERROR) |
| `src/Token.java` | Immutable token record — type, lexeme, line, column |
| `src/LanguageSpec.java` | Single source of truth for keywords, operators, and symbols |
| `src/DirectDfaLexer.java` | Approach 1: hand-coded / direct DFA |
| `src/TableDrivenLexer.java` | Approach 2: table-driven DFA |
| `src/Main.java` | CLI entry point — worked example, 27-case regression suite, performance benchmark, file input |
| `src/LexerUI.java` | Swing GUI — code editor, token log, report tab, DFA diagram |
| `src/DfaPanel.java` | Graphics2D DFA state diagram (used by LexerUI) |
| `run_output.txt` | Captured output of a full CLI run (used for the report's Results section) |

---

## Build

```bash
cd src
javac -d ../out *.java
```

Requires JDK 17+. On macOS with Homebrew:
```bash
brew install openjdk@17
echo 'export PATH="/usr/local/opt/openjdk@17/bin:$PATH"' >> ~/.zprofile
# open a new terminal, then build
```

---

## Run

### GUI (recommended)
```bash
cd out
java LexerUI
```
Opens a window with:
- Code editor (left) — type or paste any source code
- Token Log tab — color-coded token table (line + column included)
- Report tab — token breakdown, cross-validation (Direct vs Table-Driven), per-run performance
- DFA Diagram tab — live state diagram highlighting active states for the current input

### CLI
```bash
cd out
java Main                      # worked example + 27 regression tests + benchmark
java Main interactive          # REPL: type code line by line, 'quit' to exit
java Main path/to/file.txt     # tokenize a source file
```

---

## What it recognizes

| Category | Rule |
|----------|------|
| KEYWORD | `if` `else` `while` `return` |
| IDENTIFIER | letter (letter\|digit)* |
| OPERATOR | `+` `-` `*` `/` `=` `==` |
| NUMBER | digit+ (`'.'` digit+)? |
| SYMBOL | `(` `)` `{` `}` `;` |
| ERROR | anything else — reported with line/col, never silently dropped |

---

## Example

**Input:** `if (x == 10) return y + z;`

**Output:**
```
[KEYWORD: if]
[SYMBOL: (]
[IDENTIFIER: x]
[OPERATOR: ==]
[NUMBER: 10]
[SYMBOL: )]
[KEYWORD: return]
[IDENTIFIER: y]
[OPERATOR: +]
[IDENTIFIER: z]
[SYMBOL: ;]
```

---

## CCP Requirements Checklist

| Requirement | Fulfilled |
|-------------|-----------|
| Keywords: if, else, while, return | ✅ |
| Identifiers: letter (letter\|digit)* | ✅ |
| Operators: + - * / = == | ✅ |
| Numbers: integers and floats | ✅ |
| Special characters: ( ) ; { } | ✅ |
| Token stream in order of appearance | ✅ |
| Input as single string | ✅ |
| Input from file | ✅ `java Main file.txt` |
| Finite automaton for token recognition | ✅ Direct DFA + Table-Driven DFA |
| Implementation in a programming language | ✅ Java 17 |
| Testing with various inputs | ✅ 27 regression cases, all passing |
| Performance analysis for different input sizes | ✅ 100 / 1K / 10K / 100K statements |
| Two approaches compared (report requirement) | ✅ DirectDfaLexer vs TableDrivenLexer |

---

## Test Results

All 27 regression cases pass on both implementations (see `run_output.txt`).

Benchmark (best of 7 runs, JIT warmed up):

| Statements | Tokens | Direct DFA | Table-Driven |
|------------|--------|------------|--------------|
| 100 | 825 | ~0.4 ms | ~0.4 ms |
| 1,000 | 8,250 | ~1.9 ms | ~2.8 ms |
| 10,000 | 82,500 | ~3.3 ms | ~5.5 ms |
| 100,000 | 825,000 | ~32 ms | ~35 ms |

Direct DFA is consistently faster due to no table-lookup indirection.
