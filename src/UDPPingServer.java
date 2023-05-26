import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public class UDPPingServer {
    private ServerGUI gui;
    private final ExecutorService executor;
    private final Random random = new Random();
    private AtomicBoolean running;
    private AtomicInteger messageNumber = new AtomicInteger(1);
    private ConcurrentHashMap<String, Statistic> statistics = new ConcurrentHashMap<>();
    private DatagramSocket prevSocket = null;
    private ScheduledExecutorService delayedExecutor = Executors.newSingleThreadScheduledExecutor();

    public UDPPingServer(int port, int threadPoolSize) {
        executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            ErrorDialog.showError("Failed to initialize LaF");
        }
        gui = new ServerGUI(port);
        running = new AtomicBoolean(true);
    }

    public void start() {
        initializeGui();
        new Thread(() -> {
            DatagramSocket socket = null;
            try {
                while (running.get()) {
                    socket = manageSocket(socket);
                    receiveAndHandlePackets(socket);
                }
            } catch (Exception e) {
                ErrorDialog.showError(e.getMessage());
                stop();
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }).start();
    }

    private void initializeGui() {
        SwingUtilities.invokeLater(() -> gui.setVisible(true));

        gui.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });
    }

    private DatagramSocket manageSocket(DatagramSocket socket) throws SocketException {
        if (socket == null || socket.getLocalPort() != gui.getPort()) {
            if (prevSocket != null) {
                delayedExecutor.schedule(prevSocket::close, 1, TimeUnit.SECONDS);
            }
            prevSocket = socket;
            socket = new DatagramSocket(gui.getPort());
        }
        return socket;
    }

    private void receiveAndHandlePackets(DatagramSocket socket) throws IOException {
        while (running.get() && socket.getLocalPort() == gui.getPort()) {
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            socket.receive(packet);

            // 模拟丢失
            if (simulatePacketLoss(packet)) {
                continue;
            }

            // 模拟延迟
            try {
                simulatePacketDelay(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("模拟延迟错误", e);
            }

            executor.execute(() -> {
                try {
                    handlePacket(socket, packet);
                } catch (Exception e) {
                    ErrorDialog.showError("处理数据包错误: " + e.getMessage());
                }
            });
        }
    }

    private boolean simulatePacketLoss(DatagramPacket packet) {
        int rate = gui.getLossRate();
        if (!gui.getLoss()) {
            return false;
        }
        if (random.nextInt(100) < rate) {
            String packetContent = new String(packet.getData(), packet.getOffset(), packet.getLength());
            gui.appendLog("模拟丢失该数据包，数据包内容如下：" + packetContent);
            statistics.computeIfAbsent(packet.getAddress().getHostAddress(), Statistic::new).incrementDropCount();
            updateGUI();
            return true;
        }
        return false;
    }

    private void simulatePacketDelay(DatagramPacket packet) throws InterruptedException {
        if (gui.getDelay()) {
            int delayTime = gui.getDelayTime();
            if (delayTime == -1) {
                delayTime = random.nextInt(1000);
            }
            TimeUnit.MILLISECONDS.sleep(delayTime);
            String packetContent = new String(packet.getData(), packet.getOffset(), packet.getLength());
            gui.appendLog("延迟该数据包" + delayTime + " ms，数据包内容如下：" + packetContent);
            statistics.computeIfAbsent(packet.getAddress().getHostAddress(), Statistic::new).incrementDelayCount();
            updateGUI();
        }
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> map = new HashMap<>();
        String[] items = payload.split(" ");
        if (items.length >= 3) {
            map.put("PingUDP", items[0]);
            map.put("SequenceNumber", items[1]);
            map.put("TimeStamp", items[2]);
        }
        return map;
    }

    private void handlePacket(DatagramSocket socket, DatagramPacket packet) {
        String content = new String(packet.getData(), packet.getOffset(), packet.getLength());
        Map<String, String> map = parsePayload(content);
        statistics.computeIfAbsent(packet.getAddress().getHostAddress(), Statistic::new);
        updateGUI();
        String headerInfo = "源端口: " + packet.getPort() + ", 目标端口: " + socket.getLocalPort()
                + ", 数据长度: " + packet.getLength() + ", 源地址: " + packet.getAddress().getHostAddress() + ", 数据偏移: " + packet.getOffset();
        String message = "第 " + messageNumber.getAndIncrement() + " 条消息\n收到来自 " + packet.getAddress().getHostAddress()
                + " 地址的消息\n头部信息为："
                + headerInfo + "\n有效负载为：" + map + "\n实际数据内容：" + Arrays.toString(packet.getData()) + "\n";
        gui.appendMessage(message);

        // 发送响应
        byte[] responseBytes = content.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort());
        try {
            socket.send(responsePacket);
        } catch (Exception e) {
            throw new RuntimeException("发送响应错误: " + e.getMessage(), e);
        }
    }

    private void updateGUI() {
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<String, Statistic> entry : statistics.entrySet()) {
                String ip = entry.getKey();
                Statistic stat = entry.getValue();
                gui.updateStatsTable(ip, stat.getDelayCount(), stat.getDropCount());
            }
        });
    }

    public void stop() {
        running.set(false);
        executor.shutdownNow();
        delayedExecutor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("线程池没有完全关闭");
            }
            if (!delayedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("延迟执行器没有完全关闭");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    public static void main(String[] args) {
        int port;
        int threadPoolSize;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
            threadPoolSize = Integer.parseInt(args[1]);
        } else {
            port = 8000;
            threadPoolSize = 64;
        }
        UDPPingServer server = new UDPPingServer(port, threadPoolSize);
        server.start();
    }
}

