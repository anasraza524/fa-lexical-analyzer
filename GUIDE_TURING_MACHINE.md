# Turing Machine (Japanese Acceptor) — Complete Code Explanation & Viva Guide

---

## PART 1 — WHAT IS A TURING MACHINE?

A Turing Machine (TM) is the most powerful theoretical model of computation,
introduced by Alan Turing in 1936. Unlike a DFA (which can only read input),
a TM can READ and WRITE on an infinite tape and move the head in both directions.

### Formal definition of a TM:
A TM is a 7-tuple: M = (Q, Σ, Γ, δ, q0, q_accept, q_reject)

| Component  | Meaning                                              |
|------------|------------------------------------------------------|
| Q          | Finite set of states                                 |
| Σ          | Input alphabet (does not include blank ␣)            |
| Γ          | Tape alphabet (Σ ∪ {␣}, may include extra symbols)   |
| δ          | Transition function: Q × Γ → Q × Γ × {L, R}         |
| q0         | Start state                                          |
| q_accept   | Accepting (halting) state                            |
| q_reject   | Rejecting (halting) state                            |

The transition function δ(state, symbol) returns:
(next_state, symbol_to_write, direction_to_move)

---

## PART 2 — OUR SPECIFIC TM: JAPANESE LANGUAGE ACCEPTOR

### Purpose:
Accept a string if and only if every character is Japanese (or neutral).
Reject immediately when any non-Japanese character is found.

### Our TM definition:

**States Q = {q_scan, q_accept, q_reject}**
- q_scan   — start state, scanning left to right
- q_accept — halting accept state
- q_reject — halting reject state

**Tape alphabet Γ = input characters ∪ {␣}**
- ␣ (blank) marks the end of the input

**Transition function δ:**

| Current State | Symbol Read        | Next State | Write  | Move  |
|---------------|--------------------|------------|--------|-------|
| q_scan        | Japanese/neutral   | q_scan     | same   | RIGHT |
| q_scan        | ␣ (blank)          | q_accept   | ␣      | HALT  |
| q_scan        | anything else      | q_reject   | same   | HALT  |

This TM only moves RIGHT — it is a **read-only, one-pass** TM.
It never writes anything different to the tape (write = same symbol).

### Why this is a valid TM and not just a DFA:
- It has an explicit tape with a blank symbol marking end of input.
- It has an explicit head position variable.
- It has explicit state transitions with direction (RIGHT).
- It halts in q_accept or q_reject — it does not just "run out of input".
- The blank symbol ␣ is part of the tape alphabet but NOT the input alphabet.

---

## PART 3 — JAPANESE CHARACTER CLASSIFICATION

The TM classifies each character into one of these categories:

### ACCEPTED (Japanese ranges):
| Range          | Unicode        | Script                    |
|----------------|----------------|---------------------------|
| Hiragana       | U+3040–U+309F  | あいうえお etc.            |
| Katakana       | U+30A0–U+30FF  | アイウエオ etc.            |
| Kanji (shared) | U+4E00–U+9FAF  | 日本語 etc. (also Chinese) |
| JP Punctuation | U+3000–U+303F  | 。、「」 etc.              |
| Full-width     | U+FF00–U+FFEF  | Ａ１ etc.                  |
| Katakana Ext.  | U+31F0–U+31FF  | Ainu extensions            |

### ACCEPTED (Neutral — never cause rejection):
- Whitespace (space, tab, newline)
- Digits 0–9
- Common punctuation: . , ! ? : ; ' " ( ) -

### REJECTED (non-Japanese):
| Range          | Unicode        | Script                         |
|----------------|----------------|--------------------------------|
| CJK Radicals   | U+2E80–U+2EFF  | Chinese-exclusive radicals     |
| Kangxi Radicals| U+2F00–U+2FDF  | Chinese dictionary radicals    |
| CJK Strokes    | U+31C0–U+31EF  | Chinese stroke components      |
| CJK Ext. A     | U+3400–U+4DBF  | Rare Chinese characters        |
| Latin letters  | A–Z, a–z       | English and European scripts   |
| Arabic         | U+0600–U+06FF  | Arabic script                  |
| Cyrillic       | U+0400–U+04FF  | Russian etc.                   |
| Korean Hangul  | U+AC00–U+D7AF  | Korean script                  |

### The Kanji ambiguity problem:
Characters like 你(U+4F60), 好(U+597D), 学(U+5B66) are in the SHARED
CJK Unified Ideographs block U+4E00–U+9FAF. This block is used by BOTH
Japanese and Chinese. There is no Unicode way to say "this Kanji is
Chinese" vs "this Kanji is Japanese" at the character level.

Our TM accepts all characters in U+4E00–U+9FAF as valid Japanese Kanji.
This means pure-Kanji Chinese sentences (我是学生) are accepted as
"ambiguous Kanji" — which is the formally correct behavior.

---

## PART 4 — CODE WALKTHROUGH (TuringMachinePanel.java)

### State constants:
```java
private static final String Q_SCAN   = "q_scan";
private static final String Q_ACCEPT = "q_accept";
private static final String Q_REJECT = "q_reject";
private static final char   BLANK    = '␣';
```

### Step record (Java 16+ record):
```java
private record Step(int stepNo, String state, int head, char symbol,
                    String classification, String nextState) {}
```
Each step of the TM execution is captured as an immutable record.
This is what populates the trace table in the UI.

### Tape construction:
```java
char[] tape = new char[input.length() + 1];
for (int i = 0; i < input.length(); i++) tape[i] = input.charAt(i);
tape[input.length()] = BLANK;  // append blank at end
```
The tape is the input string plus one blank symbol at the end.
The blank signals end-of-input to the TM.

### The transition loop (explicit TM mechanics):
```java
String state = Q_SCAN;   // explicit state variable
int head = 0;            // explicit head position variable

while (true) {
    char symbol = tape[head];   // read symbol under head

    if (symbol == BLANK) {
        // δ(q_scan, ␣) → q_accept, HALT
        nextState = Q_ACCEPT;
        break;
    } else if (isAccepted(symbol)) {
        // δ(q_scan, japanese/neutral) → q_scan, move RIGHT
        nextState = Q_SCAN;
        head++;   // move head right
    } else {
        // δ(q_scan, other) → q_reject, HALT
        nextState = Q_REJECT;
        break;
    }
}
```
This is NOT just a for-loop with a boolean flag. It has:
- Explicit `state` variable (the TM's current state)
- Explicit `head` variable (the tape head position)
- Explicit transition decisions matching the formal δ function
- Explicit HALT conditions (break on q_accept or q_reject)

### isJapanese() — Unicode range checks:
```java
private static boolean isJapanese(char c) {
    if (c >= 0x3040 && c <= 0x309F) return true;  // Hiragana
    if (c >= 0x30A0 && c <= 0x30FF) return true;  // Katakana
    if (c >= 0x4E00 && c <= 0x9FAF) return true;  // Kanji
    if (c >= 0x3000 && c <= 0x303F) return true;  // JP punctuation
    if (c >= 0xFF00 && c <= 0xFFEF) return true;  // Full-width
    return false;
}
```
Each condition checks if the character's Unicode code point falls within
a specific range. `char` in Java is a 16-bit unsigned integer, so
arithmetic comparison works directly on Unicode code points.

### Tape visualization:
Each character is rendered as a cell (JPanel) with:
- The character displayed in the center
- Its index shown at the top
- Green highlight = head position on ACCEPT
- Red highlight = head position on REJECT
- Gray = blank symbol
- ▲ arrow below the head cell

### Trace table columns:
| Column         | Meaning                                      |
|----------------|----------------------------------------------|
| Step           | Step number (1, 2, 3...)                     |
| State          | Current state before transition              |
| Head           | Current head position (index into tape)      |
| Symbol         | Character under the head                     |
| Classification | What type of character it is                 |
| Next State     | State after applying δ                       |

Row colors: green = next state is q_accept, red = q_reject, alternating white/blue = q_scan.

---

## PART 5 — TEST SUITE

The 🧪 Run All Tests button runs 16 predefined test cases across 7 categories:

### Category 1 — Japanese only (all ACCEPT):
- `こんにちは` — Hiragana only
- `コンピューター` — Katakana only
- `私は学生です` — Kanji + Hiragana
- `すみません、ありがとう` — Hiragana + Japanese punctuation

### Category 2 — Chinese only (ACCEPT due to Kanji ambiguity):
- `你好吗`, `我是学生`, `谢谢你` — all in shared CJK block U+4E00–9FAF
- These are accepted as ambiguous Kanji (see Part 3 for explanation)

### Category 3 — English only (REJECT):
- `Hello, how are you?` — Latin letters trigger rejection at 'H'

### Category 4 — Mixed Japanese + Chinese (ACCEPT due to Kanji ambiguity):
- `こんにちは你好` — all characters in accepted ranges

### Category 5 — Mixed Japanese + English (REJECT):
- `こんにちは hello` — rejected at 'h' (position 6)
- `私は student です` — rejected at 's'

### Category 6 — 3+ languages (REJECT):
- `こんにちは你好 hello` — rejected at 'h'
- `私は学生ですقهوة` — rejected at Arabic character ق

### Category 7 — Edge cases:
- Empty string → ACCEPT (TM hits ␣ immediately, δ(q_scan, ␣) → q_accept)
- `123!?` → ACCEPT (all neutral symbols)
- `学校` → ACCEPT (pure Kanji, ambiguous but accepted)

---

## PART 6 — VIVA QUESTIONS & ANSWERS

**Q1: What is a Turing Machine?**
A theoretical model of computation with an infinite tape, a read/write head,
a finite set of states, and a transition function. It is the most powerful
computational model — anything computable can be computed by a TM.

**Q2: What is the difference between a DFA and a Turing Machine?**

| Feature          | DFA                    | Turing Machine              |
|------------------|------------------------|-----------------------------|
| Memory           | None (only state)      | Infinite tape               |
| Head movement    | Left to right only     | Left and right              |
| Write to tape    | No                     | Yes                         |
| Power            | Regular languages      | Recursively enumerable langs|
| Halting          | Always halts           | May loop forever            |

**Q3: What are the states in your TM?**
Three states: q_scan (start), q_accept (halting accept), q_reject (halting reject).
q_scan is the only non-halting state — the TM stays in it while scanning.

**Q4: What is the transition function of your TM?**
- δ(q_scan, Japanese/neutral symbol) = (q_scan, same, RIGHT)
- δ(q_scan, ␣) = (q_accept, ␣, HALT)
- δ(q_scan, other) = (q_reject, same, HALT)

**Q5: What is the blank symbol and why is it needed?**
The blank symbol ␣ marks the end of the input on the tape. Without it,
the TM would not know when the input ends. It is part of the tape alphabet
Γ but NOT the input alphabet Σ.

**Q6: What does it mean for a TM to accept a string?**
The TM accepts a string if, starting from q_scan with the string on the tape,
it eventually reaches q_accept. It rejects if it reaches q_reject.

**Q7: Is your TM a decider?**
Yes. A decider is a TM that always halts (never loops forever). Our TM
always halts because: either it finds a non-Japanese character and halts
in q_reject, or it reaches the blank and halts in q_accept. It never loops.

**Q8: What language does your TM recognize?**
L = {w | every character in w is Japanese or neutral}
This is a regular language (it can also be recognized by a DFA), but we
implement it as a TM to demonstrate TM mechanics.

**Q9: Why does your TM accept pure-Kanji Chinese sentences?**
Because Chinese Kanji and Japanese Kanji share the same Unicode block
(U+4E00–U+9FAF). At the character level, 学 in Japanese and 学 in Chinese
are the same Unicode code point. The TM cannot distinguish them.

**Q10: What is the tape alphabet of your TM?**
Γ = all Unicode characters that appear in the input ∪ {␣}
The input alphabet Σ = Γ \ {␣} (everything except the blank).

**Q11: What happens with an empty string?**
The tape contains only ␣. The TM reads ␣ on the first step and immediately
transitions to q_accept. This is vacuously true — an empty string has no
non-Japanese characters, so it is accepted.

**Q12: How does your TM differ from a simple for-loop?**
A for-loop is just an implementation detail. Our TM has explicit:
- State variable (not just a boolean flag)
- Head position variable (not just a loop counter)
- Transition function (explicit δ decisions)
- Tape data structure (char array with blank)
- Halting conditions (break on q_accept or q_reject)
This structure mirrors the formal TM definition.

**Q13: What is the time complexity of your TM?**
O(n) where n is the length of the input. The head moves right exactly once
per character and halts after at most n+1 steps (n characters + 1 blank).

**Q14: Can a DFA recognize the same language as your TM?**
Yes. The language "all Japanese characters" is a regular language (it is
just a union of Unicode ranges). A DFA with states for each range could
recognize it. We use a TM to demonstrate TM mechanics as required by the
assignment.

**Q15: What is the Church-Turing thesis?**
The thesis states that any effectively computable function can be computed
by a Turing Machine. It is a thesis (not a theorem) because "effectively
computable" is an informal notion. It means TMs capture the full power of
computation.

**Q16: What is the difference between q_accept and q_reject?**
Both are halting states — the TM stops when it enters either one.
q_accept means the input is in the language. q_reject means it is not.
Some TM definitions only have q_accept and use "not halting" for rejection,
but having an explicit q_reject makes the machine a decider.

**Q17: What would you need to change to accept Korean instead of Japanese?**
Change the `isJapanese()` method to check Korean Hangul range U+AC00–U+D7AF
instead of Hiragana/Katakana ranges. The TM structure (states, transitions)
stays exactly the same — only the character classification changes.

**Q18: What is a configuration of a TM?**
A configuration is a snapshot of the TM at one moment: (current_state,
tape_contents, head_position). The trace table in our UI shows exactly this
for each step.

**Q19: What is the halting problem?**
The question of whether a given TM will halt on a given input. Alan Turing
proved in 1936 that no TM can solve the halting problem for all possible
TMs and inputs — it is undecidable.

**Q20: Why does your TM only move right?**
Because the language we are recognizing (Japanese text) only requires a
single left-to-right scan. More complex languages (like palindromes) would
require the head to move both left and right.
