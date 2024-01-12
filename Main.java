import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    

    public static void main(String[] args) {

        if (args.length != 5) {
            System.out.println("""
                Usage: java Main <id_process> <number_of_processes> <filename> <probability> <window_size>
                \n<id_process>            = serverHostName (e.g, localhost)
                \n<number_of_processes>   = number of clients
                \n<filename>              = file to be send to each client
                \n<probability>           = probability of an UDP send not to be successful
                \n<window_size>           = size of the window for Go-back-N
                """);
            
            /*  Example command to run the program:

                javac Main.java
                java Main localhost 2 file.txt 0.1 3
            
                COMMAND TO DELTE ALL THE COMPILED FILES BY JAVA:
                find . -name "*.class" -exec rm -f {} \;
            */

            /* TODO: include protocol (UDP) to the command line??? */
            /* TODO: what id_process??? if not localhost, make it in the server class .getLocalHost() */

            System.exit(1);
        }

        String serverHostName = args[0];                             // id_process = localhost
        int numberOfClients = Integer.parseInt(args[1]);
        String filename = args[2];
        float probability = Float.parseFloat(args[3]);         
        int window_size = Integer.parseInt(args[4]);
        
        try {
            // Launch the server in a separate thread
            Thread server_thread = new Thread(() -> {
                try {
                    launch_server(serverHostName, numberOfClients,filename, probability, window_size);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });

            // Create a list to store client threads
            List<Thread> client_threads = new ArrayList<>();

            // Launch threads for each client
            for (int i = 0; i < numberOfClients; i++) {
                // Launch a client in a separate thread
                Thread client_thread = new Thread(() -> {
                    try {
                        launch_client(serverHostName);
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                client_threads.add(client_thread);
            }

            // Start the server thread
            server_thread.start();

            // Start all client threads
            for (Thread t : client_threads) {               
                t.start();
            }

            // Wait for the server thread
            server_thread.join();

            // Wait for all client threads to finish
            for (Thread t : client_threads) {
                t.join();
            }
        } 
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static AtomicInteger serverPort = new AtomicInteger(-1);  //shared data structure

    public static void launch_server(String serverHostName, int numberOfClients, String filename, float probability, int window_size)  throws InterruptedException, IOException {
        
        // Create a server instance
        Server server = new Server(filename, probability, window_size);

        //Get the server IP address and display it
        System.out.println("Server IP Address: " + server.getServerIPAddress(serverHostName));

        // Get the dynamically assigned server port and display it
        serverPort.set(server.getAssignedServerPort());
        System.out.println("Server Port: " + serverPort);

        // Wait for clients to connect
        System.out.println("The server is waiting for "+ numberOfClients +" clients to connect...");
        server.waitForConnections(numberOfClients);

        // Send file to clients
        System.out.println("------Ready to send data file packets:------");
        server.sendFile_goBackN();
    }

    public static void launch_client(String server_IP) throws Exception {

        try {
            // Create a Client instance and connect to Server
            Client client = new Client(server_IP, serverPort);;
            client.connectToServer();

            // Receive file from the server
            client.receiveFile();
            
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}