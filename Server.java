import java.io.*;
import java.net.*;
import java.util.*;

class Server {
    
    private UnreliableSender sender;
    private int clientNumber;
    DatagramSocket serverSocket;
    List<InetSocketAddress> clientAddresses;
    
    public Server(int clientNumber) throws SocketException {
        this.clientNumber = clientNumber;
        this.serverSocket = new DatagramSocket(); //initialise serverSocket without specifying a port
        this.clientAddresses = new ArrayList<>();
        this.sender = new UnreliableSender(serverSocket);
    }

    // method to retrieve the dynamically assigned port
    public int getAssignedPort() {
        return serverSocket.getLocalPort();
    }

    private boolean send(byte[] data, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        serverSocket.send(packet);
        return true;
    }

    public void waitForConnections() throws IOException {
        while (clientAddresses.size() < clientNumber) {
            byte[] message = new byte[2048];
            DatagramPacket packet = new DatagramPacket(message, message.length);
            serverSocket.receive(packet);
    
            // Récupérer la taille réelle des données
            int dataSize = packet.getLength();
    
            // Créer un nouveau tableau pour contenir exactement les données reçues
            byte[] actualData = new byte[dataSize];
            System.arraycopy(message, 0, actualData, 0, dataSize);
    
            // Convertir les données en une chaîne si nécessaire
            String messageString = new String(actualData);
    
            if (messageString.equals("1")) {
                System.out.printf("Server: Client connected: %s%n", packet.getSocketAddress());
                clientAddresses.add((InetSocketAddress) packet.getSocketAddress());
            }
        }
        System.out.println("Server: All clients connected.");
    }

    public void sendFinishSignal() throws IOException {
        for (InetSocketAddress clientAddress : clientAddresses) {
            byte[] finishSignal = "finished".getBytes();
            send(finishSignal, clientAddress.getAddress(), clientAddress.getPort());
        }
    }

    public void closeSocket() {
        serverSocket.close();
    }

}