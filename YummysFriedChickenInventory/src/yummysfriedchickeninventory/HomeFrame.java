package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Dashboard frame, built entirely in code (no GUI Builder).
 * Change the constants below to customize the look without touching the layout code.
 */
public class HomeFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS - change these to customize the dashboard
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Dashboard";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

    private static final int LOGO_HEIGHT   = 44;
    private static final int HEADER_HEIGHT = 68;
    private static final int SIDEBAR_WIDTH = 210;
    private static final int NAV_ITEM_HEIGHT = 44;
    private static final int SLIDE_STEP    = 30;   // pixels moved per animation tick
    private static final int SLIDE_DELAY   = 8;    // milliseconds between ticks

    private static final Color BG_PAGE       = Color.WHITE;
    private static final Color ACCENT        = new Color(153, 0, 0);      // brand red
    private static final Color ACCENT_DARK   = new Color(110, 0, 0);      // darker red (hover)
    private static final Color ACCENT_LIGHT  = new Color(253, 238, 238);  // light red background
    private static final Color TEXT_PRIMARY  = new Color(33, 33, 33);
    private static final Color TEXT_MUTED    = new Color(110, 110, 110);
    private static final Color BORDER_COLOR  = new Color(228, 228, 228);
    private static final Color HOVER_BG      = new Color(245, 245, 245);

    // header colors (red header bar)
    private static final Color HEADER_BG          = ACCENT;
    private static final Color HEADER_TEXT        = Color.WHITE;
    private static final Color HEADER_TEXT_MUTED  = new Color(255, 230, 230);
    private static final Color HEADER_DIVIDER     = new Color(255, 255, 255, 90);
    private static final Color HEADER_HOVER_BG    = new Color(255, 255, 255, 35);

    private static final Color CARD_TOTAL    = new Color(44, 44, 42);     // Total Items accent
    private static final Color CARD_LOW      = new Color(239, 159, 39);   // Low Stock accent
    private static final Color CARD_OUT      = new Color(226, 75, 74);    // Out of Stock accent

    private static final Color LOW_BG        = new Color(250, 238, 218);
    private static final Color LOW_TEXT      = new Color(133, 79, 11);
    private static final Color OUT_BG        = new Color(252, 235, 235);
    private static final Color OUT_TEXT      = new Color(163, 45, 45);

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(HomeFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    public HomeFrame() {
        buildUI();
        loadDashboardData();
        loadStockAlerts();
    }

    public HomeFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadDashboardData();
        loadStockAlerts();

        applyRolePermissions();
    }

    // =====================================================================
    //  DATA / PERMISSIONS (unchanged logic)
    // =====================================================================

    private void applyRolePermissions() {
        boolean isOwner = "Owner".equalsIgnoreCase(userRole)
                || "Owner/Manager".equalsIgnoreCase(userRole);

        productbtn.setVisible(isOwner);
        supplierbtn.setVisible(isOwner);
        userManagementbtn.setVisible(isOwner);
        myAccountbtn.setVisible(isOwner);
    }

    private void loadDashboardData() {
        String totalItemsSQL = "SELECT COUNT(*) FROM inventory";
        String lowStockSQL =
                "SELECT COUNT(*) FROM inventory "
                + "WHERE quantity > 0 AND quantity <= minimum_stock";
        String outOfStockSQL =
                "SELECT COUNT(*) FROM inventory WHERE quantity <= 0";
        String totalInventoryCostSQL =
                "SELECT COALESCE(SUM(quantity * cost_price), 0) "
                + "FROM inventory WHERE is_archived = 0";

        try (Connection con = db.getConnection()) {

            // Total Items
            try (PreparedStatement pst = con.prepareStatement(totalItemsSQL);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    jLabel9.setText(String.valueOf(rs.getInt(1)));
                }
            }

            // Low Stock
            try (PreparedStatement pst = con.prepareStatement(lowStockSQL);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    jLabel10.setText(String.valueOf(rs.getInt(1)));
                }
            }

            // Out of Stock
            try (PreparedStatement pst = con.prepareStatement(outOfStockSQL);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    jLabel11.setText(String.valueOf(rs.getInt(1)));
                }
            }

            // Total Inventory Cost (active products only)
            try (PreparedStatement pst = con.prepareStatement(totalInventoryCostSQL);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    jLabel12.setText(String.format("₱%,.2f", rs.getDouble(1)));
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load dashboard data.\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void loadStockAlerts() {
        String sql = "SELECT item_name, quantity, unit, minimum_stock, "
                + "CASE "
                + "WHEN quantity <= 0 THEN 'Out of Stock' "
                + "WHEN quantity <= minimum_stock THEN 'Low Stock' "
                + "END AS status "
                + "FROM inventory "
                + "WHERE quantity <= 0 "
                + "OR (quantity > 0 AND quantity <= minimum_stock) "
                + "ORDER BY quantity ASC";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {

            // Read-only model so double-clicking a cell doesn't start editing
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"Item Name", "Quantity", "Unit", "Min Stock", "Status"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("item_name"),
                    rs.getInt("quantity"),
                    rs.getString("unit"),
                    rs.getInt("minimum_stock"),
                    rs.getString("status")
                });
            }

            jTable1.setModel(model);
            applyTableColumnWidths();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load stock alerts.\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =====================================================================
    //  UI CONSTRUCTION
    // =====================================================================

    private void buildUI() {
        setTitle(WINDOW_TITLE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_PAGE);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);
        setContentPane(root);

        setMinimumSize(new Dimension(900, 560));
        setPreferredSize(new Dimension(1100, 680));
        pack();
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- header

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setPreferredSize(new Dimension(0, HEADER_HEIGHT));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT_DARK));

        // Left: hamburger + logo
        hamburgerbtn = new HamburgerButton();
        hamburgerbtn.addActionListener(e -> toggleSidebar());
        lblLogo1 = createLogoLabel();

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridy = 0;
        lc.insets = new Insets(0, 16, 0, 12);
        left.add(hamburgerbtn, lc);
        lc.insets = new Insets(0, 0, 0, 0);
        left.add(lblLogo1, lc);

        // Right: branch | date/time | logout
        lblBranch = new JLabel(BRANCH_NAME);
        lblBranch.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        lblBranch.setForeground(HEADER_TEXT);

        JPanel divider = new JPanel();
        divider.setBackground(HEADER_DIVIDER);
        divider.setPreferredSize(new Dimension(1, 24));

        lblDateTime = new JLabel();
        lblDateTime.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblDateTime.setForeground(HEADER_TEXT_MUTED);
        updateClock();
        clockTimer = new Timer(30000, e -> updateClock());
        clockTimer.start();

        logoutbtn = new OutlineButton("Logout");
        logoutbtn.addActionListener(this::logoutbtnActionPerformed);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridy = 0;
        rc.insets = new Insets(0, 0, 0, 16);
        right.add(lblBranch, rc);
        right.add(divider, rc);
        right.add(lblDateTime, rc);
        rc.insets = new Insets(0, 8, 0, 24);
        right.add(logoutbtn, rc);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel createLogoLabel() {
        JLabel label = new JLabel();
        java.net.URL url = getClass().getResource(LOGO_PATH);
        if (url != null) {
            ImageIcon icon = new ImageIcon(url);
            int h = LOGO_HEIGHT;
            int w = icon.getIconWidth() * h / icon.getIconHeight();
            label.setIcon(new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        } else {
            label.setText("Yummy's Fried Chicken");   // fallback if the logo file isn't found
            label.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
            label.setForeground(HEADER_TEXT);
        }
        return label;
    }

    private void updateClock() {
        lblDateTime.setText(new SimpleDateFormat(DATE_FORMAT).format(new Date()));
    }

    // --------------------------------------------------------------- sidebar

    private JPanel buildSidebar() {
        sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(BG_PAGE);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR),
                new EmptyBorder(16, 0, 16, 0)));
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));

        dashboardbtn      = new NavButton("Dashboard", true);
        productbtn        = new NavButton("Manage products", false);
        supplierbtn       = new NavButton("Manage supplier", false);
        inventorybtn      = new NavButton("Inventory", false);
        stockbtn          = new NavButton("Stock In/Out", false);
        reportsbtn        = new NavButton("Reports", false);
        userManagementbtn = new NavButton("User management", false);
        myAccountbtn      = new NavButton("My account", false);

        dashboardbtn.addActionListener(this::dashboardbtnActionPerformed);
        productbtn.addActionListener(this::productbtnActionPerformed);
        supplierbtn.addActionListener(this::supplierbtnActionPerformed);
        inventorybtn.addActionListener(this::inventorybtnActionPerformed);
        stockbtn.addActionListener(this::stockbtnActionPerformed);
        reportsbtn.addActionListener(this::reportsbtnActionPerformed);
        userManagementbtn.addActionListener(this::userManagementbtnActionPerformed);
        myAccountbtn.addActionListener(this::myAccountbtnActionPerformed);

        sidebar.add(dashboardbtn);
        sidebar.add(productbtn);
        sidebar.add(supplierbtn);
        sidebar.add(inventorybtn);
        sidebar.add(stockbtn);
        sidebar.add(reportsbtn);
        sidebar.add(userManagementbtn);
        sidebar.add(myAccountbtn);
        sidebar.add(Box.createVerticalGlue());

        // The holder is the part that shrinks to 0 width when the sidebar is hidden
        sidebarHolder = new JPanel(new BorderLayout());
        sidebarHolder.setBackground(BG_PAGE);
        sidebarHolder.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebarHolder.add(sidebar, BorderLayout.WEST);
        return sidebarHolder;
    }

        private void toggleSidebar() {
            sidebarOpen = !sidebarOpen;
            final int target = sidebarOpen ? SIDEBAR_WIDTH : 0;

            if (slideTimer != null && slideTimer.isRunning()) {
                slideTimer.stop();
            }

            Timer newTimer = new Timer(SLIDE_DELAY, null);
            newTimer.addActionListener(e -> {
                int w = sidebarHolder.getPreferredSize().width;
                if (w < target) {
                    w = Math.min(target, w + SLIDE_STEP);
                } else if (w > target) {
                    w = Math.max(target, w - SLIDE_STEP);
                }
                sidebarHolder.setPreferredSize(new Dimension(w, 0));
                sidebarHolder.revalidate();
                sidebarHolder.repaint();
                if (w == target) {
                    newTimer.stop();  // Use the local 'newTimer' here instead
                }
            });
            slideTimer = newTimer;
            slideTimer.start();
        }

    // --------------------------------------------------------------- content

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(BG_PAGE);
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        jLabel1 = new JLabel("Dashboard");
        jLabel1.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        jLabel1.setForeground(TEXT_PRIMARY);
        jLabel1.setAlignmentX(Component.LEFT_ALIGNMENT);

        jLabel2 = new JLabel("Overview of current inventory status");
        jLabel2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        jLabel2.setForeground(TEXT_MUTED);
        jLabel2.setAlignmentX(Component.LEFT_ALIGNMENT);

        jLabel9  = new JLabel("0");       // Total Items value
        jLabel10 = new JLabel("0");      // Low Stock value
        jLabel11 = new JLabel("0");      // Out of Stock value
        jLabel12 = new JLabel("₱0.00");  // Total Inventory Cost value

        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setOpaque(false);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        cards.setPreferredSize(new Dimension(0, 96));
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        cards.add(new StatCard("Total Items", jLabel9, CARD_TOTAL));
        cards.add(new StatCard("Low Stock", jLabel10, CARD_LOW));
        cards.add(new StatCard("Out of Stock", jLabel11, CARD_OUT));
        cards.add(new StatCard("Total Inventory Cost", jLabel12, CARD_TOTAL));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(jLabel1);
        top.add(Box.createVerticalStrut(2));
        top.add(jLabel2);
        top.add(Box.createVerticalStrut(20));
        top.add(cards);

        jLabel6 = new JLabel("Low / Out of Stock Items");
        jLabel6.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        jLabel6.setForeground(TEXT_PRIMARY);

        JPanel tableSection = new JPanel(new BorderLayout(0, 10));
        tableSection.setOpaque(false);
        tableSection.add(jLabel6, BorderLayout.NORTH);
        tableSection.add(buildTable(), BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    private JScrollPane buildTable() {
        jTable1 = new JTable(new DefaultTableModel(
                new Object[]{"Item Name", "Quantity", "Unit", "Min Stock", "Status"}, 0));
        jTable1.setRowHeight(38);
        jTable1.setShowGrid(false);
        jTable1.setShowHorizontalLines(true);
        jTable1.setGridColor(new Color(238, 238, 238));
        jTable1.setIntercellSpacing(new Dimension(0, 1));
        jTable1.setBackground(Color.WHITE);
        jTable1.setFillsViewportHeight(true);
        jTable1.setSelectionBackground(ACCENT_LIGHT);
        jTable1.setSelectionForeground(TEXT_PRIMARY);
        jTable1.setFocusable(false);
        // Default renderer survives jTable1.setModel(...) calls
        jTable1.setDefaultRenderer(Object.class, new AlertCellRenderer());

        JTableHeader th = jTable1.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, 38));
        th.setDefaultRenderer(new HeaderRenderer());

        applyTableColumnWidths();

        jScrollPane1 = new JScrollPane(jTable1);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    private void applyTableColumnWidths() {
        int[] widths = {260, 110, 100, 110, 160};
        for (int i = 0; i < widths.length && i < jTable1.getColumnModel().getColumnCount(); i++) {
            jTable1.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static boolean isCenterColumn(int column) {
        return column == 1 || column == 3;   // Quantity, Min Stock
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    @Override
    public void dispose() {
        if (clockTimer != null) {
            clockTimer.stop();
        }
        if (slideTimer != null) {
            slideTimer.stop();
        }
        super.dispose();
    }

    // =====================================================================
    //  CUSTOM COMPONENTS (painted in code so they look the same in any L&F)
    // =====================================================================

    /** Sidebar menu item: plain text, red left bar + light red background when active. */
    private static class NavButton extends JButton {
        private final boolean active;
        private boolean hover;

        NavButton(String text, boolean active) {
            super(text);
            this.active = active;
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(SIDEBAR_WIDTH, NAV_ITEM_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, NAV_ITEM_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (active) {
                g2.setColor(ACCENT_LIGHT);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.fillRect(0, 0, 4, getHeight());
            } else if (hover) {
                g2.setColor(HOVER_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setFont(new Font(FONT_FAMILY, active ? Font.BOLD : Font.PLAIN, 14));
            g2.setColor(active ? ACCENT : TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), 24, y);
            g2.dispose();
        }
    }

    /** Three-line menu button that toggles the sidebar. */
    private static class HamburgerButton extends JButton {
        private boolean hover;

        HamburgerButton() {
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(40, 40));
            setToolTipText("Show / hide menu");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (hover) {
                g2.setColor(HEADER_HOVER_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.setColor(HEADER_TEXT);
            int x = (getWidth() - 20) / 2;
            int cy = getHeight() / 2;
            g2.fillRoundRect(x, cy - 7, 20, 2, 2, 2);
            g2.fillRoundRect(x, cy - 1, 20, 2, 2, 2);
            g2.fillRoundRect(x, cy + 5, 20, 2, 2, 2);
            g2.dispose();
        }
    }

    /** Red outlined button (fills red on hover). */
    private static class OutlineButton extends JButton {
        private boolean hover;

        OutlineButton(String text) {
            super(text);
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, 36));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(hover ? HEADER_HOVER_BG : new Color(255, 255, 255, 0));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(HEADER_TEXT);
            g2.drawRoundRect(0, 0, w, h, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(HEADER_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** White summary card with a colored accent bar on the left edge. */
    private static class StatCard extends JPanel {
        private final Color accent;

        StatCard(String title, JLabel valueLabel, Color accent) {
            super(new BorderLayout(0, 4));
            this.accent = accent;
            setOpaque(false);
            setBorder(new EmptyBorder(16, 26, 16, 16));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            titleLabel.setForeground(TEXT_MUTED);

            valueLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 32));
            valueLabel.setForeground(TEXT_PRIMARY);

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Shape card = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Color.WHITE);
            g2.fill(card);
            g2.setClip(card);
            g2.setColor(accent);
            g2.fillRect(0, 0, 6, getHeight());
            g2.setClip(null);
            g2.setColor(BORDER_COLOR);
            g2.draw(card);
            g2.dispose();
        }
    }

    /** Table header: small gray bold text on a very light background. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            setForeground(TEXT_MUTED);
            setBackground(new Color(250, 250, 250));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                    new EmptyBorder(0, 14, 0, 14)));
            setHorizontalAlignment(isCenterColumn(column) ? CENTER : LEFT);
            return this;
        }
    }

    /** Table cells: plain text, except the Status column which is drawn as a colored pill. */
    private static class AlertCellRenderer extends DefaultTableCellRenderer {
        private final StatusCell statusCell = new StatusCell();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Color rowBg = isSelected ? ACCENT_LIGHT : Color.WHITE;
            String text = value == null ? "" : value.toString();

            if (column == 4) {
                statusCell.set(text, rowBg);
                return statusCell;
            }

            setText(text);
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(rowBg);
            setOpaque(true);
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setHorizontalAlignment(isCenterColumn(column) ? CENTER : LEFT);
            return this;
        }
    }

    /** Draws a rounded status pill (Low Stock = amber, Out of Stock = red). */
    private static class StatusCell extends JPanel {
        private String text = "";
        private Color rowBg = Color.WHITE;

        StatusCell() {
            setOpaque(true);
        }

        void set(String text, Color rowBg) {
            this.text = text;
            this.rowBg = rowBg;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(rowBg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            Color bg = new Color(238, 238, 238);
            Color fg = TEXT_MUTED;
            if ("Out of Stock".equalsIgnoreCase(text)) {
                bg = OUT_BG;
                fg = OUT_TEXT;
            } else if ("Low Stock".equalsIgnoreCase(text)) {
                bg = LOW_BG;
                fg = LOW_TEXT;
            }

            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int pillW = fm.stringWidth(text) + 24;
            int pillH = 24;
            int x = 14;
            int y = (getHeight() - pillH) / 2;
            g2.setColor(bg);
            g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
            g2.setColor(fg);
            g2.drawString(text, x + 12, y + (pillH - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // =====================================================================
    //  BUTTON ACTIONS
    // =====================================================================

    private void dashboardbtnActionPerformed(java.awt.event.ActionEvent evt) {
        // Already on the dashboard, so just refresh the numbers and the table
        loadDashboardData();
        loadStockAlerts();
    }

    private void inventorybtnActionPerformed(java.awt.event.ActionEvent evt) {
    InventoryFrame inventory =
        new InventoryFrame(userId, loggedInUsername, userRole);

    inventory.setVisible(true);
    this.dispose();
    }

    private void stockbtnActionPerformed(java.awt.event.ActionEvent evt) {
    StockFrame stock =
        new StockFrame(userId, loggedInUsername, userRole);

    stock.setVisible(true);
    this.dispose();
    }

    private void reportsbtnActionPerformed(java.awt.event.ActionEvent evt) {
    ReportFrame report =
        new ReportFrame(userId, loggedInUsername, userRole);

    report.setVisible(true);
    this.dispose();
    }

    private void logoutbtnActionPerformed(java.awt.event.ActionEvent evt) {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to logout?",
                "Logout",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            LoginFrame login = new LoginFrame();
            login.setVisible(true);
            this.dispose();
        }
    }

    private void productbtnActionPerformed(java.awt.event.ActionEvent evt) {
                ProductManagentFrame product = (userRole != null)
                ? new ProductManagentFrame(userId, loggedInUsername, userRole)
                : new ProductManagentFrame();
        product.setVisible(true);
        this.dispose();
    }

    private void supplierbtnActionPerformed(java.awt.event.ActionEvent evt) {
                SupplierManagementFrame supplier = (userRole != null)
                ? new SupplierManagementFrame(userId, loggedInUsername, userRole)
                : new SupplierManagementFrame();
        supplier.setVisible(true);
        this.dispose();
    }

    private void userManagementbtnActionPerformed(java.awt.event.ActionEvent evt) {
                UserManagementFrame userMgmt = (userRole != null)
                ? new UserManagementFrame(userId, loggedInUsername, userRole)
                : new UserManagementFrame();
        userMgmt.setVisible(true);
        this.dispose();
    }

    private void myAccountbtnActionPerformed(java.awt.event.ActionEvent evt) {
            MyAccountFrame myAccount = (userRole != null)
            ? new MyAccountFrame(userId, loggedInUsername, userRole)
            : new MyAccountFrame();
        myAccount.setVisible(true);
        this.dispose();
    }

    // =====================================================================
    //  MAIN
    // =====================================================================

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new HomeFrame().setVisible(true));
    }

    // =====================================================================
    //  COMPONENT FIELDS
    // =====================================================================
    private JPanel sidebar;
    private JPanel sidebarHolder;
    private JButton hamburgerbtn;
    private JButton logoutbtn;
    private JButton dashboardbtn;
    private JButton productbtn;
    private JButton supplierbtn;
    private JButton inventorybtn;
    private JButton stockbtn;
    private JButton reportsbtn;
    private JButton userManagementbtn;
    private JButton myAccountbtn;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel jLabel1;    // "Dashboard" title
    private JLabel jLabel2;    // subtitle
    private JLabel jLabel6;    // table heading
    private JLabel jLabel9;    // Total Items value
    private JLabel jLabel10;   // Low Stock value
    private JLabel jLabel11;   // Out of Stock value
    private JLabel jLabel12;   // Total Inventory Cost value
    private JScrollPane jScrollPane1;
    private JTable jTable1;
}