import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class PacketMaker {

    private static final int MIN_PACKET_LENGTH = 11;
    static final int MAX_PACKET_LENGTH = 11 + 1013;
    static final int DATA_TYPE = 0;
    static final int CONNECTION_TYPE = 1;

    private final int packetType;
    private final int sequenceNumber;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final byte[] payload;

    private PacketMaker(int type, int sequenceNumber, InetAddress peerAddress, int peerPort, byte[] payload) {
        this.packetType = type;
        this.sequenceNumber = sequenceNumber;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.payload = payload;
    }

    Packet toBuilder(){
        return new Packet().setPacketType(packetType).setSequenceNumber(sequenceNumber).setPeerAddress(peerAddress).setPortNumber(peerPort).setPayload(payload);
    }

    ByteBuffer toBuffer() {
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

    static PacketMaker fromBuffer(ByteBuffer buf) throws Exception {
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

    int getPacketType() {
        return packetType;
    }

    int getSequenceNumber() {
        return sequenceNumber;
    }

    int getPeerPort() {
        return peerPort;
    }

    byte[] getPayload() {
        return payload;
    }

    static class Packet{

        private int packetType;
        private int sequenceNumber;
        private InetAddress peerAddress;
        private int portNumber;
        private byte[] payload;

        PacketMaker create() {
            return new PacketMaker(packetType, sequenceNumber, peerAddress, portNumber, payload);
        }

        Packet setPacketType(int packetType) {
            this.packetType = packetType;
            return this;
        }

        Packet setSequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        Packet setPeerAddress(InetAddress peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }

        Packet setPortNumber(int portNumber) {
            this.portNumber = portNumber;
            return this;
        }

        Packet setPayload(byte[] payload) {
            this.payload = payload;
            return this;
        }
    }

}
