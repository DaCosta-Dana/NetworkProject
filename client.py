import random
from socket import *
import time

class UnreliableReceiver:
    def __init__(self, socket, ack_probability):
        self.socket = socket
        self.ack_probability = ack_probability
        self.total_bytes_received = 0
        self.retransmissions_received = 0
        self.file_path = "test.txt"
        self.start_time = 0

    def unreliable_send_ack(self, packet_id, server_address, data):
        # Check if the probability of sending an acknowledgment is less than the given ack_probability
        if round(random.random(), 3) < self.ack_probability:
            time_taken = time.time() - self.start_time
            print(f"{time_taken:.4f} >> CLIENT : Packet with ID : {packet_id} lost")
            
            return False
                
        # Acknowledgment sent successfully
        time_taken = time.time() - self.start_time
        print(f"{time_taken:.4f} >> CLIENT : Acknowledgment for Packet ID {packet_id} sent successfully")
        acknowledgment = packet_id
            
        # Send the acknowledgment to the server
        self.socket.sendto(acknowledgment, server_address)
            
        return True
        
    def receive_data(self):

        self.start_time = time.time()

        while True:
            # Receive the modified message and the server address
            modified_message, server_address = self.socket.recvfrom(60000)

            if modified_message == b'finished':
                print("Transfer finished")
                break

            # Decode the modified message
            modified_message.decode()
            
            # Extract the packet ID and the data from the modified message
            packet_id1 = modified_message[:6]
            data = modified_message[6:]

            time_taken = time.time() - self.start_time
            print(f"{time_taken:.4f} >> CLIENT : Received packet with ID: {packet_id1}")

            # Check if the acknowledgment was sent successfully
            if not self.unreliable_send_ack(packet_id1, server_address, data):
                time_taken = time.time() - self.start_time
                print(f"{time_taken:.4f} >> CLIENT : Retransmission received")
                self.retransmissions_received += 1
                
            self.total_bytes_received += len(modified_message)

        print(f"Total Bytes Received: {self.total_bytes_received}")
        print(f"Total Retransmission Received: {self.retransmissions_received}")

        self.socket.close()
