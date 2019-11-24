import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

class HTTPFSClient {

    //data
    private String input;
    private boolean isHeader = false;
    private String URL = "";
    private ArrayList<String> headers;
    private boolean isContent = false;
    private String content = "";
    private int sequenceNumber = 1;
    private static final String ACK = "ACK";
    private String query;

    //Networking
    private static HashMap<Integer,String> packetList;
    private static DatagramChannel channel;
    private InetSocketAddress serverAddr;
    private static PacketMaker p;
    private static SocketAddress routerAddr = new InetSocketAddress("localhost", 3000);

    HTTPFSClient(String input) {
        try {
        this.input = input;
        headers = new ArrayList<>();
        channel = DatagramChannel.open();
        packetList = new HashMap<>();
        parseInput();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void receive(DatagramChannel channel){
        try{
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            System.out.println("Waiting for response...");
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
                String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                System.out.println("Payload: "+ payload);
                setPacketACK(resp.getSequenceNumber());
                keys.clear();
            }
        }catch (Exception e){e.printStackTrace();}
    }

    private void sendRequest() {
        try{
            StringBuilder clientRequest = new StringBuilder();
            clientRequest.append(query+"\n");
            if(isHeader) {
                for(int i = 0 ; i<headers.size();i++) {
                    clientRequest.append(headers.get(i)+"\n");
                }
            }
            if(isContent) {
                clientRequest.append("-d"+content+"\n");
            }
            clientRequest.append("\r\n");
            p = new PacketMaker.Packet()
                    .setPacketType(PacketMaker.DATA_TYPE)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(clientRequest.toString().trim().getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);
            packetList.put(sequenceNumber, "");
            System.out.println("Sending "+ clientRequest +" to router at { "+routerAddr+" }");
            receive(channel);
            //last packet from client to close the connection
            if(getPacketInfo(sequenceNumber).equals(ACK)) {
                sequenceNumber++;
                p = new PacketMaker.Packet()
                        .setPacketType(PacketMaker.DATA_TYPE)
                        .setSequenceNumber(sequenceNumber)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload("ACK".getBytes())
                        .create();
                channel.send(p.toBuffer(), routerAddr);
                packetList.put(sequenceNumber, "");
            }else {
                this.sendRequest();
            }
        }catch (Exception e){e.printStackTrace();}

    }

    void initialHandshake() throws Exception {
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
        receive(channel);

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
            this.sendRequest();
        }else {
            System.out.println("Sending \"handshake message\" to router at { "+routerAddr+" }");
            this.initialHandshake();
        }
    }

    private void parseInput(){
        try{String[] commandClient = input.split(" ");
            if(commandClient[0].equals("httpfs")) {
                for(int i =0; i<commandClient.length; i++) {
                    if(commandClient[i].equals("-h")) {
                        isHeader = true;
                        headers.add(commandClient[++i]);
                    }
                    if(commandClient[i].startsWith("http://")){
                        URL = commandClient[i];
                    }
                    if(commandClient[i].startsWith("-d")) {
                        isContent = true;
                        content = commandClient[++i];
                    }
                }
            }
            URI uri = new URI(URL);
            String host = uri.getHost();
            int port = uri.getPort();
            query = uri.getPath().substring(1);
            serverAddr = new InetSocketAddress(host, port);
        }catch (Exception e){e.printStackTrace();}
    }

    private String getPacketInfo(int packetNumber) {
        return packetList.get(packetNumber);
    }

    private static void setPacketACK(long sequenceNumber) {
        packetList.put((int) sequenceNumber, ACK);
    }

}
