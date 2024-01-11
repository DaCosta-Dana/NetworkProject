import java.util.ArrayList;
import java.util.List;
import java.net.*;

class Client {
    private DatagramSocket socket;
    private int total_bytes_received;
    private int retransmissions_received;
    private List<Integer> list_ack;
    
    public Client(DatagramSocket socket, int port) {
        this.socket = socket;
        this.total_bytes_received = 0;
        this.retransmissions_received = 0;
        this.list_ack = new ArrayList<>();
    }
    
    public boolean send_ack(int packet_id, InetAddress server_address) throws Exception {
        System.out.println("Client: Acknowledgment for Packet ID " + packet_id + " sent successfully");
        byte[] acknowledgment = Integer.toString(packet_id).getBytes();
        DatagramPacket packet = new DatagramPacket(acknowledgment, acknowledgment.length, server_address, 12000);
        socket.send(packet);
        list_ack.add(packet_id);

        return true;
    }

    public void receive_data() throws Exception {
        while (true) {
            // Create buffer to store incoming data packets
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String modified_message = new String(packet.getData(), 0, packet.getLength());

            if (modified_message.equals("finished")) {
                System.out.println("Client: Transfer finished");
                break;
            }

            int packet_id = Integer.parseInt(modified_message.substring(0, 6));

            System.out.println("Client: Received packet with ID: " + packet_id);

            // Send acknowledgment for the received packet
            send_ack(packet_id, packet.getAddress());

            total_bytes_received += modified_message.length();
        }

        socket.close();
    }
}
