import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class FileSender {
    private String fileName;
    private DatagramSocket clientSocket;
    private int size;
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

    public FileSender(String fileName, DatagramSocket clientSocket, int size, int clientNumber, List<InetSocketAddress> clientAddresses) {
        this.fileName = fileName;
        this.clientSocket = clientSocket;
        this.size = size;
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
        try {
            this.file = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }
    public void sendPacket(int packetId, byte[] data, InetSocketAddress clientAddress, long startTime) throws IOException {
        byte[] packetData = new byte[data.length + 6];
        System.arraycopy(String.format("%06d", packetId).getBytes(), 0, packetData, 0, 6);
        System.arraycopy(data, 0, packetData, 6, data.length);
    
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientAddress.getAddress(), clientAddress.getPort());
        long timeTaken = System.currentTimeMillis() - startTime;
        clientSocket.send(packet);
    
        System.out.printf("%.4f >> Data sent to client %d, Packet ID: %d%n", timeTaken / 1000.0, clientAddress.getPort(), packetId);
    
        totalBytesSent += packetData.length;
        clientSocket.setSoTimeout(500);
        sentPacketIds.add(packetId);
    }

    public void receiveAck(long startTime, int windowSize, FileInputStream file) throws IOException {
        byte[] data = new byte[2048];
        double timeout = 0.1 * windowSize;
        long timeStart = System.currentTimeMillis();
        while (!sentPacketIds.isEmpty()) {
            try {
                Thread.sleep(100);
                byte[] ackMessage = new byte[2048];
                DatagramPacket ackPacket = new DatagramPacket(ackMessage, ackMessage.length);
                clientSocket.receive(ackPacket);
                Thread.sleep(50);
                if (ackPacket.getLength() > 0) {
                    int ackId = Integer.parseInt(new String(ackPacket.getData(), 0, ackPacket.getLength()));

                    clientSocket.setSoTimeout(50);
                    long timeTaken = System.currentTimeMillis() - startTime;
                    System.out.printf("%.4f >> Acknowledgment received for Packet ID: %d%n", timeTaken / 1000.0, ackId);
                    if (ackId == ackReceived) {
                        listAck.add(ackId);
                    }
                    System.out.println(listAck);
                    if (!endAcks.contains(ackId)) {
                        if (listAck.size() >= clientAddresses.size()) {
                            while (listAck.stream().filter(a -> a == ackReceived).count() == clientAddresses.size()) {
                                if (!end) {
                                    System.out.printf("All acks received for the packet ID: %d%n", ackReceived);
                                    endAcks.add(ackReceived);
                                    while (sentPacketIds.contains(ackReceived)) {
                                        sentPacketIds.remove(ackReceived);
                                    }
                                    System.out.println("MOVING WINDOW");
                                    while (listAck.contains(ackReceived)) {
                                        listAck.remove(Integer.valueOf(ackReceived));
                                    }
                                    System.out.println("LIST ACK");
                                    System.out.println(listAck);
                                    file.read(data);
                                    for (InetSocketAddress clientAddress : clientAddresses) {
                                        sendPacket(ackReceived + size, data, clientAddress, startTime);
                                    }
                                    System.out.println("TEST");
                                    ackReceived++;
                                    timeStart = System.currentTimeMillis();
                                } else {
                                    return;
                                }
                            }
                        }
                    } else if (endAcks.contains(ackId)) {
                        System.out.println("Ack already received");
                        //int start = 0;
                    }
                }
            } catch (SocketTimeoutException e) {
                retransmissionsSent++;
                for (InetSocketAddress clientAddress : clientAddresses) {
                    for (int packetId2 = ackReceived; packetId2 < ackReceived + windowSize; packetId2++) {
                        sendPacket(packetId2, data, clientAddress, startTime);
                        System.out.println("RETRANSMISSION");
                        timeStart = System.currentTimeMillis();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void sendToClient(InetSocketAddress clientAddress) {
        int clientId = clientAddress.getPort();
        System.out.println(clientId);
        System.out.printf("Thread for client %d started.%n", clientId);
        long startTime = System.currentTimeMillis();
    
        try (FileInputStream file = new FileInputStream(fileName)) {
            int windowSize = size;
            byte[] data = new byte[2048];
            int bytesRead;
    
            while (!end) {
                for (int packetId = lastAckReceived + 1; packetId <= lastAckReceived + windowSize; packetId++) {
                    bytesRead = file.read(data);
    
                    if (bytesRead == -1) {
                        end = true;
                        break;  // Break the loop if end of file is reached
                    }
    
                    sendPacket(packetId, Arrays.copyOf(data, bytesRead), clientAddress, startTime);
                    Thread.sleep(50);
                }
    
                receiveAck(startTime, windowSize, file);
    
                if (end) {
                    // Send a final acknowledgment to confirm that the end has been reached
                    for (int packetId = lastAckReceived + 1; packetId <= lastAckReceived + windowSize; packetId++) {
                        sendPacket(packetId, new byte[0], clientAddress, startTime);
                    }
                    break;  // Break the loop after sending the end signal
                }
            }
    
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    
        System.out.printf("Thread for client %d finished.%n", clientId);
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
            System.out.println("No more data to send.");
            System.out.println("All packets sent and acknowledged. Transfer finished.");
            System.out.printf("Total Bytes Sent: %d%n", totalBytesSent);
            System.out.printf("Total Retransmissions Sent: %d%n", retransmissionsSent);
            return;
        }

        
    }
}