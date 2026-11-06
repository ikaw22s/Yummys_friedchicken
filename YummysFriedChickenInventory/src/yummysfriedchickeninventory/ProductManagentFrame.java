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
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Product Management frame — Owner only.
 * Built entirely in code (no GUI Builder). Same look as HomeFrame / InventoryFrame / StockFrame.
 * Change the constants below to customize without touching the layout code.
 */
public class ProductManagentFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Product Management";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

    /** Fixed category options shown in the Add / Edit dialog. Add more here as needed. */
    private static final String[] CATEGORY_OPTIONS = {
        "Meat", "Ingredients", "Packaging", "Beverages", "Condiments", "Cleaning Supplies", "Other"
    };

    /** Fixed unit options shown in the Add / Edit dialog. */
    private static final String[] UNIT_OPTIONS = {
        "kg", "g", "L", "ml", "Gal", "pcs", "pack", "box"
    };

    private static final int LOGO_HEIGHT     = 44;
    private static final int HEADER_HEIGHT   = 68;
    private static final int SIDEBAR_WIDTH   = 210;
    private static final int NAV_ITEM_HEIGHT = 44;
    private static final int INPUT_HEIGHT    = 34;
    private static final int SLIDE_STEP      = 30;
    private static final int SLIDE_DELAY     = 8;

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

    // header colors (red header bar)
    private static final Color HEADER_BG          = ACCENT;
    private static final Color HEADER_TEXT        = Color.WHITE;
    private static final Color HEADER_TEXT_MUTED  = new Color(255, 230, 230);
    private static final Color HEADER_DIVIDER     = new Color(255, 255, 255, 90);
    private static final Color HEADER_HOVER_BG    = new Color(255, 255, 255, 35);
    private static final Color WARN_BG      = new Color(255, 243, 205);
    private static final Color WARN_TEXT    = new Color(133, 79, 11);

    // Table columns
    private static final String[] TABLE_COLUMNS = {"ID", "Product Name", "Category", "Unit", "Min Stock"};
    private static final int[]    TABLE_WIDTHS   = {60, 260, 160, 120, 100};
    private static final int COL_ID       = 0;
    private static final int COL_MIN      = 4;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(ProductManagentFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    // =====================================================================
    //  CONSTRUCTORS
    // =====================================================================

    public ProductManagentFrame() {
        buildUI();
        loadProducts("");
    }

    public ProductManagentFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadProducts("");

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

    /**
     * Loads active (non-archived) products into the table.
     * If keyword is non-empty, filters by item_name or category (case-insensitive).
     */
    private void loadProducts(String keyword) {
        String sql = "SELECT id, item_name, category, unit, minimum_stock "
                + "FROM inventory WHERE is_archived = 0";
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql += " AND (item_name LIKE ? OR category LIKE ?)";
        }
        sql += " ORDER BY item_name ASC";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            if (hasKeyword) {
                String like = "%" + keyword.trim() + "%";
                pst.setString(1, like);
                pst.setString(2, like);
            }

            try (ResultSet rs = pst.executeQuery()) {
                DefaultTableModel model = new DefaultTableModel(TABLE_COLUMNS, 0) {
                    @Override
                    public boolean isCellEditable(int row, int col) { return false; }
                    @Override
                    public Class<?> getColumnClass(int col) {
                        return col == COL_ID || col == COL_MIN ? Integer.class : String.class;
                    }
                };

                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("unit"),
                        rs.getInt("minimum_stock")
                    });
                }

                productTable.setModel(model);
                applyTableColumnWidths();

                int n = model.getRowCount();
                lblCount.setText(n + (n == 1 ? " product" : " products"));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load products.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Add Product -------------------------------------------------------

    private void showAddDialog() {
        ProductDialog dlg = new ProductDialog(this, "Add Product", null);
        dlg.setVisible(true);

        if (!dlg.confirmed) return;

        String name     = dlg.getName().trim();
        String category = dlg.getCategory();
        String unit     = dlg.getUnit();
        int    minStock = dlg.getMinStock();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Product name cannot be empty.");
            return;
        }

        // status = "Out of Stock" because quantity starts at 0
        String sql = "INSERT INTO inventory "
                + "(item_name, category, unit, quantity, minimum_stock, cost_price, "
                + "date_purchased, status, is_archived) "
                + "VALUES (?, ?, ?, 0, ?, 0.00, ?, 'Out of Stock', 0)";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, name);
            pst.setString(2, category);
            pst.setString(3, unit);
            pst.setInt(4, minStock);
            pst.setDate(5, new java.sql.Date(System.currentTimeMillis()));
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this,
                    "\"" + name + "\" added successfully.\n"
                    + "Use Stock In/Out to record opening stock.");
            loadProducts(searchField.getText());

        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate")) {
                JOptionPane.showMessageDialog(this,
                        "A product with that name already exists.");
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to add product.\n" + e.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---- View / Restore Archived Products ---------------------------------

    private void showArchivedProductsDialog() {
        String[] columns = {"ID", "Product Name", "Category", "Unit", "Min Stock"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable archivedTable = new JTable(model);
        archivedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archivedTable.setRowHeight(28);

        String sql = "SELECT id, item_name, category, unit, minimum_stock "
                + "FROM inventory WHERE is_archived = 1 ORDER BY item_name ASC";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[] {
                    rs.getInt("id"), rs.getString("item_name"),
                    rs.getString("category"), rs.getString("unit"),
                    rs.getInt("minimum_stock")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load archived products.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Archived Products", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.add(new JLabel("Select a product and click Restore."), BorderLayout.NORTH);
        dialog.add(new JScrollPane(archivedTable), BorderLayout.CENTER);
        JButton restoreButton = new OutlineButton("Restore");
        JButton closeButton = new OutlineButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(restoreButton);
        buttons.add(closeButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        restoreButton.addActionListener(e -> {
            int row = archivedTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(dialog, "Please select a product to restore.");
                return;
            }
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            String name = String.valueOf(model.getValueAt(row, 1));
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "Restore \"" + name + "\" to the active product list?",
                    "Restore Product", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;

            try (Connection con = db.getConnection();
                 PreparedStatement pst = con.prepareStatement(
                         "UPDATE inventory SET is_archived = 0 WHERE id = ? AND is_archived = 1")) {
                pst.setInt(1, id);
                if (pst.executeUpdate() > 0) {
                    model.removeRow(row);
                    loadProducts(searchField.getText());
                    JOptionPane.showMessageDialog(dialog, "\"" + name + "\" has been restored.");
                } else {
                    JOptionPane.showMessageDialog(dialog, "Product was not found in the archive.");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to restore product.\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());
        dialog.setSize(700, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---- Edit Product ------------------------------------------------------

    private void showEditDialog() {
        int row = productTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a product to edit.");
            return;
        }

        int    id       = (int) productTable.getValueAt(row, COL_ID);
        String name     = (String) productTable.getValueAt(row, 1);
        String category = (String) productTable.getValueAt(row, 2);
        String unit     = (String) productTable.getValueAt(row, 3);
        int    minStock = (int)    productTable.getValueAt(row, COL_MIN);

        ProductDialog.ExistingProduct existing =
                new ProductDialog.ExistingProduct(id, name, category, unit, minStock);

        ProductDialog dlg = new ProductDialog(this, "Edit Product", existing);
        dlg.setVisible(true);

        if (!dlg.confirmed) return;

        String newName     = dlg.getName().trim();
        String newCategory = dlg.getCategory();
        String newUnit     = dlg.getUnit();
        int    newMinStock = dlg.getMinStock();

        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Product name cannot be empty.");
            return;
        }

        // Recalculate status in case minimum_stock changed
        String recalcSQL =
                "UPDATE inventory "
                + "SET item_name = ?, category = ?, unit = ?, minimum_stock = ?, "
                + "status = CASE "
                + "WHEN quantity <= 0 THEN 'Out of Stock' "
                + "WHEN quantity <= ? THEN 'Low Stock' "
                + "ELSE 'In Stock' END "
                + "WHERE id = ?";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(recalcSQL)) {

            pst.setString(1, newName);
            pst.setString(2, newCategory);
            pst.setString(3, newUnit);
            pst.setInt(4, newMinStock);
            pst.setInt(5, newMinStock);   // same value used in the CASE condition
            pst.setInt(6, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "\"" + newName + "\" updated successfully.");
            loadProducts(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update product.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Archive Product ---------------------------------------------------

    private void archiveSelected() {
        int row = productTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a product to archive.");
            return;
        }

        int    id   = (int) productTable.getValueAt(row, COL_ID);
        String name = (String) productTable.getValueAt(row, 1);

        // 1. Check current quantity — warn Owner if stock remains
        String checkSQL = "SELECT quantity FROM inventory WHERE id = ?";
        int currentQty = 0;
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(checkSQL)) {
            pst.setInt(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) currentQty = rs.getInt("quantity");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check product stock.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. Build confirmation message
        String message;
        if (currentQty > 0) {
            message = "<html><b>" + name + "</b> still has <b>" + currentQty
                    + " unit(s)</b> in stock.<br><br>"
                    + "Archiving will hide it from the active product list<br>"
                    + "and prevent new Stock In/Out transactions.<br><br>"
                    + "Its stock movement history will remain in Reports.<br><br>"
                    + "Are you sure you want to archive it?</html>";
        } else {
            message = "<html>Archive <b>" + name + "</b>?<br><br>"
                    + "It will be hidden from the active product list<br>"
                    + "and its history will remain in Reports.</html>";
        }

        int choice = JOptionPane.showConfirmDialog(this, message,
                "Archive Product", JOptionPane.YES_NO_OPTION,
                currentQty > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        // 3. Soft-delete: set is_archived = 1
        String archiveSQL = "UPDATE inventory SET is_archived = 1 WHERE id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(archiveSQL)) {
            pst.setInt(1, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this,
                    "\"" + name + "\" has been archived.");
            loadProducts(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to archive product.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
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
            label.setIcon(new ImageIcon(
                    icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        } else {
            label.setText("Yummy's Fried Chicken");
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
        productbtn        = new NavButton("Manage products", true);   // active
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

        // --- page title ---
        JLabel titleLabel = new JLabel("Product Management");
        titleLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Add, edit, or archive inventory products");
        subtitle.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- toolbar: search + buttons ---
        searchField = new HintTextField("Search products...");
        searchField.setPreferredSize(new Dimension(260, INPUT_HEIGHT));
        searchField.setMaximumSize(new Dimension(260, INPUT_HEIGHT));

        FilledButton searchbtn = new FilledButton("Search");
        searchbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        searchbtn.addActionListener(e -> loadProducts(searchField.getText()));

        addProductbtn = new FilledButton("+ Add Product");
        addProductbtn.setPreferredSize(new Dimension(130, INPUT_HEIGHT));
        addProductbtn.addActionListener(e -> showAddDialog());

        archivedProductsbtn = new OutlineButton("Archived Items");
        archivedProductsbtn.setPreferredSize(new Dimension(125, INPUT_HEIGHT));
        archivedProductsbtn.addActionListener(e -> showArchivedProductsDialog());

        editProductbtn = new OutlineButton("Edit");
        editProductbtn.setPreferredSize(new Dimension(80, INPUT_HEIGHT));
        editProductbtn.addActionListener(e -> showEditDialog());

        archiveProductbtn = new DangerButton("Archive");
        archiveProductbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        archiveProductbtn.addActionListener(e -> archiveSelected());

        refreshbtn = new OutlineButton("Refresh");
        refreshbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        refreshbtn.addActionListener(e -> {
            searchField.setText("");
            loadProducts("");
        });

        backbtn = new OutlineButton("← Back");
        backbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        backbtn.addActionListener(this::backbtnActionPerformed);

        // Left group: search + Add Product. Right group: Edit / Archive / Refresh / Back.
        // The two groups sit in a BorderLayout row so the right group hugs the right edge.
        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbarLeft.setOpaque(false);
        toolbarLeft.add(searchField);
        toolbarLeft.add(searchbtn);
        toolbarLeft.add(addProductbtn);

        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbarRight.setOpaque(false);
        toolbarRight.add(editProductbtn);
        toolbarRight.add(archiveProductbtn);
        toolbarRight.add(refreshbtn);
        toolbarRight.add(backbtn);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(toolbarLeft, BorderLayout.WEST);
        toolbar.add(toolbarRight, BorderLayout.EAST);
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, INPUT_HEIGHT));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(titleLabel);
        top.add(Box.createVerticalStrut(2));
        top.add(subtitle);
        top.add(Box.createVerticalStrut(16));
        top.add(toolbar);

        // --- table section ---
        JLabel tableHeadingLabel = new JLabel("Product List");
        tableHeadingLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        tableHeadingLabel.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 products");
        lblCount.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblCount.setForeground(TEXT_MUTED);

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(tableHeadingLabel, BorderLayout.WEST);
        JPanel headingRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headingRight.setOpaque(false);
        headingRight.add(archivedProductsbtn);
        headingRight.add(lblCount);
        tableHeading.add(headingRight, BorderLayout.EAST);

        JPanel tableSection = new JPanel(new BorderLayout(0, 10));
        tableSection.setOpaque(false);
        tableSection.add(tableHeading, BorderLayout.NORTH);
        tableSection.add(buildTable(), BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    // ------------------------------------------------------------------ table

    private JScrollPane buildTable() {
        productTable = new JTable(new DefaultTableModel(TABLE_COLUMNS, 0));
        productTable.setRowHeight(38);
        productTable.setShowGrid(false);
        productTable.setIntercellSpacing(new Dimension(0, 0));
        productTable.setBackground(Color.WHITE);
        productTable.setFillsViewportHeight(true);
        productTable.setSelectionBackground(ACCENT_LIGHT);
        productTable.setSelectionForeground(TEXT_PRIMARY);
        productTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productTable.setFocusable(false);
        productTable.setDefaultRenderer(Object.class, new ProductCellRenderer());

        JTableHeader th = productTable.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, 38));
        th.setDefaultRenderer(new HeaderRenderer());

        // Double-click a row to open the Edit dialog
        productTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showEditDialog();
            }
        });

        applyTableColumnWidths();

        jScrollPane1 = new JScrollPane(productTable);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    private void applyTableColumnWidths() {
        for (int i = 0; i < TABLE_WIDTHS.length
                && i < productTable.getColumnModel().getColumnCount(); i++) {
            productTable.getColumnModel().getColumn(i).setPreferredWidth(TABLE_WIDTHS[i]);
        }
    }

    private static boolean isCenterColumn(int col) { return col == COL_ID || col == COL_MIN; }

    private static javax.swing.border.Border cellBorder(int col) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR),
                new EmptyBorder(0, isCenterColumn(col) ? 6 : 14, 0, 14));
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    @Override
    public void dispose() {
        if (clockTimer != null) clockTimer.stop();
        if (slideTimer != null) slideTimer.stop();
        super.dispose();
    }

    // =====================================================================
    //  ADD / EDIT DIALOG
    // =====================================================================

    /**
     * Modal dialog for both Add and Edit.
     * Pass {@code existing = null} for Add; pass an ExistingProduct for Edit.
     */
    static class ProductDialog extends JDialog {

        static class ExistingProduct {
            final int id;
            final String name, category, unit;
            final int minStock;
            ExistingProduct(int id, String name, String category, String unit, int minStock) {
                this.id = id; this.name = name; this.category = category;
                this.unit = unit; this.minStock = minStock;
            }
        }

        boolean confirmed = false;
        private final JTextField nameField;
        private final JComboBox<String> categoryBox;
        private final JComboBox<String> unitBox;
        private final JSpinner minStockSpinner;

        ProductDialog(JFrame owner, String title, ExistingProduct existing) {
            super(owner, title, true);   // modal = true

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 14, 0);

            // Product Name
            nameField = new JTextField(existing != null ? existing.name : "");
            nameField.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            nameField.setPreferredSize(new Dimension(300, 34));
            nameField.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
            addField(panel, c, 0, "Product Name", nameField);

            // Category
            categoryBox = new FlatComboBox(CATEGORY_OPTIONS);
            categoryBox.setPreferredSize(new Dimension(300, 34));
            if (existing != null) categoryBox.setSelectedItem(existing.category);
            addField(panel, c, 1, "Category", categoryBox);

            // Unit
            unitBox = new FlatComboBox(UNIT_OPTIONS);
            unitBox.setPreferredSize(new Dimension(300, 34));
            if (existing != null) unitBox.setSelectedItem(existing.unit);
            addField(panel, c, 2, "Unit", unitBox);

            // Min Stock
            minStockSpinner = new JSpinner(new SpinnerNumberModel(
                    existing != null ? existing.minStock : 5, 0, 100000, 1));
            minStockSpinner.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            JComponent editor = minStockSpinner.getEditor();
            if (editor instanceof JSpinner.DefaultEditor) {
                ((JSpinner.DefaultEditor) editor).getTextField()
                        .setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
                ((JSpinner.DefaultEditor) editor).getTextField()
                        .setBorder(new EmptyBorder(0, 10, 0, 4));
            }
            minStockSpinner.setBorder(new LineBorder(BORDER_COLOR));
            minStockSpinner.setPreferredSize(new Dimension(300, 34));
            addField(panel, c, 3, "Minimum Stock", minStockSpinner);

            // Buttons
            FilledButton saveBtn = new FilledButton(
                    existing == null ? "Add Product" : "Save Changes");
            saveBtn.setPreferredSize(new Dimension(140, 38));
            saveBtn.addActionListener(e -> { confirmed = true; dispose(); });

            OutlineButton cancelBtn = new OutlineButton("Cancel");
            cancelBtn.setPreferredSize(new Dimension(100, 38));
            cancelBtn.addActionListener(e -> dispose());

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            btnRow.setBackground(Color.WHITE);
            btnRow.add(cancelBtn);
            btnRow.add(saveBtn);

            c.gridy = 4;
            c.insets = new Insets(6, 0, 0, 0);
            panel.add(btnRow, c);

            setContentPane(panel);
            pack();
            setLocationRelativeTo(owner);

            // Allow pressing Enter to confirm
            getRootPane().setDefaultButton(saveBtn);
        }

        private static void addField(JPanel panel, GridBagConstraints c,
                int row, String label, JComponent field) {
            JLabel l = new JLabel(label);
            l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
            l.setForeground(TEXT_MUTED);
            c.gridy = row;
            c.insets = new Insets(0, 0, 3, 0);
            panel.add(l, c);

            // Re-add on the same row but below the label via a wrapper
            JPanel block = new JPanel();
            block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
            block.setBackground(Color.WHITE);
            block.add(l);
            block.add(Box.createVerticalStrut(4));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            block.add(field);

            // Replace the single-label add with the block
            panel.remove(l);
            c.insets = new Insets(0, 0, 14, 0);
            panel.add(block, c);
        }

        public String getName()    { return nameField.getText(); }
        String getCategory(){ return (String) categoryBox.getSelectedItem(); }
        String getUnit()    { return (String) unitBox.getSelectedItem(); }
        int    getMinStock(){ return ((Number) minStockSpinner.getValue()).intValue(); }
    }

    // =====================================================================
    //  CUSTOM COMPONENTS (same style as the rest of the project)
    // =====================================================================

    private static class NavButton extends JButton {
        private final boolean active;
        private boolean hover;
        NavButton(String text, boolean active) {
            super(text);
            this.active = active;
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(SIDEBAR_WIDTH, NAV_ITEM_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, NAV_ITEM_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (active) {
                g2.setColor(ACCENT_LIGHT); g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);       g2.fillRect(0, 0, 4, getHeight());
            } else if (hover) {
                g2.setColor(HOVER_BG); g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setFont(new Font(FONT_FAMILY, active ? Font.BOLD : Font.PLAIN, 14));
            g2.setColor(active ? ACCENT : TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 24, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static class HamburgerButton extends JButton {
        private boolean hover;
        HamburgerButton() {
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(40, 40));
            setToolTipText("Show / hide menu");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (hover) { g2.setColor(HEADER_HOVER_BG); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
            g2.setColor(HEADER_TEXT);
            int x = (getWidth() - 20) / 2, cy = getHeight() / 2;
            g2.fillRoundRect(x, cy - 7, 20, 2, 2, 2);
            g2.fillRoundRect(x, cy - 1, 20, 2, 2, 2);
            g2.fillRoundRect(x, cy + 5, 20, 2, 2, 2);
            g2.dispose();
        }
    }

    private static class FilledButton extends JButton {
        private boolean hover;
        FilledButton(String text) {
            super(text);
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(hover ? ACCENT_DARK : ACCENT);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Header-only version of OutlineButton (transparent, white text, for the red header bar). */
    private static class HeaderOutlineButton extends JButton {
        private boolean hover;
        HeaderOutlineButton(String text) {
            super(text);
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1, h = getHeight() - 1;
            g2.setColor(hover ? HEADER_HOVER_BG : new Color(255, 255, 255, 0));
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(HEADER_TEXT);
            g2.drawRoundRect(0, 0, w, h, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(HEADER_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static class OutlineButton extends JButton {
        private boolean hover;
        OutlineButton(String text) {
            super(text);
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1, h = getHeight() - 1;
            g2.setColor(hover ? ACCENT : Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(ACCENT);
            g2.drawRoundRect(0, 0, w, h, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(hover ? Color.WHITE : ACCENT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Amber/warning-colored button used for Archive. */
    private static class DangerButton extends JButton {
        private static final Color BG   = new Color(180, 0, 0);
        private static final Color BG_H = new Color(140, 0, 0);
        private boolean hover;
        DangerButton(String text) {
            super(text);
            setFocusPainted(false); setFocusable(false);
            setBorderPainted(false); setContentAreaFilled(false); setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, INPUT_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(hover ? BG_H : BG);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static class HintTextField extends JTextField {
        private final String hint;
        HintTextField(String hint) {
            this.hint = hint;
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(Color.WHITE);
            setCaretColor(TEXT_PRIMARY);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = smooth(g);
                g2.setFont(getFont());
                g2.setColor(TEXT_HINT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, getInsets().left,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    private static class FlatComboBox extends JComboBox<String> {
        FlatComboBox(String[] items) {
            super(items);
            setUI(new BasicComboBoxUI() {
                @Override protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override protected void paintComponent(Graphics g) {
                            Graphics2D g2 = smooth(g);
                            g2.setColor(TEXT_MUTED);
                            g2.setStroke(new BasicStroke(1.6f));
                            int cx = getWidth() / 2, cy = getHeight() / 2;
                            g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                            g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                            g2.dispose();
                        }
                    };
                    b.setBorder(null); b.setContentAreaFilled(false);
                    b.setFocusable(false); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    return b;
                }
                @Override protected ComboPopup createPopup() {
                    BasicComboPopup popup = new BasicComboPopup(comboBox);
                    popup.setBorder(new LineBorder(BORDER_COLOR));
                    return popup;
                }
                @Override public void paintCurrentValueBackground(
                        Graphics g, Rectangle bounds, boolean hasFocus) { }
            });
            setFocusable(false); setBackground(Color.WHITE);
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setBorder(new LineBorder(BORDER_COLOR));
            setMaximumRowCount(8);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(
                        JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
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

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() { setOpaque(true); setVerticalAlignment(SwingConstants.CENTER); }
        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            setForeground(TEXT_MUTED);
            setBackground(new Color(250, 250, 250));
            setBorder(cellBorder(column));
            setHorizontalAlignment(isCenterColumn(column) ? CENTER : LEFT);
            return this;
        }
    }

    private static class ProductCellRenderer extends DefaultTableCellRenderer {
        ProductCellRenderer() { setVerticalAlignment(SwingConstants.CENTER); }
        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Color rowBg = isSelected ? ACCENT_LIGHT : Color.WHITE;
            setText(value == null ? "" : value.toString());
            setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setBackground(rowBg);
            setOpaque(true);
            setBorder(cellBorder(column));
            setHorizontalAlignment(isCenterColumn(column) ? CENTER : LEFT);
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

    private void backbtnActionPerformed(java.awt.event.ActionEvent evt) {
        dashboardbtnActionPerformed(evt);   // Back goes to Home/Dashboard
    }

    private void productbtnActionPerformed(java.awt.event.ActionEvent evt) {
        searchField.setText("");
        loadProducts("");
    }

    private void inventorybtnActionPerformed(java.awt.event.ActionEvent evt) {
        InventoryFrame inv = (userRole != null)
                ? new InventoryFrame(userId, loggedInUsername, userRole)
                : new InventoryFrame();
        inv.setVisible(true);
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
                this, "Are you sure you want to logout?",
                "Logout", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            new LoginFrame().setVisible(true);
            this.dispose();
        }
    }

    private void supplierbtnActionPerformed(java.awt.event.ActionEvent evt) {         SupplierManagementFrame supplier = (userRole != null)
                ? new SupplierManagementFrame(userId, loggedInUsername, userRole)
                : new SupplierManagementFrame();
        supplier.setVisible(true);
        this.dispose();}
    private void userManagementbtnActionPerformed(java.awt.event.ActionEvent evt) {         UserManagementFrame userMgmt = (userRole != null)
                ? new UserManagementFrame(userId, loggedInUsername, userRole)
                : new UserManagementFrame();
        userMgmt.setVisible(true);
        this.dispose(); }
    private void myAccountbtnActionPerformed(java.awt.event.ActionEvent evt) {     MyAccountFrame myAccount = (userRole != null)
            ? new MyAccountFrame(userId, loggedInUsername, userRole)
            : new MyAccountFrame();
    myAccount.setVisible(true);
    this.dispose(); }

    // =====================================================================
    //  MAIN
    // =====================================================================

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info :
                    javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new ProductManagentFrame().setVisible(true));
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
    private JButton addProductbtn;
    private JButton archivedProductsbtn;
    private JButton editProductbtn;
    private JButton archiveProductbtn;
    private JButton refreshbtn;
    private JButton backbtn;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblCount;
    private HintTextField searchField;
    private JScrollPane jScrollPane1;
    private JTable productTable;
}