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
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
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
 * Stock In/Out frame, built entirely in code (no GUI Builder).
 * Same look as HomeFrame / InventoryFrame: header, collapsible sidebar, custom-painted components.
 * Change the constants below to customize the look without touching the layout code.
 */
public class StockFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS - change these to customize the frame
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Stock In/Out";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";   // header clock
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

    private static final String TABLE_DATE_FORMAT = "MMM dd, yyyy  hh:mm a";
    private static final String[] UNIT_OPTIONS = {"Kg", "ml", "L", "Gal", "pcs", " "};

    private static final int LOGO_HEIGHT     = 44;
    private static final int HEADER_HEIGHT   = 68;
    private static final int SIDEBAR_WIDTH   = 210;
    private static final int NAV_ITEM_HEIGHT = 44;
    private static final int INPUT_HEIGHT    = 36;
    private static final int SLIDE_STEP      = 30;
    private static final int SLIDE_DELAY     = 8;

    // Stock In/Out form: one compact horizontal strip with dropdowns for Item and Supplier.
    // Each field gets just enough width for its own content. Tweak these to rebalance the strip.
    private static final int FORM_FIELD_GAP       = 12;   // gap between fields in the strip
    private static final int ITEM_FIELD_WIDTH     = 160;  // combobox for items
    private static final int DATE_FIELD_WIDTH     = 110;
    private static final int QTY_FIELD_WIDTH      = 65;
    private static final int UNIT_FIELD_WIDTH     = 55;
    private static final int SUPPLIER_FIELD_WIDTH = 150;  // combobox for suppliers
    private static final int COST_FIELD_WIDTH     = 85;
    private static final int SUBMIT_BTN_WIDTH     = 130;

    private static final Color BG_PAGE      = Color.WHITE;
    private static final Color ACCENT       = new Color(153, 0, 0);
    private static final Color ACCENT_DARK  = new Color(120, 0, 0);
    private static final Color ACCENT_LIGHT = new Color(253, 238, 238);
    private static final Color TEXT_PRIMARY = new Color(33, 33, 33);
    private static final Color TEXT_MUTED   = new Color(110, 110, 110);
    private static final Color TEXT_HINT    = new Color(170, 170, 170);
    private static final Color BORDER_COLOR = new Color(228, 228, 228);
    private static final Color GRID_COLOR   = new Color(217, 217, 217);
    private static final Color HOVER_BG     = new Color(245, 245, 245);

    // Header colors: header background is ACCENT (red), so header text/icons use white-based tones
    // instead of the TEXT_PRIMARY/TEXT_MUTED used everywhere else on the white page.
    private static final Color HEADER_BG          = ACCENT;
    private static final Color HEADER_TEXT        = Color.WHITE;
    private static final Color HEADER_TEXT_MUTED  = new Color(255, 230, 230);
    private static final Color HEADER_DIVIDER     = new Color(255, 255, 255, 90);
    private static final Color HEADER_HOVER_BG    = new Color(255, 255, 255, 35);

    // Table layout: Item, Date, Quantity, Unit, Supplier, Cost
    private static final String[] TABLE_COLUMNS = {"Item", "Date", "Quantity", "Unit", "Supplier", "Cost"};
    private static final int[] TABLE_WIDTHS = {210, 170, 100, 90, 190, 120};
    private static final int COL_QUANTITY = 2;
    private static final int COL_COST     = 5;

    // Recent Stock Movement table: bigger rows/text so it's easier to read at a glance.
    private static final int TABLE_ROW_HEIGHT    = 46;
    private static final int TABLE_HEADER_HEIGHT = 42;
    private static final int TABLE_FONT_SIZE     = 15;
    private static final int TABLE_HEADER_FONT_SIZE = 13;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(StockFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private boolean isStockIn = true;    // current form mode
    private Timer slideTimer;
    private Timer clockTimer;

    public StockFrame() {
        buildUI();
        loadItemsDropdown();
        loadSuppliersDropdown();
        loadRecentStockMovements();
    }

    public StockFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadItemsDropdown();
        loadSuppliersDropdown();
        loadRecentStockMovements();

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

    /** Load all items from inventory into the Item dropdown. */
    private void loadItemsDropdown() {
        String sql = "SELECT DISTINCT item_name FROM inventory ORDER BY item_name ASC";
        try (Connection con = db.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                while (rs.next()) {
                    model.addElement(rs.getString("item_name"));
                }
                cmbItem.setModel(model);
            }
        } catch (SQLException e) {
            logger.log(java.util.logging.Level.WARNING, "Failed to load items: " + e.getMessage());
        }
    }

    /** Load all suppliers from supplier management into the Supplier dropdown. */
    private void loadSuppliersDropdown() {
        String sql = "SELECT DISTINCT supplier_name FROM suppliers ORDER BY supplier_name ASC";
        try (Connection con = db.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                while (rs.next()) {
                    model.addElement(rs.getString("supplier_name"));
                }
                cmbSupplier.setModel(model);
            }
        } catch (SQLException e) {
            logger.log(java.util.logging.Level.WARNING, "Failed to load suppliers: " + e.getMessage());
        }
    }


private void loadRecentStockMovements() {
    String sql = "SELECT i.item_name, sm.movement_date, sm.quantity, "
            + "sm.unit, sm.source_reason, sm.cost "
            + "FROM stock_movements sm "
            + "JOIN inventory i ON sm.item_id = i.id "
            + "ORDER BY sm.movement_date DESC, sm.id DESC "
            + "LIMIT 20";

    try (Connection con = db.getConnection()) {
        if (con == null) {
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            SimpleDateFormat dateFmt =
                    new SimpleDateFormat(TABLE_DATE_FORMAT);

            DecimalFormat money =
                    new DecimalFormat("#,##0.00");

            DefaultTableModel model = new DefaultTableModel(
                    TABLE_COLUMNS, 0) {

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            int movementCount = 0;

            while (rs.next()) {
                String item = rs.getString("item_name");
                Timestamp moved = rs.getTimestamp("movement_date");
                BigDecimal cost = rs.getBigDecimal("cost");

                Object[] row = {
                    item == null ? "" : item,
                    moved == null ? "" : dateFmt.format(moved),
                    rs.getInt("quantity"),
                    rs.getString("unit"),
                    rs.getString("source_reason"),
                    cost == null ? "" : money.format(cost)
                };

                model.addRow(row);
                movementCount++;
            }

            tblRecent.setModel(model);
            applyTableColumnWidths();

            lblCount.setText(
                    movementCount
                    + (movementCount == 1
                    ? " movement"
                    : " movements")
            );
        }

    } catch (SQLException e) {
        JOptionPane.showMessageDialog(
                this,
                "Unable to load stock movements: " + e.getMessage()
        );
    }
}


    /** Switches the form between Stock In and Stock Out: toggle buttons, submit button, cost field, and supplier visibility. */
    private void setMode(boolean stockIn) {
        isStockIn = stockIn;
        btnStockIn.setToggled(stockIn);
        btnStockOut.setToggled(!stockIn);
        btnSubmit.setText(stockIn ? "Add Stock" : "Remove Stock");
        spnCost.setEnabled(stockIn);           // cost is only recorded for Stock In; saved as 0 otherwise
        pnlSupplierBlock.setVisible(stockIn);  // only show Supplier for Stock In; hide for Stock Out
    }

    private void clearForm() {
        cmbItem.setSelectedIndex(-1);
        spnDate.setValue(new java.util.Date());
        spnQuantity.setValue(1);
        cmbSupplier.setSelectedIndex(-1);
        spnCost.setValue(0.0);
        cmbItem.requestFocusInWindow();
    }

    /** Midnight today-plus-one, i.e. the first instant that counts as "tomorrow". Used as the exclusive upper bound for allowed dates. */
    private static java.util.Date startOfTomorrow() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        return cal.getTime();
    }

    /** Same validation + transactional update/insert logic as the original form, just reading the new fields. */
    private void submitMovement(java.awt.event.ActionEvent evt) {
        Object itemObj = cmbItem.getSelectedItem();
        Object supplierObj = cmbSupplier.getSelectedItem();
        
        
        
        String itemName = itemObj == null ? "" : itemObj.toString().trim();
        String unit = String.valueOf(cmbUnit.getSelectedItem()).trim();
        String sourceReason = supplierObj == null ? "" : supplierObj.toString().trim();
        String remarks = "";  // Remarks field removed
        
        

        int quantity = ((Number) spnQuantity.getValue()).intValue();
        double cost = ((Number) spnCost.getValue()).doubleValue();
        String movementType = isStockIn ? "Stock In" : "Stock Out";
        
        

        if (itemName.isEmpty() || unit.isEmpty() || sourceReason.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select the item, unit, and supplier.");
            return;
        }
        if (quantity <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be greater than zero.");
            return;
        }
        if (cost < 0) {
            JOptionPane.showMessageDialog(this, "Cost cannot be negative.");
            return;
        }

        java.util.Date selectedDate = (java.util.Date) spnDate.getValue();
        if (!selectedDate.before(startOfTomorrow())) {
            JOptionPane.showMessageDialog(this,
                    "Movement date cannot be in the future.",
                    "Invalid Date", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Timestamp movementDate = new Timestamp(selectedDate.getTime());

        try (Connection con = db.getConnection()) {
            if (con == null) {
                JOptionPane.showMessageDialog(this, "Database connection failed.");
                return;
            }

            con.setAutoCommit(false);

            try {
                int itemId;
                int currentQuantity;
                int minimumStock;
                String databaseUnit;

                String selectSQL = "SELECT id, quantity, unit, minimum_stock FROM inventory "
                        + "WHERE item_name = ? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(selectSQL)) {
                    ps.setString(1, itemName);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(this, "Item not found in Inventory.");
                            con.rollback();
                            return;
                        }
                        itemId = rs.getInt("id");
                        currentQuantity = rs.getInt("quantity");
                        minimumStock = rs.getInt("minimum_stock");
                        databaseUnit = rs.getString("unit");
                    }
                }

                if (!databaseUnit.equalsIgnoreCase(unit)) {
                    JOptionPane.showMessageDialog(this,
                            "Selected unit does not match the item's inventory unit.");
                    con.rollback();
                    return;
                }

                if (!isStockIn && quantity > currentQuantity) {
                    JOptionPane.showMessageDialog(this,
                            "Insufficient stock! Available quantity: " + currentQuantity);
                    con.rollback();
                    return;
                }

                int newQuantity = isStockIn ? currentQuantity + quantity : currentQuantity - quantity;
                String status = newQuantity == 0 ? "Out of Stock"
                        : newQuantity <= minimumStock ? "Low Stock" : "In Stock";

                String updateSQL = "UPDATE inventory SET quantity = ?, status = ? WHERE item_name = ?";
                try (PreparedStatement ps = con.prepareStatement(updateSQL)) {
                    ps.setInt(1, newQuantity);
                    ps.setString(2, status);
                    ps.setString(3, itemName);
                    ps.executeUpdate();
                }
                
                        if (isStockIn) {
            String costSQL = "UPDATE inventory SET cost_price = ? WHERE item_name = ?";
            try (PreparedStatement ps = con.prepareStatement(costSQL)) {
                ps.setDouble(1, cost);
                ps.setString(2, itemName);
                ps.executeUpdate();
            }
        }

                String insertSQL = "INSERT INTO stock_movements "
                        + "(item_id, movement_type, quantity, unit, source_reason, cost, remarks, movement_date) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insertSQL)) {
                    ps.setInt(1, itemId);
                    ps.setString(2, movementType);
                    ps.setInt(3, quantity);
                    ps.setString(4, unit);
                    ps.setString(5, sourceReason);
                    ps.setDouble(6, isStockIn ? cost : 0.00);
                    ps.setString(7, remarks);
                    ps.setTimestamp(8, movementDate);
                    ps.executeUpdate();
                }

                con.commit();

                JOptionPane.showMessageDialog(this, movementType + " recorded successfully!");
                loadRecentStockMovements();
                clearForm();

            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
            e.printStackTrace();
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

        setMinimumSize(new Dimension(1250, 650));
        setPreferredSize(new Dimension(1320, 820));
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
        stockbtn          = new NavButton("Stock In/Out", true);     // active page
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

        jLabel1 = new JLabel("Stock In/Out");
        jLabel1.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        jLabel1.setForeground(TEXT_PRIMARY);
        jLabel1.setAlignmentX(Component.LEFT_ALIGNMENT);

        jLabel2 = new JLabel("Record every movement of inventory");
        jLabel2.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        jLabel2.setForeground(TEXT_MUTED);
        jLabel2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel formCard = buildFormCard();
        formCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, formCard.getPreferredSize().height));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(jLabel1);
        top.add(Box.createVerticalStrut(2));
        top.add(jLabel2);
        top.add(Box.createVerticalStrut(14));
        top.add(formCard);

        jLabel6 = new JLabel("Recent Stock Movement");
        jLabel6.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        jLabel6.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 movements");
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
        tableSection.setPreferredSize(new Dimension(0, 460));

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    // ------------------------------------------------------------------ form

    private JPanel buildFormCard() {
        CardPanel card = new CardPanel(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(18, 20, 20, 20));

        // ---- Stock In / Stock Out toggle ----
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toggleRow.setOpaque(false);

        btnStockIn = new ToggleButton("Stock In", true);
        btnStockOut = new ToggleButton("Stock Out", false);
        btnStockIn.addActionListener(e -> setMode(true));
        btnStockOut.addActionListener(e -> setMode(false));
        toggleRow.add(btnStockIn);
        toggleRow.add(btnStockOut);

        // ---- fields ----
        cmbItem = new FlatComboBox(new String[]{});  // populated by loadItemsDropdown()
        
        spnDate = new JSpinner(new SpinnerDateModel(new java.util.Date(), null, startOfTomorrow(), java.util.Calendar.DAY_OF_MONTH));
        spnDate.setEditor(new JSpinner.DateEditor(spnDate, "MMM dd, yyyy"));
        styleSpinner(spnDate);

        spnQuantity = new JSpinner(new SpinnerNumberModel(1, 0, 1000000, 1));
        styleSpinner(spnQuantity);

        cmbUnit = new FlatComboBox(UNIT_OPTIONS);

        cmbSupplier = new FlatComboBox(new String[]{});  // populated by loadSuppliersDropdown()

        spnCost = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10000000.0, 0.5));
        styleSpinner(spnCost);

        // One horizontal strip: every field lives at gridy = 0, left to right, in the
        // order it's recorded. The submit button sits at the end of the same row.
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        addGridField(grid, 0, "Item", cmbItem, ITEM_FIELD_WIDTH);
        addGridField(grid, 1, "Date", spnDate, DATE_FIELD_WIDTH);
        addGridField(grid, 2, "Quantity", spnQuantity, QTY_FIELD_WIDTH);
        addGridField(grid, 3, "Unit", cmbUnit, UNIT_FIELD_WIDTH);
        
        // Supplier field wrapped in a panel so we can hide/show it based on Stock In/Out mode
        pnlSupplierBlock = new JPanel();
        pnlSupplierBlock.setLayout(new BoxLayout(pnlSupplierBlock, BoxLayout.Y_AXIS));
        pnlSupplierBlock.setOpaque(false);
        
        JLabel supplierLabel = new JLabel("Supplier");
        supplierLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
        supplierLabel.setForeground(TEXT_MUTED);
        supplierLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        supplierLabel.setHorizontalAlignment(SwingConstants.LEFT);
        supplierLabel.setMaximumSize(supplierLabel.getPreferredSize());   // stop BoxLayout from stretching it wide
        
        cmbSupplier.setPreferredSize(new Dimension(SUPPLIER_FIELD_WIDTH, INPUT_HEIGHT));
        cmbSupplier.setMaximumSize(new Dimension(SUPPLIER_FIELD_WIDTH, INPUT_HEIGHT));
        cmbSupplier.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        pnlSupplierBlock.add(supplierLabel);
        pnlSupplierBlock.add(Box.createVerticalStrut(3));
        pnlSupplierBlock.add(cmbSupplier);
        
        GridBagConstraints supplierC = new GridBagConstraints();
        supplierC.gridx = 4;
        supplierC.gridy = 0;
        supplierC.anchor = GridBagConstraints.WEST;
        supplierC.insets = new Insets(0, 0, 0, FORM_FIELD_GAP);
        grid.add(pnlSupplierBlock, supplierC);
        
        addGridField(grid, 5, "Cost", spnCost, COST_FIELD_WIDTH);

        // ---- submit (same row, aligned with the input fields rather than the labels) ----
        btnSubmit = new FilledButton("Add Stock");
        btnSubmit.setPreferredSize(new Dimension(SUBMIT_BTN_WIDTH, INPUT_HEIGHT));
        btnSubmit.setMaximumSize(new Dimension(SUBMIT_BTN_WIDTH, INPUT_HEIGHT));
        btnSubmit.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSubmit.addActionListener(this::submitMovement);

        JLabel submitSpacer = new JLabel(" ");
        submitSpacer.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
        submitSpacer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel submitBlock = new JPanel();
        submitBlock.setLayout(new BoxLayout(submitBlock, BoxLayout.Y_AXIS));
        submitBlock.setOpaque(false);
        submitBlock.add(submitSpacer);
        submitBlock.add(Box.createVerticalStrut(3));
        submitBlock.add(btnSubmit);

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 7;
        sc.gridy = 0;
        sc.anchor = GridBagConstraints.WEST;
        grid.add(submitBlock, sc);

        card.add(toggleRow, BorderLayout.NORTH);
        card.add(grid, BorderLayout.WEST);  // ← change CENTER to WEST
        return card;
    }

    /** Flat border + consistent font for a spinner, so it matches the other inputs. */
    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
            textField.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            textField.setBorder(new EmptyBorder(0, 10, 0, 4));
        }
        spinner.setBorder(new LineBorder(BORDER_COLOR));
    }

    /** A label above a fixed-width field, placed at the given column of the single form row. */
    private void addGridField(JPanel grid, int col, String label, JComponent field, int fieldWidth) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel(label);
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 10));
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setMaximumSize(l.getPreferredSize());   // stop BoxLayout from stretching the label wide

        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setPreferredSize(new Dimension(fieldWidth, INPUT_HEIGHT));
        field.setMaximumSize(new Dimension(fieldWidth, INPUT_HEIGHT));

        block.add(l);
        block.add(Box.createVerticalStrut(3));
        block.add(field);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, FORM_FIELD_GAP);
        grid.add(block, c);
    }

    // ------------------------------------------------------------------ table

    private JScrollPane buildTable() {
        tblRecent = new JTable(new DefaultTableModel(TABLE_COLUMNS, 0));
        tblRecent.setRowHeight(TABLE_ROW_HEIGHT);
        // NOTE: Nimbus (set in main()) ignores setShowGrid/setGridColor/setIntercellSpacing,
        // so the grid is drawn as a border baked into each cell renderer instead.
        tblRecent.setShowGrid(false);
        tblRecent.setIntercellSpacing(new Dimension(0, 0));
        tblRecent.setBackground(Color.WHITE);
        tblRecent.setFillsViewportHeight(true);
        tblRecent.setSelectionBackground(ACCENT_LIGHT);
        tblRecent.setSelectionForeground(TEXT_PRIMARY);
        tblRecent.setFocusable(false);
        tblRecent.setDefaultRenderer(Object.class, new MovementCellRenderer());

        JTableHeader th = tblRecent.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, TABLE_HEADER_HEIGHT));
        th.setDefaultRenderer(new HeaderRenderer());

        applyTableColumnWidths();

        jScrollPane1 = new JScrollPane(tblRecent);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    private void applyTableColumnWidths() {
        for (int i = 0; i < TABLE_WIDTHS.length && i < tblRecent.getColumnModel().getColumnCount(); i++) {
            tblRecent.getColumnModel().getColumn(i).setPreferredWidth(TABLE_WIDTHS[i]);
        }
    }

    private static boolean isCenterColumn(int column) {
        return column == COL_QUANTITY;
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

    /** One matte line on the bottom + right of every cell draws the grid regardless of Look and Feel. */
    private static javax.swing.border.Border cellBorder(int column) {
        int rightPad = isRightColumn(column) ? 18 : 15;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR),
                new EmptyBorder(0, 16, 0, rightPad));
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
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
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
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
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

    /** The Stock In / Stock Out segmented toggle: filled red when selected, outlined when not. */
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
            setPreferredSize(new Dimension(118, 34));
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

    /** Flat text field with a thin border and a gray hint shown while empty. The hint can change at runtime. */
    private static class HintTextField extends JTextField {
        private String hint;

        HintTextField(String hint) {
            this.hint = hint;
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(Color.WHITE);
            setCaretColor(TEXT_PRIMARY);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
        }

        void setHint(String hint) {
            this.hint = hint;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = smooth(g);
                g2.setFont(getFont());
                g2.setColor(TEXT_HINT);
                FontMetrics fm = g2.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(hint, getInsets().left, y);
                g2.dispose();
            }
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
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setBorder(new LineBorder(BORDER_COLOR));
            setMaximumRowCount(8);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
                    setForeground(TEXT_PRIMARY);
                    setBackground(isSelected && index >= 0 ? ACCENT_LIGHT : Color.WHITE);
                    setBorder(new EmptyBorder(6, 10, 6, 10));
                    return this;
                }
            });
        }
    }

    /** Marker object placed in the Item column for an item's header row in the Recent Stock Movement table. */
    private static class GroupHeader {
        private final String itemName;

        GroupHeader(String itemName) {
            this.itemName = itemName;
        }

        @Override
        public String toString() {
            return itemName == null ? "" : itemName;
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
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.BOLD, TABLE_HEADER_FONT_SIZE));
            setForeground(TEXT_MUTED);
            setBackground(new Color(250, 250, 250));
            setBorder(cellBorder(column));
            setHorizontalAlignment(alignmentFor(column));
            return this;
        }
    }

    /** Table cells: plain text, right-aligned Cost and centered Quantity. */
    private static class MovementCellRenderer extends DefaultTableCellRenderer {
        MovementCellRenderer() {
            setVerticalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setOpaque(true);
            setBorder(cellBorder(column));
            setHorizontalAlignment(alignmentFor(column));

            // Header row: the item name in the first column, the rest of the row left blank.

            Color rowBg = isSelected ? ACCENT_LIGHT : Color.WHITE;
            // The Item column is already named by the group header above these rows,
            // so leave it blank here instead of repeating the item name on every row.
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.PLAIN, TABLE_FONT_SIZE));
            setForeground(TEXT_PRIMARY);
            setBackground(rowBg);
            return this;
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
        // Already on Stock In/Out, so just refresh the form and the recent movements
        setMode(true);
        clearForm();
        loadRecentStockMovements();
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
    UserManagementFrame userMgmt =
            new UserManagementFrame(
                    userId,
                    loggedInUsername,
                    userRole
            );

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

        java.awt.EventQueue.invokeLater(() -> new StockFrame().setVisible(true));
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
    private ToggleButton btnStockIn;
    private ToggleButton btnStockOut;
    private JButton btnSubmit;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblCount;     // "N movements" label above the table
    private JLabel jLabel1;      // "Stock In/Out" title
    private JLabel jLabel2;      // subtitle
    private JLabel jLabel6;      // table heading
    private JComboBox<String> cmbItem;           // dropdown: items from inventory
    private JSpinner spnDate;
    private JSpinner spnQuantity;
    private JComboBox<String> cmbUnit;
    private JComboBox<String> cmbSupplier;       // dropdown: suppliers (hidden in Stock Out mode)
    private JPanel pnlSupplierBlock;             // wrapper around supplier field so we can hide/show it
    private JSpinner spnCost;
    private JScrollPane jScrollPane1;
    private JTable tblRecent;
}