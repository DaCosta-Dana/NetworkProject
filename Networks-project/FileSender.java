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
    private List<InetSocketAddress> clientAddresses;
    private Deque<Integer> sentPacketIds;
    private List<Integer> listAck;
    private boolean end;
    private int base;  // Base of the window
    private int nextSeqNum;  // Next sequence number to be sent
    private boolean[] ackReceivedArray;  // To keep track of received acks
    private long fileSize;
    private static final int ACK_TIMEOUT = 500;
    private Map<Integer, Long> sentTimes;
    private static final int MAX_RETRANSMISSIONS = 5; // Maximum number of retransmissions


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
                // Store the time this packet was sent
                sentTimes.put(packetId, System.currentTimeMillis());

                System.out.printf("Server: %.4f >> Data sent to client %d, Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), packetId);

                totalBytesSent += packetData.length;
                clientSocket.setSoTimeout(500);
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
    
            while (base < fileSize / 2048) {
                int packetsSentInWindow = 0;
    
                while (packetsSentInWindow < windowSize && base + packetsSentInWindow < fileSize / 2048) {
                    sendPacket(base + packetsSentInWindow, clientAddress, startTime);
                    packetsSentInWindow++;
                }
    
                // Wait for a short duration before checking for acknowledgments
                Thread.sleep(50);
    
                // Check for received acknowledgments
                receiveAck(startTime, clientAddress);
    
                // Move the base of the window
                base += packetsSentInWindow;
                nextSeqNum = base + windowSize;
    
                // If not all clients have acknowledged, wait before sending the next packets
                while (!allClientsAcked(base)) {
                    Thread.sleep(50);
                }
            }
            end = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        System.out.printf("Server: Thread for client %d finished.%n", clientId);
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

    // Add a new method to retransmit packets
    private void retransmitPackets(long startTime, InetSocketAddress clientAddress) {
        for (int i = base; i < nextSeqNum; i++) {
            if (!listAck.contains(i)) {
                try {
                    // Check if the packet was sent before and not acknowledged
                    if (sentTimes.containsKey(i)) {
                        long timeSinceSent = System.currentTimeMillis() - sentTimes.get(i);
                        // Retransmit the packet if the timeout has passed
                        if (timeSinceSent >= ACK_TIMEOUT) {
                            sendPacket(i, clientAddress, startTime);
                            retransmissionsSent++;
                            System.out.println("Server: Retransmission for packet" + i + "has been send");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    

    public void receiveAck(long startTime, InetSocketAddress clientAddress) throws IOException {
        while (base < nextSeqNum) {
            byte[] ackMessage = new byte[2048];
            DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length);
    
            try {
                // Set a timeout for acknowledgment reception
                clientSocket.setSoTimeout(ACK_TIMEOUT);
    
                clientSocket.receive(ackPacket);
    
                if (ackPacket.getLength() > 0) {
                    int ackId = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));
    
                    // Process acknowledgment
                    processAck(ackId, clientAddress, startTime);
    
                    // Check if acknowledgments are received from all clients for the current packet
                    if (allClientsAcked(ackId)) {
                        // Slide the window
                        slideWindow(startTime, clientAddress);
                    }
                }
            } catch (SocketTimeoutException e) {
                // Handle timeout - retransmit the packets
                System.out.println("Server: Acknowledgment timeout. Retransmitting packets...");
                retransmitPackets(startTime, clientAddress);
            }
        }
        
        // Check if all clients have acknowledged the last packet in the window
        while (!allClientsAcked(base)) {
            // Wait until all clients acknowledge the last packet
        }
    
        // Move the window
        slideWindow(startTime, clientAddress);
    }
    

// Modify the processAck method to use an iterator and remove elements safely
    private void processAck(int ackId, InetSocketAddress clientAddress, long startTime) {
    // Check if acknowledgment is from the expected client
    if (sentPacketIds.contains(ackId)) {
        // Mark acknowledgment from the specific client
        listAck.add(ackId);

        // Check if acknowledgments are received from all clients for the current packet
        if (allClientsAcked(ackId)) {
            // Remove the ackId for each client using an iterator
            Iterator<Integer> iterator = listAck.iterator();
            while (iterator.hasNext()) {
                int id = iterator.next();
                if (id == ackId) {
                    iterator.remove();
                }
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