import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class PingClient {
    private static final int MAX_TIMEOUT = 1000; // in milliseconds
    private static final int DEFAULT_PING_COUNT = 10;

    public static void main(String[] args) throws IOException {
        // Command line argument validation
        if (args.length < 2) {
            System.out.println("Required arguments: host port [pingCount]");
            return;
        }

        // Retrieve host and port from arguments
        InetAddress host = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        //If there is a third parameter, convert it to an integer as the number of pings, otherwise use the default value
        int pingCount = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_PING_COUNT;

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(MAX_TIMEOUT);
            List<Long> rttList = new ArrayList<>();
            //ensure that data inconsistency does not occur when multiple threads access simultaneously
            AtomicInteger received = new AtomicInteger(0);
            System.out.println("正在 Ping " + args[0] + ":");
            final long seq = System.currentTimeMillis();
            for (int i=0;i<pingCount;i++){
                String payload = String.format("PingUDP %d %d\r\n",seq+i,System.currentTimeMillis());
                DatagramPacket request = new DatagramPacket(payload.getBytes(StandardCharsets.UTF_8), payload.length(), host, port);

                long start = System.currentTimeMillis();
                try {
                    socket.send(request);

                    byte[] buffer = new byte[1024];
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);

                    socket.receive(response);
                    final int length = response.getLength();
                    String str = new String(buffer,0,length);
                    if(!str.equals(payload))
                        throw new SocketTimeoutException();
                    long rtt = System.currentTimeMillis() - start;
                    rttList.add(rtt);
                    received.incrementAndGet();
                    System.out.printf("来自 %s 的回复: 字节=%d 时间=%dms%n",args[0],length,rtt);
                } catch (IOException e) {
                    System.out.println("请求超时。");
                }
            }

            // Calculating statistics
            System.out.println("\n" + args[0] + " 的 Ping 统计信息:");
            System.out.printf("\t数据包: 已发送 = %d，已接收 = %d，丢失 = %d(%d%% 丢失)%n",
                    pingCount,received.get(),
                    pingCount - received.get(),
                    (pingCount - received.get()) * 100 / pingCount);
            if (!rttList.isEmpty()) {
                //用Java8的新特性
                long sum = rttList.stream().mapToLong(Long::longValue).sum();
                System.out.println("往返行程的估计时间(以毫秒为单位):");
                System.out.printf("\t最短RTT = %dms，最长RTT = %dms，平均RTT = %dms%n",
                        rttList.stream().max(Long::compareTo).get(),
                        rttList.stream().min(Long::compareTo).get(),
                        (sum / rttList.size()));
            } else {
                System.out.println("所有数据包均已丢失，无法计算 RTT 统计信息。");
            }
        }
    }
}
