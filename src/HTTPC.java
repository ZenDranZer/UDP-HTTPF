import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
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

public class HTTPC{

    //Data
    private String command;
    private int sequenceNumber = 1;
    private static String payload;
    private boolean headerOption=false,inlineDataOption=false,sendfileOption=false;
    private String requestCommand,url="",inlineData="",fileToRead="";
    private ArrayList<String> headerslist = new ArrayList<>();

    //Packet Types
    private static final String ACK = "ACK";

    //DataStructure
    private PacketMaker p;
    private static HashMap<Integer, String> packetList;
    private StringBuilder writerBuilder;

    //Networking
    private URI uri;
    private String host;
    private String path;
    private String query;
    private String protocol;
    private Socket socket;
    private int port;
    private InetSocketAddress serverAddr;
    private static SocketAddress routerAddr = new InetSocketAddress("localhost", 3000);
    private static DatagramChannel channel;

    HTTPC(String command) {
        this.command = command;
    }

    private void initialHandshake() throws Exception {
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
        }else {
            System.out.println("Sending \"handshake message\" to router at { "+routerAddr+" }");
            this.initialHandshake();
        }
    }

    private void openChannel() {
        try {
            channel = DatagramChannel.open();
            packetList = new HashMap<>();
            this.initialHandshake();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getUrlData(String url2) {
        try{
            uri = new URI(url2);
            host = uri.getHost();
            path = uri.getRawPath();
            query = uri.getRawQuery();
            protocol = uri.getScheme();
            port = uri.getPort();

            if (path == null) {
                path = "";
            }
            if (query == null) {
                query = "";
            }
            if (query.length() > 0 || path.length() > 0) {
                path = path + "?" + query;
            }
            if (port == -1) {
                if (protocol.equals("http")) {
                    port = 80;
                }
                if (protocol.equals("https")) {
                    port = 443;
                }
            }
            serverAddr = new InetSocketAddress(host, port);
            this.openChannel();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void receive(DatagramChannel channel) throws Exception{
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

    void parseInput(){
        String[] input = command.split(" ");
        if(input.length > 1){
            if(input[0].equals("httpc")){
                requestCommand = input[1];
                if(requestCommand.toLowerCase().equals("get") || requestCommand.toLowerCase().equals("post")){
                    for(int i = 0; i< input.length ; i++) {
                        if(input[i].equals("-h")) {
                            headerOption = true;
                            headerslist.add(input[++i]);
                        }
                        if(input[i].equals("-d") || input[i].equals("--d")) {
                            inlineDataOption = true;
                            inlineData = input[++i];
                        }
                        if(input[i].equals("-f")) {
                            sendfileOption = true;
                            fileToRead = input[++i];
                        }
                        if(input[i].startsWith("http://") || input[i].startsWith("https://")) {
                            url = input[i];
                        }
                    }
                    if(url != null) {
                        this.getUrlData(url);
                        if(!(sendfileOption && inlineDataOption) ) {
                            if(requestCommand.equals("get")  ) {
                                this.getRequest();
                            }
                            else if(requestCommand.equals("post")) {
                                this.postRequest();
                            }
                        }else {System.out.println("-d AND -f CAN NOT BE USED TOGETHER."); }
                    }else {System.out.println("please enter correct URL");}
                }else{printGeneralHelp();}
            }else{System.out.println("COMMAND SHOULD START WITH 'httpc'.");}
        }else{System.out.println("NO SUCH COMMAND!!!"); }
    }

    private String getPacketInfo(int packetNumber) {
        return packetList.get(packetNumber);
    }

    private void flush() {
        try {
            p = new PacketMaker.Packet()
                    .setPacketType(PacketMaker.DATA_TYPE)
                    .setSequenceNumber(sequenceNumber)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(writerBuilder.toString().trim().getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);
            packetList.put(sequenceNumber, "");
            System.out.println("Sending " + writerBuilder.toString().trim() +" to router at " + routerAddr);
            receive(channel);
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
                this.getRequest();
            }
        }catch (Exception e){e.printStackTrace();}
    }

    private void getRequest() {

        try{
            writerBuilder = new  StringBuilder();
            if (path.length() == 0) { writerBuilder.append("GET / HTTP/1.1\n");
            }else { writerBuilder.append("GET " + path + " HTTP/1.1\n"); }

            writerBuilder.append("Host:" + host+"\n");
            if (!headerslist.isEmpty()) {
                for (String s : headerslist) {
                    if (headerOption) {
                        String[] headerKeyValue = s.split(":");
                        writerBuilder.append(headerKeyValue[0] + ":" + headerKeyValue[1] + "\n");
                    }
                }
            }
            writerBuilder.append("\r\n\n");
            this.flush();
            this.checkForRedirection("getRedirect");
        }catch (Exception e){e.printStackTrace();}
    }

    private void postRequest() {
        try {
            String fileData = "";
            if (path.length() == 0) { writerBuilder.append("POST / HTTP/1.1\r\n");
            } else { writerBuilder.append("POST " + path + " HTTP/1.1\r\n"); }

            writerBuilder.append("Host:" + host + "\r\n");

            if(headerOption) {
                if (!headerslist.isEmpty()) {
                    for (String s : headerslist) {
                        String[] headerKeyValue = s.split(":");
                        writerBuilder.append(headerKeyValue[0] + ":" + headerKeyValue[1] + "\r\n");
                    }
                }
            }
            if(inlineDataOption) {
                writerBuilder.append("Content-Length:" + inlineData.length() + "\r\n");
            }
            else if(sendfileOption) {
                FileReader fr = new FileReader(fileToRead);
                BufferedReader brreader = new BufferedReader(fr);
                String sCurrentLine;
                while ((sCurrentLine = brreader.readLine()) != null) {
                    fileData = fileData + sCurrentLine;
                }
                writerBuilder.append("Content-Length:" + fileData.length() + "\r\n");
            }

            writerBuilder.append("\r\n");
            if (inlineData != null) {
                inlineData = inlineData.replace("\'", "");
                writerBuilder.append(inlineData);
                writerBuilder.append("\r\n");
            }
            if (!fileData.isEmpty()) {
                writerBuilder.append(fileData);
                writerBuilder.append("\r\n");
            }
            writerBuilder.append("\r\n\n");
            this.flush();
            this.checkForRedirection("postRedirect");
        }catch (Exception e){e.printStackTrace();}
    }

    private void checkForRedirection(String requestRedirect) {
        boolean isRedirect = false;
            if (payload.contains("301") || payload.contains("302") || payload.contains("303"))
                isRedirect = true;

            String Location;
            int index = payload.indexOf("Location:");
            String locationString = payload.substring(index);
            Location = locationString.split(":")[1];
        if (isRedirect) {
            try {
                System.out.println();
                Thread.sleep(1000);

                System.out.print("Connecting to:"+Location);
                System.out.println();
                System.out.println();

                this.getUrlData(Location);
                if(requestRedirect.equals("getRedirect")) {
                    this.getRequest();
                }
                else if(requestRedirect.equals("postRedirect")) {
                    this.postRequest();
                }
                System.out.println("Redirect Done...");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static void setPacketACK(long sequenceNumber) {
        packetList.put((int) sequenceNumber, ACK);
    }

    private void printGeneralHelp(){
        String help = "\n" +
                "httpc is a curl-like application but supports HTTP protocol only.\n" +
                "Usage: \n    httpc [get/post/help] URL [-v] [-h key:value] [-d dataWithoutSpace] [-f fileName] [-o fileName]\n" +
                "    get     executes a HTTP GET request and prints the response.\n" +
                "    post    executes a HTTP POST request and prints the response.\n" +
                "    help    prints this screen.\n\n";
        System.out.println(help);
        printGetHelp();
        printPostHelp();
    }

    private void printGetHelp(){
        System.out.println("httpc help get\n" +
                "usage: httpc get [-v] [-h key:value] URL\n"
                + "Get executes a HTTP GET request for a given URL.\n"
                + "-v\tPrints the detail of the response such as protocol, status, and headers.\n"
                + "-h key:value\tAssociates headers to HTTP Request with the format 'key:value'.");
    }

    private void printPostHelp(){
        System.out.println("httpc help post\n"
                + "usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL\n"
                + "Post executes a HTTP POST request for a given URL with inline data or from file.\n"
                + "-v\tPrints the detail of the response such as protocol, status, and headers.\n"
                + "-h key:value\tAssociates headers to HTTP Request with the format 'key:value'.\n"
                + "-d string\tAssociates an inline data to the body HTTP POST request.\n"
                + "-f file\tAssociates the content of a file to the body HTTP POST request.\n\n"
                + "Either [-d] or [-f] can be used but not both.");
    }
}
