import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void start_server(int port, int client_number, int size, String filename) throws InterruptedException, IOException {
        
            Server server = new Server(port, client_number);
            System.out.println("The server is waiting for clients to connect");
            server.waitForConnections();

            List<Thread> threads = new ArrayList<>();
            FileSender fileSender = new FileSender(filename, server.serverSocket, size, client_number, server.clientAddresses);

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

    public static void start_client(String server_name, int server_port, float ack_probability) throws Exception {
        try {
            DatagramSocket clientSocket = new DatagramSocket();
            String message = "1";
            byte[] sendData = message.getBytes();
            InetAddress serverAddress = InetAddress.getByName(server_name);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, server_port);
            clientSocket.send(sendPacket);
            Receiver receiver = new Receiver(clientSocket, ack_probability);
            receiver.receive_data();

        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 7) {
            System.out.println("Usage: java main.java <port> <client_number> <size> <server_name> <server_port> <ack_probability> <filename>");
            
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        int client_number = Integer.parseInt(args[1]);
        int size = Integer.parseInt(args[2]);
        String server_name = args[3];
        int server_port = Integer.parseInt(args[4]);
        float ack_probability = Float.parseFloat(args[5]);
        String filename = args[6];
        try {
            Thread server_thread = new Thread(() -> {
                try {
                    start_server(port, client_number, size, filename);
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });

            List<Thread> client_threads = new ArrayList<>();
            for (int i = 0; i < client_number; i++) {
                Thread client_thread = new Thread(() -> {
                    try {
                        start_client(server_name, server_port, ack_probability);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
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


