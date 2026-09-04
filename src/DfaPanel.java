import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Draws the DFA state diagram for the mini-language lexer.
 * States: START, IN_ID, IN_INT, IN_FLOAT, IN_EQ, ACCEPT, ERROR
 * Highlights states that were actually visited for the current token list.
 */
public class DfaPanel extends JPanel {

    private List<Token> tokens;

    // State indices
    private static final int S_START  = 0;
    private static final int S_ID     = 1;
    private static final int S_INT    = 2;
    private static final int S_FLOAT  = 3;
    private static final int S_EQ     = 4;
    private static final int S_ACCEPT = 5;
    private static final int S_ERROR  = 6;

    private static final String[] STATE_NAMES = {
        "START", "IN_ID", "IN_INT", "IN_FLOAT", "IN_EQ", "ACCEPT", "ERROR"
    };

    // Colors per token type that "activates" a state
    private static final Color C_KW   = new Color(100, 149, 237);
    private static final Color C_ID   = new Color(60,  179, 113);
    private static final Color C_NUM  = new Color(218, 165,  32);
    private static final Color C_OP   = new Color(210, 105,  30);
    private static final Color C_SYM  = new Color(147, 112, 219);
    private static final Color C_ERR  = new Color(220,  50,  47);
    private static final Color C_DEF  = new Color(180, 180, 180);

    public void setTokens(List<Token> tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setBackground(Color.WHITE);
        g2.clearRect(0, 0, getWidth(), getHeight());

        int W = getWidth(), H = getHeight();
        int r = 38; // state circle radius

        // Layout: place states in a logical flow
        // START -> IN_ID -> ACCEPT
        //       -> IN_INT -> IN_FLOAT -> ACCEPT
        //       -> IN_EQ  -> ACCEPT
        //       -> ACCEPT (symbols/single ops)
        //       -> ERROR

        int cx = W / 2, cy = H / 2;
        int[] sx = { cx - 280, cx - 100, cx - 100, cx + 80,  cx + 80,  cx + 260, cx - 100 };
        int[] sy = { cy,       cy - 130, cy,        cy - 130, cy,       cy,       cy + 130 };

        // Determine which states are active based on token types
        boolean[] active = new boolean[STATE_NAMES.length];
        Color[] activeColor = new Color[STATE_NAMES.length];
        for (int i = 0; i < activeColor.length; i++) activeColor[i] = C_DEF;

        if (tokens != null) {
            for (Token t : tokens) {
                active[S_START] = true;
                active[S_ACCEPT] = true;
                activeColor[S_START] = new Color(100, 200, 100);
                activeColor[S_ACCEPT] = new Color(100, 200, 100);
                switch (t.getType()) {
                    case KEYWORD:
                    case IDENTIFIER:
                        active[S_ID] = true;
                        activeColor[S_ID] = t.getType() == TokenType.KEYWORD ? C_KW : C_ID;
                        break;
                    case NUMBER:
                        active[S_INT] = true;
                        activeColor[S_INT] = C_NUM;
                        if (t.getLexeme().contains(".")) {
                            active[S_FLOAT] = true;
                            activeColor[S_FLOAT] = C_NUM;
                        }
                        break;
                    case OPERATOR:
                        if (t.getLexeme().startsWith("=")) {
                            active[S_EQ] = true;
                            activeColor[S_EQ] = C_OP;
                        }
                        break;
                    case ERROR:
                        active[S_ERROR] = true;
                        activeColor[S_ERROR] = C_ERR;
                        break;
                    default: break;
                }
            }
        }

        // Draw transitions (arrows) first so they appear behind states
        g2.setStroke(new BasicStroke(1.6f));

        // START -> IN_ID
        drawArrow(g2, sx[S_START], sy[S_START], sx[S_ID], sy[S_ID], r, "letter", active[S_ID] ? C_ID : C_DEF, false);
        // START -> IN_INT
        drawArrow(g2, sx[S_START], sy[S_START], sx[S_INT], sy[S_INT], r, "digit", active[S_INT] ? C_NUM : C_DEF, false);
        // START -> IN_EQ
        drawArrow(g2, sx[S_START], sy[S_START], sx[S_EQ], sy[S_EQ], r, "'='", active[S_EQ] ? C_OP : C_DEF, false);
        // START -> ACCEPT (symbols / single ops)
        drawArrow(g2, sx[S_START], sy[S_START], sx[S_ACCEPT], sy[S_ACCEPT], r, "sym/op", C_DEF, false);
        // START -> ERROR
        drawArrow(g2, sx[S_START], sy[S_START], sx[S_ERROR], sy[S_ERROR], r, "other", active[S_ERROR] ? C_ERR : C_DEF, false);

        // IN_ID -> IN_ID (self-loop)
        drawSelfLoop(g2, sx[S_ID], sy[S_ID], r, "letter|digit", active[S_ID] ? C_ID : C_DEF);
        // IN_ID -> ACCEPT
        drawArrow(g2, sx[S_ID], sy[S_ID], sx[S_ACCEPT], sy[S_ACCEPT], r, "other", active[S_ID] ? C_ID : C_DEF, false);

        // IN_INT -> IN_INT (self-loop)
        drawSelfLoop(g2, sx[S_INT], sy[S_INT], r, "digit", active[S_INT] ? C_NUM : C_DEF);
        // IN_INT -> IN_FLOAT
        drawArrow(g2, sx[S_INT], sy[S_INT], sx[S_FLOAT], sy[S_FLOAT], r, "'.'digit", active[S_FLOAT] ? C_NUM : C_DEF, false);
        // IN_INT -> ACCEPT
        drawArrow(g2, sx[S_INT], sy[S_INT], sx[S_ACCEPT], sy[S_ACCEPT], r, "other", active[S_INT] ? C_NUM : C_DEF, true);

        // IN_FLOAT -> IN_FLOAT (self-loop)
        drawSelfLoop(g2, sx[S_FLOAT], sy[S_FLOAT], r, "digit", active[S_FLOAT] ? C_NUM : C_DEF);
        // IN_FLOAT -> ACCEPT
        drawArrow(g2, sx[S_FLOAT], sy[S_FLOAT], sx[S_ACCEPT], sy[S_ACCEPT], r, "other", active[S_FLOAT] ? C_NUM : C_DEF, false);

        // IN_EQ -> ACCEPT (single =)
        drawArrow(g2, sx[S_EQ], sy[S_EQ], sx[S_ACCEPT], sy[S_ACCEPT], r, "other", active[S_EQ] ? C_OP : C_DEF, true);
        // IN_EQ -> ACCEPT (==)
        drawSelfLoop(g2, sx[S_EQ], sy[S_EQ], r, "'='→==", active[S_EQ] ? C_OP : C_DEF);

        // Draw states
        for (int i = 0; i < STATE_NAMES.length; i++) {
            drawState(g2, sx[i], sy[i], r, STATE_NAMES[i], activeColor[i],
                    i == S_START, i == S_ACCEPT);
        }

        // Legend
        drawLegend(g2, 10, H - 130);
    }

    private void drawState(Graphics2D g2, int x, int y, int r, String name,
                           Color fill, boolean isStart, boolean isAccept) {
        // Fill
        g2.setColor(fill);
        g2.fillOval(x - r, y - r, 2 * r, 2 * r);
        // Border
        g2.setColor(fill.darker());
        g2.setStroke(new BasicStroke(isAccept ? 3f : 1.8f));
        g2.drawOval(x - r, y - r, 2 * r, 2 * r);
        // Double ring for accept state
        if (isAccept) {
            g2.drawOval(x - r + 5, y - r + 5, 2 * r - 10, 2 * r - 10);
        }
        // Start arrow
        if (isStart) {
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x - r - 30, y, x - r - 2, y);
            drawArrowHead(g2, x - r - 30, y, x - r - 2, y, Color.DARK_GRAY);
        }
        // Label
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(name, x - fm.stringWidth(name) / 2, y + fm.getAscent() / 2 - 1);
    }

    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2,
                           int r, String label, Color color, boolean curved) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.5f));

        double angle = Math.atan2(y2 - y1, x2 - x1);
        int sx = (int) (x1 + r * Math.cos(angle));
        int sy = (int) (y1 + r * Math.sin(angle));
        int ex = (int) (x2 - r * Math.cos(angle));
        int ey = (int) (y2 - r * Math.sin(angle));

        if (curved) {
            int mx = (sx + ex) / 2 + (int) (40 * -Math.sin(angle));
            int my = (sy + ey) / 2 + (int) (40 * Math.cos(angle));
            QuadCurve2D curve = new QuadCurve2D.Float(sx, sy, mx, my, ex, ey);
            g2.draw(curve);
            // label at midpoint
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.drawString(label, (sx + mx + ex) / 3, (sy + my + ey) / 3 - 4);
        } else {
            g2.drawLine(sx, sy, ex, ey);
            int lx = (sx + ex) / 2;
            int ly = (sy + ey) / 2 - 6;
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.drawString(label, lx, ly);
        }
        drawArrowHead(g2, sx, sy, ex, ey, color);
    }

    private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2, Color color) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int size = 8;
        int[] xp = {
            x2,
            x2 - (int) (size * Math.cos(angle - 0.4)),
            x2 - (int) (size * Math.cos(angle + 0.4))
        };
        int[] yp = {
            y2,
            y2 - (int) (size * Math.sin(angle - 0.4)),
            y2 - (int) (size * Math.sin(angle + 0.4))
        };
        g2.setColor(color);
        g2.fillPolygon(xp, yp, 3);
    }

    private void drawSelfLoop(Graphics2D g2, int x, int y, int r, String label, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.5f));
        int loopR = 18;
        g2.drawOval(x - loopR, y - r - loopR * 2, loopR * 2, loopR * 2);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g2.drawString(label, x - g2.getFontMetrics().stringWidth(label) / 2, y - r - loopR * 2 - 3);
        drawArrowHead(g2, x - loopR, y - r - loopR, x, y - r - 2, color);
    }

    private void drawLegend(Graphics2D g2, int x, int y) {
        String[] labels = {"KEYWORD", "IDENTIFIER", "NUMBER", "OPERATOR", "ERROR", "inactive"};
        Color[]  colors = {C_KW, C_ID, C_NUM, C_OP, C_ERR, C_DEF};
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < labels.length; i++) {
            g2.setColor(colors[i]);
            g2.fillRect(x, y + i * 18, 14, 12);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(x, y + i * 18, 14, 12);
            g2.drawString(labels[i], x + 18, y + i * 18 + 11);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(700, 500);
    }
}
