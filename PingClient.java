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
            System.out.println("���� Ping " + args[0] + ":");
            final long seq = System.currentTimeMillis();
            IntStream.range(0, pingCount).forEach(i -> {    //use lambda
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
                    System.out.println(String.format("���� %s �Ļظ�: �ֽ�=%d ʱ��=%dms",args[0],length,rtt));
                } catch (IOException e) {
                    System.out.println("����ʱ��");
                }
            });

            // Calculating statistics
            System.out.println("\n" + args[0] + " �� Ping ͳ����Ϣ:");
            System.out.println(String.format("\t���ݰ�: �ѷ��� = %d���ѽ��� = %d����ʧ = %d(%d%% ��ʧ)",pingCount,received.get(),pingCount - received.get(),(pingCount - received.get()) * 100 / pingCount));
            if (!rttList.isEmpty()) {
                Collections.sort(rttList);
                ////Using streams to calculate the sum of all elements in the round-trip time list
                long sum = rttList.stream().mapToLong(Long::longValue).sum();
                System.out.println("�����г̵Ĺ���ʱ��(�Ժ���Ϊ��λ):");
                System.out.println(String.format("\t���RTT = %dms���RTT = %dms��ƽ��RTT = %dms",rttList.get(0),rttList.get(rttList.size() - 1),(sum / rttList.size())));
            } else {
                System.out.println("�������ݰ����Ѷ�ʧ���޷����� RTT ͳ����Ϣ��");
            }
        }
    }
}
