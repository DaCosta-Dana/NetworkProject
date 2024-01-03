import random
from socket import *
import threading
import time

class UnreliableSender:
    def __init__(self, socket):
        self.socket = socket

    def send(self, data, address):
        self.socket.sendto(data, address)
        return True


class FileSender:
    def __init__(self, file_name, client_socket, client_address, size):
        self.file_name = file_name
        self.client_socket = client_socket
        self.client_address = client_address
        self.size = size
        self.total_bytes_sent = 0
        self.retransmissions_sent = 0

    def send_file(self):
        with open(self.file_name, 'rb') as file:
            last_ack_received = -1
            sent_packet_ids = []
            window_size = min(self.size, 5)
            timeout_duration = 1.0

            while True:
                for packet_id in range(last_ack_received + 1, last_ack_received + 1 + window_size):
                    data = file.read(2048)
                    if not data:
                        break

                    packet_data = {
                        'id': packet_id,
                        'packet': f"{packet_id:04d}".encode() + data
                    }

                    sent_packet_ids.append(packet_id)

                    send_time = time.time()
                    self.sender.send(packet_data['packet'], self.client_address)
                    print(f"Data sent to client, Packet ID: {packet_id}")
                    self.total_bytes_sent += len(packet_data['packet'])

                if not data:
                    break

                self.client_socket.settimeout(timeout_duration)
                while sent_packet_ids:
                    try:
                        ack_message, _ = self.client_socket.recvfrom(2048)
                        if ack_message:
                            ack_id = int(ack_message.decode())
                            print(f"Acknowledgment received for Packet ID: {ack_id}")
                            if ack_id in sent_packet_ids:
                                sent_packet_ids.remove(ack_id)
                                last_ack_received = max(last_ack_received, ack_id)

                                #decalage window
                                if last_ack_received % window_size == window_size - 1:
                                    window_size = min(self.size - last_ack_received - 1, window_size) if last_ack_received < self.size - 1 else window_size
                                    print(f"Moving window to next {window_size} packets")

                    except timeout:
                        for id in sent_packet_ids:
                            packet_data = {
                                'id': id,
                                'packet': f"{id:04d}".encode() + data
                            }
                            self.sender.send(packet_data['packet'], self.client_address)
                            print(f"Timeout: Retransmission sent to client, Packet ID: {id}")
                            self.total_bytes_sent += len(packet_data['packet'])
                            self.retransmissions_sent += 1

                self.client_socket.settimeout(None)

            print("All packets sent and acknowledged. Transfer finished.")

            print(f"Total Bytes Sent: {self.total_bytes_sent}")
            print(f"Total Retransmissions Sent: {self.retransmissions_sent}")


class Server:
    def __init__(self, server_port, client_number):
        self.server_port = server_port
        self.client_number = client_number
        self.server_socket = socket(AF_INET, SOCK_DGRAM)
        self.server_socket.bind(('', self.server_port))
        self.sender = UnreliableSender(self.server_socket)
        self.client_addresses = []

    def wait_for_connections(self):
        while len(self.client_addresses) < self.client_number:
            message, client_address = self.server_socket.recvfrom(4096)

            if message == b'1':
                print(f"Client connected: {client_address}")
                self.client_addresses.append(client_address)

        print("All clients connected.")

    def send_finish_signal(self):
        for client_address in self.client_addresses:
            self.server_socket.sendto(b'finished', client_address)

    def close_socket(self):
        self.server_socket.close()

def main():
    server = Server(12000, client_number=5)  # Set the desired number of clients
    print('The server is ready to send')

    server.wait_for_connections()

    size = 5

    threads = []
    for client_address in server.client_addresses:
        file_sender = FileSender('filename.txt', server.server_socket, client_address, size)
        file_sender.sender = server.sender

        thread = threading.Thread(target=file_sender.send_file)
        threads.append(thread)
        thread.start()

    for thread in threads:
        thread.join()

    server.send_finish_signal()
    server.close_socket()

if __name__ == "__main__":
    main()