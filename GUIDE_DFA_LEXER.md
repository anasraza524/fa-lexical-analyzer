# DFA Lexical Analyzer — Complete Code Explanation & Viva Guide

---

## PART 1 — WHAT IS A LEXICAL ANALYZER?

A lexical analyzer (lexer / scanner) is the **first phase of a compiler**.
It reads raw source code character by character and groups characters into
meaningful units called **tokens**.

Example:
```
Input:  if (x == 10) return y + z;
Output: [KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==]
        [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y]
        [OPERATOR: +] [IDENTIFIER: z] [SYMBOL: ;]
```

The lexer does NOT check grammar (that is the parser's job).
It only answers: "what kind of thing is this chunk of text?"

---

## PART 2 — THE MINI LANGUAGE SPECIFICATION

Defined in `LanguageSpec.java` — single source of truth for both lexers.

| Category   | Members                        | Rule                          |
|------------|-------------------------------|-------------------------------|
| KEYWORD    | if, else, while, return        | exact match after scanning ID |
| IDENTIFIER | myVar, x1, returnValue         | letter (letter\|digit)*       |
| OPERATOR   | + - * / = ==                   | single char or == (2 chars)   |
| NUMBER     | 42, 3.14                       | digit+ ('.' digit+)?          |
| SYMBOL     | ( ) { } ;                      | exact single character        |
| ERROR      | @ # _ 123abc                   | anything unrecognized         |

Key design decision: `=` is NOT in SINGLE_OPERATORS because it needs
lookahead to decide if it is `=` or `==`. All other operators are
single-character and need no lookahead.

---

## PART 3 — TOKEN.JAVA AND TOKENTYPE.JAVA

### TokenType.java
```java
public enum TokenType {
    KEYWORD, IDENTIFIER, OPERATOR, NUMBER, SYMBOL, ERROR
}
```
An enum is used so the compiler enforces that only valid categories exist.
ERROR is included so invalid input is never silently dropped — it becomes
a token with a location, which is important for error reporting.

### Token.java
```java
public final class Token {
    private final TokenType type;
    private final String lexeme;
    private final int line;
    private final int column;
}
```
- `final` class — immutable, cannot be subclassed or modified after creation.
- `lexeme` — the actual text (e.g. "while", "3.14", "+").
- `line` and `column` — 1-based position in the source, used for error messages.
- `toString()` returns `[TYPE: lexeme]` — exactly the format required by the assignment.
- `equals()` compares type + lexeme only (ignores position) — used in regression tests.

---

## PART 4 — APPROACH 1: DIRECT DFA (DirectDfaLexer.java)

### What is a Direct DFA?
Each state of the finite automaton is represented as a **branch in code**
(if/else, method call). Transitions are Java control flow, not data.

### State machine diagram:
```
         letter → [IN_ID] ──(letter|digit loop)──→ ACCEPT
        /
[START] ── digit → [IN_INT] ──(digit loop)──→ [IN_FLOAT] → ACCEPT
        \                    ↘ '.' + digit
         '=' → [IN_EQ] ──────────────────────────→ ACCEPT
        /                  ↘ second '=' → ==
         sym/op ──────────────────────────────────→ ACCEPT (direct)
        \
         other ────────────────────────────────────→ ERROR
```

### Key variables:
```java
private final String src;   // the full input string
private int pos;            // current character position (head)
private int line;           // current line number (1-based)
private int col;            // current column number (1-based)
```

### The main loop (tokenize method):
```java
while (pos < src.length()) {
    char c = src.charAt(pos);
    // skip whitespace
    // record startLine, startCol
    // branch to correct scanner based on first character
}
```
The first character determines which state we enter:
- Letter → `scanIdentifierOrKeyword()`
- Digit  → `scanNumber()`
- `=`    → `scanEquals()`
- `+`,`-`,`*`,`/` → single OPERATOR token
- `(`,`)`,`{`,`}`,`;` → single SYMBOL token
- anything else → ERROR token

### scanIdentifierOrKeyword():
```java
while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) {
    advance();
}
String lexeme = src.substring(start, pos);
// check if lexeme is in KEYWORDS set → KEYWORD, else → IDENTIFIER
```
This implements the rule: letter (letter|digit)*
After scanning, it checks the keyword set. This is why "returnValue" is
an IDENTIFIER, not split into KEYWORD "return" + IDENTIFIER "Value".

### scanNumber():
Three sub-states: IN_INT → (optional) IN_DOT → IN_FLOAT
```java
// scan digits (IN_INT state)
while (digit) advance();

// lookahead: '.' followed by digit → enter IN_FLOAT
if (src[pos] == '.' && isDigit(src[pos+1])) {
    advance(); // consume '.'
    while (digit) advance(); // IN_FLOAT state
}

// edge case: 123abc → ERROR (not a valid number or identifier)
if (isLetter(src[pos])) { ... emit ERROR; return; }
```
The 1-character lookahead for '.' is necessary because "5." should produce
NUMBER(5) + ERROR(.) not NUMBER(5.) — the dot is only consumed if a digit follows.

### scanEquals():
```java
advance(); // consume first '='
if (src[pos] == '=') {
    advance(); // consume second '='
    emit OPERATOR("==");
} else {
    emit OPERATOR("=");
}
```
This is **maximal munch** — always consume the longest valid token.

### advance():
```java
private void advance() { pos++; col++; }
```
Newlines are handled separately in the main loop to reset col to 1 and
increment line. This keeps line/column tracking accurate.

---

## PART 5 — APPROACH 2: TABLE-DRIVEN DFA (TableDrivenLexer.java)

### What is a Table-Driven DFA?
Instead of encoding transitions as code, they are stored as a **2D array**.
A generic engine loop reads the table. Adding new token types means editing
data, not code — this is how tools like lex/flex work internally.

### The transition table:
```java
int[][] TRANSITION = new int[NUM_STATES][NUM_CLASSES];
// States:  S_ID=0, S_INT=1, S_FLOAT=2
// Classes: C_LETTER=0, C_DIGIT=1, C_OTHER=2
// Values:  CONTINUE=1 (keep consuming), STOP=0 (end token)
```

Filled in a static block:
```java
TRANSITION[S_ID][C_LETTER] = CONTINUE;  // letters extend identifiers
TRANSITION[S_ID][C_DIGIT]  = CONTINUE;  // digits extend identifiers
TRANSITION[S_ID][C_OTHER]  = STOP;      // anything else ends the token

TRANSITION[S_INT][C_DIGIT] = CONTINUE;  // digits extend integers
TRANSITION[S_INT][C_LETTER]= STOP;      // letter after digit = error case
TRANSITION[S_INT][C_OTHER] = STOP;
```

### Character classification:
```java
private static int classify(char c) {
    if (Character.isLetter(c)) return C_LETTER;
    if (Character.isDigit(c))  return C_DIGIT;
    return C_OTHER;
}
```
Every character is mapped to one of 3 classes before the table lookup.

### Generic table engine (runTable):
```java
while (pos < src.length()) {
    int cls = classify(src.charAt(pos));
    if (TRANSITION[state][cls] == STOP) break;
    advance();
}
```
This single loop handles ALL multi-character tokens. The state variable
tells it which row of the table to use.

### Direct DFA vs Table-Driven — comparison:

| Aspect            | Direct DFA              | Table-Driven              |
|-------------------|-------------------------|---------------------------|
| Transitions       | Java if/else code       | 2D array lookup           |
| Adding new token  | Edit code               | Edit table data           |
| Speed             | Faster (no indirection) | Slightly slower           |
| Readability       | Easy to follow          | More abstract             |
| Used in           | Hand-written compilers  | Generated scanners (flex) |
| Our benchmark     | ~32ms / 100K stmts      | ~35ms / 100K stmts        |

Both produce **identical output** — verified by the cross-validation in
the Report tab and all 27 regression tests.

---

## PART 6 — EDGE CASES (27 REGRESSION TESTS)

### 1. Keyword vs Identifier boundary
- `returnValue` → IDENTIFIER (not split into KEYWORD + IDENTIFIER)
- `return` → KEYWORD
- Rule: scan the full word first, THEN check if it is a keyword.

### 2. Maximal munch for ==
- `a==b` → IDENTIFIER(a) OPERATOR(==) IDENTIFIER(b)
- `a===b` → IDENTIFIER(a) OPERATOR(==) OPERATOR(=) IDENTIFIER(b)
- Always consume the longest valid token.

### 3. Float edge cases
- `3.14` → NUMBER(3.14)
- `5.`   → NUMBER(5) ERROR(.)   — dot not followed by digit
- `3.14.15` → NUMBER(3.14) ERROR(.) NUMBER(15)

### 4. Number followed by letter
- `123abc` → ERROR(123abc) — the whole run is one error token
- Not split into NUMBER(123) + IDENTIFIER(abc) because identifiers
  must START with a letter per the grammar.

### 5. Unknown characters
- `@`, `#`, `_`, `?` → ERROR token each
- Never silently dropped — always reported with line and column.

### 6. Whitespace handling
- Spaces, tabs, newlines are all skipped (not tokens).
- Newlines increment the line counter and reset column to 1.

---

## PART 7 — PERFORMANCE BENCHMARK

The benchmark in Main.java generates synthetic programs of increasing size
and times both lexers (best of 7 runs, after 5 JIT warmup runs):

| Statements | Tokens  | Direct DFA | Table-Driven |
|------------|---------|------------|--------------|
| 100        | 825     | ~0.4 ms    | ~0.4 ms      |
| 1,000      | 8,250   | ~1.9 ms    | ~2.8 ms      |
| 10,000     | 82,500  | ~3.3 ms    | ~5.5 ms      |
| 100,000    | 825,000 | ~32 ms     | ~35 ms       |

Direct DFA is consistently faster because it has no table-lookup indirection.
The gap widens at larger inputs because the table lookup overhead accumulates.

---

## PART 8 — VIVA QUESTIONS & ANSWERS

**Q1: What is lexical analysis?**
The first phase of compilation. It reads source code and converts it into
a stream of tokens. It handles whitespace, comments, and reports invalid
characters. It does NOT check grammar.

**Q2: What is a token?**
A token is a pair (type, lexeme). Type is the category (KEYWORD, NUMBER etc.)
and lexeme is the actual text. Our Token also stores line and column.

**Q3: What is a DFA?**
A Deterministic Finite Automaton. A mathematical model with:
- A finite set of states Q
- An input alphabet Σ
- A transition function δ: Q × Σ → Q
- A start state q0
- A set of accepting states F
At each step, given the current state and input symbol, there is exactly
ONE next state (deterministic).

**Q4: What is the difference between DFA and NFA?**
NFA (Non-deterministic) can have multiple possible next states for the same
input, or ε-transitions (moves without consuming input). DFA has exactly
one next state per (state, symbol) pair. Every NFA can be converted to an
equivalent DFA (subset construction algorithm).

**Q5: What is maximal munch?**
The rule that the lexer always produces the longest possible token. Example:
`==` is always one OPERATOR token, never two `=` tokens. `3.14` is one
NUMBER, never NUMBER(3) + ERROR(.) + NUMBER(14).

**Q6: Why is `=` handled separately from other operators?**
Because `=` can be either a single-character operator OR the first character
of the two-character operator `==`. It requires one character of lookahead
to decide. The other operators (+, -, *, /) are always single-character.

**Q7: Why does `123abc` produce an ERROR instead of NUMBER + IDENTIFIER?**
Because identifiers must START with a letter per the grammar. `123abc` does
not match any valid token pattern, so the whole run is reported as one ERROR.

**Q8: What is the difference between Direct DFA and Table-Driven DFA?**
Direct DFA encodes transitions as Java control flow (if/else, method calls).
Table-Driven stores transitions in a 2D array and uses a generic loop.
Both produce identical output. Direct DFA is faster; Table-Driven is more
scalable for large grammars.

**Q9: How does the lexer track line and column numbers?**
It maintains `line` and `col` variables. `col` increments on every `advance()`.
When a newline `\n` is encountered, `line` increments and `col` resets to 1.

**Q10: What happens to whitespace?**
Whitespace (spaces, tabs, newlines) is consumed and skipped — it is not
a token. It acts as a separator between tokens.

**Q11: How does keyword recognition work?**
The lexer first scans a full identifier (letter followed by letters/digits).
After the full word is collected, it checks if the word is in the KEYWORDS
HashSet. If yes → KEYWORD token. If no → IDENTIFIER token. This ensures
"returnValue" is not split.

**Q12: What is the role of LanguageSpec.java?**
It is the single source of truth for the language definition. Both lexers
read from it. This guarantees they agree on what the language is — they
only differ in HOW they scan it.

**Q13: How are the two implementations validated against each other?**
The `check()` method in Main.java runs every test input through BOTH lexers
and compares their output. A test passes only if both implementations agree
AND both match the hand-verified expected output.

**Q14: What is the time complexity of the lexer?**
O(n) where n is the length of the input string. Each character is visited
at most twice (once in the main loop, once inside a scanner method).

**Q15: What would you need to add to support comments?**
In the main loop, when `//` is detected, skip all characters until `\n`.
For `/* */` block comments, skip until `*/` is found. Comments are not
tokens — they are discarded like whitespace.

---

## PART 9 — DFA DIAGRAM (DfaPanel.java)

The DFA Diagram tab draws the automaton using Java Graphics2D.

States drawn:
- START — entry point, arrow from left
- IN_ID — scanning identifier/keyword
- IN_INT — scanning integer part of number
- IN_FLOAT — scanning fractional part of number
- IN_EQ — seen one `=`, deciding if `=` or `==`
- ACCEPT — double-ring circle (standard DFA notation for accepting state)
- ERROR — reached on unrecognized character

States are highlighted in color based on which token types appear in the
current input. If the input has keywords, IN_ID lights up blue. If it has
numbers with decimals, both IN_INT and IN_FLOAT light up gold.

The diagram updates live every time you click Analyze.
