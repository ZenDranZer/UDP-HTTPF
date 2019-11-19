import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PacketMaker {

    public static final int MIN_PACKET_LENGTH = 11;
    public static final int MAX_PACKET_LENGTH = 11 + 1013;
    public static final int DATA_TYPE = 0;
    public static final int CONNECTION_TYPE = 1;

    private final int packetType;
    private final int sequenceNumber;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final byte[] payload;

    public PacketMaker(int type, int sequenceNumber, InetAddress peerAddress, int peerPort, byte[] payload) {
        this.packetType = type;
        this.sequenceNumber = sequenceNumber;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.payload = payload;
    }

    public Packet toBuilder(){
        return new Packet().setPacketType(packetType).setSequenceNumber(sequenceNumber).setPeerAddress(peerAddress).setPortNumber(peerPort).setPayload(payload);
    }

    public byte[] toBytes() {
        ByteBuffer buf = toBuffer();
        byte[] raw = new byte[buf.remaining()];
        buf.get(raw);
        return raw;
    }

    public static PacketMaker fromBytes(byte[] bytes) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(MAX_PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buf.put(bytes);
        buf.flip();
        return fromBuffer(buf);
    }

    public ByteBuffer toBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(MAX_PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN);
        write(buf);
        buf.flip();
        return buf;
    }

    private void write(ByteBuffer buf) {
        buf.put((byte) packetType);
        buf.putInt(sequenceNumber);
        buf.put(peerAddress.getAddress());
        buf.putShort((short) peerPort);
        buf.put(payload);
    }

    public static PacketMaker fromBuffer(ByteBuffer buf) throws Exception {
        if (buf.limit() < MIN_PACKET_LENGTH || buf.limit() > MAX_PACKET_LENGTH) {
            System.out.println(buf.limit());
            throw new Exception("Invalid length");
        }

        Packet packet = new Packet();

        packet.setPacketType(Byte.toUnsignedInt(buf.get()));
        packet.setSequenceNumber(Byte.toUnsignedInt(buf.get()));

        byte[] host = new byte[]{buf.get(), buf.get(), buf.get(), buf.get()};
        packet.setPeerAddress(Inet4Address.getByAddress(host));
        packet.setPortNumber(Short.toUnsignedInt(buf.getShort()));

        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        packet.setPayload(payload);

        return packet.create();
    }

    public int getPacketType() {
        return packetType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public InetAddress getPeerAddress() {
        return peerAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public byte[] getPayload() {
        return payload;
    }

    public static class Packet{

        private int packetType;
        private int sequenceNumber;
        private InetAddress peerAddress;
        private int portNumber;
        private byte[] payload;

        public PacketMaker create() {
            return new PacketMaker(packetType, sequenceNumber, peerAddress, portNumber, payload);
        }

        public Packet setPacketType(int packetType) {
            this.packetType = packetType;
            return this;
        }

        public Packet setSequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Packet setPeerAddress(InetAddress peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }

        public Packet setPortNumber(int portNumber) {
            this.portNumber = portNumber;
            return this;
        }

        public Packet setPayload(byte[] payload) {
            this.payload = payload;
            return this;
        }
    }

}
