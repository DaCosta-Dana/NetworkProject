import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class FileSender {
    private String fileName;
    private DatagramSocket clientSocket;
    private int windowSize;
    private int totalBytesSent;
    private int retransmissionsSent;
    private int clientNumber;
    private List<InetSocketAddress> clientAddresses;
    private Lock ackLock;
    private Deque<Integer> sentPacketIds;
    private List<Integer> listAck;
    private int lastAckReceived;
    private boolean end;
    private int ackReceived;
    private List<Integer> endAcks;
    private boolean test;
    private FileInputStream file;
    private int base;  // Base of the window
    private int nextSeqNum;  // Next sequence number to be sent
    private boolean[] ackReceivedArray;  // To keep track of received acks
    private long fileSize;


    public FileSender(String fileName, DatagramSocket clientSocket, int size, int clientNumber, List<InetSocketAddress> clientAddresses) {
        this.fileName = fileName;
        this.clientSocket = clientSocket;
        this.windowSize = size;
        this.totalBytesSent = 0;
        this.retransmissionsSent = 0;
        this.clientNumber = clientNumber;
        this.clientAddresses = clientAddresses;
        this.ackLock = new ReentrantLock();
        this.sentPacketIds = new ArrayDeque<>();
        this.listAck = new ArrayList<>();
        this.lastAckReceived = -1;
        this.end = false;
        this.ackReceived = 0;
        this.endAcks = new ArrayList<>();
        this.test = true;
        this.base = 0;
        try (FileInputStream fileInputStream = new FileInputStream(fileName)) {
            this.fileSize = fileInputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.ackReceivedArray = new boolean[windowSize];
  
        

    }
    private void sendPacket(int packetId, InetSocketAddress clientAddress, long startTime) throws IOException {
        byte[] data = new byte[2048];
        int bytesRead;

        try (FileInputStream file = new FileInputStream(fileName)) {
            file.skip(packetId * 2048);  // Move the file pointer to the correct position
            bytesRead = file.read(data);

            if (bytesRead > 0) {
                byte[] packetData = new byte[bytesRead + 6];
                System.arraycopy(String.format("%06d", packetId).getBytes(), 0, packetData, 0, 6);
                System.arraycopy(data, 0, packetData, 6, bytesRead);

                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress.getAddress(), clientAddress.getPort());
                long timeTaken = System.currentTimeMillis() - startTime;
                clientSocket.send(packet);

                System.out.printf("Server: %.4f >> Data sent to client %d, Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), packetId);

                totalBytesSent += packetData.length;
                clientSocket.setSoTimeout(500);
                sentPacketIds.add(packetId);
            }
        }
    }

    public void sendToClient(InetSocketAddress clientAddress) {
        int clientId = clientAddress.getPort();
        System.out.println(clientId);
        System.out.printf("Thread for client %d started.%n", clientId);
    
        try {
            long startTime = System.currentTimeMillis();
            int base = 0;
            int nextSeqNum = 0;
    
            while (base * 2048 < fileSize) {
                for (int i = 0; i < windowSize && (base + i) * 2048 < fileSize; i++) {
                    sendPacket(base + i, clientAddress, startTime);
                    nextSeqNum++;
                }
    
                // Check for received acknowledgments
                receiveAck(startTime, clientAddress);
    
                // Move the base of the window
                base = nextSeqNum;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        System.out.printf("Thread for client %d finished.%n", clientId);
    }
    

    private void slideWindow(long startTime, InetSocketAddress clientAddress){
        // Move the base of the window
        for (int i = 0; i < clientAddresses.size(); i++) {
            try {
                sendPacket(base, clientAddress, startTime);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        nextSeqNum++;
        base = nextSeqNum - windowSize;


    }

    private boolean allClientsAcked(int ackId) {
        // Count how many times ackId appears in listAck
        long count = listAck.stream().filter(id -> id == ackId).count();
    
        // Check if the count is equal to the number of clients
        return count == clientAddresses.size();
    }
    

    // Modified receiveAck method
    public void receiveAck(long startTime, InetSocketAddress clientAddress) throws IOException {
        while (base < nextSeqNum) {
            byte[] ackMessage = new byte[2048];
            DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length);
            clientSocket.receive(ackPacket);

            if (ackPacket.getLength() > 0) {
                int ackId = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));

                // Process acknowledgment
                processAck(ackId, clientAddress, startTime);

                
            }
        }
    }

    private void processAck(int ackId, InetSocketAddress clientAddress, long startTime) {
        // Check if acknowledgment is from the expected client
        if (sentPacketIds.contains(ackId)) {
            // Mark acknowledgment from the specific client
            listAck.add(ackId);

            // Check if acknowledgments are received from all clients for the current packet
            if (allClientsAcked(ackId)) {
                // Remove the ackId for each client
                for (int i = 0; i < clientAddresses.size(); i++) {
                    listAck.remove(ackId);
                }
                
                // Slide the window
                slideWindow(startTime, clientAddress);
            }

            long timeTaken = System.currentTimeMillis() - startTime;
            System.out.printf("Server: %.4f >> Acknowledgment received from client %d for Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), ackId);
        }
        
        // Print the current state of acknowledgment arrays
        System.out.println("Server: AckReceivedArray: " + Arrays.toString(ackReceivedArray));
        System.out.println("Server: ListAck: " + listAck);
    }

    

    public void sendFile() {
        List<Thread> threads = new ArrayList<>();
        for (InetSocketAddress clientAddress : clientAddresses) {
            Thread thread = new Thread(() -> sendToClient(clientAddress));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (end) {
            System.out.println("Server: No more data to send.");
            System.out.println("Server: All packets sent and acknowledged. Transfer finished.");
            System.out.printf("Server: Total Bytes Sent: %d%n", totalBytesSent);
            System.out.printf("Server: Total Retransmissions Sent: %d%n", retransmissionsSent);
            return;
        }

        
    }
}