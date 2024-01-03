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
    def __init__(self, file_name, client_socket, size, client_number, client_addresses):
        self.file_name = file_name
        self.client_socket = client_socket
        self.size = size
        self.total_bytes_sent = 0
        self.retransmissions_sent = 0
        self.client_number = client_number
        self.ack_number = 0
        self.client_addresses = client_addresses
        self.ack_lock = threading.Lock()
        self.sent_packet_ids = []

    def send_to_client(self, client_address):
        clientid = str(client_address[1])
        print(clientid)
        print(f"Thread for client {clientid} started.")
        start_time = time.time()

        with open(self.file_name, 'rb') as file:
            last_ack_received = -1
            window_size = self.size
            timeout_duration = 2.3
            list_ack = []

            while True:
                for packet_id in range(last_ack_received + 1, last_ack_received + 1 + window_size):
                    data = file.read(2048)
                    if not data:
                        return
                    
                   
                    packet_data = {
                        'id': clientid + str(packet_id),
                        'packet': f"{clientid}".encode() + f"{str(packet_id)}".encode() + data
                    }
                    time_taken = time.time() - start_time
                    self.client_socket.sendto(packet_data['packet'], client_address)
                    print(f"{time_taken:.4f} >> Data sent to client {clientid}, Packet ID: {packet_data['id']}")
                    self.total_bytes_sent += len(packet_data['packet'])

                    with self.ack_lock:
                        self.sent_packet_ids.append(int(packet_data['id']))
                        print(self.sent_packet_ids)

                    
                    time.sleep(0.1)

                if not data:
                    break

                self.client_socket.settimeout(timeout_duration)

                while self.sent_packet_ids:
                    try:
                        ack_message, _ = self.client_socket.recvfrom(2048)
                        if ack_message:
                            ack_id = int(ack_message.decode())

                            list_ack.append(str(ack_id)[5:])
                            print(list_ack)

                            time_taken = time.time() - start_time
                            print(f"{time_taken:.4f} >> Acknowledgment received for Packet ID: {ack_id}")

                            
                            with self.ack_lock:
                                if ack_id in self.sent_packet_ids:
                                    self.sent_packet_ids.remove(ack_id)
                                    print(self.sent_packet_ids)
                                    last_ack_received = max(last_ack_received, packet_id)

                                    if not self.sent_packet_ids:
                                        window_size = min(self.size - last_ack_received - 1, window_size) if last_ack_received < self.size - 1 else window_size
                                        print(f"Moving window to next {window_size} packets")
                                        

                    except timeout:
                        with self.ack_lock:
                            for id in self.sent_packet_ids:
                                client_id1 = str(id)[:5]
                                
                                packet_data1 = {
                                    'id': str(id),
                                    'packet': f"{str(id)}".encode() + data
                                }
                                if client_id1 in str(client_address[1]):

                                    time_taken = time.time() - start_time
                                    self.client_socket.sendto(packet_data1['packet'], client_address)
                                    print(f"{time_taken:.4f} >> Timeout: Retransmission sent to client {clientid}, Packet ID: {packet_data1['id']}")
                                    self.total_bytes_sent += len(packet_data['packet'])
                                    self.retransmissions_sent += 1
                    
                    except ConnectionResetError:
                        print(f"Connection reset by client {clientid}")
                        self.client_socket.close()
                        break

                self.client_socket.settimeout(None)

        print(f"Thread for client {clientid} finished.")

    def send_file(self):
        threads = []

        for client_address in self.client_addresses:
            thread = threading.Thread(target=self.send_to_client, args=(client_address,))
            threads.append(thread)
            thread.start()

        # Wait for all threads to finish
        for thread in threads:
            thread.join()

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
            message, client_address = self.server_socket.recvfrom(2048)

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
    client_number = 2
    server = Server(12000, client_number)  # Set the desired number of clients
    print('The server is ready to send')

    server.wait_for_connections()

    size = 3

    threads = []

    file_sender = FileSender('filename.txt', server.server_socket, size, client_number, server.client_addresses)
    file_sender.sender = server.sender

    thread = threading.Thread(target=file_sender.send_file)
    threads.append(thread)

    # Start all threads outside the loop
    for thread in threads:
        thread.start()

    # Wait for all threads to finish
    for thread in threads:
        thread.join()

    server.send_finish_signal()
    server.close_socket()

if __name__ == "__main__":
    main()
    
