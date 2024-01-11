import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void start_server(int serverListeningPort, int client_number, int size, String filename, float ack_probability) throws InterruptedException, IOException {
        
            Server server = new Server(serverListeningPort, client_number);
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

    public static void start_client(String server_name, int serverConnectionPort) throws Exception {
        try {
            Thread.sleep(1000);
            DatagramSocket clientSocket = new DatagramSocket();
            String message = "1";
            byte[] sendData = message.getBytes();
            InetAddress serverAddress = InetAddress.getByName(server_name); // Set to "localhost"
            int port = serverConnectionPort; // Set to 12000
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            clientSocket.send(sendPacket);
            Client receiver = new Client(clientSocket, serverConnectionPort);
            receiver.receive_data();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        if (args.length != 7) {
            System.out.println("Usage: java Main <serverListeningPort> <numberOfClients> <window_size> <server_name> <serverConnectionPort> <ack_probability> <filename>");
            
            /*  COPY TO TERMINAL
                javac main.java
                java Main 12000 2 3 localhost 12000 0.1 file.txt
             */

             /* TODO: do we need the port? */
             /* TODO: include protocol (UDP) to the command line??? */

            System.exit(1);
        }


        int serverListeningPort = Integer.parseInt(args[0]);
        int numberOfClients = Integer.parseInt(args[1]);
        int window_size = Integer.parseInt(args[2]);
        String server_name = args[3];                           // id_process
        int serverConnectionPort = Integer.parseInt(args[4]);
        float ack_probability = Float.parseFloat(args[5]);
        String filename = args[6];
        
        
        try {
            Thread server_thread = new Thread(() -> {
                try {
                    start_server(serverListeningPort, numberOfClients, window_size, filename, ack_probability);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });

            List<Thread> client_threads = new ArrayList<>();
            for (int i = 0; i < numberOfClients; i++) {
                Thread client_thread = new Thread(() -> {
                    try {
                        start_client(server_name, serverConnectionPort);
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


