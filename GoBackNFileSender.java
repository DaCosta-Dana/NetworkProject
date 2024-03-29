import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//File sending logic: Go-back-N Protocol
class GoBackNFileSender {
    private DatagramSocket serverSocket;
    private List<InetSocketAddress> clientAddresses;
    private String filename;
    private int window_size;
    private float probability;
    private int bufferSize;

    private long fileSize;

    // timer management
    private static final int waitFor_ACK = 500; // in milliseconds (1000 milliseconds = 1 second)
    private double startTime;
    private double endTime;             //TODO: check if it is used more than once? if not, then initialize it where it is used

    // statistics summary
    private double totalTimeSpent;      //TODO: check if it is used more than once? if not, then initialize it where it is used
    private int totalBytesSent;
    private int totalRetransmissionsSent;

    // Declare ackCountMap as a ConcurrentHashMap
    private Map<Integer, AtomicInteger> ackCountDictionary;

    //TODO: Idk what the variables below are meant for
        
        private Deque<Integer> sentPacketIDs;
        private boolean end;

        private Map<Integer, Integer> baseMap;
        private Map<Integer, Integer> nextSeqNumMap;
        
        private boolean[] ackReceivedArray; 
        private Set<Integer> acknowledgedPackets;  // To keep track of acknowledged packets
    
    // Constructor to initialize FileSender
    public GoBackNFileSender(DatagramSocket serverSocket, List<InetSocketAddress> clientAddresses, String filename, int window_size, float probability, int bufferSize){
        this.serverSocket = serverSocket;
        this.clientAddresses = clientAddresses;
        this.filename = filename;
        this.window_size = window_size;
        this.probability = probability;
        this.bufferSize = bufferSize;

        // Open a FileInputStream to read a file, estimates its size using available()
        try (FileInputStream fileInputStream = new FileInputStream(filename)) {
            fileSize = fileInputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ackCountDictionary = new ConcurrentHashMap<>();

        // statistics summary
        totalBytesSent = 0;
        totalRetransmissionsSent = 0;

        // TODO: no idea yet of its use
            // Initialize a set to keep track of acknowledged packets for each file transfer
            acknowledgedPackets = new HashSet<>();
            ackReceivedArray = new boolean[window_size]; // To keep track of received acks

            
            sentPacketIDs = new ArrayDeque<>();
            totalTimeSpent = 0;
            end = false;
            
            ackReceivedArray = new boolean[window_size];

            baseMap = new HashMap<>();
            nextSeqNumMap = new HashMap<>();

            // Assuming clientAddresses is a list of InetSocketAddress containing all your clients
            for (InetSocketAddress clientAddress : clientAddresses) {
                int client_ID = clientAddress.getPort();
                baseMap.put(client_ID, 0); // Initialize base packet ID for each client to 0
                nextSeqNumMap.put(client_ID, 0); // Initialize next sequence number for each client to 0
            }
    }

    // Public method to initiate the file transfer to all clients
    public void sendFile(int numberOfClients) {

        // Register start time
        startTime = (System.currentTimeMillis())/1000.0;

        // Create a list to store destination threads
        List<Thread> destination_threads = new ArrayList<>();

        // Launch a separate thread for each destination (clientAddresses) to send data concurrently
        for (InetSocketAddress clientAddress : clientAddresses) {
            Thread destination_thread = new Thread(() -> {

                // Send the start time to the client
                try {
                    sendStartTimeToClient(clientAddress, startTime);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Send data to the client
                sendToClient(numberOfClients, clientAddress, probability);
            });
            destination_threads.add(destination_thread);

        }

        // Start all destination threads
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
            System.out.printf("Server: Total Retransmissions Sent: %d%n", totalRetransmissionsSent);
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

        // Print information about sending the start time
        System.out.printf("Server: Start time sent to client %d%n", clientAddress.getPort());
    }

    // Private method to handle sending data to a specific client
    private void sendToClient(int numberOfClients, InetSocketAddress clientAddress, float probability) {

        // Identify the client
        int client_ID = clientAddress.getPort();
        System.out.printf("Server: Thread for Client with Port %d started.%n", client_ID);

        int oldestUnacknowledgedPacket = 0;             //initialised to 0

        // Loop that continues until all packets are acknowledged
        while (oldestUnacknowledgedPacket < fileSize / bufferSize) {
            
            // Calculates the end of the current window, to not extend beyond the total number of packets
            int windowEnd = (int) Math.min(oldestUnacknowledgedPacket + window_size, fileSize / bufferSize);

            // Loop iterates through the packets in the current window (e.g., 0, 1, 2 for window_size = 3)
            for (int packet_ID = oldestUnacknowledgedPacket; packet_ID < windowEnd; packet_ID++) {

                try {
                    // Send the packet to the client
                    sendPacketWithLoss_UDP( clientAddress, packet_ID, probability);

                    // Wait for acknowledgment for the sent packet
                    waitFor_receiveAck(clientAddress, packet_ID);       //TODO: Note this returns an int for packet_ID -> received

                    // Update acknowledgment count for the packet ID using ConcurrentHashMap TODO: make this work
                    // ackCountMap.compute(packet_ID, (k, v) -> (v == null) ? new AtomicInteger(1) : new AtomicInteger(v.get() + 1));

                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }

            // Move to the next window
            oldestUnacknowledgedPacket = windowEnd;
            windowEnd += window_size;
        }

        endTime = System.currentTimeMillis();
        totalTimeSpent += (endTime - startTime);
        end = true;

        System.out.printf("Server: Thread for client %d finished.%n", client_ID);
    }

    // Wrapper function to the UDP send function to simulate loss
    private void sendPacketWithLoss_UDP(InetSocketAddress clientAddress, int packet_ID, float probability) throws IOException {
        // Simulate packet loss based on acknowledgment probability
        if (Math.random() < (1 - probability)) {
            // If the random number is within the success probability, send the packet
            sendPacket_UDP(clientAddress, packet_ID);
        } else {
            System.out.println("Server: Packet lost for Client " + clientAddress.getPort() + " with ID " + packet_ID);
            // Simulate packet loss, you may choose to handle it as needed (e.g., log or retry)
            return;
        }
    }

    // Private method to send a specific packet to the client
    private void sendPacket_UDP(InetSocketAddress clientAddress, int packet_ID) throws IOException { 

        // FileInputStream ('file') is created to read from the specified 'filename'.
        try (FileInputStream file = new FileInputStream(filename)) {
            
            // Skip bytes in the file to reach the appropriate position for the current packet.
            long skipBytes = packet_ID * bufferSize;    // calculates the number of bytes to skip based on the packet_ID and bufferSize
            
            while (skipBytes > 0) {
                
                long skipped = file.skip(skipBytes);
                
                if (skipped <= 0) {
                    System.err.println("Error skipping bytes in the file.");
                    return;
                }
                skipBytes -= skipped;
            }

            // Initialize buffer to store the data that will be read from the file
            byte[] buffer = new byte[bufferSize];
    
            // Read data from the modified (after skip) 'file' into the 'buffer' , and the actual number of bytes read is stored in 'numberOf_bytesRead'.
            int numberOf_bytesRead = file.read(buffer);
    
            if (numberOf_bytesRead > 0) {

                // Create the packet data declared as a byte array with a length of 'bytesRead + 6' to combinie packet ID and the actual file content (data)
                byte[] packetData = new byte[numberOf_bytesRead + 6];        // additional 6 bytes are used to store the formatted packet ID (e.g., 000001) at the beginning.

                // Copy the formatted packet ID bytes to the beginning of the packetData array
                System.arraycopy(String.format("%06d", packet_ID).getBytes(), 0, packetData, 0, 6);
                    // %06d -> '0'  = character used for padding; '6' = minimum width of the field
                    // .getBytes()  = converts the formatted string into a byte array
                    // 0            = starting position in the source array (here: String.format("%06d", packetId).getBytes())
                    // packetData   = destination array (where we are copying the data)
                    // 6            = number of elements to copy (length of the formatted packet ID)

                // Copy the actual content (data) read from the file (buffer) into the packetData array starting from position 6 (after the packet ID)
                System.arraycopy(buffer, 0, packetData, 6, numberOf_bytesRead);
                    // buffer                = source array (data read from the file)
                    // 0                     = starting position in the source array
                    // 6                     = starting position in the destination array (packetData, after the packet ID)
                    // numberOf_bytesRead    = number of elements to copy (lenght of the buffer)
    
                // Create a DatagramPacket 'packet' with the packet data and send it to the client
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress.getAddress(), clientAddress.getPort());
                serverSocket.send(packet);      // Send this packet using the serverSocket
    
                // Print information about the sent packet
                double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds
                System.out.printf("%.4f >> Server: Data sent to Client %d, Packet ID: %d%n", timeTaken, clientAddress.getPort(), packet_ID);
    
                // Update statistics and tracking for the sent packet
                totalBytesSent += packetData.length;
                sentPacketIDs.add(packet_ID);                   //TODO: use?
            }
        }
    }

    // Private method to receive acknowledgment from the client
    private int waitFor_receiveAck(InetSocketAddress clientAddress, int packet_ID) throws SocketException { //TODO: understand and go through
        
        // Initialize buffer to store the ACK data
        byte[] buffer = new byte[bufferSize];

        // DatagramPacket ackPacket is created to receive the acknowledgment packet.
        DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);

        try {
            // Setting a maximum time that the server socket will block while waiting for an ACK
            serverSocket.setSoTimeout(waitFor_ACK);

            // Attempt to receive the ackPacket using the serverSocket
            serverSocket.receive(ackPacket);

        } catch (SocketTimeoutException e) {        //occurs means that no acknowledgment was received within the specified timeout (waitFor_ACK). 

            // Handle timeout - retransmit the packets if needed
            System.err.printf("Server: Wait for ACK Timeout over: ACK not received from Client %d for Packet ID %d. Retransmitting packet...%n", clientAddress.getPort(), packet_ID);

            //TODO: what does the retransmission return?
            retransmitPacketsAndWait(clientAddress, packet_ID);
            return -1;

        } catch (IOException e) {
            // Handle other IOException, e.g., log or retry
            e.printStackTrace();
        }

        // If the acknowledgment packet has a length greater than 0, it means an acknowledgment is received.
        if (ackPacket.getLength() > 0) {

            // Extracts the acknowledgment ID from the acknowledgment message
            int ACK_ID = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));    //TODO: how does the ack look like?? necessary to parse??

            // Update acknowledgment state for the specific client
            processAck(ACK_ID, clientAddress, clientAddress.getPort());     //TODO: I think unecessary

            // Print information about the sent packet
            double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds
            System.out.printf("%.4f >> Server: ACK received from Client %d for Packet ID: %d%n", timeTaken, clientAddress.getPort(), ACK_ID);
        }

        return packet_ID;           // ID of the last packet that was successfully acknowledged.

    }

    // Private method to retransmit packets in case of acknowledgment timeout
    private void retransmitPacketsAndWait(InetSocketAddress clientAddress, int packet_ID) {
        try {
            System.out.println("Server: Retransmission for Packet ID " + packet_ID + " will be sent.");
            sendPacket_UDP(clientAddress, packet_ID);
            totalRetransmissionsSent++;
    
            // Wait for acknowledgment for this retransmitted packet
            int ackId = waitFor_receiveAck(clientAddress, packet_ID);
    
            if (ackId == -1) {
                // Handle acknowledgment timeout, e.g., log or take appropriate action
                System.out.println("Server: Acknowledgment timeout for packet " + packet_ID);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    // Private method to process acknowledgment from the client
    private void processAck(int ackId, InetSocketAddress clientAddress, int client_ID) { //TODO: understand and go through
        // Check if acknowledgment is from the expected client
        if (sentPacketIDs.contains(ackId)) {

            // Mark acknowledgment from the specific client
            boolean baseAckedByAll = true;
            if (sentPacketIDs.contains(ackId)) {
                // Update acknowledgment state for the specific client
                int base = baseMap.get(client_ID);
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
                }
            }
        }
    }

}