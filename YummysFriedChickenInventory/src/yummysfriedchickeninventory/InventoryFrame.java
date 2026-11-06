package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
 
/**
 * Inventory frame, built entirely in code (no GUI Builder).
 * Same look as HomeFrame: header, collapsible sidebar, custom-painted components.
 * Change the constants below to customize the look without touching the layout code.
 */
public class InventoryFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS - change these to customize the frame
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Inventory";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";   // header clock
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

    // How dates are shown in the table
    private static final String TABLE_DATE_FORMAT    = "MMM dd, yyyy";
    private static final String TABLE_UPDATED_FORMAT = "MMM dd, yyyy  hh:mm a";

    private static final int LOGO_HEIGHT     = 44;
    private static final int HEADER_HEIGHT   = 68;
    private static final int SIDEBAR_WIDTH   = 210;
    private static final int NAV_ITEM_HEIGHT = 44;
    private static final int INPUT_HEIGHT    = 32;
    private static final int SLIDE_STEP      = 30;   // pixels moved per animation tick
    private static final int SLIDE_DELAY     = 8;    // milliseconds between ticks

    private static final Color BG_PAGE      = Color.WHITE;
    private static final Color ACCENT       = new Color(153, 0, 0);      // brand red
    private static final Color ACCENT_DARK  = new Color(120, 0, 0);      // Apply button hover
    private static final Color ACCENT_LIGHT = new Color(253, 238, 238);  // light red background
    private static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    private static final Color TEXT_MUTED   = new Color(110, 110, 110);
    private static final Color TEXT_HINT    = new Color(170, 170, 170);
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

    // Filter option labels
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_STATUS     = "All status";
    private static final String[] STATUS_OPTIONS =
            {ALL_STATUS, "In Stock", "Low Stock", "Out of Stock"};

    private static final String QTY_ANY = "Any quantity";
    private static final String[] QTY_OPTIONS =
            {QTY_ANY, "0", "1 - 10", "11 - 50", "51 - 100", "100+"};

    private static final String DATE_TODAY    = "Today";
    private static final String DATE_7DAYS    = "Last 7 days";
    private static final String DATE_30DAYS   = "Last 30 days";
    private static final String DATE_MONTH    = "This month";
    private static final String DATE_CUSTOM   = "Custom range";
    private static final String DATE_ANY      = "Any date";
    private static final String[] DATE_PRESET_OPTIONS =
            {DATE_ANY, DATE_TODAY, DATE_7DAYS, DATE_30DAYS, DATE_MONTH, DATE_CUSTOM};

    private static final String[] MONTH_NAMES = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    // Table layout
    private static final String[] TABLE_COLUMNS = {
        "Item Name", "Date Purchased", "Category", "Quantity", "Unit",
        "Min Stock", "Cost", "Status"
    };
    private static final int[] TABLE_WIDTHS = {190, 120, 120, 80, 70, 90, 100, 120};
    private static final int COL_QUANTITY  = 3;
    private static final int COL_MIN_STOCK = 5;
    private static final int COL_COST      = 6;
    private static final int COL_STATUS    = 7;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(InventoryFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    public InventoryFrame() {
        buildUI();
        loadCategories();
        loadInventoryData();
    }

    public InventoryFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadCategories();
        loadInventoryData();

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

    /** Fills the Category filter with the distinct categories found in the inventory table. */
    private void loadCategories() {
        String sql = "SELECT DISTINCT category FROM inventory "
                + "WHERE category IS NOT NULL AND category <> '' ORDER BY category";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {

            cmbCategory.removeAllItems();
            cmbCategory.addItem(ALL_CATEGORIES);
            while (rs.next()) {
                cmbCategory.addItem(rs.getString(1));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load categories.\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** Reads the filter inputs, builds the query, and fills the table. */
    private void loadInventoryData() {
        StringBuilder sql = new StringBuilder(
                "SELECT item_name, date_purchased, category, quantity, unit, "
                + "minimum_stock, cost_price, "
                + "CASE "
                + "WHEN quantity <= 0 THEN 'Out of Stock' "
                + "WHEN quantity <= minimum_stock THEN 'Low Stock' "
                + "ELSE 'In Stock' "
                + "END AS stock_status "
                + "FROM inventory WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // Category
        String category = (String) cmbCategory.getSelectedItem();
        if (category != null && !ALL_CATEGORIES.equals(category)) {
            sql.append(" AND category = ?");
            params.add(category);
        }

        // Status (same rules as the dashboard)
        String status = (String) cmbStatus.getSelectedItem();
        if ("Out of Stock".equals(status)) {
            sql.append(" AND quantity <= 0");
        } else if ("Low Stock".equals(status)) {
            sql.append(" AND quantity > 0 AND quantity <= minimum_stock");
        } else if ("In Stock".equals(status)) {
            sql.append(" AND quantity > minimum_stock");
        }

        // Quantity range (dropdown of preset bands)
        String qty = (String) cmbQuantity.getSelectedItem();
        if ("0".equals(qty)) {
            sql.append(" AND quantity = 0");
        } else if ("1 - 10".equals(qty)) {
            sql.append(" AND quantity BETWEEN 1 AND 10");
        } else if ("11 - 50".equals(qty)) {
            sql.append(" AND quantity BETWEEN 11 AND 50");
        } else if ("51 - 100".equals(qty)) {
            sql.append(" AND quantity BETWEEN 51 AND 100");
        } else if ("100+".equals(qty)) {
            sql.append(" AND quantity > 100");
        }

        // Date purchased (preset dropdown, or Day/Month/Year dropdowns when Custom range)
        LocalDate from = null;
        LocalDate to = null;
        String datePreset = (String) cmbDatePreset.getSelectedItem();
        LocalDate today = LocalDate.now();

        if (DATE_TODAY.equals(datePreset)) {
            from = today;
            to = today;
        } else if (DATE_7DAYS.equals(datePreset)) {
            from = today.minusDays(6);
            to = today;
        } else if (DATE_30DAYS.equals(datePreset)) {
            from = today.minusDays(29);
            to = today;
        } else if (DATE_MONTH.equals(datePreset)) {
            YearMonth ym = YearMonth.from(today);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else if (DATE_CUSTOM.equals(datePreset)) {
            from = readDropdownDate(cmbFromDay, cmbFromMonth, cmbFromYear);
            to = readDropdownDate(cmbToDay, cmbToMonth, cmbToYear);
            if (from != null && to != null && from.isAfter(to)) {
                warn("\"From\" date must be on or before the \"To\" date.");
                return;
            }
        }

        if (from != null) {
            sql.append(" AND date_purchased >= ?");
            params.add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND date_purchased < ?");               // up to the end of the "to" day
            params.add(java.sql.Date.valueOf(to.plusDays(1)));
        }

        sql.append(" ORDER BY id DESC");

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pst.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pst.executeQuery()) {
                // Read-only model so double-clicking a cell doesn't start editing
                DefaultTableModel model = new DefaultTableModel(TABLE_COLUMNS, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };

                SimpleDateFormat dateFmt = new SimpleDateFormat(TABLE_DATE_FORMAT);
                SimpleDateFormat updatedFmt = new SimpleDateFormat(TABLE_UPDATED_FORMAT);
                DecimalFormat money = new DecimalFormat("#,##0.00");

                while (rs.next()) {
                    java.sql.Date purchased = rs.getDate("date_purchased");
                    BigDecimal cost = rs.getBigDecimal("cost_price");
                    model.addRow(new Object[]{
                        rs.getString("item_name"),
                        purchased == null ? "" : dateFmt.format(purchased),
                        rs.getString("category"),
                        rs.getInt("quantity"),
                        rs.getString("unit"),
                        rs.getInt("minimum_stock"),
                        cost == null ? "" : money.format(cost),
                        rs.getString("stock_status")
                    });
                }

                jTable1.setModel(model);
                applyTableColumnWidths();

                int n = model.getRowCount();
                lblCount.setText(n + (n == 1 ? " item" : " items"));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load inventory data.\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
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

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Invalid Filter", JOptionPane.WARNING_MESSAGE);
    }

    private void resetFilters() {
        cmbCategory.setSelectedIndex(0);
        cmbStatus.setSelectedIndex(0);
        cmbQuantity.setSelectedIndex(0);
        cmbDatePreset.setSelectedIndex(0);
        cmbFromDay.setSelectedIndex(0);
        cmbFromMonth.setSelectedIndex(0);
        cmbFromYear.setSelectedIndex(0);
        cmbToDay.setSelectedIndex(0);
        cmbToMonth.setSelectedIndex(0);
        cmbToYear.setSelectedIndex(0);
        customDateRow.setVisible(false);
        loadInventoryData();
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

        setMinimumSize(new Dimension(1000, 600));
        setPreferredSize(new Dimension(1280, 720));
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

        dashboardbtn      = new NavButton("Dashboard", false);
        productbtn        = new NavButton("Manage products", false);
        supplierbtn       = new NavButton("Manage supplier", false);
        inventorybtn      = new NavButton("Inventory", true);      // active page
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

        jLabel1 = new JLabel("Inventory");
        jLabel1.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        jLabel1.setForeground(TEXT_PRIMARY);
        jLabel1.setAlignmentX(Component.LEFT_ALIGNMENT);

        jLabel2 = new JLabel("View and filter all inventory items");
        jLabel2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        jLabel2.setForeground(TEXT_MUTED);
        jLabel2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filterCard = buildFilterCard();
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(jLabel1);
        top.add(Box.createVerticalStrut(2));
        top.add(jLabel2);
        top.add(Box.createVerticalStrut(20));
        top.add(filterCard);

        jLabel6 = new JLabel("Inventory Items");
        jLabel6.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        jLabel6.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 items");
        lblCount.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblCount.setForeground(TEXT_MUTED);

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(jLabel6, BorderLayout.WEST);
        tableHeading.add(lblCount, BorderLayout.EAST);

        JPanel tableSection = new JPanel(new BorderLayout(0, 10));
        tableSection.setOpaque(false);
        tableSection.add(tableHeading, BorderLayout.NORTH);
        tableSection.add(buildTable(), BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    // ---------------------------------------------------------------- filters

    /**
     * Single compact row: Category | Status | Quantity | Date preset | Apply | Reset.
     * A second row with Day/Month/Year dropdowns for From/To appears only when
     * "Custom range" is chosen in the date preset dropdown.
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

        // ---- main row ----
        JPanel mainRow = new JPanel(new GridBagLayout());
        mainRow.setOpaque(false);

        cmbCategory   = new FlatComboBox(new String[]{ALL_CATEGORIES});
        cmbStatus     = new FlatComboBox(STATUS_OPTIONS);
        cmbQuantity   = new FlatComboBox(QTY_OPTIONS);
        cmbDatePreset = new FlatComboBox(DATE_PRESET_OPTIONS);
        cmbDatePreset.addActionListener(e ->
                customDateRow.setVisible(DATE_CUSTOM.equals(cmbDatePreset.getSelectedItem())));

        addMainField(mainRow, 0, "Category", cmbCategory, 1.0);
        addMainField(mainRow, 1, "Status", cmbStatus, 0.9);
        addMainField(mainRow, 2, "Quantity", cmbQuantity, 0.9);
        addMainField(mainRow, 3, "Date purchased", cmbDatePreset, 1.1);

        btnApply = new FilledButton("Apply");
        btnApply.addActionListener(e -> loadInventoryData());
        btnReset = new OutlineButton("Reset");
        btnReset.addActionListener(e -> resetFilters());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnApply);
        buttons.add(btnReset);

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 4;
        bc.gridy = 1;
        bc.anchor = GridBagConstraints.WEST;
        bc.insets = new Insets(0, 2, 0, 0);
        mainRow.add(buttons, bc);

        // ---- custom range row (hidden unless "Custom range" is selected) ----
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

    /** A small "Label: [Day][Month][Year]" group used in the custom-range row. */
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

    /** Adds a small muted label with its input underneath, in the given column. */
    private void addMainField(JPanel row, int col, String label, JComponent field, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.weightx = weight;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        JLabel l = new JLabel(label);
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        l.setForeground(TEXT_MUTED);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 3, 10);
        row.add(l, c);

        field.setPreferredSize(new Dimension(100, INPUT_HEIGHT));
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 10);
        row.add(field, c);
    }

    // ------------------------------------------------------------------ table

    private JScrollPane buildTable() {
        jTable1 = new JTable(new DefaultTableModel(TABLE_COLUMNS, 0));
        jTable1.setRowHeight(38);
        // NOTE: Nimbus (set in main()) ignores setShowGrid/setGridColor/setIntercellSpacing,
        // so the grid is drawn as a border baked into each cell renderer instead - that
        // works under any Look and Feel.
        jTable1.setShowGrid(false);
        jTable1.setIntercellSpacing(new Dimension(0, 0));
        jTable1.setBackground(Color.WHITE);
        jTable1.setFillsViewportHeight(true);
        jTable1.setSelectionBackground(ACCENT_LIGHT);
        jTable1.setSelectionForeground(TEXT_PRIMARY);
        jTable1.setFocusable(false);
        // Default renderer survives jTable1.setModel(...) calls
        jTable1.setDefaultRenderer(Object.class, new InventoryCellRenderer());

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
        for (int i = 0; i < TABLE_WIDTHS.length && i < jTable1.getColumnModel().getColumnCount(); i++) {
            jTable1.getColumnModel().getColumn(i).setPreferredWidth(TABLE_WIDTHS[i]);
        }
    }

    private static boolean isCenterColumn(int column) {
        return column == COL_QUANTITY || column == COL_MIN_STOCK;
    }

    private static boolean isRightColumn(int column) {
        return column == COL_COST;
    }

    private static int alignmentFor(int column) {
        if (isCenterColumn(column)) {
            return SwingConstants.CENTER;
        }
        return isRightColumn(column) ? SwingConstants.RIGHT : SwingConstants.LEFT;
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

    /** Solid red button with white text (darkens on hover). Used for the main action. */
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

    /** White rounded panel with a thin border (the filter bar background). */
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

    /**
     * One matte line on the bottom + right of every cell draws the grid regardless of
     * Look and Feel (Nimbus silently ignores JTable's own setShowGrid/setGridColor).
     * The left padding is wider so left-aligned text never touches the grid line.
     */
    private static javax.swing.border.Border cellBorder(int column) {
        int rightPad = isRightColumn(column) ? 16 : 13;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR),
                new EmptyBorder(0, 14, 0, rightPad));
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
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            setForeground(TEXT_MUTED);
            setBackground(new Color(250, 250, 250));
            setBorder(cellBorder(column));
            setHorizontalAlignment(alignmentFor(column));
            return this;
        }
    }

    /** Table cells: plain text, except the Status column which is drawn as a colored pill. */
    private static class InventoryCellRenderer extends DefaultTableCellRenderer {
        private final StatusCell statusCell = new StatusCell();

        InventoryCellRenderer() {
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Color rowBg = isSelected ? ACCENT_LIGHT : Color.WHITE;
            String text = value == null ? "" : value.toString();

            if (column == COL_STATUS) {
                statusCell.set(text, rowBg);
                return statusCell;
            }

            setText(text);
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(rowBg);
            setOpaque(true);
            setBorder(cellBorder(column));
            setHorizontalAlignment(alignmentFor(column));
            return this;
        }
    }

    /** Draws a rounded status pill (In Stock = green, Low Stock = amber, Out of Stock = red). */
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
            if ("Out of Stock".equalsIgnoreCase(text)) {
                bg = OUT_BG;
                fg = OUT_TEXT;
            } else if ("Low Stock".equalsIgnoreCase(text)) {
                bg = LOW_BG;
                fg = LOW_TEXT;
            } else if ("In Stock".equalsIgnoreCase(text)) {
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
                ? new HomeFrame(userId, loggedInUsername, userRole)   // keeps the role permissions
                : new HomeFrame();
        home.setVisible(true);
        this.dispose();
    }

    private void inventorybtnActionPerformed(java.awt.event.ActionEvent evt) {
        // Already on the inventory page, so just refresh the filters and the table
        loadCategories();
        resetFilters();
    }

    private void stockbtnActionPerformed(java.awt.event.ActionEvent evt) {
        StockFrame stock = new StockFrame(
                userId,
                loggedInUsername,
                userRole
        );
        stock.setVisible(true);
        this.dispose();
    }

    private void reportsbtnActionPerformed(java.awt.event.ActionEvent evt) {
        ReportFrame report = new ReportFrame(
                userId,
                loggedInUsername,
                userRole
        );
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

        java.awt.EventQueue.invokeLater(() -> new InventoryFrame().setVisible(true));
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
    private JButton btnApply;
    private JButton btnReset;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblCount;     // "N items" label above the table
    private JLabel jLabel1;      // "Inventory" title
    private JLabel jLabel2;      // subtitle
    private JLabel jLabel6;      // table heading
    private JComboBox<String> cmbCategory;
    private JComboBox<String> cmbStatus;
    private JComboBox<String> cmbQuantity;
    private JComboBox<String> cmbDatePreset;
    private JPanel customDateRow;
    private JComboBox<String> cmbFromDay;
    private JComboBox<String> cmbFromMonth;
    private JComboBox<String> cmbFromYear;
    private JComboBox<String> cmbToDay;
    private JComboBox<String> cmbToMonth;
    private JComboBox<String> cmbToYear;
    private JScrollPane jScrollPane1;
    private JTable jTable1;
}