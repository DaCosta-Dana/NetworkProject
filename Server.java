import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Server {
    private int serverPort;
    private int clientNumber;
    private DatagramSocket serverSocket;
    private List<InetSocketAddress> clientAddresses;

    public Server(int serverPort, int clientNumber) throws SocketException {
        this.serverPort = serverPort;
        this.clientNumber = clientNumber;
        this.serverSocket = new DatagramSocket(serverPort);
        this.clientAddresses = new ArrayList<>();
        this.sender = new DatagramSocket(serverSocket);
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
            if (Arrays.equals(message, "1".getBytes())) {
                System.out.printf("Client connected: %s%n", packet.getSocketAddress());
                clientAddresses.add((InetSocketAddress) packet.getSocketAddress());
            }
        }
        System.out.println("All clients connected.");
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