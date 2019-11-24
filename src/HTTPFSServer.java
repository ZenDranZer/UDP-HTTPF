import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;


public class HTTPFSServer {

    private static int port = 8080,count = 0;
    private static String dir = "src/server/Files";
    private static boolean isV = false;

    public static void main(String[] args) {
        try{

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String[] input = br.readLine().split(" ");

            for (int i=0; i < input.length;i++) {
                if(input[i].equals("-p"))
                    port = Integer.parseInt(input[++i]);

                if(input[i].equals("-d"))
                    dir = input[++i];

                if(!isV)
                    isV = input[i].equals("-v");
            }
            if(isV)
                System.out.println("Server ON! \nListening to Port # : " + port);
            listen();
        }catch (Exception e){e.printStackTrace();}
    }

    private static void listen() throws Exception{
        PacketMaker packet;
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));

            ByteBuffer buf = ByteBuffer.allocate(PacketMaker.MAX_PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN);
            while (true){
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                packet = PacketMaker.fromBuffer(buf);
                buf.flip();

                String payload = new String(packet.getPayload(), UTF_8);
                System.out.println("Packet :" + packet);
                System.out.println("Payload :" + payload);
                System.out.println("Router :" + router);
                System.out.println();

                int packetNumber = packet.getSequenceNumber();
                String ACK = "send packet from "+(++packetNumber);
                if(packet.getPacketType() == PacketMaker.CONNECTION_TYPE && !payload.equals("ACK")) {
                    PacketMaker resp = packet.toBuilder().setPayload(ACK.getBytes()).create();
                    channel.send(resp.toBuffer(), router);

                    System.out.println(">> Client #"+count+" "+packet.getPeerPort()+" connection established");
                    System.out.println(packet.getPacketType());
                    System.out.println(payload);

                }else if(packet.getPacketType() == PacketMaker.DATA_TYPE && !payload.equals("ACK")) {
                    ParallelServer hst = new ParallelServer(channel,packet,dir,router);
                    hst.start();
                    count++;
                }
                else if(packet.getPacketType() == PacketMaker.DATA_TYPE && payload.equals("ACK")) {
                    System.out.println("DONE");
                }
            }
        }
    }
}
