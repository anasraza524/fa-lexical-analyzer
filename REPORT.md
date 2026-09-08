# CCP Assignment Report
## Lexical Analyzer for a Mini Programming Language
### Theory of Automata — FEST, BS Computer Science

---

## Table of Contents

1. Introduction
2. Language Specification
3. System Architecture
4. Approach 1 — Direct DFA Lexer
5. Approach 2 — Table-Driven DFA Lexer
6. Comparison of Both Approaches
7. Turing Machine Extension
8. GUI Implementation
9. Test Results — All 27 Cases
10. Performance Analysis
11. CCP Requirements Checklist
12. Screenshots
13. Conclusion

---

## 1. Introduction

This project implements a **lexical analyzer (lexer)** for a mini programming language, built in Java (JDK 17). A lexical analyzer is the first phase of a compiler. It reads raw source code as a string and breaks it into a sequence of **tokens** — the smallest meaningful units of the language (keywords, identifiers, numbers, operators, symbols, and errors).

Two separate implementations were built and compared:

- **Approach 1 — Direct DFA**: Transitions are written as Java control flow (if/else, switch). This mirrors how hand-written compilers work.
- **Approach 2 — Table-Driven DFA**: Transitions are stored in a 2D array. A generic engine reads the table. This mirrors how generated scanners (lex/flex) work.

Both implementations are cross-validated against each other on every test case. They must produce identical token streams.

A **Turing Machine** extension was also built that checks whether input text is written entirely in Japanese, demonstrating a more powerful computational model.

---

## 2. Language Specification

The mini language is defined exactly as per the CCP problem statement.

### 2.1 Token Categories

| Token Type | Description |
|------------|-------------|
| KEYWORD | Reserved words: `if`, `else`, `while`, `return` |
| IDENTIFIER | Starts with a letter, followed by letters or digits |
| OPERATOR | `+` `-` `*` `/` `=` `==` |
| NUMBER | Integer (`42`) or float (`3.14`) |
| SYMBOL | `(` `)` `{` `}` `;` |
| ERROR | Any unrecognized character — never silently dropped |

### 2.2 Formal Grammar Rules

```
keyword    → if | else | while | return
identifier → letter (letter | digit)*
operator   → + | - | * | / | = | ==
number     → digit+ ('.' digit+)?
symbol     → ( | ) | { | } | ;
letter     → [a-zA-Z]
digit      → [0-9]
```

### 2.3 Single Source of Truth — LanguageSpec.java

All token definitions live in one file so both lexer implementations are guaranteed to agree on the language:

```java
public final class LanguageSpec {
    public static final Set<String> KEYWORDS =
        new HashSet<>(Arrays.asList("if", "else", "while", "return"));

    public static final Set<Character> SYMBOLS =
        new HashSet<>(Arrays.asList('(', ')', ';', '{', '}'));

    // '=' is handled separately because it can extend into "=="
    public static final Set<Character> SINGLE_OPERATORS =
        new HashSet<>(Arrays.asList('+', '-', '*', '/'));
}
```

---

## 3. System Architecture

### 3.1 File Structure

```
fa-lexical-analyzer/
├── src/
│   ├── TokenType.java          — Enum: KEYWORD, IDENTIFIER, OPERATOR, NUMBER, SYMBOL, ERROR
│   ├── Token.java              — Immutable token: type + lexeme + line + column
│   ├── LanguageSpec.java       — Single source of truth for keywords/operators/symbols
│   ├── DirectDfaLexer.java     — Approach 1: hand-coded DFA
│   ├── TableDrivenLexer.java   — Approach 2: table-driven DFA
│   ├── Main.java               — CLI: worked example + 27 regression tests + benchmark
│   ├── LexerUI.java            — Swing GUI
│   ├── DfaPanel.java           — Graphics2D DFA state diagram
│   └── TuringMachinePanel.java — Turing Machine mode (Japanese acceptor)
├── out/                        — Compiled .class files
├── run.sh                      — One-command build + launch
└── run_output.txt              — Captured CLI output
```

### 3.2 Dependency Graph

```
TokenType  ←  Token  ←  LanguageSpec
                  ↑              ↑
         DirectDfaLexer    TableDrivenLexer
                  ↑              ↑
                     Main.java
                     LexerUI.java
```

### 3.3 Token.java — Immutable Token Record

Every recognized unit is stored as a `Token`:

```java
public final class Token {
    private final TokenType type;
    private final String    lexeme;
    private final int       line;
    private final int       column;

    @Override
    public String toString() {
        return "[" + type + ": " + lexeme + "]";
    }
}
```

- `type` — which category (KEYWORD, IDENTIFIER, etc.)
- `lexeme` — the exact characters from the source
- `line` / `column` — 1-based position for error reporting

---

## 4. Approach 1 — Direct DFA Lexer

**File:** `src/DirectDfaLexer.java`

### 4.1 What is a Direct DFA?

A Direct DFA (also called a "hand-coded scanner") encodes every state transition as Java control flow — `if`, `else`, and method calls. Each method represents one DFA state. This is the style used by hand-written compilers such as early C compilers and CPython's tokenizer.

### 4.2 DFA State Diagram

```
              letter → [IN_ID] ──(letter|digit)──┐
             /                  └─────────────────┘
            /                        ↓ other → ACCEPT
[START] ──── digit  → [IN_INT] ──(digit)──┐
            \                  └──────────┘
             \                      ↓ '.'digit → [IN_FLOAT] → ACCEPT
              '=' → [IN_EQ] ──── '=' → ACCEPT (==)
              |                └──── other → ACCEPT (=)
              sym/op ──────────────────────→ ACCEPT
              other ──────────────────────→ ERROR
```

States:
- **START** — initial state, reads the next character
- **IN_ID** — inside an identifier or keyword (letter followed by letters/digits)
- **IN_INT** — inside the integer part of a number
- **IN_FLOAT** — inside the fractional part of a number (after `.`)
- **IN_EQ** — just read `=`, waiting to see if next char is also `=`
- **ACCEPT** — token is complete
- **ERROR** — unrecognized character

### 4.3 Code Walkthrough

#### Main Loop (START state)

```java
while (pos < src.length()) {
    char c = src.charAt(pos);

    if (c == '\n') { advance(); line++; col = 1; continue; }
    if (Character.isWhitespace(c)) { advance(); continue; }

    if      (Character.isLetter(c))              scanIdentifierOrKeyword(startLine, startCol);
    else if (Character.isDigit(c))               scanNumber(startLine, startCol);
    else if (c == '=')                           scanEquals(startLine, startCol);
    else if (LanguageSpec.SINGLE_OPERATORS.contains(c)) { /* emit OPERATOR */ advance(); }
    else if (LanguageSpec.SYMBOLS.contains(c))   { /* emit SYMBOL */ advance(); }
    else                                         { /* emit ERROR */ advance(); }
}
```

Whitespace and newlines are skipped (they are not tokens). Every other character dispatches to the correct state method.

#### IN_ID State — scanIdentifierOrKeyword()

```java
private void scanIdentifierOrKeyword(int startLine, int startCol) {
    int start = pos;
    while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) {
        advance();
    }
    String lexeme = src.substring(start, pos);
    TokenType type = LanguageSpec.KEYWORDS.contains(lexeme)
                     ? TokenType.KEYWORD : TokenType.IDENTIFIER;
    tokens.add(new Token(type, lexeme, startLine, startCol));
}
```

Keeps consuming while the character is a letter or digit (maximal munch). After the loop, checks if the collected lexeme is a keyword. This correctly handles `returnValue` — it is scanned as one identifier, not split into `return` + `Value`.

#### IN_INT / IN_FLOAT State — scanNumber()

```java
private void scanNumber(int startLine, int startCol) {
    int start = pos;
    while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();

    // Fractional part: '.' must be followed by at least one digit
    if (pos < src.length() && src.charAt(pos) == '.'
            && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
        advance(); // consume '.'
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
    }

    // Edge case: digit run followed by letter (e.g. "123abc") → single ERROR
    if (pos < src.length() && Character.isLetter(src.charAt(pos))) {
        while (pos < src.length() && Character.isLetterOrDigit(src.charAt(pos))) advance();
        tokens.add(new Token(TokenType.ERROR, src.substring(start, pos), startLine, startCol));
        return;
    }

    tokens.add(new Token(TokenType.NUMBER, src.substring(start, pos), startLine, startCol));
}
```

Key design decisions:
- `5.` (trailing dot, no digit after) → the `.` is NOT consumed as part of the number. It becomes a separate ERROR token.
- `3.14.15` → scanned as `NUMBER:3.14`, `ERROR:.`, `NUMBER:15`
- `123abc` → the entire run is one ERROR token (not split into NUMBER + IDENTIFIER)

#### IN_EQ State — scanEquals()

```java
private void scanEquals(int startLine, int startCol) {
    advance(); // consume first '='
    if (pos < src.length() && src.charAt(pos) == '=') {
        advance(); // consume second '='
        tokens.add(new Token(TokenType.OPERATOR, "==", startLine, startCol));
    } else {
        tokens.add(new Token(TokenType.OPERATOR, "=", startLine, startCol));
    }
}
```

One-character lookahead. If the next character is also `=`, emit `==`. Otherwise emit `=`. This correctly handles `a===b` → `==` then `=` (maximal munch).

---

## 5. Approach 2 — Table-Driven DFA Lexer

**File:** `src/TableDrivenLexer.java`

### 5.1 What is a Table-Driven DFA?

Instead of encoding transitions as control flow, the automaton is expressed as **data** — a 2D array `TRANSITION[state][charClass]`. A small generic engine reads the table in a loop. This is how generated scanners (lex, flex, JFlex) work internally.

### 5.2 States and Character Classes

```java
// States
private static final int S_ID    = 0;  // inside identifier/keyword
private static final int S_INT   = 1;  // inside integer part of number
private static final int S_FLOAT = 2;  // inside fractional part of number

// Character classes
private static final int C_LETTER = 0;
private static final int C_DIGIT  = 1;
private static final int C_OTHER  = 2;

// Table values
private static final int STOP     = 0;  // end the current token
private static final int CONTINUE = 1;  // consume char, stay in state
```

### 5.3 The Transition Table

```java
private static final int[][] TRANSITION = new int[3][3];
static {
    // S_ID: letters and digits extend an identifier
    TRANSITION[S_ID][C_LETTER] = CONTINUE;
    TRANSITION[S_ID][C_DIGIT]  = CONTINUE;
    TRANSITION[S_ID][C_OTHER]  = STOP;

    // S_INT: only digits extend the integer part
    TRANSITION[S_INT][C_LETTER] = STOP;
    TRANSITION[S_INT][C_DIGIT]  = CONTINUE;
    TRANSITION[S_INT][C_OTHER]  = STOP;

    // S_FLOAT: only digits extend the fractional part
    TRANSITION[S_FLOAT][C_LETTER] = STOP;
    TRANSITION[S_FLOAT][C_DIGIT]  = CONTINUE;
    TRANSITION[S_FLOAT][C_OTHER]  = STOP;
}
```

Visualized as a table:

| State \ Class | C_LETTER | C_DIGIT | C_OTHER |
|---------------|----------|---------|---------|
| S_ID          | CONTINUE | CONTINUE | STOP   |
| S_INT         | STOP     | CONTINUE | STOP   |
| S_FLOAT       | STOP     | CONTINUE | STOP   |

### 5.4 Generic Engine — runTable()

```java
private void runTable(int state, int startLine, int startCol) {
    int start = pos;
    while (pos < src.length()) {
        int cls = classify(src.charAt(pos));
        if (TRANSITION[state][cls] == STOP) break;
        advance();
    }
    String lexeme = src.substring(start, pos);
    TokenType type = LanguageSpec.KEYWORDS.contains(lexeme)
                     ? TokenType.KEYWORD : TokenType.IDENTIFIER;
    tokens.add(new Token(type, lexeme, startLine, startCol));
}
```

The engine never changes regardless of how many token types are added. Only the table needs updating.

### 5.5 Character Classifier

```java
private static int classify(char c) {
    if (Character.isLetter(c)) return C_LETTER;
    if (Character.isDigit(c))  return C_DIGIT;
    return C_OTHER;
}
```

Maps every possible character to one of three classes, reducing the alphabet from 65,536 Unicode code points to just 3 columns in the table.

---

## 6. Comparison of Both Approaches

| Aspect | Direct DFA | Table-Driven DFA |
|--------|-----------|-----------------|
| Transitions encoded as | Java control flow (if/else) | 2D array lookup |
| Adding a new token type | Edit code | Edit table data |
| Runtime speed | Faster (no indirection) | Slightly slower (array lookup) |
| Readability | Easy to follow state-by-state | Requires understanding the engine |
| Scalability | Gets complex for large grammars | Scales well (data, not code) |
| Used by | Hand-written compilers | lex/flex/JFlex generated scanners |
| Output agreement | ✅ Identical on all 27 test cases | ✅ Identical on all 27 test cases |

### 6.1 Benchmark Results (Best of 7 runs, JIT warmed up)

| Statements | Tokens | Direct DFA | Table-Driven | Characters |
|------------|--------|------------|--------------|------------|
| 100 | 825 | 0.355 ms | 0.371 ms | 2,888 |
| 1,000 | 8,250 | 0.694 ms | 0.748 ms | 30,113 |
| 10,000 | 82,500 | 2.000 ms | 2.093 ms | 313,613 |
| 100,000 | 825,000 | 22.535 ms | 24.285 ms | 3,261,113 |

Direct DFA is consistently faster because it avoids the array-lookup indirection on every character. At 100,000 statements (825,000 tokens), the difference is about 1.75 ms — negligible in practice but measurable.

Both implementations scale **linearly** with input size (O(n)), as expected for a single-pass lexer.

---

## 7. Turing Machine Extension

**File:** `src/TuringMachinePanel.java`

### 7.1 Formal Definition

The Turing Machine checks whether input text is written entirely in Japanese.

- **States:** `q_scan` (start), `q_accept`, `q_reject`
- **Tape alphabet:** All Unicode characters + `␣` (blank)
- **Head movement:** Always RIGHT (read-only, left-to-right scan)

**Transition function δ:**

| Current State | Symbol Read | Next State | Action |
|---------------|-------------|------------|--------|
| q_scan | Japanese character | q_scan | Move RIGHT |
| q_scan | ␣ (blank/end) | q_accept | HALT |
| q_scan | Non-Japanese | q_reject | Continue RIGHT (scan all) |

> **Design note:** Unlike a classical TM that halts on first rejection, this implementation continues scanning the entire tape so that **all** rejected characters are reported, not just the first one.

### 7.2 Accepted Character Ranges

| Range | Unicode | Script |
|-------|---------|--------|
| Hiragana | U+3040–U+309F | あいうえお |
| Katakana | U+30A0–U+30FF | アイウエオ |
| Shared CJK (Kanji) | U+4E00–U+9FAF | 漢字 |
| JP Punctuation | U+3000–U+303F | 。、「」 |
| Full-width forms* | U+FF00–U+FFEF | ！？ |
| Katakana Phonetic Ext. | U+31F0–U+31FF | ㇰㇱ |
| Halfwidth Katakana | U+FF65–U+FF9F | ｦｧ |

*Full-width digits (FF10–FF19) and full-width Latin (FF21–FF3A, FF41–FF5A) are excluded.

**Everything else causes REJECT** — spaces, ASCII digits, punctuation, Latin letters, Arabic, Korean, emoji.

### 7.3 isJapanese() — Core Classification Logic

```java
private static boolean isJapanese(char c) {
    if (c >= 0x3040 && c <= 0x309F) return true;  // Hiragana
    if (c >= 0x30A0 && c <= 0x30FF) return true;  // Katakana
    if (c >= 0x4E00 && c <= 0x9FAF) return true;  // Shared CJK (Kanji)
    if (c >= 0x3000 && c <= 0x303F) return true;  // JP Punctuation
    if (c >= 0xFF00 && c <= 0xFFEF) {
        if (c >= 0xFF10 && c <= 0xFF19) return false; // full-width digits
        if (c >= 0xFF21 && c <= 0xFF3A) return false; // full-width A-Z
        if (c >= 0xFF41 && c <= 0xFF5A) return false; // full-width a-z
        return true;
    }
    if (c >= 0x31F0 && c <= 0x31FF) return true;  // Katakana Phonetic Ext.
    if (c >= 0xFF65 && c <= 0xFF9F) return true;  // Halfwidth Katakana
    return false;
}
```

### 7.4 Full-Tape Scan — All Rejections Reported

```java
while (true) {
    char symbol = tape[head];
    if (symbol == BLANK) {
        // end of tape — verdict depends on whether any rejections occurred
        steps.add(new Step(..., rejectPositions.isEmpty() ? Q_ACCEPT : Q_REJECT));
        break;
    } else if (isAccepted(symbol)) {
        steps.add(new Step(..., Q_SCAN));
        head++;
    } else {
        rejectPositions.add(head);          // record ALL rejected positions
        steps.add(new Step(..., Q_REJECT));
        head++;                             // continue — do NOT break
    }
}
```

For input `こんにちは hello`, the result label shows:
```
REJECTED ❌ — Non-Japanese at position(s): 6:'h'(U+0068), 7:'e'(U+0065),
              8:'l'(U+006C), 9:'l'(U+006C), 10:'o'(U+006F)
```

Every rejected cell on the tape is highlighted red with a ▲ arrow.

### 7.5 TM Test Cases (35 cases, all pass)

| Category | Input | Expected | Result |
|----------|-------|----------|--------|
| Hiragana only | `こんにちは` | ACCEPT | ✅ |
| Katakana only | `コンピューター` | ACCEPT | ✅ |
| Kanji + Hiragana | `私は学生です` | ACCEPT | ✅ |
| Hiragana + JP punct | `すみません、ありがとう` | ACCEPT | ✅ |
| Kanji only | `漢字` | ACCEPT | ✅ |
| Mixed all JP scripts | `漢字ひらがなカタカナ` | ACCEPT | ✅ |
| Chinese (shared Kanji) | `你好吗` | ACCEPT | ✅ (ambiguous) |
| English sentence | `Hello` | REJECT | ✅ |
| English + JP | `こんにちはHello` | REJECT | ✅ |
| ASCII digits | `123` | REJECT | ✅ |
| Full-width digits | `１２３` | REJECT | ✅ |
| Space in middle | `こんにちは 世界` | REJECT | ✅ |
| Tab | `こんにちは\t世界` | REJECT | ✅ |
| Exclamation `!` | `こんにちは!` | REJECT | ✅ |
| JP period `。` | `日本語。` | ACCEPT | ✅ |
| JP comma `、` | `日本語、` | ACCEPT | ✅ |
| Arabic | `مرحبا` | REJECT | ✅ |
| Korean | `한국어` | REJECT | ✅ |
| Emoji at end | `こんにちは😀` | REJECT | ✅ |
| Empty string | `` | ACCEPT | ✅ |
| Single digit | `1` | REJECT | ✅ |
| Single space | ` ` | REJECT | ✅ |

---

## 8. GUI Implementation

**File:** `src/LexerUI.java`

### 8.1 Layout

The GUI uses `CardLayout` to switch between two modes:

```
JFrame
└── contentWrapper (CardLayout)
    ├── "DFA" card
    │   └── JSplitPane
    │       ├── LEFT: input editor (JTextArea)
    │       └── RIGHT: JTabbedPane
    │           ├── Token Log tab
    │           ├── Report tab
    │           └── DFA Diagram tab (DfaPanel)
    └── "TM" card
        └── TuringMachinePanel
```

### 8.2 Token Log Tab

Each token row is color-coded by type:

| Token Type | Color |
|------------|-------|
| KEYWORD | Blue |
| IDENTIFIER | Green |
| NUMBER | Orange/Gold |
| OPERATOR | Brown/Orange |
| SYMBOL | Purple |
| ERROR | Red |

Columns: `#`, `Type`, `Lexeme`, `Line`, `Column`

### 8.3 Report Tab

Shows:
- Total token count per type
- Cross-validation result (Direct DFA vs Table-Driven — must agree)
- Per-run timing for both implementations

### 8.4 DFA Diagram Tab

`DfaPanel.java` draws a live state diagram using `Graphics2D`. States visited for the current input are highlighted in their token-type color. States not visited are shown in grey.

States drawn: `START`, `IN_ID`, `IN_INT`, `IN_FLOAT`, `IN_EQ`, `ACCEPT`, `ERROR`

### 8.5 Turing Machine Mode

Clicking **⚙ Turing Machine** in the top bar switches the `CardLayout` to the TM card. The TM panel shows:
- Input text area
- Tape visualization (each cell is a colored box with index)
- Step-by-step trace table (Step, State, Head, Symbol, Classification, Next State)
- Result banner (green = ACCEPT, red = REJECT with all rejected positions listed)
- **🧪 Run All Tests** button — opens a dialog with all 35 test cases and pass/fail status

---

## 9. Test Results — All 27 Regression Cases

All 27 cases pass on **both** implementations. The `check()` method in `Main.java` runs each input through both lexers and verifies:
1. Both implementations produce identical output (cross-validation)
2. The output matches the hand-verified expected token stream

```
----------------------------------------------------------------
ASSIGNMENT WORKED EXAMPLE
----------------------------------------------------------------
INPUT:  if (x == 10) return y + z;
OUTPUT (Direct DFA):
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

### 9.1 Full Test Suite Results

| # | Test Case | Input | Expected Output | Result |
|---|-----------|-------|-----------------|--------|
| 1 | Simple if-statement | `if (x == 10) return y + z;` | `[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [OPERATOR: +] [IDENTIFIER: z] [SYMBOL: ;]` | ✅ PASS |
| 2 | While loop with float | `while (rate == 3.14) { total = total + 1; }` | `[KEYWORD: while] [SYMBOL: (] [IDENTIFIER: rate] [OPERATOR: ==] [NUMBER: 3.14] [SYMBOL: )] [SYMBOL: {] [IDENTIFIER: total] [OPERATOR: =] [IDENTIFIER: total] [OPERATOR: +] [NUMBER: 1] [SYMBOL: ;] [SYMBOL: }]` | ✅ PASS |
| 3 | Operator outside spec `<` | `while (rate < 3.14) total = total + 1;` | `... [ERROR: <] ...` | ✅ PASS |
| 4 | Empty string | `` | `` | ✅ PASS |
| 5 | Whitespace only | `   \n\t  ` | `` | ✅ PASS |
| 6 | Keyword-prefixed identifier | `returnValue = 1;` | `[IDENTIFIER: returnValue] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]` | ✅ PASS |
| 7 | Exact keyword | `else` | `[KEYWORD: else]` | ✅ PASS |
| 8 | Identifier with digits | `a1b2 = 3;` | `[IDENTIFIER: a1b2] [OPERATOR: =] [NUMBER: 3] [SYMBOL: ;]` | ✅ PASS |
| 9 | Single-letter identifier | `a = b;` | `[IDENTIFIER: a] [OPERATOR: =] [IDENTIFIER: b] [SYMBOL: ;]` | ✅ PASS |
| 10 | `==` maximal munch | `a==b` | `[IDENTIFIER: a] [OPERATOR: ==] [IDENTIFIER: b]` | ✅ PASS |
| 11 | Single `=` at end | `a=` | `[IDENTIFIER: a] [OPERATOR: =]` | ✅ PASS |
| 12 | Chained `===` | `a===b` | `[IDENTIFIER: a] [OPERATOR: ==] [OPERATOR: =] [IDENTIFIER: b]` | ✅ PASS |
| 13 | Adjacent operators `a=-b` | `a=-b` | `[IDENTIFIER: a] [OPERATOR: =] [OPERATOR: -] [IDENTIFIER: b]` | ✅ PASS |
| 14 | Plain integer | `42;` | `[NUMBER: 42] [SYMBOL: ;]` | ✅ PASS |
| 15 | Plain float | `3.14;` | `[NUMBER: 3.14] [SYMBOL: ;]` | ✅ PASS |
| 16 | Trailing dot `5.` | `5.;` | `[NUMBER: 5] [ERROR: .] [SYMBOL: ;]` | ✅ PASS |
| 17 | Two decimal points | `3.14.15;` | `[NUMBER: 3.14] [ERROR: .] [NUMBER: 15] [SYMBOL: ;]` | ✅ PASS |
| 18 | Number + letters `123abc` | `123abc = 1;` | `[ERROR: 123abc] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]` | ✅ PASS |
| 19 | Leading dot `.5` | `.5;` | `[ERROR: .] [NUMBER: 5] [SYMBOL: ;]` | ✅ PASS |
| 20 | Unknown char `@` | `a = @;` | `[IDENTIFIER: a] [OPERATOR: =] [ERROR: @] [SYMBOL: ;]` | ✅ PASS |
| 21 | Multiple unknowns `#$?` | `#$?` | `[ERROR: #] [ERROR: $] [ERROR: ?]` | ✅ PASS |
| 22 | Underscore `my_var` | `my_var = 1;` | `[IDENTIFIER: my] [ERROR: _] [IDENTIFIER: var] [OPERATOR: =] [NUMBER: 1] [SYMBOL: ;]` | ✅ PASS |
| 23 | No spaces between tokens | `if(x==10)return y+z;` | `[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [OPERATOR: +] [IDENTIFIER: z] [SYMBOL: ;]` | ✅ PASS |
| 24 | Tabs and newlines | `if\t(x\n==\n   10)\treturn y;` | `[KEYWORD: if] [SYMBOL: (] [IDENTIFIER: x] [OPERATOR: ==] [NUMBER: 10] [SYMBOL: )] [KEYWORD: return] [IDENTIFIER: y] [SYMBOL: ;]` | ✅ PASS |
| 25 | All special characters | `(){};` | `[SYMBOL: (] [SYMBOL: )] [SYMBOL: {] [SYMBOL: }] [SYMBOL: ;]` | ✅ PASS |
| 26 | All arithmetic operators | `+ - * / =` | `[OPERATOR: +] [OPERATOR: -] [OPERATOR: *] [OPERATOR: /] [OPERATOR: =]` | ✅ PASS |
| 27 | Nested block | `if (count == 3) { while (sum == 0) { sum = sum + 42.5; } } else return sum;` | Full token stream | ✅ PASS |

**Summary: 27 / 27 PASS — 0 FAIL**

---

## 10. Performance Analysis

### 10.1 Methodology

- Benchmark runs both lexers on synthetic programs of 100 / 1,000 / 10,000 / 100,000 statements
- **5 warm-up runs** before timing (allows JVM JIT compiler to compile the hot loops)
- **7 timed runs** — best time is recorded (eliminates GC pauses and OS scheduling noise)
- Synthetic program cycles through 4 statement patterns to produce realistic token variety

### 10.2 Results

| Statements | Tokens | Direct DFA | Table-Driven | Characters |
|------------|--------|------------|--------------|------------|
| 100 | 825 | 0.355 ms | 0.371 ms | 2,888 |
| 1,000 | 8,250 | 0.694 ms | 0.748 ms | 30,113 |
| 10,000 | 82,500 | 2.000 ms | 2.093 ms | 313,613 |
| 100,000 | 825,000 | 22.535 ms | 24.285 ms | 3,261,113 |

### 10.3 Analysis

**Time complexity:** O(n) for both implementations — each character is visited exactly once.

**Space complexity:** O(t) where t = number of tokens produced — both store the full token list.

**Why Direct DFA is faster:** Each character transition in the Direct DFA is a branch prediction-friendly `if/else` chain. The Table-Driven DFA adds one array index computation and one memory read per character (`TRANSITION[state][classify(c)]`). At 825,000 tokens this overhead accumulates to ~1.75 ms.

**Scaling:** From 100 to 100,000 statements (1000× more input):
- Direct DFA: 0.355 ms → 22.535 ms (~63× slower, near-linear)
- Table-Driven: 0.371 ms → 24.285 ms (~65× slower, near-linear)

Both scale linearly as expected for a single-pass O(n) algorithm.

---

## 11. CCP Requirements Checklist

| # | Requirement | Implementation | Status |
|---|-------------|----------------|--------|
| 1 | Keywords: `if`, `else`, `while`, `return` | `LanguageSpec.KEYWORDS`, recognized in both lexers | ✅ |
| 2 | Identifiers: `letter (letter\|digit)*` | `scanIdentifierOrKeyword()` / `runTable(S_ID)` | ✅ |
| 3 | Operators: `+` `-` `*` `/` `=` `==` | `SINGLE_OPERATORS` set + `scanEquals()` with lookahead | ✅ |
| 4 | Numbers: integers and floats | `scanNumber()` / `scanNumberViaTable()` with `.` lookahead | ✅ |
| 5 | Special characters: `(` `)` `;` `{` `}` | `LanguageSpec.SYMBOLS` | ✅ |
| 6 | Token stream in order of appearance | `List<Token>` built left-to-right, single pass | ✅ |
| 7 | Input as single string | `DirectDfaLexer(String)` / `TableDrivenLexer(String)` | ✅ |
| 8 | Input from file | `java Main path/to/file.txt` in `Main.java` | ✅ |
| 9 | Finite automaton for token recognition | Direct DFA + Table-Driven DFA, both fully implemented | ✅ |
| 10 | Implementation in a programming language | Java 17 | ✅ |
| 11 | Testing with various inputs | 27 regression cases covering all edge cases, all passing | ✅ |
| 12 | Performance analysis for different input sizes | 100 / 1K / 10K / 100K statements benchmarked | ✅ |
| 13 | Two approaches compared | DirectDfaLexer vs TableDrivenLexer, cross-validated | ✅ |

**All 13 CCP requirements fulfilled.**

---

## 12. Screenshots

> **Instructions:** Insert the following screenshots from the `images/` folder at each location below.

### 12.1 DFA Lexer — Token Log Tab
*(Insert `images/screenshot_1.png`)*

Shows the color-coded token table after analyzing input. Each row displays token number, type, lexeme, line, and column. Keywords appear in blue, identifiers in green, numbers in gold, operators in brown, symbols in purple, errors in red.

---

### 12.2 DFA Lexer — Report Tab
*(Insert `images/screenshot_2.png`)*

Shows the token breakdown by category, cross-validation result confirming both lexers agree, and per-run timing for Direct DFA and Table-Driven DFA.

---

### 12.3 DFA Lexer — DFA Diagram Tab
*(Insert `images/screenshot_3.png`)*

Shows the live state diagram. States visited for the current input are highlighted in their token-type color. Inactive states are grey. Transitions are labeled with the characters that trigger them.

---

### 12.4 Turing Machine Mode
*(Insert `images/screenshot_4.png`)*

Shows the TM panel with tape visualization, step-by-step trace table, and result banner. Rejected characters are highlighted red on the tape with ▲ arrows. The result label lists all rejected positions with their Unicode code points.

---

## 13. Conclusion

This project successfully implements a complete lexical analyzer for a mini programming language, fulfilling all 13 CCP requirements.

**Key achievements:**

1. **Two DFA approaches** — Direct DFA and Table-Driven DFA are both fully implemented, cross-validated on 27 test cases, and benchmarked. They produce identical output on all inputs.

2. **Robust error handling** — Invalid characters are never silently dropped. They are reported as ERROR tokens with exact line and column numbers. Edge cases like `123abc`, `5.`, `3.14.15`, and `returnValue` are all handled correctly.

3. **Maximal munch** — The lexer always produces the longest possible token. `==` is preferred over two `=` tokens. `3.14` is preferred over `3` + `.` + `14`.

4. **Performance** — Both implementations scale linearly (O(n)). At 100,000 statements (825,000 tokens), Direct DFA takes 22.5 ms and Table-Driven takes 24.3 ms.

5. **Turing Machine extension** — A TM that accepts Japanese-only text was implemented with full tape visualization, step-by-step trace, and complete rejection reporting (all rejected positions shown, not just the first).

6. **GUI** — A Swing GUI provides a code editor, color-coded token log, report tab with cross-validation, live DFA diagram, and Turing Machine mode — all in one application.

**Design principle:** Both lexer implementations share a single `LanguageSpec.java` as the source of truth for the language definition. This guarantees they can only differ in *how* they scan, never in *what* they recognize.

---

*Report prepared for Theory of Automata — FEST, BS Computer Science*
