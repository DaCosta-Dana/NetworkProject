import java.util.ArrayList;
import java.util.List;
import java.net.*;

class Receiver {
    private DatagramSocket socket;
    private double ack_probability;
    private int total_bytes_received;
    private int retransmissions_received;
    private List<Integer> list_ack;
    private int port;
    
    public Receiver(DatagramSocket socket, double ack_probability, int port) {
        this.socket = socket;
        this.ack_probability = ack_probability;
        this.total_bytes_received = 0;
        this.retransmissions_received = 0;
        this.list_ack = new ArrayList<Integer>();
        this.port = port;
    }
    
    public boolean send_ack(int packet_id, InetAddress server_address) throws Exception {
        if (Math.round(Math.random() * 1000) / 1000.0 < this.ack_probability) {
            System.out.println("Client: Packet with ID : " + packet_id + " lost");
            return false;
        }
        
        System.out.println("Client: Acknowledgment for Packet ID " + packet_id + " sent successfully");
        byte[] acknowledgment = Integer.toString(packet_id).getBytes();
        DatagramPacket packet = new DatagramPacket(acknowledgment, acknowledgment.length, server_address, 12000); //to change 
        socket.send(packet);
        list_ack.add(packet_id);
        
        return true;
    }
    
    public void receive_data() throws Exception {
        while (true) {
            Thread.sleep(5);
            // Create buffer to store incoming data packets
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String modified_message = new String(packet.getData(), 0, packet.getLength());
            
            if (modified_message.equals("finished")) {
                System.out.println("Client: Transfer finished");
                break;
            }
            
            int packet_id1 = Integer.parseInt(modified_message.substring(0, 6));
            
            System.out.println("Client: Received packet with ID: " + packet_id1);
            
            if (!send_ack(packet_id1, packet.getAddress())) {
                System.out.println("Client: Retransmission received");
                retransmissions_received++;
            }
            
            total_bytes_received += modified_message.length();
        }
    
        socket.close();
    }
}