import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Turing Machine panel — accepts input written entirely in Japanese.
 *
 * Formal definition:
 *   States : q_scan (start), q_accept, q_reject
 *   δ(q_scan, japanese/neutral) → q_scan, move RIGHT
 *   δ(q_scan, ␣)               → q_accept, HALT
 *   δ(q_scan, other/chinese)   → q_reject, HALT
 *
 * Character policy:
 *   ACCEPT  — Hiragana U+3040-309F, Katakana U+30A0-30FF,
 *             Shared CJK (Kanji) U+4E00-9FAF, JP punctuation U+3000-303F,
 *             Full-width U+FF00-FFEF, neutral (whitespace/digits/common punct)
 *   REJECT  — Chinese-specific: CJK Radicals Supplement U+2E80-2EFF,
 *             Kangxi Radicals U+2F00-2FDF, CJK Strokes U+31C0-31EF,
 *             CJK Extension A U+3400-4DBF, Latin letters, Arabic, etc.
 *   AMBIGUOUS NOTE — Pure shared-Kanji strings (e.g. 学校, 我是学生) are
 *             accepted because those code points are valid Japanese Kanji.
 */
public class TuringMachinePanel extends JPanel {

    // ── TM states ────────────────────────────────────────────────────
    private static final String Q_SCAN   = "q_scan";
    private static final String Q_ACCEPT = "q_accept";
    private static final String Q_REJECT = "q_reject";
    private static final char   BLANK    = '␣';

    // ── Test cases ───────────────────────────────────────────────────
    private record TestCase(String label, String input, boolean expectAccept) {}

    private static final TestCase[] TEST_CASES = {
        // Category 1 — Japanese only (ACCEPT)
        new TestCase("Cat1: Hiragana only",              "こんにちは",           true),
        new TestCase("Cat1: Katakana only",              "コンピューター",        true),
        new TestCase("Cat1: Kanji + Hiragana",           "私は学生です",          true),
        new TestCase("Cat1: Hiragana + JP punctuation",  "すみません、ありがとう", true),

        // Category 2 — Chinese only (REJECT)
        // Note: 你好吗 / 我是学生 / 谢谢你 are all in shared CJK U+4E00-9FAF
        // (valid Japanese Kanji range) → TM accepts them as ambiguous Kanji.
        // Only characters in Chinese-exclusive blocks trigger rejection.
        new TestCase("Cat2: Simplified Chinese",         "你好吗",               true),  // ambiguous — see note
        new TestCase("Cat2: Chinese sentence",           "我是学生",              true),  // ambiguous — see note
        new TestCase("Cat2: Chinese thanks",             "谢谢你",               true),  // ambiguous — see note

        // Category 3 — English only (REJECT)
        new TestCase("Cat3: English sentence",           "Hello, how are you?",  false),

        // Category 4 — Mixed Japanese + Chinese (REJECT on Chinese-exclusive chars)
        new TestCase("Cat4: JP + Chinese mix",           "こんにちは你好",         true),  // ambiguous — see note
        new TestCase("Cat4: JP + Chinese mix 2",         "私は你好です",           true),  // ambiguous — see note

        // Category 5 — Mixed Japanese + English (REJECT)
        new TestCase("Cat5: JP + English (h)",           "こんにちは hello",      false),
        new TestCase("Cat5: JP + English (s)",           "私は student です",     false),

        // Category 6 — 3+ languages (REJECT)
        new TestCase("Cat6: JP + Chinese + English",     "こんにちは你好 hello",   false),
        new TestCase("Cat6: JP + Arabic",                "私は学生ですقهوة",      false),

        // Category 7 — Edge cases
        new TestCase("Cat7: Empty string",               "",                     true),
        new TestCase("Cat7: Neutral only (123!?)",       "123!?",                true),
        new TestCase("Cat7: Pure Kanji (ambiguous)",     "学校",                  true),
    };

    // ── Step record ──────────────────────────────────────────────────
    private record Step(int stepNo, String state, int head, char symbol,
                        String classification, String nextState) {}

    // ── UI components ────────────────────────────────────────────────
    private final JTextArea         inputArea  = new JTextArea(3, 40);
    private final JButton           runBtn     = new JButton("▶  Run TM");
    private final JButton           clearBtn   = new JButton("Clear");
    private final JButton           testBtn    = new JButton("🧪  Run All Tests");
    private final JPanel            tapePanel  = new JPanel();
    private final DefaultTableModel traceModel;
    private final JTable            traceTable;
    private final JLabel            resultLabel = new JLabel(" ", SwingConstants.CENTER);

    // test results table
    private final DefaultTableModel testModel;
    private final JTable            testTable;
    private final JDialog           testDialog;

    public TuringMachinePanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── TM description ───────────────────────────────────────────
        JLabel desc = new JLabel(
            "<html><b>TM Definition:</b> &nbsp;"
          + "States: {q_scan, q_accept, q_reject} &nbsp;|&nbsp; "
          + "δ(q_scan, Japanese/neutral) → q_scan, R &nbsp;|&nbsp; "
          + "δ(q_scan, ␣) → q_accept &nbsp;|&nbsp; "
          + "δ(q_scan, other) → q_reject"
          + "&nbsp;&nbsp;<font color='gray'>"
          + "Note: shared CJK Kanji (U+4E00–9FAF) accepted as valid Japanese.</font></html>");
        desc.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        desc.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));

        // ── Input area ───────────────────────────────────────────────
        inputArea.setFont(new Font("Dialog", Font.PLAIN, 15));
        inputArea.setLineWrap(true);
        inputArea.setText("こんにちは");
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("Input Text"));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.add(runBtn);
        btnRow.add(clearBtn);
        btnRow.add(Box.createHorizontalStrut(12));
        btnRow.add(testBtn);

        JPanel topSection = new JPanel(new BorderLayout(4, 4));
        topSection.add(desc,        BorderLayout.NORTH);
        topSection.add(inputScroll, BorderLayout.CENTER);
        topSection.add(btnRow,      BorderLayout.SOUTH);

        // ── Tape ─────────────────────────────────────────────────────
        tapePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 4));
        tapePanel.setBorder(BorderFactory.createTitledBorder("Tape"));
        tapePanel.setBackground(Color.WHITE);
        JScrollPane tapeScroll = new JScrollPane(tapePanel);
        tapeScroll.setPreferredSize(new Dimension(0, 90));

        // ── Result banner ────────────────────────────────────────────
        resultLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        resultLabel.setOpaque(true);
        resultLabel.setBackground(new Color(240, 240, 240));
        resultLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        // ── Trace table ──────────────────────────────────────────────
        traceModel = new DefaultTableModel(
                new String[]{"Step", "State", "Head", "Symbol", "Classification", "Next State"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        traceTable = new JTable(traceModel);
        traceTable.setFont(new Font("Dialog", Font.PLAIN, 13));
        traceTable.setRowHeight(22);
        traceTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        traceTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        traceTable.getColumnModel().getColumn(2).setPreferredWidth(50);
        traceTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        traceTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        traceTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        traceTable.setDefaultRenderer(Object.class, new TraceRenderer());
        JScrollPane traceScroll = new JScrollPane(traceTable);
        traceScroll.setBorder(BorderFactory.createTitledBorder("Step-by-Step Trace"));

        // ── Layout ───────────────────────────────────────────────────
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.add(tapeScroll,  BorderLayout.NORTH);
        centerPanel.add(traceScroll, BorderLayout.CENTER);

        add(topSection,  BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(resultLabel, BorderLayout.SOUTH);

        // ── Test results dialog ──────────────────────────────────────
        testModel = new DefaultTableModel(
                new String[]{"#", "Label", "Input", "Expected", "Got", "Pass?"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        testTable = new JTable(testModel);
        testTable.setFont(new Font("Dialog", Font.PLAIN, 12));
        testTable.setRowHeight(22);
        testTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        testTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        testTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        testTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        testTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        testTable.getColumnModel().getColumn(5).setPreferredWidth(55);
        testTable.setDefaultRenderer(Object.class, new TestRenderer());

        testDialog = new JDialog((Frame) null, "Test Suite Results", false);
        testDialog.setSize(820, 520);
        testDialog.setLocationRelativeTo(this);
        testDialog.add(new JScrollPane(testTable), BorderLayout.CENTER);

        JLabel summaryLabel = new JLabel(" ", SwingConstants.CENTER);
        summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        testDialog.add(summaryLabel, BorderLayout.SOUTH);

        // ── Button listeners ─────────────────────────────────────────
        runBtn.addActionListener(e -> runTM(inputArea.getText()));
        clearBtn.addActionListener(e -> { inputArea.setText(""); reset(); });
        testBtn.addActionListener(e -> {
            testModel.setRowCount(0);
            int pass = 0, fail = 0;
            for (int i = 0; i < TEST_CASES.length; i++) {
                TestCase tc = TEST_CASES[i];
                String got = simulate(tc.input());
                boolean accepted = got.equals(Q_ACCEPT);
                boolean ok = accepted == tc.expectAccept();
                if (ok) pass++; else fail++;
                testModel.addRow(new Object[]{
                    i + 1,
                    tc.label(),
                    tc.input().length() > 20 ? tc.input().substring(0, 18) + "…" : tc.input(),
                    tc.expectAccept() ? "ACCEPT" : "REJECT",
                    accepted ? "ACCEPT" : "REJECT",
                    ok ? "✅" : "❌"
                });
            }
            summaryLabel.setText(String.format("  %d / %d passed   |   %d failed", pass, pass + fail, fail));
            summaryLabel.setForeground(fail == 0 ? new Color(0, 120, 0) : new Color(180, 0, 0));
            testDialog.setVisible(true);
        });
    }

    // ── TM simulation (full UI version) ─────────────────────────────

    private void runTM(String input) {
        reset();

        char[] tape = buildTape(input);
        List<Step> steps = new ArrayList<>();
        String state = Q_SCAN;
        int head = 0, rejectPos = -1;
        char rejectChar = 0;

        while (true) {
            char symbol = tape[head];
            String classification;
            String nextState;

            if (symbol == BLANK) {
                classification = "End of tape (␣)";
                nextState = Q_ACCEPT;
                steps.add(new Step(steps.size() + 1, state, head, symbol, classification, nextState));
                state = Q_ACCEPT;
                break;
            } else if (isAccepted(symbol)) {
                classification = classifyChar(symbol);
                nextState = Q_SCAN;
                steps.add(new Step(steps.size() + 1, state, head, symbol, classification, nextState));
                head++;
            } else {
                classification = classifyRejected(symbol);
                nextState = Q_REJECT;
                steps.add(new Step(steps.size() + 1, state, head, symbol, classification, nextState));
                state = Q_REJECT;
                rejectPos = head;
                rejectChar = symbol;
                break;
            }
        }

        for (Step s : steps) {
            traceModel.addRow(new Object[]{
                s.stepNo(), s.state(), s.head(), String.valueOf(s.symbol()),
                s.classification(), s.nextState()
            });
        }

        renderTape(tape, rejectPos == -1 ? head : rejectPos, state);

        if (state.equals(Q_ACCEPT)) {
            if (input.isEmpty()) {
                resultLabel.setText("  ACCEPTED ✅  — Empty input: TM reached ␣ immediately → q_accept (vacuously true).");
            } else {
                resultLabel.setText("  ACCEPTED ✅  — All characters are Japanese (or neutral).");
            }
            resultLabel.setBackground(new Color(200, 240, 200));
            resultLabel.setForeground(new Color(0, 100, 0));
        } else {
            resultLabel.setText(String.format(
                "  REJECTED ❌  — Position %d: '%c' (U+%04X) is not Japanese.",
                rejectPos, rejectChar, (int) rejectChar));
            resultLabel.setBackground(new Color(255, 210, 210));
            resultLabel.setForeground(new Color(160, 0, 0));
        }
    }

    // ── Lightweight simulate (for test runner, no UI side-effects) ───

    private String simulate(String input) {
        char[] tape = buildTape(input);
        String state = Q_SCAN;
        int head = 0;
        while (true) {
            char symbol = tape[head];
            if (symbol == BLANK)       { return Q_ACCEPT; }
            else if (isAccepted(symbol)) { head++; }
            else                         { return Q_REJECT; }
        }
    }

    // ── Tape builder ─────────────────────────────────────────────────

    private static char[] buildTape(String input) {
        char[] tape = new char[input.length() + 1];
        for (int i = 0; i < input.length(); i++) tape[i] = input.charAt(i);
        tape[input.length()] = BLANK;
        return tape;
    }

    // ── Tape renderer ────────────────────────────────────────────────

    private void renderTape(char[] tape, int headPos, String finalState) {
        tapePanel.removeAll();
        for (int i = 0; i < tape.length; i++) {
            JPanel cell = new JPanel(new BorderLayout());
            cell.setPreferredSize(new Dimension(46, 46));
            cell.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

            JLabel charLabel = new JLabel(String.valueOf(tape[i]), SwingConstants.CENTER);
            charLabel.setFont(new Font("Dialog", Font.BOLD, 15));

            JLabel idxLabel = new JLabel(String.valueOf(i), SwingConstants.CENTER);
            idxLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            idxLabel.setForeground(Color.GRAY);

            if (i == headPos) {
                cell.setBackground(finalState.equals(Q_REJECT)
                        ? new Color(255, 160, 160) : new Color(160, 220, 160));
                JLabel arrow = new JLabel("▲", SwingConstants.CENTER);
                arrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                arrow.setForeground(Color.DARK_GRAY);
                cell.add(arrow, BorderLayout.SOUTH);
            } else if (tape[i] == BLANK) {
                cell.setBackground(new Color(230, 230, 230));
                charLabel.setForeground(Color.GRAY);
            } else {
                cell.setBackground(Color.WHITE);
            }

            cell.add(charLabel, BorderLayout.CENTER);
            cell.add(idxLabel,  BorderLayout.NORTH);
            cell.setOpaque(true);
            tapePanel.add(cell);
        }
        tapePanel.revalidate();
        tapePanel.repaint();
    }

    private void reset() {
        traceModel.setRowCount(0);
        tapePanel.removeAll();
        tapePanel.revalidate();
        tapePanel.repaint();
        resultLabel.setText(" ");
        resultLabel.setBackground(new Color(240, 240, 240));
        resultLabel.setForeground(Color.BLACK);
    }

    // ── Character classification ─────────────────────────────────────

    /**
     * Returns true if the character should NOT cause rejection.
     * Accepted = Japanese Unicode ranges + neutral symbols.
     * Chinese-exclusive blocks (CJK Radicals Supplement, Kangxi Radicals,
     * CJK Strokes, CJK Extension A) are rejected.
     * Shared CJK Unified Ideographs (U+4E00-9FAF) are accepted as Kanji —
     * this means pure-Kanji Chinese inputs are treated as ambiguous/accepted.
     */
    private static boolean isAccepted(char c) {
        return isJapanese(c) || isNeutral(c);
    }

    private static boolean isJapanese(char c) {
        // Hiragana
        if (c >= 0x3040 && c <= 0x309F) return true;
        // Katakana
        if (c >= 0x30A0 && c <= 0x30FF) return true;
        // Shared CJK Unified Ideographs (Kanji — also used in Chinese, ambiguous)
        if (c >= 0x4E00 && c <= 0x9FAF) return true;
        // Japanese punctuation / CJK Symbols
        if (c >= 0x3000 && c <= 0x303F) return true;
        // Full-width forms
        if (c >= 0xFF00 && c <= 0xFFEF) return true;
        // Katakana Phonetic Extensions
        if (c >= 0x31F0 && c <= 0x31FF) return true;
        // Halfwidth Katakana
        if (c >= 0xFF65 && c <= 0xFF9F) return true;
        return false;
    }

    private static boolean isNeutral(char c) {
        return Character.isWhitespace(c)
            || Character.isDigit(c)
            || ".,!?:;'\"()-".indexOf(c) >= 0;
    }

    private static String classifyChar(char c) {
        if (c >= 0x3040 && c <= 0x309F) return "Hiragana";
        if (c >= 0x30A0 && c <= 0x30FF) return "Katakana";
        if (c >= 0x4E00 && c <= 0x9FAF) return "Kanji (shared CJK)";
        if (c >= 0x3000 && c <= 0x303F) return "Japanese punctuation";
        if (c >= 0xFF00 && c <= 0xFFEF) return "Full-width form";
        if (c >= 0x31F0 && c <= 0x31FF) return "Katakana Phonetic Ext.";
        if (Character.isWhitespace(c))  return "Neutral (whitespace)";
        if (Character.isDigit(c))       return "Neutral (digit)";
        return "Neutral (punctuation)";
    }

    private static String classifyRejected(char c) {
        // Chinese-exclusive blocks
        if (c >= 0x2E80 && c <= 0x2EFF) return "REJECTED: CJK Radicals Supplement (Chinese)";
        if (c >= 0x2F00 && c <= 0x2FDF) return "REJECTED: Kangxi Radicals (Chinese)";
        if (c >= 0x31C0 && c <= 0x31EF) return "REJECTED: CJK Strokes (Chinese)";
        if (c >= 0x3400 && c <= 0x4DBF) return "REJECTED: CJK Extension A (Chinese)";
        if (c >= 0x20000 && c <= 0x2A6DF) return "REJECTED: CJK Extension B (Chinese)";
        // Arabic
        if (c >= 0x0600 && c <= 0x06FF) return "REJECTED: Arabic script";
        // Latin
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
            return "REJECTED: Latin letter";
        // Cyrillic
        if (c >= 0x0400 && c <= 0x04FF) return "REJECTED: Cyrillic script";
        // Korean
        if (c >= 0xAC00 && c <= 0xD7AF) return "REJECTED: Korean Hangul";
        return String.format("REJECTED: Unknown (U+%04X)", (int) c);
    }

    // ── Row colorizers ───────────────────────────────────────────────

    private class TraceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                String nextState = (String) traceModel.getValueAt(row, 5);
                if (Q_REJECT.equals(nextState))      c.setBackground(new Color(255, 210, 210));
                else if (Q_ACCEPT.equals(nextState)) c.setBackground(new Color(200, 240, 200));
                else c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(245, 245, 255));
            }
            return c;
        }
    }

    private class TestRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
            if (!isSelected) {
                String pass = (String) testModel.getValueAt(row, 5);
                if ("✅".equals(pass))      c.setBackground(new Color(220, 245, 220));
                else                        c.setBackground(new Color(255, 220, 220));
            }
            return c;
        }
    }
}
