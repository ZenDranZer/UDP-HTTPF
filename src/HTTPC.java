import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class HTTPC {

    //Data
    private String command = "";
    int sequenceNumber = 1;
    static String payload;

    //Packet Types
    static final String ACK = "ACK";

    //DataStructure
    PacketMaker p;
    static HashMap<Integer, String> packetList;

    //Networking
    InetSocketAddress serverAddr;
    static SocketAddress routerAddr = new InetSocketAddress("localhost", 3000);
    static DatagramChannel channel;

    public HTTPC(String command) {
        this.command = command;
    }

    public void initialHandshake() throws Exception {
        p = new PacketMaker.Packet()
                .setPacketType(PacketMaker.CONNECTION_TYPE)
                .setSequenceNumber(sequenceNumber)
                .setPortNumber(serverAddr.getPort())
                .setPeerAddress(serverAddr.getAddress())
                .setPayload("handshake".getBytes())
                .create();
        channel.send(p.toBuffer(), routerAddr);
        packetList.put(sequenceNumber, "");
        System.out.println("Sending \"handshake message\" to router at { "+routerAddr+" }");
        receive(channel,routerAddr);

        if(getPacketInfo(sequenceNumber).equals(ACK)) {
            sequenceNumber++;
            p = new PacketMaker.Packet()
                    .setPacketType(PacketMaker.CONNECTION_TYPE)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload("ACK".getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);
            packetList.put(sequenceNumber, "");
            System.out.println("Sending \"handshake message\" to router at { "+routerAddr+" }");
            sequenceNumber++;
        }else {
            System.out.println("Sending \"handshake message\" to router at { "+routerAddr+" }");
            this.initialHandshake();
        }
    }


    public void openChannel() {
        try {
            channel = DatagramChannel.open();
            packetList = new HashMap<>();
            this.initialHandshake();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void receive(DatagramChannel channel,SocketAddress routerAddr) throws Exception{
        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        System.out.println("Waiting for the response");
        selector.select(10000);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            System.out.println("No response after timeout");
            return;
        }

        while(true) {
            ByteBuffer buf = ByteBuffer.allocate(PacketMaker.MAX_PACKET_LENGTH);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            if(buf.limit() == 0)
                break;
            PacketMaker resp = PacketMaker.fromBuffer(buf);
            System.out.println("Packet: "+ resp);
            System.out.println("Router: "+ router);
            payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            System.out.println("Payload: "+ payload);
            setPacketACK(resp.getSequenceNumber());
            keys.clear();
        }
    }




    public String getPacketInfo(int packetNumber) {
        return packetList.get(packetNumber);
    }

    public void setPacketInfo(HashMap<Integer, String> packetInfo) {
        packetList = packetInfo;
    }

    public static void setPacketACK(long sequenceNumber) {
        packetList.put((int) sequenceNumber, ACK);
    }

}
