package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * User Management frame — Owner only.
 * Built entirely in code (no GUI Builder). Same look as the rest of the project.
 * Change the constants below to customize without touching the layout code.
 */
public class UserManagementFrame extends JFrame {

    // =====================================================================
    //  EDITABLE CONSTANTS
    // =====================================================================
    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - User Management";
    private static final String BRANCH_NAME  = "Puerto Branch";
    private static final String DATE_FORMAT  = "MMM dd, yyyy  hh:mm a";
    private static final String LOGO_PATH    = "/yummysfriedchickeninventory/images/Logo2.png";
    private static final String FONT_FAMILY  = "Arial";
    private static final String TABLE_DATE_FORMAT = "MMM dd, yyyy";

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
    private static final Color IN_BG        = new Color(234, 243, 222);   // Active pill
    private static final Color IN_TEXT      = new Color(59, 109, 17);
    private static final Color OUT_BG       = new Color(252, 235, 235);   // Inactive pill
    private static final Color OUT_TEXT     = new Color(163, 45, 45);

    // Password policy
    private static final int MIN_PASSWORD_LENGTH = 8;

    // Table columns
    private static final String[] TABLE_COLUMNS =
            {"User ID", "Username", "Role", "Account Status", "Date Created"};
    private static final int[] TABLE_WIDTHS = {70, 190, 100, 130, 160};
    private static final int COL_ID      = 0;
    private static final int COL_USERNAME = 1;
    private static final int COL_ROLE    = 2;
    private static final int COL_STATUS  = 3;
    private static final int COL_CREATED = 4;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(UserManagementFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    // =====================================================================
    //  CONSTRUCTORS
    // =====================================================================

    public UserManagementFrame() {
    JOptionPane.showMessageDialog(
        null,
        "Please log in as Owner to access User Management.",
        "Access Denied",
        JOptionPane.ERROR_MESSAGE
    );

    dispose();
    }

    public UserManagementFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        // Defensive check: Staff should never reach this frame (the nav link is
        // already hidden for them), but guard against it being opened directly.
        boolean isOwner = "Owner".equalsIgnoreCase(role) || "Owner/Manager".equalsIgnoreCase(role);
        if (!isOwner) {
            JOptionPane.showMessageDialog(null,
                    "Only the Owner can access User Management.",
                    "Access Denied", JOptionPane.ERROR_MESSAGE);
            buildUI();
            dashboardbtnActionPerformed(null);
            return;
        }

        buildUI();
        loadUsers("");
        applyRolePermissions();
    }

    // =====================================================================
    //  DATA
    // =====================================================================

    private void applyRolePermissions() {
        boolean isOwner = "Owner".equalsIgnoreCase(userRole)
                || "Owner/Manager".equalsIgnoreCase(userRole);

        productbtn.setVisible(isOwner);
        supplierbtn.setVisible(isOwner);
        userManagementbtn.setVisible(isOwner);
        myAccountbtn.setVisible(isOwner);
    }

    /** Loads ALL accounts (Active and Inactive) so Activate/Deactivate has something to act on. */
    private void loadUsers(String keyword) {
        String sql = "SELECT user_id, username, role, account_status, created_at FROM users WHERE 1=1";
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql += " AND (username LIKE ? OR role LIKE ?)";
        }
        sql += " ORDER BY user_id ASC";

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

                SimpleDateFormat dateFmt = new SimpleDateFormat(TABLE_DATE_FORMAT);

                while (rs.next()) {
                    Timestamp created = rs.getTimestamp("created_at");
                    model.addRow(new Object[]{
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("account_status"),
                        created == null ? "" : dateFmt.format(created)
                    });
                }

                userTable.setModel(model);
                applyTableColumnWidths();

                int n = model.getRowCount();
                lblCount.setText(n + (n == 1 ? " account" : " accounts"));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load user accounts.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** True if an account with this username already exists (case-insensitive). excludeId ignored if < 0. */
    private boolean usernameAlreadyExists(String username, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)"
                + (excludeId >= 0 ? " AND user_id <> ?" : "");
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, username);
            if (excludeId >= 0) pst.setInt(2, excludeId);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Minimum length + at least one uppercase letter + at least one digit. */
    private static boolean isValidPassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD_LENGTH) return false;
        boolean hasUpper = false, hasDigit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasDigit;
    }

    // ---- Add User -------------------------------------------------------

    private void showAddDialog() {
        UserDialog dlg = new UserDialog(this, "Add User", null);
        dlg.setVisible(true);
        if (!dlg.confirmed) return;

        String username = dlg.getUsername().trim();
        String password  = dlg.getPassword();
        String confirm   = dlg.getConfirmPassword();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty.");
            return;
        }
        if (!isValidPassword(password)) {
            JOptionPane.showMessageDialog(this,
                    "Password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and include at least one uppercase letter and one number.");
            return;
        }
        if (!password.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.");
            return;
        }

        try {
            if (usernameAlreadyExists(username, -1)) {
                JOptionPane.showMessageDialog(this, "An account with that username already exists.");
                return;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check existing accounts.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sql = "INSERT INTO users (username, password, role, account_status) "
                + "VALUES (?, ?, 'Staff', 'Active')";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, username);
            pst.setString(2, password);   // stored as plaintext per project decision
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "Account \"" + username + "\" created successfully.");
            loadUsers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to create account.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Edit User ------------------------------------------------------

    private void showEditDialog() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an account to edit.");
            return;
        }

        int    id          = (int) userTable.getValueAt(row, COL_ID);
        String currentRole = (String) userTable.getValueAt(row, COL_ROLE);

        if ("Owner".equalsIgnoreCase(currentRole)) {
            JOptionPane.showMessageDialog(this,
                    "The Owner account cannot be edited through User Management.",
                    "Protected Account", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String currentUsername = (String) userTable.getValueAt(row, COL_USERNAME);

        UserDialog.ExistingUser existing =
                new UserDialog.ExistingUser(id, currentUsername, currentRole);
        UserDialog dlg = new UserDialog(this, "Edit User", existing);
        dlg.setVisible(true);
        if (!dlg.confirmed) return;

        String newUsername = dlg.getUsername().trim();
        String newRole      = dlg.getSelectedRole();

        if (newUsername.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty.");
            return;
        }

        try {
            if (usernameAlreadyExists(newUsername, id)) {
                JOptionPane.showMessageDialog(this, "An account with that username already exists.");
                return;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to check existing accounts.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if ("Owner".equalsIgnoreCase(newRole) && !"Owner".equalsIgnoreCase(currentRole)) {
            int confirmPromote = JOptionPane.showConfirmDialog(this,
                    "<html>Promote <b>" + newUsername + "</b> to Owner?<br><br>"
                    + "This grants full access, including User Management itself.</html>",
                    "Confirm Promotion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmPromote != JOptionPane.YES_OPTION) return;
        }

        String sql = "UPDATE users SET username = ?, role = ? WHERE user_id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newUsername);
            pst.setString(2, newRole);
            pst.setInt(3, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "Account updated successfully.");
            loadUsers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update account.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Activate / Deactivate -------------------------------------------

    private void toggleActivation() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an account.");
            return;
        }

        int    id       = (int) userTable.getValueAt(row, COL_ID);
        String username = (String) userTable.getValueAt(row, COL_USERNAME);
        String role     = (String) userTable.getValueAt(row, COL_ROLE);
        String status   = (String) userTable.getValueAt(row, COL_STATUS);

        if ("Owner".equalsIgnoreCase(role)) {
            JOptionPane.showMessageDialog(this,
                    "The Owner account cannot be deactivated.",
                    "Protected Account", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean currentlyActive = "Active".equalsIgnoreCase(status);
        String newStatus = currentlyActive ? "Inactive" : "Active";
        String message = currentlyActive
                ? "Deactivate \"" + username + "\"? They won't be able to log in until reactivated."
                : "Activate \"" + username + "\"? They will be able to log in again.";

        int choice = JOptionPane.showConfirmDialog(this, message,
                currentlyActive ? "Deactivate Account" : "Activate Account",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        String sql = "UPDATE users SET account_status = ? WHERE user_id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newStatus);
            pst.setInt(2, id);
            pst.executeUpdate();

            JOptionPane.showMessageDialog(this, "\"" + username + "\" is now " + newStatus + ".");
            loadUsers(searchField.getText());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update account status.\n" + e.getMessage(),
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

        setMinimumSize(new Dimension(950, 580));
        setPreferredSize(new Dimension(1150, 700));
        pack();
        setLocationRelativeTo(null);
    }

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
        lblDateTime.setText(new SimpleDateFormat(DATE_FORMAT).format(new Date()));
    }

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
        reportsbtn        = new NavButton("Reports", false);
        userManagementbtn = new NavButton("User management", true);   // active
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

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(BG_PAGE);
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel titleLabel = new JLabel("User Management");
        titleLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Create and manage staff accounts");
        subtitle.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- toolbar: Option A - search+Add on the left, actions pushed right ---
        searchField = new HintTextField("Search users...");
        searchField.setPreferredSize(new Dimension(260, INPUT_HEIGHT));
        searchField.setMaximumSize(new Dimension(260, INPUT_HEIGHT));

        FilledButton searchbtn = new FilledButton("Search");
        searchbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        searchbtn.addActionListener(e -> loadUsers(searchField.getText()));

        addUserbtn = new FilledButton("+ Add User");
        addUserbtn.setPreferredSize(new Dimension(120, INPUT_HEIGHT));
        addUserbtn.addActionListener(e -> showAddDialog());

        editUserbtn = new OutlineButton("Edit");
        editUserbtn.setPreferredSize(new Dimension(80, INPUT_HEIGHT));
        editUserbtn.addActionListener(e -> showEditDialog());

        activateDeactivatebtn = new OutlineButton("Activate / Deactivate");
        activateDeactivatebtn.setPreferredSize(new Dimension(160, INPUT_HEIGHT));
        activateDeactivatebtn.addActionListener(e -> toggleActivation());

        refreshbtn = new OutlineButton("Refresh");
        refreshbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        refreshbtn.addActionListener(e -> {
            searchField.setText("");
            loadUsers("");
        });

        backbtn = new OutlineButton("← Back");
        backbtn.setPreferredSize(new Dimension(90, INPUT_HEIGHT));
        backbtn.addActionListener(this::backbtnActionPerformed);

        JPanel toolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbarLeft.setOpaque(false);
        toolbarLeft.add(searchField);
        toolbarLeft.add(searchbtn);
        toolbarLeft.add(addUserbtn);

        JPanel toolbarRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        toolbarRight.setOpaque(false);
        toolbarRight.add(editUserbtn);
        toolbarRight.add(activateDeactivatebtn);
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

        JLabel tableHeadingLabel = new JLabel("Accounts");
        tableHeadingLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        tableHeadingLabel.setForeground(TEXT_PRIMARY);

        lblCount = new JLabel("0 accounts");
        lblCount.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        lblCount.setForeground(TEXT_MUTED);

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(tableHeadingLabel, BorderLayout.WEST);
        tableHeading.add(lblCount, BorderLayout.EAST);

        JPanel tableSection = new JPanel(new BorderLayout(0, 10));
        tableSection.setOpaque(false);
        tableSection.add(tableHeading, BorderLayout.NORTH);
        tableSection.add(buildTable(), BorderLayout.CENTER);

        content.add(top, BorderLayout.NORTH);
        content.add(tableSection, BorderLayout.CENTER);
        return content;
    }

    private JScrollPane buildTable() {
        userTable = new JTable(new DefaultTableModel(TABLE_COLUMNS, 0));
        userTable.setRowHeight(38);
        userTable.setShowGrid(false);
        userTable.setIntercellSpacing(new Dimension(0, 0));
        userTable.setBackground(Color.WHITE);
        userTable.setFillsViewportHeight(true);
        userTable.setSelectionBackground(ACCENT_LIGHT);
        userTable.setSelectionForeground(TEXT_PRIMARY);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.setFocusable(false);
        userTable.setDefaultRenderer(Object.class, new UserCellRenderer());

        JTableHeader th = userTable.getTableHeader();
        th.setReorderingAllowed(false);
        th.setPreferredSize(new Dimension(0, 38));
        th.setDefaultRenderer(new HeaderRenderer());

        userTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) showEditDialog();
            }
        });

        applyTableColumnWidths();

        jScrollPane1 = new JScrollPane(userTable);
        jScrollPane1.setBorder(new LineBorder(BORDER_COLOR));
        jScrollPane1.getViewport().setBackground(Color.WHITE);
        return jScrollPane1;
    }

    private void applyTableColumnWidths() {
        for (int i = 0; i < TABLE_WIDTHS.length
                && i < userTable.getColumnModel().getColumnCount(); i++) {
            userTable.getColumnModel().getColumn(i).setPreferredWidth(TABLE_WIDTHS[i]);
        }
    }

    private static boolean isCenterColumn(int col) {
        return col == COL_ID || col == COL_ROLE || col == COL_STATUS || col == COL_CREATED;
    }

    private static javax.swing.border.Border cellBorder(int col) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, GRID_COLOR),
                new EmptyBorder(0, isCenterColumn(col) ? 6 : 14, 0, 14));
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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

    static class UserDialog extends JDialog {

        static class ExistingUser {
            final int id;
            final String username, role;
            ExistingUser(int id, String username, String role) {
                this.id = id; this.username = username; this.role = role;
            }
        }

        boolean confirmed = false;
        private final boolean isEdit;
        private final JTextField usernameField;
        private final JPasswordField passwordField;
        private final JPasswordField confirmPasswordField;
        private final JComboBox<String> roleCombo;

        UserDialog(JFrame owner, String title, ExistingUser existing) {
            super(owner, title, true);
            this.isEdit = existing != null;

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            int row = 0;

            usernameField = plainField(existing != null ? existing.username : "");
            addField(panel, c, row++, "Username *", usernameField);

            if (!isEdit) {
                passwordField = new JPasswordField();
                stylePasswordField(passwordField);
                addField(panel, c, row++,
                        "Password * (min " + MIN_PASSWORD_LENGTH + " chars, 1 uppercase, 1 number)",
                        passwordField);

                confirmPasswordField = new JPasswordField();
                stylePasswordField(confirmPasswordField);
                addField(panel, c, row++, "Confirm Password *", confirmPasswordField);

                JLabel roleLabel = new JLabel("Role");
                roleLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
                roleLabel.setForeground(TEXT_MUTED);
                JLabel roleFixed = new JLabel("Staff");
                roleFixed.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
                roleFixed.setForeground(TEXT_PRIMARY);

                JPanel roleBlock = new JPanel();
                roleBlock.setLayout(new BoxLayout(roleBlock, BoxLayout.Y_AXIS));
                roleBlock.setBackground(Color.WHITE);
                roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                roleFixed.setAlignmentX(Component.LEFT_ALIGNMENT);
                roleBlock.add(roleLabel);
                roleBlock.add(Box.createVerticalStrut(4));
                roleBlock.add(roleFixed);

                c.gridy = row++;
                c.insets = new Insets(0, 0, 14, 0);
                panel.add(roleBlock, c);

                roleCombo = null;   // not used when adding; role is always "Staff"
            } else {
                passwordField = null;
                confirmPasswordField = null;

                roleCombo = new JComboBox<>(new String[]{"Staff", "Owner"});
                roleCombo.setSelectedItem(existing.role);
                roleCombo.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
                roleCombo.setPreferredSize(new Dimension(320, 34));
                addField(panel, c, row++, "Role *", roleCombo);
            }

            FilledButton saveBtn = new FilledButton(isEdit ? "Save Changes" : "Add User");
            saveBtn.setPreferredSize(new Dimension(150, 38));
            saveBtn.addActionListener(e -> { confirmed = true; dispose(); });

            OutlineButton cancelBtn = new OutlineButton("Cancel");
            cancelBtn.setPreferredSize(new Dimension(100, 38));
            cancelBtn.addActionListener(e -> dispose());

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            btnRow.setBackground(Color.WHITE);
            btnRow.add(cancelBtn);
            btnRow.add(saveBtn);

            c.gridy = row;
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

        private static void stylePasswordField(JPasswordField f) {
            f.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
            f.setPreferredSize(new Dimension(320, 34));
            f.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
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

        String getUsername()        { return usernameField.getText(); }
        String getPassword()        { return passwordField == null ? "" : new String(passwordField.getPassword()); }
        String getConfirmPassword() { return confirmPasswordField == null ? "" : new String(confirmPasswordField.getPassword()); }
        String getSelectedRole()    { return roleCombo == null ? "Staff" : (String) roleCombo.getSelectedItem(); }
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

    /** Plain cells, except Account Status which is drawn as a colored pill. */
    private static class UserCellRenderer extends DefaultTableCellRenderer {
        private final StatusCell statusCell = new StatusCell();

        UserCellRenderer() { setVerticalAlignment(SwingConstants.CENTER); }

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

    /** Green "Active" / red "Inactive" pill. */
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

            boolean active = "Active".equalsIgnoreCase(text);
            Color bg = active ? IN_BG : OUT_BG;
            Color fg = active ? IN_TEXT : OUT_TEXT;

            g2.setFont(new Font(FONT_FAMILY, Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int pillW = fm.stringWidth(text) + 24;
            int pillH = 24;
            int x = (getWidth() - pillW) / 2;
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

    private void backbtnActionPerformed(java.awt.event.ActionEvent evt) {
        dashboardbtnActionPerformed(evt);
    }

    private void userManagementbtnActionPerformed(java.awt.event.ActionEvent evt) {
        searchField.setText("");
        loadUsers("");
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
            ReportFrame report =
            new ReportFrame(userId, loggedInUsername, userRole);

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
        java.awt.EventQueue.invokeLater(() -> new UserManagementFrame().setVisible(true));
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
    private JButton addUserbtn;
    private JButton editUserbtn;
    private JButton activateDeactivatebtn;
    private JButton refreshbtn;
    private JButton backbtn;
    private JLabel lblLogo1;
    private JLabel lblBranch;
    private JLabel lblDateTime;
    private JLabel lblCount;
    private HintTextField searchField;
    private JScrollPane jScrollPane1;
    private JTable userTable;
}