import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SenderLogic_GoBackN {
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
    private double endTime;           


    // statistics summary
    private double totalTimeSpent = 0;      
    private int totalBytesSent = 0;
    private int totalRetransmissionsSent = 0;

    // Define a map to store the nextSeqNum for each client
    private Map<Integer, Integer> send_baseIDMap = new ConcurrentHashMap<>();
    private Map<Integer, Integer> nextSeqIDMap = new ConcurrentHashMap<>();

    // Constructor to initialize FileSender
    public SenderLogic_GoBackN(DatagramSocket serverSocket, List<InetSocketAddress> clientAddresses, String filename, int window_size, float probability, int bufferSize){
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
                try {
                    senderLogic_goBackN(clientAddress);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
        System.out.println("Server: No more data to send.");
        System.out.println("Server: All packets sent and acknowledged. Transfer finished.");
        System.out.printf("Server: Total Bytes Sent: %d%n", totalBytesSent);
        System.out.printf("Server: Total Retransmissions Sent: %d%n", totalRetransmissionsSent);
        System.out.printf("Server: Total Time Spent: %.4f seconds%n", totalTimeSpent);

    }

    // Private method to send the start time to a specific client
    private void sendStartTimeToClient(InetSocketAddress clientAddress, double startTime) throws IOException {
        // Convert the start time to bytes
        byte[] startTimeBytes = String.valueOf(startTime).getBytes();

        // Create a DatagramPacket with the start time data and send it to the client
        DatagramPacket startTimePacket = new DatagramPacket(startTimeBytes, startTimeBytes.length, clientAddress.getAddress(), clientAddress.getPort());
        serverSocket.send(startTimePacket);

        // Print information about sending the start time
        System.out.printf("Server: Start time sent to Client %d.%n", clientAddress.getPort());
    }

    private void senderLogic_goBackN(InetSocketAddress clientAddress) throws IOException, SocketTimeoutException {

        // Identify the client
        int client_ID = clientAddress.getPort();
        System.out.printf("Server: Thread for Client with Port %d started.%n", client_ID);

        // Initialize sendBase and nextSeqNum for the current client
        send_baseIDMap.put(client_ID, 0);
        nextSeqIDMap.put(client_ID, 0);

         // Flag to indicate whether the transfer is finished
        boolean transferFinished = false;

        // Loop that continues until all packets are acknowledged
        while (!transferFinished) {
            try {

                //Two pointers initialised to keep track of
                int send_baseID = send_baseIDMap.get(client_ID);         // oldest unACKed packet
                int nextSeqID = nextSeqIDMap.get(client_ID);             // next packet to send

                // Print the state of the sliding window
                System.out.printf("Sliding Window for Client %d: [%d, %d), Next Packet to be sent: %d%n", client_ID ,send_baseID, send_baseID + window_size, nextSeqID);

                if (nextSeqID < send_baseID + window_size) {
                    sendPacketWithLoss_UDP(clientAddress, nextSeqID);
                    nextSeqIDMap.put(client_ID, nextSeqID + 1);
                }
    
                if (waitFor_receiveAck(clientAddress, send_baseID)) {
                    send_baseIDMap.put(client_ID, send_baseID + 1);
    
                    if (send_baseID == nextSeqID) {
                        // Stop the timer (by setting SO_TIMEOUT to 0, meaning no timeout)
                        serverSocket.setSoTimeout(0);

                    } else {
                        // Start the timer (measures the timeout for the packet at send_Base)
                        serverSocket.setSoTimeout(waitFor_ACK);
                    }
                }

                // Check if transfer is finished
                if (send_baseID >= fileSize / bufferSize) {
                    transferFinished = true;
                }
            
                // Handle socket timeout (timeoutOccurred)
            } catch (SocketTimeoutException e) {
                
                // Start the timer (measures the timeout for the packet at send_Base)
                serverSocket.setSoTimeout(waitFor_ACK);

                // Print information about the retransmission
                double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds
                System.err.printf("%.4f >>> Server: Timeout over - Retransmission to Client %d triggered.", timeTaken, clientAddress.getPort());

                // Retransmit all packets from send_Base to nextSeqNum-1
                for (int ID = send_baseIDMap.get(client_ID); ID < nextSeqIDMap.get(client_ID); ID++) {
                    sendPacketWithLoss_UDP(clientAddress, ID);
                     // Increment the total retransmissions count
                    totalRetransmissionsSent++;
                }
            // } finally {
  
            // }
            }
        }

        endTime = System.currentTimeMillis();
        totalTimeSpent += (endTime - startTime);

        System.out.printf("Server: Thread for client %d finished.%n", client_ID);
    }

    // Wrapper function to the UDP send function to simulate loss
    private void sendPacketWithLoss_UDP(InetSocketAddress clientAddress, int packet_ID) throws IOException {

        // Simulate packet loss based on acknowledgment probability
        if (Math.random() < (1 - probability)) {
            // If the random number is within the success probability, send the packet
            sendPacket_UDP(clientAddress, packet_ID);
        } else {
            // Print information about the loss simulation.
            double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds
            System.out.printf("%.4f >>> Server: For Client %d - Packet ID %d Lost (static lost prob = %.2f) %n", timeTaken, clientAddress.getPort(), packet_ID ,probability);

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
                System.out.printf("%.4f >>> Server: Data sent to Client %d, Packet ID: %d%n", timeTaken, clientAddress.getPort(), packet_ID);
    
                // Update statistics and tracking for the sent packet
                totalBytesSent += packetData.length;
            }
        }
    }

    // Private method to receive acknowledgment from the client
    private boolean waitFor_receiveAck(InetSocketAddress clientAddress, int packet_ID) throws IOException { 
        
        // Initialize buffer to store the ACK data
        byte[] buffer = new byte[bufferSize];

        // DatagramPacket ackPacket is created to receive the acknowledgment packet.
        DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);

        try {
            // Setting a maximum time that the server socket will block while waiting for an ACK
            serverSocket.setSoTimeout(waitFor_ACK);

            // Attempt to receive the ackPacket using the serverSocket
            serverSocket.receive(ackPacket);

            // If the ACK packet has a length greater than 0, meaning an ACK packet is received.
            if (ackPacket.getLength() > 0) {

                // Extracts the ACK ID from the ACK message (e.g., get ACK ID 0 for Packet ID 0)
                int ACK_ID = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));   
                    // ackPacket.getData()      = retrieves the raw bytes of the ACK packet into a byte array
                    // new String(...)          = converts the byte array to a string
                    // 0                        = starting index in the byte array from which to begin converting.
                    // ackPacket.getLength()    = number of bytes to convert. This ensures that only the actual data of the ACK packet is converted
                 
                
                double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds   

                //TODO: this needs to be checked
                // System.out.println(ACK_ID==packet_ID); 
                // System.out.println(ACK_ID);
               
                System.out.printf("%.4f >>> Server: ACK ID %d received from Client %d for Packet ID: %d%n", timeTaken, ACK_ID, clientAddress.getPort(), packet_ID);

                return true;
                

            } else{
                return false;
            }


        } catch (SocketTimeoutException e) {        //occurs means that no acknowledgment was received within the specified timeout (waitFor_ACK). 

            // Start the timer (measures the timeout for the packet at send_Base)
            serverSocket.setSoTimeout(waitFor_ACK);

            // Print information about the retransmission
            double timeTaken = System.currentTimeMillis()/1000.0 - startTime; // in seconds
            System.err.printf("%.4f >>> Server: Timeout over - Retransmission to Client %d triggered.%n", timeTaken, clientAddress.getPort());

            // Retransmit all packets from send_Base to nextSeqNum-1
            for (int ID = send_baseIDMap.get(clientAddress.getPort()); ID < nextSeqIDMap.get(clientAddress.getPort()); ID++) {
                sendPacketWithLoss_UDP(clientAddress, ID);
                // Increment the total retransmissions count
                totalRetransmissionsSent++;
            }
            
            return false; 

        } catch (IOException e) {
            // Handle other IOException, e.g., log or retry
            e.printStackTrace();
            return false;
        }   
    }
}

