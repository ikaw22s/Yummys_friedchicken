package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Supplier Management frame — Owner only.
 * Built entirely in code (no GUI Builder). Same look as HomeFrame / ProductFrame / InventoryFrame / StockFrame.
 * Change the constants below to customize without touching the layout code.
 */
public class SupplierManagementFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - Supplier Management";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";

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
    private static final Color IN_BG        = new Color(234, 243, 222);
    private static final Color IN_TEXT      = new Color(59, 109, 17);

    // Table columns: only non-archived suppliers are ever loaded, so Status always reads "Active"
    private static final String[] TABLE_COLUMNS = {"ID", "Supplier Name", "Contact Number", "Status"};
    private static final int[]    TABLE_WIDTHS   = {60, 280, 160, 100};
    private static final int COL_ID     = 0;
    private static final int COL_STATUS = 3;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(SupplierManagementFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    // =====================================================================
    //  CONSTRUCTORS
    // =====================================================================

    public SupplierManagementFrame() {
        buildUI();
        loadSuppliers("");
    }

    public SupplierManagementFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadSuppliers("");

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
     * Loads active (non-archived) suppliers into the table.
     * If keyword is non-empty, filters by supplier_name or contact_person (case-insensitive).
     */
    private void loadSuppliers(String keyword) {
        String sql = "SELECT id, supplier_name, contact_number "
                + "FROM suppliers WHERE is_archived = 0";
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql += " AND (supplier_name LIKE ? OR contact_person LIKE ?)";
        }
        sql += " ORDER BY supplier_name ASC";

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
                        return col == COL_ID ? Integer.class : String.class;
                    }
                };

                while (rs.next()) {
                    String contact = rs.getString("contact_number");
                    model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("supplier_name"),
                        (contact == null || contact.isEmpty()) ? "-" : contact,
                        "Active"   // only active rows are ever queried
                    });
                }

                supplierTable.setModel(model);
                applyTableColumnWidths();

                int n = model.getRowCount();
                lblCount.setText(n + (n == 1 ? " supplier" : " suppliers"));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load suppliers.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Validates a PH mobile number (e.g. 09XX XXX XXXX). Empty is allowed since the field is optional. */
    private static boolean isValidPhMobile(String raw) {
        if (raw == null) return true;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return true;
        String digits = trimmed.replaceAll("[^0-9]", "");
        return digits.length() == 11 && digits.startsWith("09");
    }

    /** Returns null for a blank field so optional columns store NULL instead of empty strings. */
    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void setNullableString(PreparedStatement pst, int index, String value) throws SQLException {
        if (value == null) {
            pst.setNull(index, Types.VARCHAR);
        } else {
            pst.setString(index, value);
        }
    }

    /** True if an active supplier with this name already exists (case-insensitive). excludeId is ignored if < 0. */
    private boolean nameAlreadyExists(String name, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM suppliers "
                + "WHERE LOWER(supplier_name) = LOWER(?) AND is_archived = 0"
                + (excludeId >= 0 ? " AND id <> ?" : "");

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, name);
            if (excludeId >= 0) pst.setInt(2, excludeId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ---- Add Supplier -------------------------------------------------------

    private void showAddDialog() {
        SupplierDialog dlg = new SupplierDialog(this, "Add Supplier", null);
        dlg.setVisible(true);

        if (!dlg.confirmed) return;

        String name    = dlg.getSupplierName().trim();
        String person  = blankToNull(dlg.getContactPerson());
        String contact = blankToNull(dlg.getContactNumber());
        String address = blankToNull(dlg.getAddress());
        String email   = blankToNull(dlg.getEmail());

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name cannot be empty.");
            return;
        }
        if (!isValidPhMobile(contact)) {
            JOptionPane.showMessageDialog(this,
                    "Contact number must be a valid PH mobile number (e.g. 09XX XXX XXXX).");
            return;
        }

        try {
            if (nameAlreadyExists(name, -1)) {
                JOptionPane.showMessageDialog(this, "A supplier with that name already exists.");
                return;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check existing suppliers.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql = "INSERT INTO suppliers "
                + "(supplier_name, contact_person, contact_number, address, email, is_archived) "
                + "VALUES (?, ?, ?, ?, ?, 0)";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, name);
            setNullableString(pst, 2, person);
            setNullableString(pst, 3, contact);
            setNullableString(pst, 4, address);
            setNullableString(pst, 5, email);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "\"" + name + "\" added successfully.");
            loadSuppliers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to add supplier.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- View / Restore Archived Suppliers --------------------------------

    private void showArchivedSuppliersDialog() {
        String[] columns = {"ID", "Supplier Name", "Contact Number", "Contact Person", "Email"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable archivedTable = new JTable(model);
        archivedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archivedTable.setRowHeight(28);

        String sql = "SELECT id, supplier_name, contact_number, contact_person, email "
                + "FROM suppliers WHERE is_archived = 1 ORDER BY supplier_name ASC";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[] {
                    rs.getInt("id"), rs.getString("supplier_name"),
                    rs.getString("contact_number"), rs.getString("contact_person"),
                    rs.getString("email")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load archived suppliers.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Archived Suppliers", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.add(new JLabel("Select a supplier and click Restore."), BorderLayout.NORTH);
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
                JOptionPane.showMessageDialog(dialog, "Please select a supplier to restore.");
                return;
            }
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            String name = String.valueOf(model.getValueAt(row, 1));
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "Restore \"" + name + "\" to the active supplier list?",
                    "Restore Supplier", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;

            try {
                if (nameAlreadyExists(name, -1)) {
                    JOptionPane.showMessageDialog(dialog,
                            "An active supplier with this name already exists. Rename or archive the active supplier first.");
                    return;
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to check supplier name.\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try (Connection con = db.getConnection();
                 PreparedStatement pst = con.prepareStatement(
                         "UPDATE suppliers SET is_archived = 0 WHERE id = ? AND is_archived = 1")) {
                pst.setInt(1, id);
                if (pst.executeUpdate() > 0) {
                    model.removeRow(row);
                    loadSuppliers(searchField.getText());
                    JOptionPane.showMessageDialog(dialog, "\"" + name + "\" has been restored.");
                } else {
                    JOptionPane.showMessageDialog(dialog, "Supplier was not found in the archive.");
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to restore supplier.\n" + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        closeButton.addActionListener(e -> dialog.dispose());
        dialog.setSize(760, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ---- Edit Supplier ------------------------------------------------------

    private void showEditDialog() {
        int row = supplierTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a supplier to edit.");
            return;
        }
        int id = (int) supplierTable.getValueAt(row, COL_ID);

        // The table doesn't carry every field, so fetch the full row first.
        String sql = "SELECT supplier_name, contact_person, contact_number, address, email "
                + "FROM suppliers WHERE id = ?";
        SupplierDialog.ExistingSupplier existing;

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(this, "Supplier not found. It may have been archived.");
                    loadSuppliers(searchField.getText());
                    return;
                }
                existing = new SupplierDialog.ExistingSupplier(
                        id,
                        rs.getString("supplier_name"),
                        rs.getString("contact_person"),
                        rs.getString("contact_number"),
                        rs.getString("address"),
                        rs.getString("email")
                );
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load supplier details.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SupplierDialog dlg = new SupplierDialog(this, "Edit Supplier", existing);
        dlg.setVisible(true);

        if (!dlg.confirmed) return;

        String newName    = dlg.getSupplierName().trim();
        String newPerson  = blankToNull(dlg.getContactPerson());
        String newContact = blankToNull(dlg.getContactNumber());
        String newAddress = blankToNull(dlg.getAddress());
        String newEmail   = blankToNull(dlg.getEmail());

        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name cannot be empty.");
            return;
        }
        if (!isValidPhMobile(newContact)) {
            JOptionPane.showMessageDialog(this,
                    "Contact number must be a valid PH mobile number (e.g. 09XX XXX XXXX).");
            return;
        }

        try {
            if (nameAlreadyExists(newName, id)) {
                JOptionPane.showMessageDialog(this, "A supplier with that name already exists.");
                return;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check existing suppliers.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String updateSQL = "UPDATE suppliers "
                + "SET supplier_name = ?, contact_person = ?, contact_number = ?, address = ?, email = ? "
                + "WHERE id = ?";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(updateSQL)) {

            pst.setString(1, newName);
            setNullableString(pst, 2, newPerson);
            setNullableString(pst, 3, newContact);
            setNullableString(pst, 4, newAddress);
            setNullableString(pst, 5, newEmail);
            pst.setInt(6, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "\"" + newName + "\" updated successfully.");
            loadSuppliers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update supplier.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Archive Supplier ---------------------------------------------------

    private void archiveSelected() {
        int row = supplierTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a supplier to archive.");
            return;
        }

        int    id   = (int) supplierTable.getValueAt(row, COL_ID);
        String name = (String) supplierTable.getValueAt(row, 1);

        // Informational only: check whether this supplier has delivery history
        int deliveryCount = 0;
        String checkSQL = "SELECT COUNT(*) FROM stock_movements WHERE supplier_id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(checkSQL)) {
            pst.setInt(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) deliveryCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check delivery history.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message;
        if (deliveryCount > 0) {
            message = "<html><b>" + name + "</b> has <b>" + deliveryCount
                    + " recorded deliver" + (deliveryCount == 1 ? "y" : "ies") + "</b> in Stock In history.<br><br>"
                    + "Archiving will hide it from the active supplier list<br>"
                    + "and from the Stock In supplier dropdown.<br><br>"
                    + "Its delivery history will remain available in Reports.<br><br>"
                    + "Continue?</html>";
        } else {
            message = "<html>Archive <b>" + name + "</b>?<br><br>"
                    + "It will be hidden from the active supplier list<br>"
                    + "and from the Stock In supplier dropdown.</html>";
        }

        int choice = JOptionPane.showConfirmDialog(this, message,
                "Archive Supplier", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        String archiveSQL = "UPDATE suppliers SET is_archived = 1 WHERE id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(archiveSQL)) {
            pst.setInt(1, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "\"" + name + "\" has been archived.");
            loadSuppliers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to archive supplier.\n" + e.getMessage(),
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
        productbtn        = new NavButton("Manage products", false);
        supplierbtn       = new NavButton("Manage supplier", true);   // active
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
        JLabel titleLabel = new JLabel("Supplier Management");
        titleLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Add, edit, or archive suppliers");
        subtitle.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- toolbar: Option A - search+Add on the left, actions pushed right ---
        searchField = new HintTextField("Search suppliers...");
        searchField.setPreferredSize(new Dimension(260, INPUT_HEIGHT));
        searchField.setMaximumSize(new Dimension(260, INPUT_HEIGHT));

        FilledButton searchbtn = new FilledButton("Search");
        searchbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        searchbtn.addActionListener(e -> loadSuppliers(searchField.getText()));

        addSupplierbtn = new FilledButton("+ Add Supplier");
        addSupplierbtn.setPreferredSize(new Dimension(140, INPUT_HEIGHT));
        addSupplierbtn.addActionListener(e -> showAddDialog());

        archivedSuppliersbtn = new OutlineButton("Archived Items");
        archivedSuppliersbtn.setPreferredSize(new Dimension(125, INPUT_HEIGHT));
        archivedSuppliersbtn.addActionListener(e -> showArchivedSuppliersDialog());

        editSupplierbtn = new OutlineButton("Edit");
        editSupplierbtn.setPreferredSize(new Dimension(80, INPUT_HEIGHT));
        editSupplierbtn.addActionListener(e -> showEditDialog());

        archiveSupplierbtn = new DangerButton("Archive");
        archiveSupplierbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        archiveSupplierbtn.addActionListener(e -> archiveSelected());

        refreshbtn = new OutlineButton("Refresh");
        refreshbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        refreshbtn.addActionListener(e -> {
            searchField.setText("");
            loadSuppliers("");
        });

        backbtn = new OutlineButton("← Back");
        backbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        backbtn.addActionListener(this::backbtnActionPerformed);

        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbarLeft.setOpaque(false);
        toolbarLeft.add(searchField);
        toolbarLeft.add(searchbtn);
        toolbarLeft.add(addSupplierbtn);

        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbarRight.setOpaque(false);
        toolbarRight.add(editSupplierbtn);
        toolbarRight.add(archiveSupplierbtn);
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
        JLabel tableHeadingLabel = new JLabel("Supplier List");
        tableHeadingLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        tableHeadingLabel.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 suppliers");
        lblCount.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblCount.setForeground(TEXT_MUTED);

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(tableHeadingLabel, BorderLayout.WEST);
        JPanel headingRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headingRight.setOpaque(false);
        headingRight.add(archivedSuppliersbtn);
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
        supplierTable = new JTable(new DefaultTableModel(TABLE_COLUMNS, 0));
        supplierTable.setRowHeight(38);
        supplierTable.setShowGrid(false);
        supplierTable.setIntercellSpacing(new Dimension(0, 0));
        supplierTable.setBackground(Color.WHITE);
        supplierTable.setFillsViewportHeight(true);
        supplierTable.setSelectionBackground(ACCENT_LIGHT);
        supplierTable.setSelectionForeground(TEXT_PRIMARY);
        supplierTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        supplierTable.setFocusable(false);
        supplierTable.setDefaultRenderer(Object.class, new SupplierCellRenderer());

        JTableHeader th = supplierTable.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, 38));
        th.setDefaultRenderer(new HeaderRenderer());

        supplierTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showEditDialog();
            }
        });

        applyTableColumnWidths();

        jScrollPane1 = new JScrollPane(supplierTable);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    private void applyTableColumnWidths() {
        for (int i = 0; i < TABLE_WIDTHS.length
                && i < supplierTable.getColumnModel().getColumnCount(); i++) {
            supplierTable.getColumnModel().getColumn(i).setPreferredWidth(TABLE_WIDTHS[i]);
        }
    }

    private static boolean isCenterColumn(int col) { return col == COL_ID || col == COL_STATUS; }

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

    static class SupplierDialog extends JDialog {

        static class ExistingSupplier {
            final int id;
            final String name, contactPerson, contactNumber, address, email;
            ExistingSupplier(int id, String name, String contactPerson,
                    String contactNumber, String address, String email) {
                this.id = id; this.name = name; this.contactPerson = contactPerson;
                this.contactNumber = contactNumber; this.address = address; this.email = email;
            }
        }

        boolean confirmed = false;
        private final JTextField nameField;
        private final JTextField contactPersonField;
        private final JTextField contactNumberField;
        private final JTextField addressField;
        private final JTextField emailField;

        SupplierDialog(JFrame owner, String title, ExistingSupplier existing) {
            super(owner, title, true);

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;

            nameField = plainField(existing != null ? existing.name : "");
            addField(panel, c, 0, "Supplier Name *", nameField);

            contactPersonField = plainField(existing != null ? existing.contactPerson : "");
            addField(panel, c, 1, "Contact Person", contactPersonField);

            contactNumberField = plainField(existing != null ? existing.contactNumber : "");
            addField(panel, c, 2, "Contact Number (e.g. 09XX XXX XXXX)", contactNumberField);

            addressField = plainField(existing != null ? existing.address : "");
            addField(panel, c, 3, "Address", addressField);

            emailField = plainField(existing != null ? existing.email : "");
            addField(panel, c, 4, "Email", emailField);

            FilledButton saveBtn = new FilledButton(
                    existing == null ? "Add Supplier" : "Save Changes");
            saveBtn.setPreferredSize(new Dimension(150, 38));
            saveBtn.addActionListener(e -> { confirmed = true; dispose(); });

            OutlineButton cancelBtn = new OutlineButton("Cancel");
            cancelBtn.setPreferredSize(new Dimension(100, 38));
            cancelBtn.addActionListener(e -> dispose());

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            btnRow.setBackground(Color.WHITE);
            btnRow.add(cancelBtn);
            btnRow.add(saveBtn);

            c.gridy = 5;
            c.insets = new Insets(6, 0, 0, 0);
            panel.add(btnRow, c);

            setContentPane(panel);
            pack();
            setLocationRelativeTo(owner);
            getRootPane().setDefaultButton(saveBtn);
        }

        private static JTextField plainField(String value) {
            JTextField f = new JTextField(value == null ? "" : value);
            f.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            f.setPreferredSize(new Dimension(320, 34));
            f.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
            return f;
        }

        private static void addField(JPanel panel, GridBagConstraints c,
                int row, String label, JComponent field) {
            JLabel l = new JLabel(label);
            l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
            l.setForeground(TEXT_MUTED);

            JPanel block = new JPanel();
            block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
            block.setBackground(Color.WHITE);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            block.add(l);
            block.add(Box.createVerticalStrut(4));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            block.add(field);

            c.gridy = row;
            c.insets = new Insets(0, 0, 14, 0);
            panel.add(block, c);
        }

        String getSupplierName()   { return nameField.getText(); }
        String getContactPerson()  { return contactPersonField.getText(); }
        String getContactNumber()  { return contactNumberField.getText(); }
        String getAddress()        { return addressField.getText(); }
        String getEmail()          { return emailField.getText(); }
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

    /** Same solid-red Archive button used in Product Management, for visual consistency. */
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

    /** Plain cells, except Status which is drawn as a green "Active" pill. */
    private static class SupplierCellRenderer extends DefaultTableCellRenderer {
        private final StatusCell statusCell = new StatusCell();

        SupplierCellRenderer() { setVerticalAlignment(SwingConstants.CENTER); }

        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
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
            setHorizontalAlignment(isCenterColumn(column) ? CENTER : LEFT);
            return this;
        }
    }

    /** Green "Active" pill — every visible row is active by definition (archived rows are filtered out). */
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

            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int pillW = fm.stringWidth(text) + 24;
            int pillH = 24;
            int x = (getWidth() - pillW) / 2;
            int y = (getHeight() - pillH) / 2;
            g2.setColor(IN_BG);
            g2.fillRoundRect(x, y, pillW, pillH, pillH, pillH);
            g2.setColor(IN_TEXT);
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

    private void backbtnActionPerformed(java.awt.event.ActionEvent evt) {
        dashboardbtnActionPerformed(evt);
    }

    private void supplierbtnActionPerformed(java.awt.event.ActionEvent evt) {
        searchField.setText("");
        loadSuppliers("");
    }

    private void productbtnActionPerformed(java.awt.event.ActionEvent evt) {
        ProductManagentFrame product = (userRole != null)
                ? new ProductManagentFrame(userId, loggedInUsername, userRole)
                : new ProductManagentFrame();
        product.setVisible(true);
        this.dispose();
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
        java.awt.EventQueue.invokeLater(() -> new SupplierManagementFrame().setVisible(true));
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
    private JButton addSupplierbtn;
    private JButton archivedSuppliersbtn;
    private JButton editSupplierbtn;
    private JButton archiveSupplierbtn;
    private JButton refreshbtn;
    private JButton backbtn;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblCount;
    private HintTextField searchField;
    private JScrollPane jScrollPane1;
    private JTable supplierTable;
}