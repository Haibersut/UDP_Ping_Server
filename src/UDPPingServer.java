import java.io.IOException;
import java.net.*;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Random random = new Random();
    private AtomicInteger delay = new AtomicInteger();
    private AtomicBoolean running;
    private AtomicInteger messageNumber = new AtomicInteger(1);
    public UDPPingServer(int port) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }
        gui = new ServerGUI(port);
        this.delay.set(gui.getDelayTime());
        running = new AtomicBoolean(true);
    }

    public void start() {
        while (running.get()) {
            try (DatagramSocket socket = new DatagramSocket(gui.getPort())) {
                receiveAndHandlePackets(socket);
            } catch (Exception e) {
                ErrorDialog.showError("服务器启动错误: " + e.getMessage());
            }
        }
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
                throw new RuntimeException(e);
            }

            executor.execute(() -> handlePacket(socket, packet));
        }
    }

    private boolean simulatePacketLoss(DatagramPacket packet) {
        int rate = gui.getLossRate();
        if (random.nextInt(100) < rate) {
            String packetContent = new String(packet.getData(), packet.getOffset(), packet.getLength());
            gui.appendLog("模拟丢失该数据包，数据包内容如下：" + packetContent);
            return true;
        }
        return false;
    }

    private void simulatePacketDelay(DatagramPacket packet) throws InterruptedException {
        if (gui.getDelay()) {
            TimeUnit.MILLISECONDS.sleep(delay.get()); // 使用 AtomicInteger 的 get 方法获取延迟时间
            String packetContent = new String(packet.getData(), packet.getOffset(), packet.getLength());
            gui.appendLog("延迟该数据包" + delay.get() + " ms，数据包内容如下：" + packetContent);
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
        try {
            String content = new String(packet.getData(), packet.getOffset(), packet.getLength());
            Map<String, String> map = parsePayload(content);

            String headerInfo = "源端口: " + packet.getPort() + ", 目标端口: " + socket.getLocalPort()
                    + ", 长度: " + packet.getLength() + ", 地址: " + packet.getAddress().getHostAddress();
            String message = "第 " + messageNumber.getAndIncrement() + " 条消息\n收到来自 " + packet.getAddress().getHostAddress()
                    + " 地址的消息\n头部信息为："
                    + headerInfo + "\n有效负载为：" + map.toString() + "\n";
            gui.appendMessage(message);

            // 发送响应
            byte[] responseBytes = ("回复: " + map.toString()).getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort());
            socket.send(responsePacket);
        } catch (Exception e) {
            ErrorDialog.showError("处理数据包错误: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8000;
        }
        UDPPingServer server = new UDPPingServer(port);
        server.start();
        while (true) {
            if (server.gui.getPort() != port) {
                server.running.set(false);
                port = server.gui.getPort();
                server = new UDPPingServer(port);
                server.start();
            }
        }
    }
}

