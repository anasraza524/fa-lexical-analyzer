import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class LexerUI extends JFrame {

    private final JTextArea codeInput = new JTextArea();
    private final JTable tokenTable;
    private final DefaultTableModel tokenModel;
    private final JTextArea reportArea = new JTextArea();
    private final DfaPanel dfaPanel = new DfaPanel();
    private final JComboBox<String> lexerChoice = new JComboBox<>(new String[]{"Direct DFA", "Table-Driven"});
    private final JLabel statusBar = new JLabel(" Ready");

    public LexerUI() {
        super("CCP Lexical Analyzer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        tokenModel = new DefaultTableModel(new String[]{"#", "Type", "Lexeme", "Line", "Col"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        tokenTable = new JTable(tokenModel);
        tokenTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        tokenTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        tokenTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        tokenTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        tokenTable.getColumnModel().getColumn(4).setPreferredWidth(50);

        codeInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codeInput.setText("if (x == 10) return y + z;");
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        reportArea.setEditable(false);

        JButton analyzeBtn = new JButton("▶  Analyze");
        analyzeBtn.addActionListener(e -> analyze());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topBar.add(new JLabel("Lexer:"));
        topBar.add(lexerChoice);
        topBar.add(analyzeBtn);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                inputPanel(), outputTabs());
        split.setDividerLocation(420);
        split.setResizeWeight(0.38);

        add(topBar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        analyze();
        setVisible(true);
    }

    private JPanel inputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Source Code"));
        p.add(new JScrollPane(codeInput), BorderLayout.CENTER);

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> codeInput.setText(""));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(clearBtn);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JTabbedPane outputTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Token Log", new JScrollPane(tokenTable));
        tabs.addTab("Report", new JScrollPane(reportArea));
        tabs.addTab("DFA Diagram", new JScrollPane(dfaPanel));
        return tabs;
    }

    private void analyze() {
        String src = codeInput.getText();
        boolean useDirect = lexerChoice.getSelectedIndex() == 0;

        List<Token> tokens = useDirect
                ? new DirectDfaLexer(src).tokenize()
                : new TableDrivenLexer(src).tokenize();

        // --- Token Log ---
        tokenModel.setRowCount(0);
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            tokenModel.addRow(new Object[]{i + 1, t.getType().name(), t.getLexeme(), t.getLine(), t.getColumn()});
        }
        colorizeRows();

        // --- Report ---
        buildReport(src, tokens, useDirect);

        // --- DFA ---
        dfaPanel.setTokens(tokens);
        dfaPanel.repaint();

        long errors = tokens.stream().filter(t -> t.getType() == TokenType.ERROR).count();
        statusBar.setText("  " + tokens.size() + " tokens  |  " + errors + " error(s)  |  "
                + (useDirect ? "Direct DFA" : "Table-Driven"));
    }

    private void colorizeRows() {
        tokenTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String type = (String) tokenModel.getValueAt(row, 1);
                    switch (type) {
                        case "KEYWORD":    c.setBackground(new Color(220, 235, 255)); break;
                        case "IDENTIFIER": c.setBackground(new Color(240, 255, 240)); break;
                        case "NUMBER":     c.setBackground(new Color(255, 250, 220)); break;
                        case "OPERATOR":   c.setBackground(new Color(255, 235, 210)); break;
                        case "SYMBOL":     c.setBackground(new Color(245, 240, 255)); break;
                        case "ERROR":      c.setBackground(new Color(255, 210, 210)); break;
                        default:           c.setBackground(Color.WHITE);
                    }
                }
                return c;
            }
        });
    }

    private void buildReport(String src, List<Token> tokens, boolean useDirect) {
        // Run both lexers for cross-validation
        List<Token> direct = new DirectDfaLexer(src).tokenize();
        List<Token> table  = new TableDrivenLexer(src).tokenize();

        StringBuilder sb = new StringBuilder();
        sb.append("=== LEXER REPORT ===\n\n");
        sb.append("Input length : ").append(src.length()).append(" chars\n");
        sb.append("Total tokens : ").append(tokens.size()).append("\n");

        long kw = tokens.stream().filter(t -> t.getType() == TokenType.KEYWORD).count();
        long id = tokens.stream().filter(t -> t.getType() == TokenType.IDENTIFIER).count();
        long num = tokens.stream().filter(t -> t.getType() == TokenType.NUMBER).count();
        long op = tokens.stream().filter(t -> t.getType() == TokenType.OPERATOR).count();
        long sym = tokens.stream().filter(t -> t.getType() == TokenType.SYMBOL).count();
        long err = tokens.stream().filter(t -> t.getType() == TokenType.ERROR).count();

        sb.append("\nToken breakdown:\n");
        sb.append(String.format("  KEYWORD    : %d%n", kw));
        sb.append(String.format("  IDENTIFIER : %d%n", id));
        sb.append(String.format("  NUMBER     : %d%n", num));
        sb.append(String.format("  OPERATOR   : %d%n", op));
        sb.append(String.format("  SYMBOL     : %d%n", sym));
        sb.append(String.format("  ERROR      : %d%n", err));

        // Cross-validation
        String dStr = joinTokens(direct);
        String tStr = joinTokens(table);
        boolean agree = dStr.equals(tStr);
        sb.append("\nCross-validation (Direct vs Table-Driven): ")
          .append(agree ? "PASS ✓" : "MISMATCH ✗").append("\n");

        if (!agree) {
            sb.append("  Direct  : ").append(dStr).append("\n");
            sb.append("  Table   : ").append(tStr).append("\n");
        }

        // Performance
        sb.append("\n--- Performance (this input) ---\n");
        int RUNS = 200;
        long t0 = System.nanoTime();
        for (int i = 0; i < RUNS; i++) new DirectDfaLexer(src).tokenize();
        long directNs = (System.nanoTime() - t0) / RUNS;

        long t1 = System.nanoTime();
        for (int i = 0; i < RUNS; i++) new TableDrivenLexer(src).tokenize();
        long tableNs = (System.nanoTime() - t1) / RUNS;

        sb.append(String.format("  Direct DFA   : %.3f ms (avg over %d runs)%n", directNs / 1e6, RUNS));
        sb.append(String.format("  Table-Driven : %.3f ms (avg over %d runs)%n", tableNs / 1e6, RUNS));

        if (err > 0) {
            sb.append("\n--- Errors ---\n");
            tokens.stream()
                  .filter(t -> t.getType() == TokenType.ERROR)
                  .forEach(t -> sb.append(String.format("  Line %d, Col %d : '%s'%n",
                          t.getLine(), t.getColumn(), t.getLexeme())));
        }

        reportArea.setText(sb.toString());
        reportArea.setCaretPosition(0);
    }

    private String joinTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LexerUI::new);
    }
}
