import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class Client {
    private String server_IP;
    private AtomicInteger serverPort;
    private int bufferSize;

    private DatagramSocket clientSocket;
    private List<Integer> list_ack;

    private long startTime;

    private int total_bytes_received;
    // private int retransmissions_received;
    
    // Constructor to initialize the Client
    public Client(String server_IP, AtomicInteger serverPort, int bufferSize) throws SocketException {
        this.server_IP = server_IP;
        this.serverPort = serverPort;
        this.bufferSize = bufferSize;

        this.clientSocket = new DatagramSocket();
        this.list_ack = new ArrayList<>();
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

        // Create buffer to store incoming data packets
        byte[] buffer = new byte[bufferSize];

        // Create a DatagramPacket to receive start time from the server
        DatagramPacket startTimePacket = new DatagramPacket(buffer, buffer.length);
        clientSocket.receive(startTimePacket);

        // Extract start time from the received message
        this.startTime = Long.parseLong(new String(startTimePacket.getData(), 0, startTimePacket.getLength()));

        // Create and reuse (in while) a DatagramPacket to receive data packet from the server
        DatagramPacket receiveData = new DatagramPacket(buffer, buffer.length); // Reuse the same buffer to store incoming data packets
            //second parameter "buffer.length" = maximum amount of data that the packet can hold

        while (true) {
            // Wait for and Receive a data packet from the server
            clientSocket.receive(receiveData);

            // Convert the received data to a string
            String received_data = new String(receiveData.getData(), 0, receiveData.getLength());

            // Check if the "finished" signal is received
            if (received_data.equals("finished")) {
                System.out.println("Client: Transfer finished");
                break;
            }

            // Extract the packet ID from the received message
            int packet_id = Integer.parseInt(received_data.substring(0, 6));

            // Print the received packet ID and synchronized time to the console
            long synchronizedTime = System.currentTimeMillis() - startTime;
            System.out.printf("Client: %.4f >> Received packet with ID: %d%n", synchronizedTime / 1000.0, packet_id);

            // Send acknowledgment for the received packet
            send_ack(packet_id, receiveData.getAddress());

            // Update the total bytes received
            total_bytes_received += received_data.length();
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
