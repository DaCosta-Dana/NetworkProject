import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

//The Server class provides a simple implementation for a server that can accept multiple client connections using DatagramSocket.
class Server {
    private int numberOfClients;
    DatagramSocket serverSocket;
    List<InetSocketAddress> clientAddresses;
    
    // Constructor to initialize the Server
    public Server(int numberOfClients) throws SocketException {
        this.numberOfClients = numberOfClients;
        this.serverSocket = new DatagramSocket();
        this.clientAddresses = new ArrayList<>();
    }

    // Method to retrieve the dynamically assigned port
    public int getAssignedPort() {
        return serverSocket.getLocalPort();
    }

    // Private method to send a DatagramPacket to a specified address and port
    private boolean send(byte[] data, InetAddress address, int assignedPort) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, assignedPort);
        serverSocket.send(packet);
        return true;
    }

    // Method to wait for connections from clients
    public void waitForConnections() throws IOException {

        // Loop used to continuously wait for connections from cliens until the desired numberOfClients
        while (clientAddresses.size() < numberOfClients) {
            byte[] message = new byte[2048];
            DatagramPacket packet = new DatagramPacket(message, message.length);
            
            // Receive incoming connection requests
            serverSocket.receive(packet);
    
            // Retrieve the actual size of the data
            int dataSize = packet.getLength();
    
            // Create a new array to hold exactly the received data
            byte[] actualData = new byte[dataSize];
            System.arraycopy(message, 0, actualData, 0, dataSize);
    
            // Convert the data into a string if necessary
            String messageString = new String(actualData);
    
            // Check if the received message is a connection request (e.g., "1")
            if (messageString.equals("1")) {
                // Print information about the connected client
                System.out.printf("Server: Client connected - IP: %s, Port: %d%n", packet.getAddress().getHostAddress(), packet.getPort());   
                       //e.g., Server: Client connected - IP: 127.0.0.1, Port: 56463 -> client with IP address "127.0.0.1" has connected to the server using port "56463"
                
                // Add the client's address to the list of connected clients
                clientAddresses.add((InetSocketAddress) packet.getSocketAddress());

            }
        }
        
        System.out.println("Server: All clients connected.");
    }

    // Method to send a finish signal to all connected clients
    public void sendFinishSignal() throws IOException {
        for (InetSocketAddress clientAddress : clientAddresses) {
            byte[] finishSignal = "finished".getBytes();
            send(finishSignal, clientAddress.getAddress(), clientAddress.getPort());
        }
    }

    // Method to close the server socket
    public void closeSocket() {
        serverSocket.close();
    }

}