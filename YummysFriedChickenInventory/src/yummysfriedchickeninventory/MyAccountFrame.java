package yummysfriedchickeninventory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
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


public class MyAccountFrame extends JFrame {

    private static final String WINDOW_TITLE = "Yummy's Fried Chicken - My Account";
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

    // Password policy
    private static final int MIN_PASSWORD_LENGTH = 8;

    // =====================================================================

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(MyAccountFrame.class.getName());

    private int userId;
    private String loggedInUsername;
    private String userRole;

    private boolean sidebarOpen = true;
    private Timer slideTimer;
    private Timer clockTimer;

    // Account info (loaded on init)
    private String currentUsername;
    private String currentRole;
    private String currentPassword;   // stored for verification
    private Timestamp createdAt;

    // =====================================================================
    //  CONSTRUCTORS
    // =====================================================================

    public MyAccountFrame() {
        buildUI();
        loadAccountInfo();
    }

    public MyAccountFrame(int userId, String username, String role) {
        this.userId = userId;
        this.loggedInUsername = username;
        this.userRole = role;

        buildUI();
        loadAccountInfo();
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

    /** Load the logged-in user's account information from the database. */
    private void loadAccountInfo() {
       String sql = "SELECT user_id, username, password, role, created_at FROM users WHERE user_id = ?";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setInt(1, userId);
            
            try (ResultSet rs = pst.executeQuery()) {
                System.out.println("MyAccountFrame ID: " + System.identityHashCode(this)
        + ", userId: " + userId);
                if (rs.next()) {
                    currentUsername = rs.getString("username");
                    currentPassword = rs.getString("password");   // stored for verification only
                    currentRole = rs.getString("role");
                    createdAt = rs.getTimestamp("created_at");

                    // Display the account info in the UI
                    usernameValue.setText(currentUsername);
                    roleValue.setText(currentRole);
                    SimpleDateFormat dateFmt = new SimpleDateFormat(TABLE_DATE_FORMAT);
                    createdValue.setText(createdAt == null ? "" : dateFmt.format(createdAt));
                } else {
                    System.out.println("NO ACCOUNT FOUND for userId: " + userId);
                    System.out.println("NO ACCOUNT FOUND for userId: " + userId);
                    JOptionPane.showMessageDialog(this,
                            "Account not found. Please log in again.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    goBack();
                }
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load account information.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Verify the entered current password matches the stored password. */
    private boolean verifyCurrentPassword(String entered) {
        if (currentPassword == null) return false;
        // Plaintext comparison (matching the project's existing approach)
        return currentPassword.equals(entered);
    }

    /** Validate password complexity: 8+ chars, 1 uppercase, 1 digit. */
    private static boolean isValidPassword(String pw) {
        if (pw == null || pw.length() < MIN_PASSWORD_LENGTH) return false;
        boolean hasUpper = false, hasDigit = false;
        for (char c : pw.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasDigit;
    }

    /** Change the password in the database. */
    private void changePassword() {
        String currentPw = new String(currentPasswordField.getPassword());
        String newPw     = new String(newPasswordField.getPassword());
        String confirmPw = new String(confirmPasswordField.getPassword());

        if (currentPw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your current password.");
            currentPasswordField.requestFocus();
            return;
        }
        if (newPw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a new password.");
            newPasswordField.requestFocus();
            return;
        }
        if (confirmPw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please confirm your new password.");
            confirmPasswordField.requestFocus();
            return;
        }

        if (!verifyCurrentPassword(currentPw)) {
            JOptionPane.showMessageDialog(this,
                    "Current password is incorrect.",
                    "Authentication Failed", JOptionPane.WARNING_MESSAGE);
            currentPasswordField.selectAll();
            currentPasswordField.requestFocus();
            return;
        }

        if (!isValidPassword(newPw)) {
            JOptionPane.showMessageDialog(this,
                    "New password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and include at least one uppercase letter and one number.",
                    "Password Policy", JOptionPane.WARNING_MESSAGE);
            newPasswordField.selectAll();
            newPasswordField.requestFocus();
            return;
        }

        if (!newPw.equals(confirmPw)) {
            JOptionPane.showMessageDialog(this,
                    "New password and confirmation do not match.",
                    "Mismatch", JOptionPane.WARNING_MESSAGE);
            confirmPasswordField.selectAll();
            confirmPasswordField.requestFocus();
            return;
        }

        String sql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (Connection con = db.getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, newPw);
            pst.setInt(2, userId);
            pst.executeUpdate();

            // Update the in-memory copy so verification works for future changes
            currentPassword = newPw;

            // Clear the form and show success
            currentPasswordField.setText("");
            newPasswordField.setText("");
            confirmPasswordField.setText("");

            JOptionPane.showMessageDialog(this,
                    "Password changed successfully.\nUse your new password for your next login.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to change password.\n" + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void goBack() {
        HomeFrame home = (userRole != null)
                ? new HomeFrame(userId, loggedInUsername, userRole)
                : new HomeFrame();
        home.setVisible(true);
        this.dispose();
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

        setMinimumSize(new Dimension(800, 600));
        setPreferredSize(new Dimension(950, 720));
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
        userManagementbtn = new NavButton("User management", false);
        myAccountbtn      = new NavButton("My account", true);   // active

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

        JLabel titleLabel = new JLabel("My Account");
        titleLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("View your account information and change your password");
        subtitle.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(titleLabel);
        top.add(Box.createVerticalStrut(2));
        top.add(subtitle);
        top.add(Box.createVerticalStrut(16));

        // ---- Account Information card ----
        CardPanel accountInfoCard = new CardPanel(new BorderLayout(0, 12));
        accountInfoCard.setBorder(new EmptyBorder(18, 20, 20, 20));
        accountInfoCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel accountInfoHeading = new JLabel("Account Information");
        accountInfoHeading.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        accountInfoHeading.setForeground(TEXT_PRIMARY);

        JPanel accountInfoHeaderPad = new JPanel(new BorderLayout());
        accountInfoHeaderPad.setOpaque(false);
        accountInfoHeaderPad.add(accountInfoHeading, BorderLayout.CENTER);
        accountInfoHeaderPad.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel accountInfoHeaderBlock = new JPanel(new BorderLayout());
        accountInfoHeaderBlock.setOpaque(false);
        accountInfoHeaderBlock.add(accountInfoHeaderPad, BorderLayout.NORTH);
        accountInfoHeaderBlock.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JPanel accountInfoPanel = new JPanel(new GridBagLayout());
        accountInfoPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 40);

        addInfoField(accountInfoPanel, c, 0, "Username", usernameValue = new JLabel(""));
        addInfoField(accountInfoPanel, c, 1, "Role", roleValue = new JLabel(""));
        c.insets = new Insets(0, 0, 0, 0);
        addInfoField(accountInfoPanel, c, 2, "Date Created", createdValue = new JLabel(""));

        JPanel accountInfoPanelPad = new JPanel(new BorderLayout());
        accountInfoPanelPad.setOpaque(false);
        accountInfoPanelPad.add(accountInfoPanel, BorderLayout.WEST);
        accountInfoPanelPad.setBorder(new EmptyBorder(14, 0, 0, 0));

        accountInfoCard.add(accountInfoHeaderBlock, BorderLayout.NORTH);
        accountInfoCard.add(accountInfoPanelPad, BorderLayout.CENTER);

        JPanel accountInfoSection = new JPanel(new BorderLayout());
        accountInfoSection.setOpaque(false);
        accountInfoSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountInfoSection.add(accountInfoCard, BorderLayout.NORTH);
        accountInfoSection.setBorder(new EmptyBorder(0, 0, 20, 0));

        // ---- Change Password card ----
        CardPanel changePasswordCard = new CardPanel(new BorderLayout(0, 12));
        changePasswordCard.setBorder(new EmptyBorder(18, 20, 20, 20));
        changePasswordCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel changePasswordHeading = new JLabel("Change Password");
        changePasswordHeading.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        changePasswordHeading.setForeground(TEXT_PRIMARY);

        JPanel changePasswordHeaderPad = new JPanel(new BorderLayout());
        changePasswordHeaderPad.setOpaque(false);
        changePasswordHeaderPad.add(changePasswordHeading, BorderLayout.CENTER);
        changePasswordHeaderPad.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel changePasswordHeaderBlock = new JPanel(new BorderLayout());
        changePasswordHeaderBlock.setOpaque(false);
        changePasswordHeaderBlock.add(changePasswordHeaderPad, BorderLayout.NORTH);
        changePasswordHeaderBlock.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JPanel changePasswordPanel = new JPanel(new GridBagLayout());
        changePasswordPanel.setOpaque(false);

        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 16);

        currentPasswordField = new JPasswordField();
        stylePasswordField(currentPasswordField);
        addPasswordField(changePasswordPanel, c, 0, "Current Password *", currentPasswordField);

        newPasswordField = new JPasswordField();
        stylePasswordField(newPasswordField);
        addPasswordField(changePasswordPanel, c, 1, "New Password *", newPasswordField);

        confirmPasswordField = new JPasswordField();
        stylePasswordField(confirmPasswordField);
        c.insets = new Insets(0, 0, 0, 0);
        addPasswordField(changePasswordPanel, c, 2, "Confirm New Password *", confirmPasswordField);

        JPanel changePasswordPanelPad = new JPanel(new BorderLayout());
        changePasswordPanelPad.setOpaque(false);
        changePasswordPanelPad.add(changePasswordPanel, BorderLayout.WEST);
        changePasswordPanelPad.setBorder(new EmptyBorder(14, 0, 16, 0));

        FilledButton changePasswordBtn = new FilledButton("Change Password");
        changePasswordBtn.setPreferredSize(new Dimension(150, 38));
        changePasswordBtn.addActionListener(e -> changePassword());

        OutlineButton backBtn = new OutlineButton("← Back");
        backBtn.setPreferredSize(new Dimension(90, 38));
        backBtn.addActionListener(e -> goBack());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(changePasswordBtn);
        buttonPanel.add(backBtn);

        JPanel changePasswordFieldsBlock = new JPanel(new BorderLayout());
        changePasswordFieldsBlock.setOpaque(false);
        changePasswordFieldsBlock.add(changePasswordPanelPad, BorderLayout.NORTH);
        changePasswordFieldsBlock.add(buttonPanel, BorderLayout.CENTER);

        changePasswordCard.add(changePasswordHeaderBlock, BorderLayout.NORTH);
        changePasswordCard.add(changePasswordFieldsBlock, BorderLayout.CENTER);

        JPanel changePasswordSection = new JPanel(new BorderLayout());
        changePasswordSection.setOpaque(false);
        changePasswordSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        changePasswordSection.add(changePasswordCard, BorderLayout.NORTH);

        // Main scrollable area
        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);
        scrollContent.add(top);
        scrollContent.add(accountInfoSection);
        scrollContent.add(changePasswordSection);
        scrollContent.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_PAGE);

        content.add(scrollPane, BorderLayout.CENTER);
        return content;
    }

    private static void addInfoField(JPanel panel, GridBagConstraints c,
            int col, String label, JLabel valueLabel) {
        JLabel l = new JLabel(label);
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        l.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font(FONT_FAMILY, Font.BOLD, 13));
        valueLabel.setForeground(TEXT_PRIMARY);

        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(l);
        block.add(Box.createVerticalStrut(4));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(valueLabel);

        c.gridx = col;
        panel.add(block, c);
    }

    private static void stylePasswordField(JPasswordField f) {
        f.setFont(new Font(FONT_FAMILY, Font.PLAIN, 13));
        f.setPreferredSize(new Dimension(180, 34));
        f.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR), new EmptyBorder(0, 10, 0, 10)));
    }

    private static void addPasswordField(JPanel panel, GridBagConstraints c,
            int col, String label, JPasswordField field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font(FONT_FAMILY, Font.PLAIN, 11));
        l.setForeground(TEXT_MUTED);

        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(l);
        block.add(Box.createVerticalStrut(4));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(field);

        c.gridx = col;
        panel.add(block, c);
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

    private void myAccountbtnActionPerformed(java.awt.event.ActionEvent evt) {
        // Already on this page
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

    private void reportsbtnActionPerformed(java.awt.event.ActionEvent evt) {    ReportFrame report = new ReportFrame(
        userId,
        loggedInUsername,
        userRole
    );

    report.setVisible(true);
    this.dispose();
    }

    private void userManagementbtnActionPerformed(java.awt.event.ActionEvent evt) {
        UserManagementFrame userMgmt = (userRole != null)
                ? new UserManagementFrame(userId, loggedInUsername, userRole)
                : new UserManagementFrame();
        userMgmt.setVisible(true);
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
        java.awt.EventQueue.invokeLater(() -> new MyAccountFrame(1, "admin", "Owner").setVisible(true));
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
    private JLabel usernameValue;
    private JLabel roleValue;
    private JLabel createdValue;
    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
}