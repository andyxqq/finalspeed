// Copyright (c) 2015 D1SM.net

package net.fs.client;

import net.fs.rudp.Route;
import net.fs.utils.JsonUtils;
import net.fs.utils.LogOutputStream;
import net.fs.utils.MLog;
import net.fs.utils.Tools;
import net.miginfocom.swing.MigLayout;
import org.pcap4j.core.Pcaps;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class ClientUI implements ClientUII, WindowListener {
    private static final int CLIENT_VERSION = 5;
    private static final String CONFIG_FILE_PATH = "client_config.json";
    private static final String LOGO_IMG = "img/offline.png";

    JFrame mainFrame;

    private JComboBox text_serverAddress;

    MapClient mapClient;

    private JLabel downloadSpeedField;
    private JLabel stateText;
    private ClientConfig config;

    private int serverVersion = -1;

    private String homeUrl;

    public static ClientUI ui;

    private JTextField text_ds;
    private JTextField text_us;

    private MapRuleListModel model;

    public MapRuleListTable tcpMapRuleListTable;

    private boolean b1 = false;

    private boolean success_firewall_windows = true;

    private boolean success_firewall_osx = true;

    private String systemName = null;

    public boolean osx_fw_pf = false;

    public boolean osx_fw_ipfw = false;

    public boolean isVisible = true;

    private JRadioButton r_tcp;
    private JRadioButton r_udp;

    private String updateUrl;

    private LogFrame logFrame;

    private LogOutputStream los;

    {
        homeUrl = "http://www.ip4a.com/?client_fs";
        updateUrl = "http://fs.d1sm.net/finalspeed/update.properties";
    }

    ClientUI(final boolean isVisible,boolean min) {
        setVisible(isVisible);

        if(isVisible){
             los=new LogOutputStream(System.out);
             System.setOut(los);
             System.setErr(los);
        }


        systemName = System.getProperty("os.name").toLowerCase();
        MLog.info("System: " + systemName + " " + System.getProperty("os.version"));
        ui = this;
        mainFrame = new JFrame();
        mainFrame.setIconImage(Toolkit.getDefaultToolkit().getImage(LOGO_IMG));
        initUI();
        loadConfig();
        mainFrame.setTitle("FinalSpeed 1.2");
        JComponent mainPanel = (JPanel) mainFrame.getContentPane();
        mainPanel.setLayout(new MigLayout("align center , insets 10 10 10 10"));
        mainPanel.setBorder(null);

        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                text_ds.requestFocus();
            }
        });

        JPanel centerPanel = new JPanel();
        mainPanel.add(centerPanel, "wrap");
        centerPanel.setLayout(new MigLayout("insets 0 0 0 0"));

        JPanel loginPanel = new JPanel();
        centerPanel.add(loginPanel, "");
        loginPanel.setLayout(new MigLayout("insets 0 0 0 0"));

        JLabel label_msg = new JLabel();
        label_msg.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new MigLayout("insets 10 0 10 0"));

        centerPanel.add(rightPanel, "width :: ,top");

        JPanel mapPanel = new JPanel();
        mapPanel.setLayout(new MigLayout("insets 0 0 0 0"));
        mapPanel.setBorder(BorderFactory.createTitledBorder("加速列表"));

        rightPanel.add(mapPanel);

        model = new MapRuleListModel();
        tcpMapRuleListTable = new MapRuleListTable(this, model);

        JScrollPane tablePanel = new JScrollPane();
        tablePanel.setViewportView(tcpMapRuleListTable);

        mapPanel.add(tablePanel, "height 50:160:1024 ,growy,width :250:,wrap");
        tablePanel.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {
                tcpMapRuleListTable.clearSelection();
            }

            public void mouseEntered(MouseEvent e) {}

            public void mouseExited(MouseEvent e) {}

            public void mousePressed(MouseEvent e) {}

            public void mouseReleased(MouseEvent e) {}
        });

        JPanel p9 = new JPanel();
        p9.setLayout(new MigLayout("insets 1 0 3 0 "));
        mapPanel.add(p9, "align center,wrap");
        JButton button_add = createButton("添加");
        p9.add(button_add);
        button_add.addActionListener(e -> new AddMapFrame(ui, mainFrame, null, false));
        JButton button_edit = createButton("修改");
        p9.add(button_edit);
        button_edit.addActionListener(e -> {
            int index = tcpMapRuleListTable.getSelectedRow();
            if (index > -1) {
                MapRule mapRule = model.getMapRuleAt(index);
                new AddMapFrame(ui, mainFrame, mapRule, true);
            }
        });
        JButton button_remove = createButton("删除");
        p9.add(button_remove);
        button_remove.addActionListener(e -> {
            int index = tcpMapRuleListTable.getSelectedRow();
            if (index > -1) {
                MapRule mapRule = model.getMapRuleAt(index);
                mapClient.portMapManager.removeMapRule(mapRule.getName());
                loadMapRule();
            }
        });

        JPanel pa = new JPanel();
        pa.setBorder(BorderFactory.createTitledBorder("服务器"));
        pa.setLayout(new MigLayout("insets 0 0 0 0"));
        loginPanel.add(pa, "growx,wrap");
        JPanel p1 = new JPanel();
        p1.setLayout(new MigLayout("insets 0 0 0 0"));
        pa.add(p1, "wrap");
        p1.add(new JLabel("地址:"), "width 50::");
        text_serverAddress = new JComboBox();
        text_serverAddress.setToolTipText("主机:端口号");
        p1.add(text_serverAddress, "width 150::");
        text_serverAddress.setEditable(true);
        TextComponentPopupMenu.installToComponent(text_serverAddress);

        ListCellRenderer renderer = new AddressCellRenderer();
        text_serverAddress.setRenderer(renderer);
        text_serverAddress.setEditable(true);

        JButton button_removeAddress=createButton("删除");
        p1.add(button_removeAddress, "");
        button_removeAddress.addActionListener(e -> {
            String address=text_serverAddress.getSelectedItem().toString();
            if(!address.equals("")){
                int result= JOptionPane.showConfirmDialog(mainFrame, "确定删除吗?","消息", JOptionPane.YES_NO_OPTION);
                if(result==JOptionPane.OK_OPTION){
                    text_serverAddress.removeItem(address);
                    String selectText="";
                    if(text_serverAddress.getModel().getSize()>0){
                        selectText=text_serverAddress.getModel().getElementAt(0).toString();
                    }
                    text_serverAddress.setSelectedItem(selectText);
                }
            }
        });

        JPanel panelr = new JPanel();
        pa.add(panelr, "wrap");
        panelr.setLayout(new MigLayout("insets 0 0 0 0"));
        panelr.add(new JLabel("传输协议:"));
        r_tcp = new JRadioButton("TCP");
        r_tcp.setFocusPainted(false);
        panelr.add(r_tcp);
        r_udp = new JRadioButton("UDP");
        r_udp.setFocusPainted(false);
        panelr.add(r_udp);
        ButtonGroup bg = new ButtonGroup();
        bg.add(r_tcp);
        bg.add(r_udp);
        if (config.getProtocol().equals("udp")) {
            r_udp.setSelected(true);
        } else {
            r_tcp.setSelected(true);
        }

        JPanel sp = new JPanel();
        sp.setBorder(BorderFactory.createTitledBorder("物理带宽"));
        sp.setLayout(new MigLayout("insets 5 5 5 5"));
        JPanel pa1 = new JPanel();
        sp.add(pa1, "wrap");
        pa1.setLayout(new MigLayout("insets 0 0 0 0"));
        loginPanel.add(sp, "wrap");
        pa1.add(new JLabel("下载:"), "width ::");
        text_ds = new JTextField("0");
        pa1.add(text_ds, "width 80::");
        text_ds.setHorizontalAlignment(JTextField.RIGHT);
        text_ds.setEditable(false);

        JButton button_set_speed = createButton("设置带宽");
        pa1.add(button_set_speed);
        button_set_speed.addActionListener(e -> new SpeedSetFrame(ui, mainFrame));

        JPanel pa2 = new JPanel();
        sp.add(pa2, "wrap");
        pa2.setLayout(new MigLayout("insets 0 0 0 0"));
        loginPanel.add(sp, "wrap");
        pa2.add(new JLabel("上传:"), "width ::");
        text_us = new JTextField("0");
        pa2.add(text_us, "width 80::");
        text_us.setHorizontalAlignment(JTextField.RIGHT);
        text_us.setEditable(false);

        JPanel sp2 = new JPanel();
        sp2.setLayout(new MigLayout("insets 0 0 0 0"));
        loginPanel.add(sp2, "align center,  wrap");

        JButton button_show_log=createButton("显示日志");
        sp2.add(button_show_log,"wrap");
        button_show_log.addActionListener(e -> {
            if(logFrame==null){
                 logFrame=new LogFrame(ui);
                 logFrame.setSize(700, 400);
                 logFrame.setLocationRelativeTo(null);
                 los.addListener(logFrame);

                 if(los.getBuffer()!=null){
                    logFrame.showText(los.getBuffer().toString());
                     los.setBuffer(null);
                 }
            }
            logFrame.setVisible(true);
        });

        JPanel p4 = new JPanel();
        p4.setLayout(new MigLayout("insets 5 0 0 0 "));
        loginPanel.add(p4, "align center,wrap");
        JButton button_save = createButton("确定");
        p4.add(button_save);

        JButton button_site = createButton("网站");
        p4.add(button_site);
        button_site.addActionListener(e -> openUrl(homeUrl));

        JButton button_exit = createButton("退出");
        p4.add(button_exit);
        button_exit.addActionListener(e -> System.exit(0));
        button_save.addActionListener(e -> {
            if (config.getDownloadSpeed() == 0 || config.getUploadSpeed() == 0) {
                SpeedSetFrame sf = new SpeedSetFrame(ui, mainFrame);
            }
            setMessage("");
            saveConfig();
        });

        stateText = new JLabel("");
        mainPanel.add(stateText, "align right ,wrap");

        JPanel p5 = new JPanel();
        p5.setLayout(new MigLayout("insets 5 0 0 0 "));
        mainPanel.add(p5, "align right");
        JButton button_fsa = createButton_Link("FS高级版","http://www.xsocks.me/?fsc");
        p5.add(button_fsa);
        JButton button_wlt = createButton_Link("网络通内网穿透","http://www.youtusoft.com/?fsc");
        p5.add(button_wlt);

        downloadSpeedField = new JLabel();
        downloadSpeedField.setHorizontalAlignment(JLabel.RIGHT);
        p5.add(downloadSpeedField, "width 130:: ,align right ");

        updateUISpeed(0, 0, 0);
        setMessage(" ");

        text_serverAddress.setSelectedItem(getServerAddressFromConfig());

        mainFrame.pack();

        mainFrame.setLocationRelativeTo(null);

        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        boolean tcpEnvSuccess=true;
        checkFireWallOn();
        if (!success_firewall_windows) {
            tcpEnvSuccess=false;
            if (isVisible) {
                mainFrame.setVisible(true);
                JOptionPane.showMessageDialog(mainFrame, "启动windows防火墙失败,请先运行防火墙服务.");
            }
            MLog.println("启动windows防火墙失败,请先运行防火墙服务.");
        }
        if (!success_firewall_osx) {
            tcpEnvSuccess=false;
            if (isVisible) {
                mainFrame.setVisible(true);
                JOptionPane.showMessageDialog(mainFrame, "启动ipfw/pfctl防火墙失败,请先安装.");
            }
            MLog.println("启动ipfw/pfctl防火墙失败,请先安装.");
        }

        Thread thread = new Thread() {
            public void run() {
                try {
                    Pcaps.findAllDevs();
                    b1 = true;
                } catch (Exception e3) {
                    e3.printStackTrace();

                }
            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        if (!b1) {
            tcpEnvSuccess=false;
            try {
                SwingUtilities.invokeAndWait(() -> {
                    String msg = "启动失败,请先安装libpcap,否则无法使用tcp协议";
                    if (systemName.contains("windows")) {
                        msg = "启动失败,请先安装winpcap,否则无法使用tcp协议";
                    }
                    if (isVisible) {
                        mainFrame.setVisible(true);
                        JOptionPane.showMessageDialog(mainFrame, msg);
                    }
                    MLog.println(msg);
                });
            } catch (InvocationTargetException | InterruptedException e2) {
                e2.printStackTrace();
            }
        }

        try {
            mapClient = new MapClient(this,tcpEnvSuccess);
        } catch (final Exception e1) {
            e1.printStackTrace();
        }

        try {
            SwingUtilities.invokeAndWait(() -> {
                if (!mapClient.route_tcp.capEnv.tcpEnable) {
                    if (isVisible) {
                        mainFrame.setVisible(true);
                    }
                    r_tcp.setEnabled(false);
                    r_udp.setSelected(true);
                }
            });
        } catch (InvocationTargetException | InterruptedException e2) {
            e2.printStackTrace();
        }

        mapClient.setUi(this);

        mapClient.setMapServer(
                config.getServerAddress(),
                config.getServerPort(),
                0,
                null,
                null,
                config.isDirectCn(),
                config.getProtocol().equals("tcp"),
                null);

        Route.es.execute(this::checkUpdate);

        setSpeed(config.getDownloadSpeed(), config.getUploadSpeed());
        if (isVisible&!min) {
            mainFrame.setVisible(true);
        }

        loadMapRule();

        if (config.getDownloadSpeed() == 0 || config.getUploadSpeed() == 0) {
            new SpeedSetFrame(ui, mainFrame);
        }
    }

    private String getServerAddressFromConfig(){
         String server_addressTxt = config.getServerAddress();
         if (config.getServerAddress() != null && !config.getServerAddress().equals("")) {
             if (config.getServerPort() != 150
                     && config.getServerPort() != 0) {
                 server_addressTxt += (":" + config.getServerPort());
             }
         }
         return server_addressTxt;
    }

    private void checkFireWallOn() {
        if (systemName.contains("os x")) {
            String runFirewall = "ipfw";
            try {
                Runtime.getRuntime().exec(runFirewall, null);
                osx_fw_ipfw = true;
            } catch (IOException e) {
                //e.printStackTrace();
            }
            runFirewall = "pfctl";
            try {
                Runtime.getRuntime().exec(runFirewall, null);
                osx_fw_pf = true;
            } catch (IOException e) {
               // e.printStackTrace();
            }
            success_firewall_osx = osx_fw_ipfw | osx_fw_pf;
        } else if (systemName.contains("linux")) {
            String runFirewall = "service iptables start";
        } else if (systemName.contains("windows")) {
            String runFirewall = "netsh advfirewall set allprofiles state on";
            Thread standReadThread = null;
            Thread errorReadThread = null;
            try {
                final Process p = Runtime.getRuntime().exec(runFirewall, null);
                standReadThread = new Thread() {
                    public void run() {
                        InputStream is = p.getInputStream();
                        BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
                        while (true) {
                            String line;
                            try {
                                line = localBufferedReader.readLine();
                                if (line == null) {
                                    break;
                                } else {
                                    if (line.contains("Windows")) {
                                        success_firewall_windows = false;
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                exit();
                                break;
                            }
                        }
                    }
                };
                standReadThread.start();

                errorReadThread = new Thread() {
                    public void run() {
                        InputStream is = p.getErrorStream();
                        BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
                        while (true) {
                            String line;
                            try {
                                line = localBufferedReader.readLine();
                                if (line == null) {
                                    break;
                                } else {
                                    System.out.println("error" + line);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                exit();
                                break;
                            }
                        }
                    }
                };
                errorReadThread.start();
            } catch (IOException e) {
                e.printStackTrace();
                success_firewall_windows = false;
            }
            if (standReadThread != null) {
                try {
                    standReadThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (errorReadThread != null) {
                try {
                    errorReadThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void loadMapRule() {
        tcpMapRuleListTable.setMapRuleList(mapClient.portMapManager.getMapList());
    }

    void select(String name) {
        int index = model.getMapRuleIndex(name);
        if (index > -1) {
            tcpMapRuleListTable.getSelectionModel().setSelectionInterval(index, index);
        }
    }

    void setSpeed(int downloadSpeed, int uploadSpeed) {
        config.setDownloadSpeed(downloadSpeed);
        config.setUploadSpeed(uploadSpeed);
        int s1 = (int) ((float) downloadSpeed * 1.1f);
        text_ds.setText(" " + Tools.getSizeStringKB(s1) + "/s ");
        int s2 = (int) ((float) uploadSpeed * 1.1f);
        text_us.setText(" " + Tools.getSizeStringKB(s2) + "/s ");
        Route.localDownloadSpeed = downloadSpeed;
        Route.localUploadSpeed = config.getUploadSpeed();
        saveConfig();
    }

    private void exit() {
        mainFrame.setVisible(false);
        System.exit(0);
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e1) {
            e1.printStackTrace();
        }
    }

    public void setMessage(String message) {
        stateText.setText("状态: " + message);
    }

    private void loadConfig() {
        ClientConfig cfg = new ClientConfig();
        if (!new File(CONFIG_FILE_PATH).exists()) {
            try {
                saveFile(JsonUtils.clientConfigToJson(cfg).getBytes("UTF-8"), CONFIG_FILE_PATH);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            String content = readFileUtf8(CONFIG_FILE_PATH);
            config = JsonUtils.jsonToClientConfig(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        Thread thread = new Thread() {
            public void run() {
                boolean success = false;
                try {
                    int serverPort = 150;
                    String addressTxt = "";
                    if(text_serverAddress.getSelectedItem()!=null){
                        addressTxt =text_serverAddress.getSelectedItem().toString();
                    }
                    addressTxt = addressTxt.trim().replaceAll(" ", "");

                    String serverAddress = addressTxt;
                    if (addressTxt.startsWith("[")) {
                        int index = addressTxt.lastIndexOf("]:");
                        if (index > 0) {
                            serverAddress = addressTxt.substring(0, index + 1);
                            String ports = addressTxt.substring(index + 2);
                            serverPort = Integer.parseInt(ports);
                        }
                    } else {
                        int index = addressTxt.lastIndexOf(":");
                        if (index > 0) {
                            serverAddress = addressTxt.substring(0, index);
                            String ports = addressTxt.substring(index + 1);
                            serverPort = Integer.parseInt(ports);
                        }
                    }

                    String protocal = "tcp";
                    if (r_udp.isSelected()) {
                        protocal = "udp";
                    }

                    if(text_serverAddress.getModel().getSize()>0){
                        text_serverAddress.removeItem(addressTxt);
                    }
                    text_serverAddress.insertItemAt(addressTxt, 0);
                    text_serverAddress.setSelectedItem(addressTxt);;

                    config.setServerAddress(serverAddress);
                    config.setServerPort(serverPort);
                    config.setProtocol(protocal);

                    saveFile(JsonUtils.clientConfigToJson(config).getBytes("utf-8"), CONFIG_FILE_PATH);

                    success = true;

                    String realAddress = serverAddress;
                    realAddress = realAddress.replace("[", "");
                    realAddress = realAddress.replace("]", "");

                    boolean tcp = protocal.equals("tcp");

                    mapClient.setMapServer(realAddress, serverPort, 0, null, null, config.isDirectCn(), tcp,
                            null);
                    mapClient.closeAndTryConnect();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (!success) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(
                                        mainFrame,
                                        "保存失败请检查输入信息!",
                                        "错误",
                                        JOptionPane.ERROR_MESSAGE));
                    }
                }
            }
        };
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String readFileUtf8(String path) throws Exception {
        String str = null;
        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            File file = new File(path);

            int length = (int) file.length();
            byte[] data = new byte[length];

            fis = new FileInputStream(file);
            dis = new DataInputStream(fis);
            dis.readFully(data);
            str = new String(data, "utf-8");

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return str;
    }

    private void saveFile(byte[] data, String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data);
        } catch (IOException e) {
            if (systemName.contains("windows")) {
                JOptionPane.showMessageDialog(null, "保存配置文件失败,请尝试以管理员身份运行! " + path);
                System.exit(0);
            }
            throw e;
        }
    }

    public void updateUISpeed(int conn, int downloadSpeed, int uploadSpeed) {
        String string =
                " 下载:" + Tools.getSizeStringKB(downloadSpeed) + "/s"
                        + " 上传:" + Tools.getSizeStringKB(uploadSpeed) + "/s";
        if (downloadSpeedField != null) {
            downloadSpeedField.setText(string);
        }
    }

    private JButton createButton(String name) {
        JButton button = new JButton(name);
        button.setMargin(new Insets(0, 5, 0, 5));
        button.setFocusPainted(false);
        return button;
    }

    private JButton createButton_Link(String name, final String url) {
        JButton button = new JButton(name);
        Color c = new Color(0,0,255);
        button.setBackground(c);
        button.setForeground(new Color(100,100,255));
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setMargin(new Insets(0, 2, 0, 2));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> openUrl(url));
        return button;
    }


    private boolean haveNewVersion() {
        return serverVersion > CLIENT_VERSION;
    }

    public void checkUpdate() {
        for (int i = 0; i < 3; i++) {
            try {
                Properties propServer = new Properties();
                HttpURLConnection uc = Tools.getConnection(updateUrl);
                uc.setUseCaches(false);
                InputStream in = uc.getInputStream();
                propServer.load(in);
                serverVersion = Integer.parseInt(propServer.getProperty("version"));
                break;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(3 * 1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
        if (this.haveNewVersion()) {
            int option = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "发现新版本,立即更新吗?",
                    "提醒",
                    JOptionPane.WARNING_MESSAGE);
            if (option == JOptionPane.YES_OPTION) {
                openUrl(homeUrl);
            }
        }

    }

    private void initUI() {
        SwingUtilities.invokeLater(() -> {
            Font font = new Font("宋体", Font.PLAIN, 12);
            UIManager.put("ToolTip.font", font);
            UIManager.put("Table.font", font);
            UIManager.put("TableHeader.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("ComboBox.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("PasswordField.font", font);
            UIManager.put("TextArea.font,font", font);
            UIManager.put("TextPane.font", font);
            UIManager.put("EditorPane.font", font);
            UIManager.put("FormattedTextField.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("CheckBox.font", font);
            UIManager.put("RadioButton.font", font);
            UIManager.put("ToggleButton.font", font);
            UIManager.put("ProgressBar.font", font);
            UIManager.put("DesktopIcon.font", font);
            UIManager.put("TitledBorder.font", font);
            UIManager.put("Label.font", font);
            UIManager.put("List.font", font);
            UIManager.put("TabbedPane.font", font);
            UIManager.put("MenuBar.font", font);
            UIManager.put("Menu.font", font);
            UIManager.put("MenuItem.font", font);
            UIManager.put("PopupMenu.font", font);
            UIManager.put("CheckBoxMenuItem.font", font);
            UIManager.put("RadioButtonMenuItem.font", font);
            UIManager.put("Spinner.font", font);
            UIManager.put("Tree.font", font);
            UIManager.put("ToolBar.font", font);
            UIManager.put("OptionPane.messageFont", font);
            UIManager.put("OptionPane.buttonFont", font);
            ToolTipManager.sharedInstance().setInitialDelay(130);
        });
    }

    @Override
    public void windowOpened(WindowEvent e) {}

    @Override
    public void windowClosing(WindowEvent e) {
        mainFrame.setVisible(false);
    }

    @Override
    public void windowClosed(WindowEvent e) {}

    @Override
    public void windowIconified(WindowEvent e) {}

    @Override
    public void windowDeiconified(WindowEvent e) {}

    @Override
    public void windowActivated(WindowEvent e) {}

    @Override
    public void windowDeactivated(WindowEvent e) {}

    @Override
    public boolean login() {
        return false;
    }

    @Override
    public boolean updateNode(boolean testSpeed) {
        return true;
    }

    public boolean isOsx_fw_pf() {
        return osx_fw_pf;
    }

    public void setOsx_fw_pf(boolean osx_fw_pf) {
        this.osx_fw_pf = osx_fw_pf;
    }

    public boolean isOsx_fw_ipfw() {
        return osx_fw_ipfw;
    }

    public void setOsx_fw_ipfw(boolean osx_fw_ipfw) {
        this.osx_fw_ipfw = osx_fw_ipfw;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }
}
