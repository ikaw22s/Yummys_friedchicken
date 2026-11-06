package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PrinterException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Reports frame, built entirely in code (no GUI Builder).
 * Same look as HomeFrame / InventoryFrame / StockFrame.
 * Change the constants below to customize the look without touching the layout code.
 */
public class ReportFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS - change these to customize the frame
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Reports";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";   // header clock
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

    private static final String REPORT_MOVEMENT  = "Stock Movement";
    private static final String REPORT_INVENTORY = "Current Inventory";
    private static final String REPORT_LOW       = "Low Stock";

    private static final String DATE_ONLY_FORMAT = "MMM dd, yyyy";
    private static final String DATE_TIME_FORMAT = "MMM dd, yyyy  hh:mm a";

    private static final int LOGO_HEIGHT     = 44;
    private static final int HEADER_HEIGHT   = 68;
    private static final int SIDEBAR_WIDTH   = 210;
    private static final int NAV_ITEM_HEIGHT = 44;
    private static final int INPUT_HEIGHT    = 32;
    private static final int SLIDE_STEP      = 30;
    private static final int SLIDE_DELAY     = 8;

    private static final Color BG_PAGE      = Color.WHITE;
    private static final Color ACCENT       = new Color(153, 0, 0);
    private static final Color ACCENT_DARK  = new Color(120, 0, 0);
    private static final Color ACCENT_LIGHT = new Color(253, 238, 238);
    private static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    private static final Color TEXT_MUTED   = new Color(110, 110, 110);
    private static final Color BORDER_COLOR = new Color(228, 228, 228);
    private static final Color GRID_COLOR   = new Color(217, 217, 217);
    private static final Color HOVER_BG     = new Color(245, 245, 245);

    // header colors (red header bar)
    private static final Color HEADER_BG          = ACCENT;
    private static final Color HEADER_TEXT        = Color.WHITE;
    private static final Color HEADER_TEXT_MUTED  = new Color(255, 230, 230);
    private static final Color HEADER_DIVIDER     = new Color(255, 255, 255, 90);
    private static final Color HEADER_HOVER_BG    = new Color(255, 255, 255, 35);

    private static final Color IN_BG    = new Color(234, 243, 222);
    private static final Color IN_TEXT  = new Color(59, 109, 17);
    private static final Color LOW_BG   = new Color(250, 238, 218);
    private static final Color LOW_TEXT = new Color(133, 79, 11);
    private static final Color OUT_BG   = new Color(252, 235, 235);
    private static final Color OUT_TEXT = new Color(163, 45, 45);

    // Date filter (Stock Movement tab only)
    private static final String DATE_ANY    = "Any date";
    private static final String DATE_TODAY  = "Today";
    private static final String DATE_7DAYS  = "Last 7 days";
    private static final String DATE_30DAYS = "Last 30 days";
    private static final String DATE_MONTH  = "This month";
    private static final String DATE_CUSTOM = "Custom range";
    private static final String[] DATE_PRESET_OPTIONS =
            {DATE_ANY, DATE_TODAY, DATE_7DAYS, DATE_30DAYS, DATE_MONTH, DATE_CUSTOM};
    private static final String[] MONTH_NAMES = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    // Same stock-status rule as the dashboard and Inventory page
    private static final String STATUS_CASE =
            "CASE WHEN quantity <= 0 THEN 'Out of Stock' "
            + "WHEN quantity <= minimum_stock THEN 'Low Stock' "
            + "ELSE 'In Stock' END AS stock_status";

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(ReportFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private String selectedReport = REPORT_MOVEMENT;
    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    /** Turns one result-set row into one table row. */
    private interface RowMapper {
        Object[] map(ResultSet rs) throws SQLException;
    }

    public ReportFrame() {
        buildUI();
        selectReport(REPORT_MOVEMENT);
    }

    public ReportFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        selectReport(REPORT_MOVEMENT);

        applyRolePermissions();
    }

    // =====================================================================
    //  DATA / PERMISSIONS
    // =====================================================================

    private void applyRolePermissions() {
        boolean isOwner = "Owner".equalsIgnoreCase(userRole)
                || "Owner/Manager".equalsIgnoreCase(userRole);

        productbtn.setVisible(isOwner);
        supplierbtn.setVisible(isOwner);
        userManagementbtn.setVisible(isOwner);
        myAccountbtn.setVisible(isOwner);
    }

    /** Switches tab, shows/hides the date filter, and loads that report. */
    private void selectReport(String report) {
        selectedReport = report;
        tabMovement.setToggled(REPORT_MOVEMENT.equals(report));
        tabInventory.setToggled(REPORT_INVENTORY.equals(report));
        tabLowStock.setToggled(REPORT_LOW.equals(report));

        filterWrap.setVisible(REPORT_MOVEMENT.equals(report));   // date filter: Stock Movement only

        if (REPORT_INVENTORY.equals(report)) {
            showCurrentInventory();
        } else if (REPORT_LOW.equals(report)) {
            showLowStock();
        } else {
            showStockMovement();
        }
    }

    private void showStockMovement() {
        LocalDate[] range = readDateRange();
        if (range == null) {
            return;     // invalid custom range; a warning was already shown
        }

        StringBuilder sql = new StringBuilder(
                "SELECT i.item_name, sm.movement_date, sm.quantity, sm.unit, sm.source_reason, sm.cost, sm.movement_type "
                + "FROM stock_movements sm "
                + "JOIN inventory i ON sm.item_id = i.id "
                + "WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (range[0] != null) {
            sql.append(" AND sm.movement_date >= ?");
            params.add(Timestamp.valueOf(range[0].atStartOfDay()));
        }
        if (range[1] != null) {
            sql.append(" AND sm.movement_date < ?");     // up to the end of the "to" day
            params.add(Timestamp.valueOf(range[1].plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY sm.movement_date DESC, sm.id DESC");

        SimpleDateFormat dateTime = new SimpleDateFormat(DATE_TIME_FORMAT);
        DecimalFormat money = new DecimalFormat("#,##0.00");

        runReport(REPORT_MOVEMENT,
                new String[]{"Item", "Date", "Quantity", "Unit", "Supplier", "Cost", "Type"},
                sql.toString(), params,
                rs -> {
                    Timestamp moved = rs.getTimestamp("movement_date");
                    BigDecimal cost = rs.getBigDecimal("cost");
                    return new Object[]{
                        rs.getString("item_name"),
                        moved == null ? "" : dateTime.format(moved),
                        rs.getInt("quantity"),
                        rs.getString("unit"),
                        rs.getString("source_reason"),
                        cost == null ? "" : money.format(cost),
                        rs.getString("movement_type")
                    };
                });
    }

    private void showCurrentInventory() {
        String sql = "SELECT item_name, category, quantity, unit, cost_price, date_purchased, "
                + "minimum_stock, " + STATUS_CASE + " FROM inventory ORDER BY item_name ASC";

        SimpleDateFormat dateOnly = new SimpleDateFormat(DATE_ONLY_FORMAT);
        DecimalFormat money = new DecimalFormat("#,##0.00");

        runReport(REPORT_INVENTORY,
                new String[]{"Item", "Category", "Quantity", "Unit", "Cost Price",
                    "Date Purchased", "Min Stock", "Status"},
                sql, new ArrayList<>(),
                rs -> {
                    BigDecimal cost = rs.getBigDecimal("cost_price");
                    java.sql.Date purchased = rs.getDate("date_purchased");
                    return new Object[]{
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getInt("quantity"),
                        rs.getString("unit"),
                        cost == null ? "" : money.format(cost),
                        purchased == null ? "" : dateOnly.format(purchased),
                        rs.getInt("minimum_stock"),
                        rs.getString("stock_status")
                    };
                });
    }

    private void showLowStock() {
        String sql = "SELECT item_name, category, quantity, unit, minimum_stock, " + STATUS_CASE
                + " FROM inventory WHERE quantity > 0 AND quantity <= minimum_stock "
                + "ORDER BY quantity ASC, item_name ASC";

        runReport(REPORT_LOW,
                new String[]{"Item", "Category", "Current Qty", "Unit", "Min Stock", "Status"},
                sql, new ArrayList<>(),
                rs -> new Object[]{
                    rs.getString("item_name"),
                    rs.getString("category"),
                    rs.getInt("quantity"),
                    rs.getString("unit"),
                    rs.getInt("minimum_stock"),
                    rs.getString("stock_status")
                });
    }

    /** Runs the query, fills a read-only model, and updates the table, heading, and count. */
    private void runReport(String title, String[] columns, String sql, List<Object> params, RowMapper mapper) {
        try (Connection con = db.getConnection()) {
            if (con == null) {
                JOptionPane.showMessageDialog(this, "Database connection failed.");
                return;
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    DefaultTableModel model = new DefaultTableModel(columns, 0) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    };

                    while (rs.next()) {
                        model.addRow(mapper.map(rs));
                    }

                    tblReport.setModel(model);
                    applyTableColumnWidths();

                    lblHeading.setText(title);
                    int n = model.getRowCount();
                    lblCount.setText(n + (n == 1 ? " record" : " records"));
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to load " + title.toLowerCase() + " report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------ date filter

    /**
     * Returns {from, to} from the date preset (either may be null = no limit),
     * or null if the custom range is invalid (a warning is shown).
     */
    private LocalDate[] readDateRange() {
        LocalDate from = null;
        LocalDate to = null;
        String preset = (String) cmbDatePreset.getSelectedItem();
        LocalDate today = LocalDate.now();

        if (DATE_TODAY.equals(preset)) {
            from = today;
            to = today;
        } else if (DATE_7DAYS.equals(preset)) {
            from = today.minusDays(6);
            to = today;
        } else if (DATE_30DAYS.equals(preset)) {
            from = today.minusDays(29);
            to = today;
        } else if (DATE_MONTH.equals(preset)) {
            YearMonth ym = YearMonth.from(today);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else if (DATE_CUSTOM.equals(preset)) {
            from = readDropdownDate(cmbFromDay, cmbFromMonth, cmbFromYear);
            to = readDropdownDate(cmbToDay, cmbToMonth, cmbToYear);
            if (from != null && to != null && from.isAfter(to)) {
                JOptionPane.showMessageDialog(this,
                        "\"From\" date must be on or before the \"To\" date.",
                        "Invalid Filter", JOptionPane.WARNING_MESSAGE);
                return null;
            }
        }
        return new LocalDate[]{from, to};
    }

    /** Reads a LocalDate from a Day / Month / Year trio of dropdowns, or null if Day is blank. */
    private static LocalDate readDropdownDate(JComboBox<String> day, JComboBox<String> month, JComboBox<String> year) {
        Object d = day.getSelectedItem();
        if (d == null || "--".equals(d)) {
            return null;
        }
        int dayNum = Integer.parseInt(d.toString());
        int monthNum = month.getSelectedIndex() + 1;
        int yearNum = Integer.parseInt((String) year.getSelectedItem());
        int maxDay = YearMonth.of(yearNum, monthNum).lengthOfMonth();
        return LocalDate.of(yearNum, monthNum, Math.min(dayNum, maxDay));
    }

    private void resetFilters() {
        cmbDatePreset.setSelectedIndex(0);
        cmbFromDay.setSelectedIndex(0);
        cmbFromMonth.setSelectedIndex(0);
        cmbFromYear.setSelectedIndex(0);
        cmbToDay.setSelectedIndex(0);
        cmbToMonth.setSelectedIndex(0);
        cmbToYear.setSelectedIndex(0);
        customDateRow.setVisible(false);
        showStockMovement();
    }

    // ----------------------------------------------------- print / export CSV

    private void generateReport(java.awt.event.ActionEvent evt) {
        if (tblReport.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No data available to generate a report.");
            return;
        }

        Object[] options = {"Print", "Export CSV", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "How do you want to generate the " + selectedReport + " report?",
                "Generate report",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice == 0) {
            printReport();
        } else if (choice == 1) {
            exportCsv();
        }
    }

    private void printReport() {
        try {
            boolean complete = tblReport.print(
                    JTable.PrintMode.FIT_WIDTH,
                    new MessageFormat(selectedReport + " Report"),
                    new MessageFormat("Page {0}")
            );

            if (complete) {
                JOptionPane.showMessageDialog(this, "Report printed successfully.");
            }

        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this, "Unable to print report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Saves exactly what the table shows as a CSV file the user picks via a Save dialog. */
    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File(
                selectedReport.replace(' ', '_') + "_Report_" + LocalDate.now() + ".csv"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }

        if (file.exists()) {
            int replace = JOptionPane.showConfirmDialog(this,
                    file.getName() + " already exists. Replace it?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (replace != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            out.write('\uFEFF');     // byte-order mark so Excel opens it as UTF-8

            int cols = tblReport.getColumnCount();
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                if (c > 0) {
                    line.append(',');
                }
                line.append(csv(tblReport.getColumnName(c)));
            }
            out.write(line.toString());
            out.newLine();

            for (int r = 0; r < tblReport.getRowCount(); r++) {
                line.setLength(0);
                for (int c = 0; c < cols; c++) {
                    if (c > 0) {
                        line.append(',');
                    }
                    Object value = tblReport.getValueAt(r, c);
                    line.append(csv(value == null ? "" : value.toString()));
                }
                out.write(line.toString());
                out.newLine();
            }

            JOptionPane.showMessageDialog(this, "Report saved to:\n" + file.getAbsolutePath());

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to save CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

        setMinimumSize(new Dimension(1020, 620));
        setPreferredSize(new Dimension(1200, 760));
        pack();
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- header

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setPreferredSize(new Dimension(0, HEADER_HEIGHT));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT_DARK));

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

        logoutbtn = new HeaderOutlineButton("Logout");
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
            label.setText("Yummy's Fried Chicken");
            label.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
            label.setForeground(HEADER_TEXT);
        }
        return label;
    }

    private void updateClock() {
        lblDateTime.setText(new SimpleDateFormat(DATE_FORMAT).format(new java.util.Date()));
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

        dashboardbtn      = new NavButton("Dashboard", false);
        productbtn        = new NavButton("Manage products", false);
        supplierbtn       = new NavButton("Manage supplier", false);
        inventorybtn      = new NavButton("Inventory", false);
        stockbtn          = new NavButton("Stock In/Out", false);
        reportsbtn        = new NavButton("Reports", true);          // active page
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
            newTimer.stop();
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

        jLabel1 = new JLabel("Reports");
        jLabel1.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        jLabel1.setForeground(TEXT_PRIMARY);
        jLabel1.setAlignmentX(Component.LEFT_ALIGNMENT);

        jLabel2 = new JLabel("Review and summarize inventory activity");
        jLabel2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        jLabel2.setForeground(TEXT_MUTED);
        jLabel2.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ---- report tabs (left) + Generate report (right) ----
        tabMovement = new ToggleButton(REPORT_MOVEMENT, true);
        tabInventory = new ToggleButton(REPORT_INVENTORY, false);
        tabLowStock = new ToggleButton(REPORT_LOW, false);
        tabMovement.addActionListener(e -> selectReport(REPORT_MOVEMENT));
        tabInventory.addActionListener(e -> selectReport(REPORT_INVENTORY));
        tabLowStock.addActionListener(e -> selectReport(REPORT_LOW));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tabs.setOpaque(false);
        tabs.add(tabMovement);
        tabs.add(tabInventory);
        tabs.add(tabLowStock);

        btnGenerate = new FilledButton("Generate report");
        btnGenerate.setPreferredSize(new Dimension(150, 34));
        btnGenerate.addActionListener(this::generateReport);

        JPanel tabRow = new JPanel(new BorderLayout());
        tabRow.setOpaque(false);
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabRow.add(tabs, BorderLayout.WEST);
        tabRow.add(btnGenerate, BorderLayout.EAST);
        tabRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // ---- date filter (only visible on the Stock Movement tab) ----
        filterWrap = new JPanel();
        filterWrap.setLayout(new BoxLayout(filterWrap, BoxLayout.Y_AXIS));
        filterWrap.setOpaque(false);
        filterWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel filterCard = buildFilterCard();
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterWrap.add(Box.createVerticalStrut(14));
        filterWrap.add(filterCard);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(jLabel1);
        top.add(Box.createVerticalStrut(2));
        top.add(jLabel2);
        top.add(Box.createVerticalStrut(20));
        top.add(tabRow);
        top.add(filterWrap);

        // ---- table section ----
        lblHeading = new JLabel(REPORT_MOVEMENT);
        lblHeading.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        lblHeading.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 records");
        lblCount.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblCount.setForeground(TEXT_MUTED);

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(lblHeading, BorderLayout.WEST);
        tableHeading.add(lblCount, BorderLayout.EAST);

        JPanel tableSection = new JPanel(new BorderLayout(0, 10));
        tableSection.setOpaque(false);
        tableSection.add(tableHeading, BorderLayout.NORTH);
        tableSection.add(buildTable(), BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    /**
     * Date preset + Apply / Reset in one row. A second row with Day/Month/Year dropdowns
     * for From/To appears only when "Custom range" is chosen.
     */
    private JPanel buildFilterCard() {
        // The maximum height follows the card's current preferred height, so the
        // custom-range row is never clipped when it appears.
        CardPanel card = new CardPanel(new BorderLayout(0, 10)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        cmbDatePreset = new FlatComboBox(DATE_PRESET_OPTIONS);
        cmbDatePreset.addActionListener(e ->
                customDateRow.setVisible(DATE_CUSTOM.equals(cmbDatePreset.getSelectedItem())));

        JPanel mainRow = new JPanel(new GridBagLayout());
        mainRow.setOpaque(false);

        JLabel l = new JLabel("Date");
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        l.setForeground(TEXT_MUTED);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 3, 10);
        mainRow.add(l, c);

        cmbDatePreset.setPreferredSize(new Dimension(200, INPUT_HEIGHT));
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 12);
        mainRow.add(cmbDatePreset, c);

        btnApply = new FilledButton("Apply");
        btnApply.addActionListener(e -> showStockMovement());
        btnReset = new OutlineButton("Reset");
        btnReset.addActionListener(e -> resetFilters());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnApply);
        buttons.add(btnReset);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        mainRow.add(buttons, c);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 2;
        filler.weightx = 1.0;
        mainRow.add(Box.createHorizontalGlue(), filler);

        customDateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        customDateRow.setOpaque(false);
        customDateRow.setVisible(false);

        cmbFromDay   = new FlatComboBox(dayOptions());
        cmbFromMonth = new FlatComboBox(MONTH_NAMES);
        cmbFromYear  = new FlatComboBox(yearOptions());
        cmbToDay     = new FlatComboBox(dayOptions());
        cmbToMonth   = new FlatComboBox(MONTH_NAMES);
        cmbToYear    = new FlatComboBox(yearOptions());

        customDateRow.add(labeledTrio("From", cmbFromDay, cmbFromMonth, cmbFromYear));
        customDateRow.add(labeledTrio("To", cmbToDay, cmbToMonth, cmbToYear));

        card.add(mainRow, BorderLayout.NORTH);
        card.add(customDateRow, BorderLayout.SOUTH);
        return card;
    }

    private static String[] dayOptions() {
        String[] days = new String[32];
        days[0] = "--";
        for (int i = 1; i <= 31; i++) {
            days[i] = String.valueOf(i);
        }
        return days;
    }

    private static String[] yearOptions() {
        int current = LocalDate.now().getYear();
        String[] years = new String[6];
        for (int i = 0; i < 6; i++) {
            years[i] = String.valueOf(current - 4 + i);   // 4 years back through 1 year ahead
        }
        return years;
    }

    private JPanel labeledTrio(String label, JComboBox<String> day, JComboBox<String> month, JComboBox<String> year) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        group.setOpaque(false);

        JLabel l = new JLabel(label + ":");
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
        l.setForeground(TEXT_MUTED);
        group.add(l);

        day.setPreferredSize(new Dimension(54, INPUT_HEIGHT));
        month.setPreferredSize(new Dimension(68, INPUT_HEIGHT));
        year.setPreferredSize(new Dimension(72, INPUT_HEIGHT));
        group.add(day);
        group.add(month);
        group.add(year);
        return group;
    }

    // ------------------------------------------------------------------ table

    private JScrollPane buildTable() {
        tblReport = new JTable(new DefaultTableModel(new String[]{"Item"}, 0));
        tblReport.setRowHeight(38);
        // NOTE: Nimbus (set in main()) ignores setShowGrid/setGridColor/setIntercellSpacing,
        // so the grid is drawn as a border baked into each cell renderer instead.
        tblReport.setShowGrid(false);
        tblReport.setIntercellSpacing(new Dimension(0, 0));
        tblReport.setBackground(Color.WHITE);
        tblReport.setFillsViewportHeight(true);
        tblReport.setSelectionBackground(ACCENT_LIGHT);
        tblReport.setSelectionForeground(TEXT_PRIMARY);
        tblReport.setFocusable(false);
        tblReport.setDefaultRenderer(Object.class, new ReportCellRenderer());

        JTableHeader th = tblReport.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, 38));
        th.setDefaultRenderer(new HeaderRenderer());

        jScrollPane1 = new JScrollPane(tblReport);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    /** Column widths by column name, since the columns differ per report. */
    private void applyTableColumnWidths() {
        for (int i = 0; i < tblReport.getColumnModel().getColumnCount(); i++) {
            tblReport.getColumnModel().getColumn(i)
                    .setPreferredWidth(widthFor(tblReport.getColumnName(i)));
        }
    }

    private static int widthFor(String name) {
        switch (name) {
            case "Item":           return 190;
            case "Date":           return 160;
            case "Category":       return 120;
            case "Quantity":       return 80;
            case "Current Qty":    return 100;
            case "Unit":           return 70;
            case "Supplier":       return 170;
            case "Cost":           return 100;
            case "Cost Price":     return 100;
            case "Date Purchased": return 130;
            case "Min Stock":      return 90;
            case "Type":           return 110;
            case "Status":         return 120;
            default:               return 100;
        }
    }

    // Alignment is decided by column name because the columns differ per report
    private static boolean isCenterColumn(String name) {
        return "Quantity".equals(name) || "Current Qty".equals(name) || "Min Stock".equals(name);
    }

    private static boolean isRightColumn(String name) {
        return "Cost".equals(name) || "Cost Price".equals(name);
    }

    private static boolean isPillColumn(String name) {
        return "Status".equals(name) || "Type".equals(name);
    }

    private static int alignmentFor(String name) {
        if (isCenterColumn(name)) {
            return SwingConstants.CENTER;
        }
        return isRightColumn(name) ? SwingConstants.RIGHT : SwingConstants.LEFT;
    }

    /** One matte line on the bottom + right of every cell draws the grid regardless of Look and Feel. */
    private static javax.swing.border.Border cellBorder(String name) {
        int rightPad = isRightColumn(name) ? 16 : 13;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR),
                new EmptyBorder(0, 14, 0, rightPad));
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

    /** Header-only version of OutlineButton (transparent, white text, for the red header bar). */
    private static class HeaderOutlineButton extends JButton {
        private boolean hover;

        HeaderOutlineButton(String text) {
            super(text);
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(84, INPUT_HEIGHT));
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
            setPreferredSize(new Dimension(84, INPUT_HEIGHT));
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
            g2.setColor(hover ? ACCENT : Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(ACCENT);
            g2.drawRoundRect(0, 0, w, h, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(hover ? Color.WHITE : ACCENT);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    private static class FilledButton extends JButton {
        private boolean hover;

        FilledButton(String text) {
            super(text);
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(84, INPUT_HEIGHT));
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
            g2.setColor(hover ? ACCENT_DARK : ACCENT);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** Report tab: filled red when selected, outlined when not (same style as Stock In/Out). */
    private static class ToggleButton extends JButton {
        private boolean selected;
        private boolean hover;

        ToggleButton(String text, boolean selected) {
            super(text);
            this.selected = selected;
            setFocusPainted(false);
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(150, 34));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        void setToggled(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            if (selected) {
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
            } else {
                g2.setColor(hover ? HOVER_BG : Color.WHITE);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, w, h, 10, 10);
            }
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(selected ? Color.WHITE : TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    private static class CardPanel extends JPanel {
        CardPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Shape card = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Color.WHITE);
            g2.fill(card);
            g2.setColor(BORDER_COLOR);
            g2.draw(card);
            g2.dispose();
        }
    }

    /** Flat drop-down with a thin border, a small chevron, and a red-tinted hover in the list. */
    private static class FlatComboBox extends JComboBox<String> {
        FlatComboBox(String[] items) {
            super(items);
            setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = smooth(g);
                            g2.setColor(TEXT_MUTED);
                            g2.setStroke(new BasicStroke(1.6f));
                            int cx = getWidth() / 2;
                            int cy = getHeight() / 2;
                            g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                            g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                            g2.dispose();
                        }
                    };
                    b.setBorder(null);
                    b.setContentAreaFilled(false);
                    b.setFocusable(false);
                    b.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    return b;
                }

                @Override
                protected ComboPopup createPopup() {
                    BasicComboPopup popup = new BasicComboPopup(comboBox);
                    popup.setBorder(new LineBorder(BORDER_COLOR));
                    return popup;
                }

                @Override
                public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                    // no extra background: keep it flat white
                }
            });
            setFocusable(false);
            setBackground(Color.WHITE);
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
            setBorder(new LineBorder(BORDER_COLOR));
            setMaximumRowCount(8);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    setFont(new Font(FONT_FAMILY, Font.PLAIN, 12));
                    setForeground(TEXT_PRIMARY);
                    setBackground(isSelected && index >= 0 ? ACCENT_LIGHT : Color.WHITE);
                    setBorder(new EmptyBorder(6, 10, 6, 10));
                    return this;
                }
            });
        }
    }

    /** Table header: small gray bold text on a very light background. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setOpaque(true);
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            String name = value == null ? "" : value.toString();
            setText(name);
            setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            setForeground(TEXT_MUTED);
            setBackground(new Color(250, 250, 250));
            setBorder(cellBorder(name));
            setHorizontalAlignment(alignmentFor(name));
            return this;
        }
    }

    /** Table cells: plain text, except Status / Type which are drawn as colored pills. */
    private static class ReportCellRenderer extends DefaultTableCellRenderer {
        private final StatusCell pillCell = new StatusCell();

        ReportCellRenderer() {
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            String name = table.getColumnName(column);
            Color rowBg = isSelected ? ACCENT_LIGHT : Color.WHITE;
            String text = value == null ? "" : value.toString();

            if (isPillColumn(name)) {
                pillCell.set(text, rowBg);
                return pillCell;
            }

            setText(text);
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(rowBg);
            setOpaque(true);
            setBorder(cellBorder(name));
            setHorizontalAlignment(alignmentFor(name));
            return this;
        }
    }

    /** Rounded pill: In Stock / Stock In = green, Low Stock = amber, Out of Stock / Stock Out = red. */
    private static class StatusCell extends JPanel {
        private String text = "";
        private Color rowBg = Color.WHITE;

        StatusCell() {
            setOpaque(true);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR));
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
            if ("Out of Stock".equalsIgnoreCase(text) || "Stock Out".equalsIgnoreCase(text)) {
                bg = OUT_BG;
                fg = OUT_TEXT;
            } else if ("Low Stock".equalsIgnoreCase(text)) {
                bg = LOW_BG;
                fg = LOW_TEXT;
            } else if ("In Stock".equalsIgnoreCase(text) || "Stock In".equalsIgnoreCase(text)) {
                bg = IN_BG;
                fg = IN_TEXT;
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
        HomeFrame home = (userRole != null)
                ? new HomeFrame(userId, loggedInUsername, userRole)
                : new HomeFrame();
        home.setVisible(true);
        this.dispose();
    }

    private void inventorybtnActionPerformed(java.awt.event.ActionEvent evt) {
        InventoryFrame inventory = (userRole != null)
                ? new InventoryFrame(userId, loggedInUsername, userRole)
                : new InventoryFrame();
        inventory.setVisible(true);
        this.dispose();
    }

    private void stockbtnActionPerformed(java.awt.event.ActionEvent evt) {
        StockFrame stock = (userRole != null)
                ? new StockFrame(userId, loggedInUsername, userRole)
                : new StockFrame();
        stock.setVisible(true);
        this.dispose();
    }

    private void reportsbtnActionPerformed(java.awt.event.ActionEvent evt) {
        // Already on Reports, so just reload the current tab
        selectReport(selectedReport);
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

        java.awt.EventQueue.invokeLater(() -> new ReportFrame().setVisible(true));
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
    private ToggleButton tabMovement;
    private ToggleButton tabInventory;
    private ToggleButton tabLowStock;
    private JButton btnGenerate;
    private JButton btnApply;
    private JButton btnReset;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblHeading;   // current report name above the table
    private JLabel lblCount;     // "N records" label above the table
    private JLabel jLabel1;      // "Reports" title
    private JLabel jLabel2;      // subtitle
    private JPanel filterWrap;   // date filter section (Stock Movement tab only)
    private JPanel customDateRow;
    private JComboBox<String> cmbDatePreset;
    private JComboBox<String> cmbFromDay;
    private JComboBox<String> cmbFromMonth;
    private JComboBox<String> cmbFromYear;
    private JComboBox<String> cmbToDay;
    private JComboBox<String> cmbToMonth;
    private JComboBox<String> cmbToYear;
    private JScrollPane jScrollPane1;
    private JTable tblReport;
}