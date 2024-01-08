import random
from socket import *
import threading
import time
from collections import deque

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
        self.sent_packet_ids = deque()
        self.list_ack = []
        self.last_ack_received = -1
        self.end = False
        self.ack_received = 0
        self.end_acks = []
        self.test = True
        

    def send_packet(self, packet_id, data, client_address, start_time):


        packet_data = {
            'id': packet_id,
            'packet': f"{packet_id:06d}".encode() + data
        }

       # {0, 000002aaaaaaaaaaaaaaaaaaaaaaaaaaaa}


        self.client_socket.sendto(packet_data['packet'], client_address)
        
        time_taken = time.time() - start_time
        print(f"{time_taken:.4f} >> Data sent to client {client_address[1]}, Packet ID: {packet_data['id']}")

        self.total_bytes_sent += len(packet_data['packet'])

        self.sent_packet_ids.append(int(packet_data['id']))

        self.client_socket.settimeout(0.5)
        
        if not data:
            print("No more datas to sent")
            self.end = True
            return
    

    def receive_acknowledgment(self, start_time, window_size, file):
        
        data = file.read(2048)
        
        while self.sent_packet_ids:

            try:

                time.sleep(0.1)
                ack_message, _ = self.client_socket.recvfrom(2048)
                time.sleep(0.05)
                
                if ack_message:

                    ack_id = int(ack_message.decode())
            

                    time_taken = time.time() - start_time
                    print(f"{time_taken:.4f} >> Acknowledgment received for Packet ID: {ack_id}")

                    if self.test == True and ack_id == self.ack_received:       
                        self.list_ack.append(ack_id)

                    if ack_id not in self.end_acks:
                        
                        if len(self.list_ack) >= len(self.client_addresses): 
            
                            while self.list_ack.count(self.ack_received) == len(self.client_addresses) :

                                if self.end == False:

                                    time_taken = time.time() - start_time
                                    print(f"{time_taken:.4f} >> All acks received for the packet ID : {self.ack_received}")
                                    self.end_acks.append(self.ack_received)

                                    while self.ack_received in self.sent_packet_ids:
                                        self.sent_packet_ids.remove(self.ack_received)

                                    time_taken = time.time() - start_time
                                    print(f"{time_taken:.4f} >> Moving window")
                                    
                                    
                                    while self.ack_received in self.list_ack:
                                        self.list_ack.remove(self.ack_received)


                                    data = file.read(2048)

                                    for client_address in self.client_addresses:
                                        self.send_packet(self.ack_received + self.size, data, client_address, start_time)
                                        
                                    self.ack_received += 1
                                
                                else:
                                    return

                    elif ack_id in self.end_acks:
        
                        time_taken = time.time() - start_time
                        print(f"{time_taken:.4f} >> Ack already received")
                        
                       

                       
            except timeout:

                self.retransmissions_sent += 1
                for client_address in self.client_addresses:
                    
                    for packet_id2 in range(self.ack_received, self.ack_received + window_size):
                                    
                            self.send_packet(packet_id2, data, client_address, start_time)

                            time_taken = time.time() - start_time
                            print(f"{time_taken:.4f} >> Retransmission sent")

                 

    def send_to_client(self, client_address):

        client_id = str(client_address[1])
      
        print(f"Thread for client {client_id} started.")

        start_time = time.time()
    
        with open(self.file_name, 'rb') as file:
            
            window_size = self.size

            while True:
                for packet_id in range(self.last_ack_received + 1, self.last_ack_received + 1 + window_size): # A CHANGER
                    data = file.read(2048)

                    if not data:
                        return

                    self.send_packet(packet_id, data, client_address, start_time)
                    
                    time.sleep(0.05)

                if not data:
                    break

                self.receive_acknowledgment(start_time, window_size, file)

                if self.end == True:
                    return

        print(f"Thread for client {client_id} finished.")

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


    
    
