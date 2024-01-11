import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static AtomicInteger assignedPort = new AtomicInteger(-1); //shared data structure


    // public static void start_server(int serverListeningPort, int client_number, int size, String filename, float ack_probability) throws InterruptedException, IOException {
    public static void start_server(int client_number, int size, String filename, float ack_probability)  throws InterruptedException, IOException {
         

            Server server = new Server(client_number);

            assignedPort.set(server.getAssignedPort());
            System.out.println("The assigned port is " + assignedPort);

            System.out.println("The server is waiting for clients to connect");
            server.waitForConnections();

            List<Thread> threads = new ArrayList<>();
            FileSender fileSender = new FileSender(filename, server.serverSocket, size, client_number, server.clientAddresses, ack_probability);

            Thread thread = new Thread(() -> fileSender.sendFile());

            
            threads.add(thread);

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }
            server.sendFinishSignal();
            server.closeSocket();
   
    }

    // public static void start_client(String server_name, int serverConnectionPort) throws Exception {
    public static void start_client(String server_name) throws Exception {
        try {
            Thread.sleep(1000);
            DatagramSocket clientSocket = new DatagramSocket();
            String message = "1";
            byte[] sendData = message.getBytes();
            InetAddress serverAddress = InetAddress.getByName(server_name); // Set to "localhost"
            int port = assignedPort.get(); // Access the assigned port from the shared variable
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            clientSocket.send(sendPacket);
            Client receiver = new Client(clientSocket, port);
            receiver.receive_data();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        if (args.length != 7) {
            System.out.println("Usage: java Main <server_id> <serverListeningPort> <numberOfClients> <serverConnectionPort> <filename> <probability> <window_size>");
            
            /*  COPY TO TERMINAL
                javac Main.java
                java Main localhost 12000 2 12000 file.txt 0.1 3
             */

             /* TODO: do we need the port? */
             /* TODO: include protocol (UDP) to the command line??? */

            System.exit(1);
        }


        String server_id = args[0];                             // id_process = localhost
        // int serverListeningPort = Integer.parseInt(args[1]);
        
        int numberOfClients = Integer.parseInt(args[2]);
        // int serverConnectionPort = Integer.parseInt(args[3]);

        String filename = args[4];
        float probability = Float.parseFloat(args[5]);          // probability of an UDP send not to be successful to simulate network errors
        int window_size = Integer.parseInt(args[6]);
       
        // // for debugging
        // String server_id = "localhost";
        // int numberOfClients = 2;
        // String filename = "file.txt";
        // float probability = 0.1f;
        // int window_size = 3;
   
        
        try {


            Thread server_thread = new Thread(() -> {
                try {
                    // start_server(serverListeningPort, numberOfClients, window_size, filename, probability);
                    start_server(numberOfClients, window_size, filename, probability);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });


            List<Thread> client_threads = new ArrayList<>();
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

            server_thread.start();
            for (Thread t : client_threads) {               
                t.start();
            }

            server_thread.join();
            for (Thread t : client_threads) {
                t.join();
            }
        } 
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}