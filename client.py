import random
from socket import *

class UnreliableReceiver:
    def __init__(self, socket, ack_probability):
        self.socket = socket
        self.ack_probability = ack_probability
        self.total_bytes_received = 0
        self.retransmissions_received = 0
        
        

    def unreliable_send_ack(self, packet_id, server_address):
        if random.uniform(0, 1) < self.ack_probability:
            print(f"Packet with ID : {packet_id} lost")
            return False
        
        print(f"Acknowledgment for Packet ID {packet_id} sent successfully")
        acknowledgment = packet_id
        self.socket.sendto(acknowledgment, server_address)
        
        
        return True

    def receive_data(self):
        while True:
            modified_message, server_address = self.socket.recvfrom(2048)

            if modified_message == b'finished':
                print("Transfer finished")
                break

            modified_message.decode()
            
            packet_id1 = modified_message[:4]
            print("PACKET ID")
            print(packet_id1)
        
            
            #data = modified_message[6:]
            print(f"Received packet with ID: {packet_id1}")

            if not self.unreliable_send_ack(packet_id1, server_address):
                print("Retransmission received")
                self.retransmissions_received += 1
            self.total_bytes_received += len(modified_message)

        print(f"Total Bytes Received: {self.total_bytes_received}")
        print(f"Total Retransmission Received: {self.retransmissions_received}")

        self.socket.close()


def main():
    server_name = "localhost"
    server_port = 12000
    ack_probability = 0

    client_socket = socket(AF_INET, SOCK_DGRAM)

    message = '1'  # Indicate to the server that the client is ready to receive
    client_socket.sendto(message.encode(), (server_name, server_port))

    receiver = UnreliableReceiver(client_socket, ack_probability)
    receiver.receive_data()

if __name__ == "__main__":
    main()
