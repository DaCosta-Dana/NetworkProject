import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Client {
    private DatagramSocket clientSocket;
    private int total_bytes_received;
    // private int retransmissions_received;
    private List<Integer> list_ack;
    private String server_IP;
    private AtomicInteger serverPort;
    
    
    // Constructor to initialize the Client
    public Client(String server_IP,AtomicInteger serverPort) throws SocketException {
        this.server_IP = server_IP;
        this.serverPort = serverPort;
        this.clientSocket = new DatagramSocket();
        this.list_ack = new ArrayList<>();
        // this.total_bytes_received = 0;
        // this.retransmissions_received = 0;
    }

    public void connectToServer() throws IOException {
        // Wait for the serverSocket to be dynamically assigned
        while (serverPort.get() == -1) {
            try {
                Thread.sleep(100);  // Adjust sleep duration as needed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Get the server IP Address (e.g. localhost)
        InetAddress serverIPAddress = InetAddress.getByName(server_IP);
        
        // Prepare the message to send and connect to the server
        String connectionRequestMessage = "1";
        byte[] sendData = connectionRequestMessage.getBytes();

        // Create a DatagramPacket to send the data to the server
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverIPAddress, serverPort.get());
        
        // Send the packet to the server
        clientSocket.send(sendPacket);
    }
    
    public void receiveFile() throws Exception {
        while (true) {
            // Create buffer to store incoming data packets
            byte[] buffer = new byte[2048];     // length of 2048 bytes (TODO: can be changed)

            // Create a DatagramPacket to receive data packet from the server
            DatagramPacket receiveData = new DatagramPacket(buffer, buffer.length);
                //second parameter "buffer.length" = maximum amount of data that the packet can hold

            // Wait for and Receive a data packet from the server
            clientSocket.receive(receiveData);

            // Convert the received data to a string
            String modified_message = new String(receiveData.getData(), 0, receiveData.getLength());

            // Check if the "finished" signal is received
            if (modified_message.equals("finished")) {
                System.out.println("Client: Transfer finished");
                break;
            }

            // Extract the packet ID from the received message
            int packet_id = Integer.parseInt(modified_message.substring(0, 6));

            // Print the received packet ID to the console
            System.out.println("Client: Received packet with ID: " + packet_id);

            // Send acknowledgment for the received packet
            send_ack(packet_id, receiveData.getAddress());

            // Update the total bytes received
            total_bytes_received += modified_message.length();
        }

        // Close the socket after the transfer is finished
        clientSocket.close();
    }

    public boolean send_ack(int packet_id, InetAddress server_address) throws Exception {

        // Print acknowledgment information to console
        System.out.println("Client: Acknowledgment for Packet ID " + packet_id + " sent successfully");

        // Convert the acknowledgment to bytes
        byte[] acknowledgment = Integer.toString(packet_id).getBytes();
        
        // Create a DatagramPacket with the acknowledgment data, server address, and a specific port
        DatagramPacket packet = new DatagramPacket(acknowledgment, acknowledgment.length, server_address, serverPort.get()); 
        
        // Send the acknowledgment packet using the client socket
        clientSocket.send(packet);

        // Add the packet ID to the list of acknowledged packets
        list_ack.add(packet_id);

        // Return true to indicate successful acknowledgment sending
        return true;
    }

}
