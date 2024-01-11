import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static AtomicInteger assignedPort = new AtomicInteger(-1);  //shared data structure

    public static void main(String[] args) {

        if (args.length != 5) {
            System.out.println("""
                Usage: java Main <id_process> <number_of_processes> <filename> <probability> <window_size>
                \n<id_process>            = server ID/name 
                \n<number_of_processes>   = number of clients
                \n<filename>              = file to be send to each client
                \n<probability>           = probability of an UDP send not to be successful
                \n<window_size>           = size of the window for Go-back-N
                """);
            
            /*  Example command to run the program:

                javac Main.java
                java Main localhost 2 file.txt 0.1 3
            
            */

            /* TODO: include protocol (UDP) to the command line??? */

            System.exit(1);
        }

        String server_id = args[0];                             // id_process = localhost
        int numberOfClients = Integer.parseInt(args[1]);
        String filename = args[2];
        float probability = Float.parseFloat(args[3]);         
        int window_size = Integer.parseInt(args[4]);
       
        // // for debugging
        // String server_id = "localhost";
        // int numberOfClients = 2;
        // String filename = "file.txt";
        // float probability = 0.1f;
        // int window_size = 3;
        
        try {

            // Start the server in a separate thread
            Thread server_thread = new Thread(() -> {
                try {
                    start_server(numberOfClients, window_size, filename, probability);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });

            // Create a list to store client threads
            List<Thread> client_threads = new ArrayList<>();

            // Start threads for each client
            for (int i = 0; i < numberOfClients; i++) {
                Thread client_thread = new Thread(() -> {
                    try {
                        start_client(server_id);
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                client_threads.add(client_thread);
            }

            // Start the server thread and all client threads
            server_thread.start();
            for (Thread t : client_threads) {               
                t.start();
            }

            // Wait for the server thread and all client threads to finish
            server_thread.join();
            for (Thread t : client_threads) {
                t.join();
            }
        } 
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void start_server(int numberOfClients, int size, String filename, float probability)  throws InterruptedException, IOException {
        
        // Create a server instance
        Server server = new Server(numberOfClients);

        // Get the assigned port and display it
        assignedPort.set(server.getAssignedPort());
        System.out.println("The assigned port is " + assignedPort);

        // Wait for clients to connect
        System.out.println("The server is waiting for clients to connect...");
        server.waitForConnections();

        // Initialize a list to store threads
        List<Thread> threads = new ArrayList<>();

        // Create a FileSender instance for sending the file
        GoBackNFileSender fileSender = new GoBackNFileSender(filename, server.serverSocket, size, numberOfClients, server.clientAddresses, probability);

        // Create a thread for sending the file
        Thread thread = new Thread(() -> fileSender.sendFile());

        // Add the thread to the list
        threads.add(thread);

        // Start all threads
        for (Thread t : threads) {
            t.start();
        }

        // Wait for all threads to finish
        for (Thread t : threads) {
            t.join();
        }

        // Send a finish signal and close the server socket
        server.sendFinishSignal();
        server.closeSocket();

    }

    public static void start_client(String server_id) throws Exception {
        try {
            // Pause for 1 second to allow the server to start
            Thread.sleep(1000);

            // Create a DatagramSocket for the client
            DatagramSocket clientSocket = new DatagramSocket();

            // Prepare the message to send to the server
            String message = "1";
            byte[] sendData = message.getBytes();

            // Get the InetAddress for the server using the server_id (e.g. localhost)
            InetAddress serverAddress = InetAddress.getByName(server_id);
            
            // Access the assigned port from the shared variable
            int getAssignedPort = assignedPort.get();

            // Create a DatagramPacket to send the data to the server
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, getAssignedPort);
            
            // Send the packet to the server
            clientSocket.send(sendPacket);

            // Create a client instance to receive data from the server
            Client receiver = new Client(clientSocket, getAssignedPort);

            // Receive data from the server
            receiver.receive_data();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}