import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class FileSender {
    private String fileName;
    private DatagramSocket clientSocket;
    private int windowSize;
    private int totalBytesSent;
    private int retransmissionsSent;
    private List<InetSocketAddress> clientAddresses;
    private Deque<Integer> sentPacketIds;
    private List<Integer> listAck;
    private boolean end;
    private int base;  // Base of the window
    private int nextSeqNum;  // Next sequence number to be sent
    private boolean[] ackReceivedArray = new boolean[windowSize];  // To keep track of received acks
    private long fileSize;
    private static final int ACK_TIMEOUT = 500;
    private Map<Integer, Long> sentTimes;
    //private static final int MAX_RETRANSMISSIONS = 5; // Maximum number of retransmissions
    private final Lock listAckLock;
    private CopyOnWriteArrayList<Integer> ListAck;

    public FileSender(String fileName, DatagramSocket clientSocket, int size, int clientNumber, List<InetSocketAddress> clientAddresses) {
        this.fileName = fileName;
        this.clientSocket = clientSocket;
        this.windowSize = size;
        this.totalBytesSent = 0;
        this.retransmissionsSent = 0;
        this.clientAddresses = clientAddresses;
        this.sentPacketIds = new ArrayDeque<>();
        this.listAck = new ArrayList<>();
        this.end = false;
        this.base = 0;
        try (FileInputStream fileInputStream = new FileInputStream(fileName)) {
            this.fileSize = fileInputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.ackReceivedArray = new boolean[windowSize];
        this.sentTimes = new HashMap<>();
        this.listAck = new CopyOnWriteArrayList<>();
        this.listAckLock = new ReentrantLock();        
    }

    private void sendPacket(int packetId, InetSocketAddress clientAddress, long startTime) throws IOException {
        byte[] data = new byte[2048];
        int bytesRead;
    
        try (FileInputStream file = new FileInputStream(fileName)) {
            long skipBytes = packetId * 2048;
            while (skipBytes > 0) {
                long skipped = file.skip(skipBytes);
                if (skipped <= 0) {
                    System.err.println("Error skipping bytes in the file.");
                    return;
                }
                skipBytes -= skipped;
            }
    
            bytesRead = file.read(data);
    
            if (bytesRead > 0) {
                byte[] packetData = new byte[bytesRead + 6];
                System.arraycopy(String.format("%06d", packetId).getBytes(), 0, packetData, 0, 6);
                System.arraycopy(data, 0, packetData, 6, bytesRead);
    
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress.getAddress(), clientAddress.getPort());
                long timeTaken = System.currentTimeMillis() - startTime;
                clientSocket.send(packet);
                sentTimes.put(packetId, System.currentTimeMillis());
    
                System.out.printf("Server: %.4f >> Data sent to client %d, Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), packetId);
    
                totalBytesSent += packetData.length;
                sentPacketIds.add(packetId);
            }
        }
    }
    
    public void sendToClient(InetSocketAddress clientAddress) throws InterruptedException {
        int clientId = clientAddress.getPort();
        System.out.println(clientId);
        System.out.printf("Server: Thread for client %d started.%n", clientId);
    
        try {
            long startTime = System.currentTimeMillis();
            int lastAckedPacketId = -1;
            int nextPacketId = 0;
    
            clientSocket.setSoTimeout(ACK_TIMEOUT);
    
            while (nextPacketId < fileSize / 2048) {
                int packetsSentInWindow = 0;
    
                while (packetsSentInWindow < windowSize && nextPacketId < fileSize / 2048) {
                    int packetId = nextPacketId;
    
                    // Only send the packet if it hasn't been acknowledged
                    if (packetId > lastAckedPacketId) {
                        sendPacket(packetId, clientAddress, startTime);
    
                        // Wait for acknowledgment for the current packet
                        try {
                            lastAckedPacketId = receiveAck(startTime, clientAddress, packetId);
                        } catch (SocketTimeoutException e) {
                            System.out.println("Acknowledgment timeout. Retransmitting packet...");
                            continue;  // Retry sending the current packet on timeout
                        }
    
                        packetsSentInWindow++;
                    }
    
                    nextPacketId++;
                }
            }
    
            end = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        System.out.printf("Server: Thread for client %d finished.%n", clientId);
    }
    
    
    
    
    
    

    
    

    private void slideWindow(long startTime, InetSocketAddress clientAddress) {
        listAckLock.lock();
        try {
            for (int i = base; i < nextSeqNum; i++) {
                if (!ackReceivedArray[i - base]) {
                    try {
                        sendPacket(i, clientAddress, startTime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            base = nextSeqNum;
        } finally {
            listAckLock.unlock();
        }
    }
    
    private void sendPackets(int startPacketId, int endPacketId, InetSocketAddress clientAddress, long startTime) throws IOException {
        for (int packetId = startPacketId; packetId <= endPacketId; packetId++) {
            sendPacket(packetId, clientAddress, startTime);
        }
    }
    
    

    private int receiveAck(long startTime, InetSocketAddress clientAddress, int lastAckedPacketId) throws IOException {
        byte[] ackMessage = new byte[2048];
        DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length);
        
        listAckLock.lock();
        try {
            // Set a timeout for acknowledgment reception
            clientSocket.setSoTimeout(ACK_TIMEOUT);
    
            clientSocket.receive(ackPacket);
    
            if (ackPacket.getLength() > 0) {
                int ackId = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));
    
                // Find the client that sent the acknowledgment
                InetSocketAddress clientAddr = findClientAddress(clientAddresses, ackPacket.getSocketAddress());
    
                // Process acknowledgment
                //lastAckedPacketId = processAck(ackId, clientAddr, startTime);
    
                long timeTaken = System.currentTimeMillis() - startTime;
                System.out.printf("Server: %.4f >> Acknowledgment received from client %d for Packet ID: %d%n", timeTaken / 1000.0, clientAddr.getPort(), ackId);
            }
        } catch (SocketTimeoutException e) {
            // Handle timeout - retransmit the packets if needed
            System.out.println("Server: Acknowledgment timeout. Retransmitting packets...");
            retransmitPackets(startTime, clientAddress, lastAckedPacketId);
        } finally {
            listAckLock.unlock();
        }
    
        return lastAckedPacketId;
    }

    private InetSocketAddress findClientAddress(List<InetSocketAddress> clientAddresses, SocketAddress address) {
        for (InetSocketAddress clientAddress : clientAddresses) {
            if (clientAddress.equals(address)) {
                return clientAddress;
            }
        }
        return null;
    }
    
    private void retransmitPackets(long startTime, InetSocketAddress clientAddress, int lastAckedPacketId) {
        for (int i = lastAckedPacketId + 1; i < nextSeqNum; i++) {
            if (!listAck.contains(i)) {
                try {
                    sendPacket(i, clientAddress, startTime);
                    retransmissionsSent++;
                    System.out.println("Server: Retransmission for packet " + i + " has been sent");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void processAck(int ackId, InetSocketAddress clientAddress, long startTime) {
        // Check if acknowledgment is from the expected client
        if (sentPacketIds.contains(ackId)) {
            // Mark acknowledgment from the specific client
            listAckLock.lock();
            try {
                int index = ackId - base;
                if (index >= 0 && index < windowSize) {
                    ackReceivedArray[index] = true;
    
                    // Check if the base packet has been acknowledged by all clients
                    boolean baseAckedByAll = true;
                    for (int i = 0; i < windowSize; i++) {
                        if (!ackReceivedArray[i]) {
                            baseAckedByAll = false;
                            break;
                        }
                    }
    
                    if (baseAckedByAll) {
                        // Slide the window
                        slideWindow(startTime, clientAddress);
                    }
                }
            } finally {
                listAckLock.unlock();
            }
    
            long timeTaken = System.currentTimeMillis() - startTime;
            System.out.printf("Server: %.4f >> Acknowledgment received from client %d for Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), ackId);
    
            // Print the current state of acknowledgment arrays
            System.out.println("Server: AckReceivedArray: " + Arrays.toString(ackReceivedArray));
            listAckLock.lock();
            try {
                System.out.println("Server: ListAck: " + listAck);
            } finally {
                listAckLock.unlock();
            }
        }
    }
    
    

    public void sendFile() {
        List<Thread> threads = new ArrayList<>();
        for (InetSocketAddress clientAddress : clientAddresses) {
            Thread thread = new Thread(() -> {
                try {
                    sendToClient(clientAddress);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
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