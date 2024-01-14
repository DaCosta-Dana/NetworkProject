import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//File sending logic: Go-back-N Protocol
class GoBackNFileSender {
    private DatagramSocket serverSocket;
    private List<InetSocketAddress> clientAddresses;
    private String filename;
    private int window_size;
    private float probability;
    private int bufferSize;

    private static final int waitFor_ACK = 500; // milliseconds
    private long fileSize;

    private double startTime;
    private double endTime;
    private double totalTimeSpent;

    private int totalBytesSent;
    private int retransmissionsSent;
    
    private Deque<Integer> sentPacketIds;
    private boolean end;
    private boolean[] ackReceivedArray = new boolean[window_size];  // To keep track of received acks
    
    private Map<Integer, Long> sentTimes;
    private final Lock listAckLock;
    private Map<InetSocketAddress, Integer> baseMap;
    private Map<InetSocketAddress, Integer> nextSeqNumMap;
    
    private Set<Integer> acknowledgedPackets;  // To keep track of acknowledged packets
    

    // Constructor to initialize FileSender
    public GoBackNFileSender(DatagramSocket serverSocket, List<InetSocketAddress> clientAddresses, String filename, int window_size, float probability, int bufferSize){
        this.serverSocket = serverSocket;
        this.clientAddresses = clientAddresses;
        this.filename = filename;
        this.window_size = window_size;
        this.probability = probability;
        this.bufferSize = bufferSize;

        try (FileInputStream fileInputStream = new FileInputStream(filename)) {
            // Open a FileInputStream to read a file, estimates its size using available()
            this.fileSize = fileInputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.totalBytesSent = 0;
        this.retransmissionsSent = 0;
        
        this.sentPacketIds = new ArrayDeque<>();
        this.totalTimeSpent = 0;
        this.end = false;
        
        this.ackReceivedArray = new boolean[window_size];
        this.sentTimes = new HashMap<>();
        this.listAckLock = new ReentrantLock(); 
        baseMap = new HashMap<>();
        nextSeqNumMap = new HashMap<>();

        for (InetSocketAddress clientAddress : clientAddresses) {
            baseMap.put(clientAddress, 0);
            nextSeqNumMap.put(clientAddress, 0);
        }
    }

    // Public method to initiate the file transfer to all clients
    public void sendFile() {
        // Register start time
        startTime = (System.currentTimeMillis())/1000.0;

        // Send the start time to each client before launching the threads
        for (InetSocketAddress clientAddress : clientAddresses) {
            try {
                sendStartTimeToClient(clientAddress, startTime);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Initialize a set to keep track of acknowledged packets for each file transfer
        acknowledgedPackets = new HashSet<>(); //TODO: what use???

        // Create a list to store destination threads
        List<Thread> destination_threads = new ArrayList<>();

        // Launch a separate thread for each destination (clientAddresses) to send data concurrently
        for (InetSocketAddress clientAddress : clientAddresses) {
            Thread destination_thread = new Thread(() -> {
                try {
                    sendToClient(clientAddress, probability);
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                }
            });
            destination_threads.add(destination_thread);

            // // Starting each destination thread immediately after creating it
            // destination_thread.start();
        }

        // Start all client threads
        for (Thread t : destination_threads) {               
            t.start();
        }

        // Wait for all threads to finish
        for (Thread t : destination_threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Register end time
        endTime = (System.currentTimeMillis())/1000.0;

        // Calculate total time
        totalTimeSpent = endTime - startTime;

        // Print statistics about the file transfer
        if (end) {
            System.out.println("Server: No more data to send.");
            System.out.println("Server: All packets sent and acknowledged. Transfer finished.");
            System.out.printf("Server: Total Bytes Sent: %d%n", totalBytesSent);
            System.out.printf("Server: Total Retransmissions Sent: %d%n", retransmissionsSent);
            System.out.printf("Server: Total Time Spent: %.4f seconds%n", totalTimeSpent);
            return;
        }
    }

    // Private method to send the start time to a specific client
    private void sendStartTimeToClient(InetSocketAddress clientAddress, double startTime) throws IOException {
        // Convert the start time to bytes
        byte[] startTimeBytes = String.valueOf(startTime).getBytes();

        // Create a DatagramPacket with the start time data and send it to the client
        DatagramPacket startTimePacket = new DatagramPacket(startTimeBytes, startTimeBytes.length, clientAddress.getAddress(), clientAddress.getPort());
        serverSocket.send(startTimePacket);

        // // Print information about sending the start time
        // System.out.printf("Server: Start time sent to client %d%n", clientAddress.getPort());
    }

    // Private method to handle sending data to a specific client
    private void sendToClient(InetSocketAddress clientAddress, float probability) throws SocketTimeoutException {

        // Identify the client
        int client_ID = clientAddress.getPort();
        System.out.printf("Server: Thread for Client with Port %d started.%n", client_ID);

        try {
            int oldestUnacknowledgedPacket = 0;             //initialised to 0

            serverSocket.setSoTimeout(waitFor_ACK);

            while (oldestUnacknowledgedPacket < fileSize / bufferSize) {
                
                // Calculate windowEnd to not extend beyond the total number of packets
                int windowEnd = (int) Math.min(oldestUnacknowledgedPacket + window_size, fileSize / bufferSize);

                // Send packets for the current window
                for (int packetId = oldestUnacknowledgedPacket; packetId < windowEnd; packetId++) {
                    // Send the packet and handle IOException
                    try {
                        // Simulate packet loss based on acknowledgment probability
                        if (Math.round(Math.random() * 1000) / 1000.0 < probability) {
                            System.out.println("Server: Packet lost for Client " + client_ID+ " with ID " + packetId);

                            // Handle packet loss by retransmitting and waiting for acknowledgment
                            retransmitPacketsAndWait(clientAddress, packetId);
                        } else {
                            // Send the packet to the client
                            sendPacket(packetId, clientAddress);
                        }

                        

                    } catch (IOException e) {
                        e.printStackTrace();
                        // Handle the exception, e.g., log or retry
                        continue;
                    }
                }

                // Wait for acknowledgment for the current packet
                for (int packetId = oldestUnacknowledgedPacket; packetId < windowEnd; packetId++) {
                    for (InetSocketAddress addr : clientAddresses) {
                        receiveAck(addr, packetId);
                    }
                }

                // Move to the next window
                oldestUnacknowledgedPacket = windowEnd;
                windowEnd += window_size;
            }

            long endTime = System.currentTimeMillis();
            totalTimeSpent += (endTime - startTime);
            end = true;
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.printf("Server: Thread for client %d finished.%n", client_ID);
    }

    // Private method to send a specific packet to the client
    private void sendPacket(int packetId, InetSocketAddress clientAddress) throws IOException {
        
        // Initialize buffer to store data from the file
        byte[] data = new byte[bufferSize];
        int bytesRead;
    
        try (FileInputStream file = new FileInputStream(filename)) {
            // Skip bytes in the file to reach the appropriate position for the current packet
            long skipBytes = packetId * bufferSize;
            while (skipBytes > 0) {
                long skipped = file.skip(skipBytes);
                if (skipped <= 0) {
                    System.err.println("Error skipping bytes in the file.");
                    return;
                }
                skipBytes -= skipped;
            }
    
            // Read data from the file into the buffer
            bytesRead = file.read(data);
    
            if (bytesRead > 0) {
                // Create the packet data by combining packet ID and file data
                byte[] packetData = new byte[bytesRead + 6];
                System.arraycopy(String.format("%06d", packetId).getBytes(), 0, packetData, 0, 6);
                System.arraycopy(data, 0, packetData, 6, bytesRead);
    
                // Create a DatagramPacket with the packet data and send it to the client
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress.getAddress(), clientAddress.getPort());
                double timeTaken = System.currentTimeMillis()/1000.0 - startTime;
                serverSocket.send(packet);
                sentTimes.put(packetId, System.currentTimeMillis());
    
                // Print information about the sent packet
                System.out.printf("%.4f >> Server: Data sent to client %d, Packet ID: %d%n", timeTaken, clientAddress.getPort(), packetId);
    
                // Update statistics and tracking for the sent packet
                totalBytesSent += packetData.length;
                sentPacketIds.add(packetId);
                acknowledgedPackets.add(packetId);                 // Register the sent packet for acknowledgment tracking
            }
        }
    }
    

    // Private method to slide the window after receiving acknowledgments
    //TODO: In the slideWindow method, update the acknowledgment state after sliding the window
    /* 
    private void slideWindow(long startTime, InetSocketAddress clientAddress, int base) {
        for (int i = base; i < nextSeqNumMap.get(clientAddress); i++) {
            int index = i - base;
            if (!ackReceivedArray[index]) {
                try {
                    sendPacket(i, clientAddress, startTime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        baseMap.put(clientAddress, nextSeqNumMap.get(clientAddress));
    }
    */

    // Private method to receive acknowledgment from the client
    private int receiveAck(InetSocketAddress clientAddress, int lastAckedPacketId) throws SocketException {
        byte[] ackMessage = new byte[bufferSize];
        DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length);
    
        listAckLock.lock();
        try {
            // Set a timeout for acknowledgment reception
            serverSocket.setSoTimeout(waitFor_ACK);
    
            try {
                serverSocket.receive(ackPacket);
            } catch (SocketTimeoutException e) {
                // Handle timeout - retransmit the packets if needed
                System.out.println("Server: Acknowledgment timeout. Retransmitting packets...");
                retransmitPacketsAndWait(clientAddress, lastAckedPacketId);
                return lastAckedPacketId;
            } catch (IOException e) {
                // Handle other IOException, e.g., log or retry
                e.printStackTrace();
                return lastAckedPacketId;
            }
    
            if (ackPacket.getLength() > 0) {
                int ackId = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));
    
                // Find the client that sent the acknowledgment
                InetSocketAddress clientAddr = findClientAddress(clientAddresses, ackPacket.getSocketAddress());
    
                // Update acknowledgment state for the specific client
                processAck(ackId, clientAddress, clientAddr);
    
                //System.out.printf("Server: %.4f >> Acknowledgment received from client %d for Packet ID: %d%n", timeTaken / 1000.0, clientAddr.getPort(), ackId);
            }
        } finally {
            listAckLock.unlock();
        }
    
        return lastAckedPacketId;
    }
    
    // Private method to find the client address in the list of client addresses
    private InetSocketAddress findClientAddress(List<InetSocketAddress> clientAddresses, SocketAddress address) {
        for (InetSocketAddress clientAddress : clientAddresses) {
            if (clientAddress.equals(address)) {
                return clientAddress;
            }
        }
        return null;
    }
    
    // Private method to retransmit packets in case of acknowledgment timeout
    private void retransmitPacketsAndWait(InetSocketAddress clientAddress, int lastAckedPacketId) {
        listAckLock.lock();
        try {
                try {
                    System.out.println("Server: Retransmission for packet " + lastAckedPacketId + " has been sent");
                    sendPacket(lastAckedPacketId, clientAddress);
                    retransmissionsSent++;
                    
    
                    // Wait for acknowledgment for this retransmitted packet with timeout
                    long timeout = System.currentTimeMillis() + waitFor_ACK;
                    while (!acknowledgedPackets.contains(lastAckedPacketId) && System.currentTimeMillis() < timeout) {
                        try {
                            // Sleep and wait for acknowledgment
                            Thread.sleep(10); // Adjust the sleep time if needed
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
    
                    if (!acknowledgedPackets.contains(lastAckedPacketId)) {
                        // Handle acknowledgment timeout, e.g., log or take appropriate action
                        System.out.println("Server: Acknowledgment timeout for packet " + lastAckedPacketId);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        } finally {
            listAckLock.unlock();
        }
    }
    
    // Private method to process acknowledgment from the client
    private void processAck(int ackId, InetSocketAddress clientAddress, InetSocketAddress actualClientAddress) {
        // Check if acknowledgment is from the expected client
        if (sentPacketIds.contains(ackId)) {
            // Mark acknowledgment from the specific client
            listAckLock.lock();
            try {
                boolean baseAckedByAll = true;
                if (sentPacketIds.contains(ackId)) {
                    // Update acknowledgment state for the specific client
                    int base = baseMap.get(actualClientAddress);
                    int index = ackId - base;
                    if (index >= 0 && index < window_size) {
                        ackReceivedArray[index] = true;
    
                        // Check if all packets in the window are acknowledged
                        for (int i = 0; i < window_size; i++) {
                            if (!ackReceivedArray[i]) {
                                baseAckedByAll = false;
                                break;
                            }
                        }
                        /* 
                        if (baseAckedByAll) {
                            // Slide the window for the specific client
                            slideWindow(startTime, actualClientAddress, base);
                        }
                        */
                    }
                }
            } finally {
                listAckLock.unlock();
            }
    
            double timeTaken = System.currentTimeMillis()/1000.0 - startTime;
            System.out.printf("%.4f >> Server: ACK received from client %d for Packet ID: %d%n", timeTaken, actualClientAddress.getPort(), ackId);
        }
    }
}