import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

//The Server class provides a simple implementation for a server that can accept multiple client connections using DatagramSocket.
class Server {
    private String filename;
    private float probability;
    private int window_size;
    private int bufferSize;

    DatagramSocket serverSocket;
    List<InetSocketAddress> clientAddresses;
    
    // Constructor to initialize the Server
    public Server(String filename, float probability, int window_size, int bufferSize) throws SocketException {
        this.filename = filename;
        this.probability = probability;
        this.window_size = window_size;
        this.bufferSize = bufferSize;

        this.serverSocket = new DatagramSocket();
        this.clientAddresses = new ArrayList<>();
    }

    // Method to retrieve the server IP address
    public String getServerIPAddress(String serverHostName) {
        try {
            InetAddress serverAddress = InetAddress.getByName(serverHostName);
            return serverAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null; // Return null in case of an exception
        }
    }

    // Method to retrieve the dynamically assigned port
    public int getAssignedServerPort() {
        return serverSocket.getLocalPort();
    }

    public void waitForConnections(int numberOfClients) throws IOException {

        // Loop used to continuously wait for connections from cliens until the desired numberOfClients
        while (clientAddresses.size() < numberOfClients) {

            // Create buffer to store incoming data packets
            byte[] buffer = new byte[bufferSize];
            
            // Create a DatagramPacket to receive data packet from the client
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length); 
                //second parameter "buffer.length" = maximum amount of data that the packet can hold
            
            // Wait for and receive incoming connection requests
            serverSocket.receive(receivePacket);       //= blocking call, meaning it will wait until a packet is received.
    
            // Retrieve the actual size of the data
            int dataSize = receivePacket.getLength();
    
            // Create a new array to hold exactly the received data
            byte[] actualData = new byte[dataSize];

            // Copy data from 'receivedData' to 'actualData' to ensure that only the actual received data is considered
            System.arraycopy(buffer, 0, actualData, 0, dataSize);
    
            // Convert the data into a string if necessary
            String messageString = new String(actualData);
    
            // Check if the received message is a connection request (e.g., "1")
            if (messageString.equals("1")) {
                // Print information about the connected client
                System.out.printf("Server: Client connected - IP: %s, Port: %d%n", receivePacket.getAddress().getHostAddress(), receivePacket.getPort());   
                       //e.g., Server: Client connected - IP: 127.0.0.1, Port: 56463 -> client with IP address "127.0.0.1" has connected to the server using port "56463"
                
                // Add the client's address to the list of connected clients
                clientAddresses.add((InetSocketAddress) receivePacket.getSocketAddress());

            }
        }

        System.out.println("Server: All clients are connected.");
    }

    public void sendFile_goBackN() throws InterruptedException, IOException{
        // Create a FileSender instance
        GoBackNFileSender fileSender = new GoBackNFileSender(serverSocket, clientAddresses, filename, window_size, probability, bufferSize);

        // Send the file
        fileSender.sendFile();
        
        // Send a finish signal to all connected clients
        sendFinishSignal();

        // // TODO: Rethink the design with ALTERNATIVE (applicable if the function only used once)
        // for (InetSocketAddress clientAddress : clientAddresses) {
        //     byte[] finishSignal = "finished".getBytes();
        //     DatagramPacket packet = new DatagramPacket(finishSignal, finishSignal.length, clientAddress.getAddress(), clientAddress.getPort());
        //     serverSocket.send(packet);
        // }

        // Close the server socket
        serverSocket.close();
    }

    // Method to send a finish signal to all connected clients
    public void sendFinishSignal() throws IOException {
        for (InetSocketAddress clientAddress : clientAddresses) {
            byte[] finishSignal = "finished".getBytes();
            send(finishSignal, clientAddress.getAddress(), clientAddress.getPort());
        }
    }

    // Private method to send a DatagramPacket to a specified address and port
    private void send(byte[] data, InetAddress address, int assignedPort) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, assignedPort);
        serverSocket.send(packet);
    }

}