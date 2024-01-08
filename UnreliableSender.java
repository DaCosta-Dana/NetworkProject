/*import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class UnreliableSender {
    private DatagramSocket socket;

    public UnreliableSender(DatagramSocket socket) {
        this.socket = socket;
    }

    public boolean send(byte[] data, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
        return true;
    }
}