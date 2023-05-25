import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerGUI extends JFrame {

    private JTextArea textArea;
    private JTextArea logArea;
    private JTextField portField;
    private JCheckBox lossCheckbox;
    private JCheckBox delayCheckbox;
    private JTextField delayField;
    private JButton changePortButton;
    private JButton changeDelayButton;
    private AtomicInteger atomicPort;
    private AtomicInteger atomicDelay;
    private JLabel serverInfoLabel;
    private JTextField lossRateField;
    private JButton changeLossRateButton;
    private AtomicInteger atomicLossRate;
    private JTable statsTable;
    private DefaultTableModel statsTableModel;
    public ServerGUI(int port) {
        super("UDP Ping 服务端");

        atomicPort = new AtomicInteger(port);

        atomicLossRate = new AtomicInteger(0);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("导出");

        JMenuItem exportMessageItem = new JMenuItem("导出消息内容");
        exportMessageItem.addActionListener(e -> exportLogToFile("message"));
        fileMenu.add(exportMessageItem);

        JMenuItem exportLogItem = new JMenuItem("导出日志内容");
        exportLogItem.addActionListener(e -> exportLogToFile("log"));
        fileMenu.add(exportLogItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        setBounds(100, 100, 800, 350);
        setMinimumSize(new Dimension(800, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        JLabel chatBoxLabel = new JLabel("收到的消息内容");
        chatBoxLabel.setBounds(10, 10, 120, 20);
        getContentPane().add(chatBoxLabel);

        textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBounds(10, 40, 420, 200);
        getContentPane().add(scrollPane);

        JLabel logLabel = new JLabel("日志信息");
        logLabel.setBounds(10, 250, 120, 20);
        getContentPane().add(logLabel);

        logArea = new JTextArea();
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBounds(10, 270, 420, 200);
        getContentPane().add(logScrollPane);

        JLabel portLabel = new JLabel("端口号修改");
        portLabel.setBounds(450, 50, 120, 20);
        getContentPane().add(portLabel);

        portField = new JTextField(String.valueOf(port));
        portField.setBounds(550, 50, 60, 20);
        getContentPane().add(portField);

        changePortButton = new JButton("修改端口");
        changePortButton.setBounds(620, 50, 120, 20);
        changePortButton.addActionListener(e -> {
            try {
                int newPort = Integer.parseInt(portField.getText());
                if (!isPortAvailable(newPort)) {
                    ErrorDialog.showError("端口 " + newPort + " 已被占用");
                    return;
                }
                atomicPort.set(newPort);
                serverInfoLabel.setText("服务器地址: " + getLocalAddress() + "    当前监听端口: " + getPort());  // 更新 JLabel 的内容
                appendLog("端口已更改为" + newPort);
            } catch (NumberFormatException ex) {
                ErrorDialog.showError("端口号不合法");
            }
        });
        getContentPane().add(changePortButton);

        lossCheckbox = new JCheckBox("模拟丢失");
        lossCheckbox.setBounds(450, 100, 100, 20);
        lossCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                appendLog("已启动模拟丢失功能");
            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                appendLog("已关闭模拟丢失功能");
            }
        });
        getContentPane().add(lossCheckbox);

        lossRateField = new JTextField("0");
        lossRateField.setBounds(550, 100, 60, 20);
        getContentPane().add(lossRateField);

        changeLossRateButton = new JButton("修改丢包率");
        changeLossRateButton.setBounds(620, 100, 120, 20);
        changeLossRateButton.addActionListener(e -> {
            try {
                int newLossRate = Integer.parseInt(lossRateField.getText());
                if (newLossRate < 0 || newLossRate > 100) {
                    ErrorDialog.showError("丢包率必须在0到100之间");
                } else {
                    atomicLossRate.set(newLossRate);
                    appendLog("丢包率已更改为" + newLossRate + "%");
                }
            } catch (NumberFormatException ex) {
                appendLog("丢包率不合法");
            }
        });
        getContentPane().add(changeLossRateButton);

        delayCheckbox = new JCheckBox("模拟延迟");
        delayCheckbox.setBounds(450, 150, 100, 20);
        getContentPane().add(delayCheckbox);

        delayField = new JTextField("1000");
        delayField.setBounds(550, 150, 60, 20);
        atomicDelay = new AtomicInteger(Integer.parseInt(delayField.getText()));
        delayCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                appendLog("已启动模拟延迟功能，延迟时间为" + getDelayTime() + " ms");
            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                appendLog("已关闭模拟延迟功能");
            }
        });
        getContentPane().add(delayField);

        changeDelayButton = new JButton("修改延迟时间");
        changeDelayButton.setBounds(620, 150, 120, 20);
        changeDelayButton.addActionListener(e -> {
            try {
                int newDelayTime = Integer.parseInt(delayField.getText());
                if (newDelayTime < 0) {
                    ErrorDialog.showError("延迟时间必须大于或等于0");
                } else {
                    atomicDelay.set(newDelayTime);
                    appendLog("延迟时间已更改为" + newDelayTime + " ms");
                }
            } catch (NumberFormatException ex) {
                appendLog("延迟时间不合法");
                ErrorDialog.showError("延迟时间输入不合法，必须是大于或等于0的整数");
            }
        });
        getContentPane().add(changeDelayButton);

        JLabel statisticLabel = new JLabel("统计信息");
        statisticLabel.setBounds(450, 250, 120, 20);
        getContentPane().add(statisticLabel);

        statsTableModel = new DefaultTableModel(new Object[][]{}, new String[]{"IP地址", "已延迟数", "已丢弃数"});
        statsTable = new JTable(statsTableModel);
        JScrollPane statsScrollPane = new JScrollPane(statsTable);
        statsScrollPane.setBounds(450, 270, 300, 200);
        getContentPane().add(statsScrollPane);

        serverInfoLabel = new JLabel("服务器地址: " + getLocalAddress() + "    当前监听端口: " + getPort());
        serverInfoLabel.setBounds(450, 10, 300, 20);
        getContentPane().add(serverInfoLabel);

        setVisible(true);
    }

    private void exportLogToFile(String type) {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("LOG Files", "log");
        fileChooser.setFileFilter(filter);
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getAbsolutePath().endsWith(".log")) {
                file = new File(file.getAbsolutePath() + ".log");
            }
            try (PrintWriter writer = new PrintWriter(file)) {
                if (type.equals("message")) {
                    writer.write(textArea.getText());
                } else if (type.equals("log")) {
                    writer.write(logArea.getText());
                }
            } catch (FileNotFoundException e) {
                ErrorDialog.showError("导出日志文件错误: " + e.getMessage());
            }
        }
    }

    private boolean isPortAvailable(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    public int getLossRate() {
        return atomicLossRate.get();
    }

    public void appendMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        textArea.append(timestamp + " " + message + "\n");
    }

    public void appendLog(String log) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logArea.append(timestamp + " " + log + "\n");
    }

    public int getPort() {
        return atomicPort.get();
    }

    public boolean getDelay() {
        return delayCheckbox.isSelected();
    }

    public boolean getLoss() {
        return lossCheckbox.isSelected();
    }

    public int getDelayTime() {
        return atomicDelay.get();
    }

    private String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ErrorDialog.showError("无法获取当前主机地址: " + e.getMessage());
            return "无法获取当前主机地址";
        }
    }

    public void updateStatsTable(String ip, int delayCount, int dropCount) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < statsTableModel.getRowCount(); i++) {
                if (statsTableModel.getValueAt(i, 0).equals(ip)) {
                    statsTableModel.setValueAt(delayCount, i, 1);
                    statsTableModel.setValueAt(dropCount, i, 2);
                    return;
                }
            }
            statsTableModel.addRow(new Object[]{ip, delayCount, dropCount});
        });
    }
}



