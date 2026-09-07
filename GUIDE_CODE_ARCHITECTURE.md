# How the Code Is Built — Complete Architecture & Developer Guide

---

## PART 1 — PROJECT STRUCTURE

```
fa-lexical-analyzer/
├── src/                        ← all Java source files
│   ├── TokenType.java          ← enum: 6 token categories
│   ├── Token.java              ← immutable token data class
│   ├── LanguageSpec.java       ← language definition (keywords, ops, symbols)
│   ├── DirectDfaLexer.java     ← Approach 1: hand-coded DFA
│   ├── TableDrivenLexer.java   ← Approach 2: table-driven DFA
│   ├── Main.java               ← CLI: example + 27 tests + benchmark
│   ├── LexerUI.java            ← Swing GUI main window
│   ├── DfaPanel.java           ← Graphics2D DFA state diagram
│   └── TuringMachinePanel.java ← Turing Machine mode panel
├── out/                        ← compiled .class files (generated)
├── run.sh                      ← one-command build + run script
├── README.md                   ← project overview
├── run_output.txt              ← captured CLI output for report
├── GUIDE_DFA_LEXER.md          ← DFA viva guide
├── GUIDE_TURING_MACHINE.md     ← TM viva guide
└── GUIDE_CODE_ARCHITECTURE.md  ← this file
```

---

## PART 2 — DEPENDENCY GRAPH

```
TokenType.java
    ↑
Token.java ──────────────────────────────────────────────┐
    ↑                                                     │
LanguageSpec.java                                         │
    ↑              ↑                                      │
DirectDfaLexer  TableDrivenLexer                          │
    ↑              ↑                                      │
    └──────┬────────┘                                     │
           │                                              │
        Main.java (CLI)                                   │
           │                                              │
        LexerUI.java ← DfaPanel.java ← Token.java ───────┘
           │
        TuringMachinePanel.java (standalone, no Token dependency)
```

Key design principle: each file has ONE responsibility.
- TokenType/Token — data only
- LanguageSpec — language definition only
- DirectDfaLexer/TableDrivenLexer — scanning logic only
- Main — CLI orchestration only
- LexerUI — GUI layout and wiring only
- DfaPanel — diagram drawing only
- TuringMachinePanel — TM simulation + UI, self-contained

---

## PART 3 — HOW EACH FILE WAS BUILT

### TokenType.java — built first
The simplest file. Defines the 6 categories as a Java enum.
Using an enum instead of String constants means the compiler catches typos
and switch statements are exhaustive.

```java
public enum TokenType {
    KEYWORD, IDENTIFIER, OPERATOR, NUMBER, SYMBOL, ERROR
}
```
ERROR is included from the start — a design decision that invalid input
should always produce a token (with location) rather than throw an exception.

---

### Token.java — built second
An immutable value object. `final` class, all fields `private final`.
No setters. Once created, a Token never changes.

Key methods:
- `toString()` → `[TYPE: lexeme]` — the exact output format required
- `equals()` → compares type + lexeme only (ignores line/col) — used in tests
- `hashCode()` → consistent with equals

Line and column are stored as 1-based integers (humans count from 1).

---

### LanguageSpec.java — built third
A utility class with only static fields. Private constructor prevents
instantiation. Uses `HashSet` for O(1) lookup of keywords, symbols, operators.

```java
public static final Set<String> KEYWORDS = new HashSet<>(
    Arrays.asList("if", "else", "while", "return"));
```

Why HashSet? Because `contains()` is O(1) average. We call it on every
identifier scan, so performance matters.

`=` is NOT in SINGLE_OPERATORS because it needs special handling for `==`.

---

### DirectDfaLexer.java — core of Approach 1

Built around a single `tokenize()` method that loops over the input.
The design mirrors a real DFA:
- `pos` = head position
- `line`, `col` = location tracking
- Each `scan*()` method = one DFA state

The main loop dispatches based on the first character:
```
first char is letter  → scanIdentifierOrKeyword()
first char is digit   → scanNumber()
first char is '='     → scanEquals()
first char is op      → emit OPERATOR directly
first char is symbol  → emit SYMBOL directly
anything else         → emit ERROR directly
```

`advance()` is the only method that moves `pos` and `col`. This ensures
position tracking is never accidentally skipped.

---

### TableDrivenLexer.java — core of Approach 2

Built to be structurally parallel to DirectDfaLexer but use a table.
The transition table is a 2D int array initialized in a static block.

```java
static {
    TRANSITION[S_ID][C_LETTER] = CONTINUE;
    TRANSITION[S_ID][C_DIGIT]  = CONTINUE;
    TRANSITION[S_ID][C_OTHER]  = STOP;
    // ... etc
}
```

The `runTable()` method is the generic engine:
```java
while (pos < src.length()) {
    int cls = classify(src.charAt(pos));
    if (TRANSITION[state][cls] == STOP) break;
    advance();
}
```
This same loop handles identifiers. Numbers need extra logic for the
'.' lookahead, which cannot be expressed in a simple 2D table.

---

### Main.java — CLI entry point

Three sections:
1. `printAssignmentExample()` — runs the exact example from the problem statement
2. `runEdgeCaseSuite()` — 27 regression tests, each run through BOTH lexers
3. `runBenchmark()` — performance analysis at 4 input sizes

The `check()` method is the test harness:
```java
private static void check(String description, String input, String expected) {
    List<Token> directTokens = new DirectDfaLexer(input).tokenize();
    List<Token> tableTokens  = new TableDrivenLexer(input).tokenize();
    // verify both agree with each other AND match expected
}
```

The benchmark uses `System.nanoTime()` for timing, runs 5 warmup iterations
(to let the JVM JIT-compile the hot paths), then takes the best of 7 timed runs.

---

### LexerUI.java — Swing GUI

Built as a JFrame with a CardLayout for mode switching.

Layout structure:
```
JFrame
├── topBar (FlowLayout)
│   ├── JLabel "Lexer:"
│   ├── JComboBox (Direct DFA / Table-Driven)
│   ├── JButton "Analyze"
│   ├── JSeparator
│   └── JToggleButton "Turing Machine"
├── contentWrapper (CardLayout)
│   ├── "DFA" card → dfaModePanel
│   │   └── JSplitPane
│   │       ├── inputPanel (left)
│   │       └── outputTabs (right)
│   │           ├── Token Log tab
│   │           ├── Report tab
│   │           └── DFA Diagram tab
│   └── "TM" card → TuringMachinePanel
└── statusBar (JLabel)
```

The toggle button switches between cards:
```java
tmToggle.addActionListener(e -> {
    CardLayout cl = (CardLayout) contentWrapper.getLayout();
    if (tmToggle.isSelected()) {
        cl.show(contentWrapper, "TM");
    } else {
        cl.show(contentWrapper, "DFA");
    }
});
```

The `colorizeRows()` method sets a custom `DefaultTableCellRenderer` on
the token table. It reads the type string from column 1 and sets the
background color accordingly.

The `buildReport()` method runs both lexers on the current input, computes
token counts, cross-validates, and runs a 200-iteration performance test.

---

### DfaPanel.java — Graphics2D diagram

Extends JPanel and overrides `paintComponent(Graphics g)`.
All drawing is done with `Graphics2D` (cast from the Graphics parameter).

State positions are hardcoded as arrays:
```java
int[] sx = { cx-280, cx-100, cx-100, cx+80, cx+80, cx+260, cx-100 };
int[] sy = { cy,     cy-130, cy,     cy-130, cy,    cy,     cy+130 };
```

Drawing order: arrows first (behind states), then state circles on top.

`drawArrow()` — calculates start/end points offset by radius r so arrows
touch the circle edge, not the center. Uses `Math.atan2` for the angle.

`drawArrowHead()` — draws a filled triangle at the arrow tip using
`fillPolygon()` with 3 points calculated from the arrow angle.

`drawSelfLoop()` — draws a small circle above the state for self-transitions.

`drawState()` — fills a circle, draws border, adds double ring for ACCEPT,
draws start arrow for START, writes label in white.

States are highlighted based on which token types appear in the current
token list. The `setTokens()` method is called from LexerUI after each
analysis, then `repaint()` triggers a fresh `paintComponent()` call.

---

### TuringMachinePanel.java — TM simulation

Self-contained panel. No dependency on Token, TokenType, or the lexers.

Key design decisions:
1. `record Step(...)` — Java 16 record for immutable step snapshots
2. `record TestCase(...)` — Java 16 record for test case data
3. `simulate()` — lightweight version of `runTM()` with no UI side effects,
   used by the test runner to avoid polluting the trace table
4. `buildTape()` — static helper, separated from simulation logic
5. `testDialog` — a non-modal JDialog so the test results window can stay
   open while the user interacts with the main panel

The test runner iterates TEST_CASES, calls `simulate()` for each, compares
result to `expectAccept`, and populates the test table.

---

## PART 4 — DESIGN PATTERNS USED

### 1. Single Source of Truth (LanguageSpec)
Both lexers read from one place. If you change a keyword, both lexers
automatically pick it up. No duplication, no risk of inconsistency.

### 2. Strategy Pattern (implicit)
DirectDfaLexer and TableDrivenLexer are interchangeable — both have
`tokenize()` returning `List<Token>`. LexerUI switches between them
based on the dropdown selection.

### 3. Immutable Value Object (Token)
Token is final with no setters. Once created it cannot be modified.
This makes it safe to pass around, store in lists, and compare.

### 4. Card Layout for mode switching (LexerUI)
Instead of showing/hiding panels manually, CardLayout manages two
complete panels and swaps them with a single `show()` call.

### 5. Separation of concerns
- Data (Token, TokenType) is separate from logic (lexers).
- Logic (lexers) is separate from UI (LexerUI).
- Diagram drawing (DfaPanel) is separate from the main window.
- TM simulation (TuringMachinePanel) is fully self-contained.

---

## PART 5 — BUILD SYSTEM

No build tool (Maven/Gradle) is used — plain `javac`.

### Compile:
```bash
cd src
javac -d ../out *.java
```
`-d ../out` puts all .class files in the `out/` directory.
`*.java` compiles all files at once — javac resolves dependencies automatically.

### Run GUI:
```bash
cd out
java LexerUI
```

### Run CLI:
```bash
cd out
java Main
java Main interactive
java Main path/to/file.txt
```

### One-command script (run.sh):
```bash
#!/bin/bash
export PATH="/usr/local/opt/openjdk@17/bin:$PATH"
cd "$(dirname "$0")"
javac -d out src/*.java && java -cp out LexerUI
```
`$(dirname "$0")` makes the script work from any directory.
`&&` ensures the GUI only launches if compilation succeeds.

---

## PART 6 — JAVA FEATURES USED

| Feature | Where used | Why |
|---------|-----------|-----|
| `enum` | TokenType | Type-safe token categories |
| `final class` | Token | Immutable value object |
| `HashSet` | LanguageSpec | O(1) keyword/symbol lookup |
| `ArrayList` | Both lexers | Dynamic token list |
| `record` | TuringMachinePanel | Concise immutable data classes |
| `static {}` block | TableDrivenLexer | Initialize transition table once |
| `CardLayout` | LexerUI | Panel switching without show/hide |
| `JToggleButton` | LexerUI | Mode toggle with pressed state |
| `Graphics2D` | DfaPanel | Anti-aliased diagram drawing |
| `DefaultTableModel` | LexerUI, TuringMachinePanel | Mutable table data |
| `DefaultTableCellRenderer` | Both UI files | Custom row coloring |
| `System.nanoTime()` | Main | High-resolution timing |
| `SwingUtilities.invokeLater` | LexerUI | Thread-safe Swing startup |

---

## PART 7 — VIVA QUESTIONS ABOUT THE CODE ARCHITECTURE

**Q1: Why are there two separate lexer classes instead of one with a flag?**
Separation of concerns. Each class has one job and is independently testable.
A flag would mix two different algorithms in one class, making both harder
to understand and modify.

**Q2: Why is LanguageSpec a separate class?**
Single source of truth. If keywords were hardcoded in both lexers, changing
a keyword would require editing two files and risking inconsistency. With
LanguageSpec, you change it once.

**Q3: Why is Token immutable (final class, no setters)?**
Tokens are created once and never need to change. Immutability makes them
safe to share between threads, safe to store in collections, and easier to
reason about. It also prevents accidental modification.

**Q4: Why does the benchmark use best-of-7 instead of average?**
Best-of-N is the standard for microbenchmarks because it captures the
steady-state JIT-compiled performance. Averages are skewed by GC pauses,
OS scheduling, and JIT compilation on early runs. The 5 warmup runs ensure
the JIT has compiled the hot paths before timing begins.

**Q5: Why does TuringMachinePanel have a separate simulate() method?**
The `runTM()` method has UI side effects (updates table, tape, result label).
The test runner needs to run 16 simulations without touching the UI.
`simulate()` is a pure function — same input always gives same output,
no side effects.

**Q6: Why is CardLayout used instead of just hiding/showing panels?**
CardLayout is cleaner — it manages the layout of multiple panels and
switches between them with one method call. Manual show/hide requires
calling `setVisible(false)` on one and `setVisible(true)` on another,
and can cause layout issues.

**Q7: Why does the DFA diagram redraw on every Analyze click?**
`paintComponent()` is called by Swing whenever the panel needs to be
redrawn. `setTokens()` updates the data and `repaint()` schedules a
redraw. This keeps the diagram in sync with the current analysis.

**Q8: How would you add a new token type (e.g. STRING)?**
1. Add STRING to TokenType enum.
2. Add string scanning logic to DirectDfaLexer (scan until closing `"`).
3. Add STRING state to TableDrivenLexer's transition table.
4. Add color for STRING in LexerUI's colorizeRows().
5. Add STRING state to DfaPanel's diagram.
No other files need to change.

**Q9: Why is `advance()` a separate method instead of just `pos++`?**
Because `advance()` also increments `col`. If you wrote `pos++` directly
in multiple places, you might forget to also increment `col`, causing
incorrect column numbers in error messages. Encapsulating both in one
method prevents this bug.

**Q10: What would break if you removed LanguageSpec and hardcoded everything?**
The two lexers would have their own copies of the keyword/symbol sets.
If you added a keyword to one but forgot the other, they would produce
different output for the same input — the cross-validation tests would
catch this, but it would be a maintenance problem.
