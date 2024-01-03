import random
from socket import *
import threading

class UnreliableReceiver:
    def __init__(self, socket, ack_probability, total_clients):
        self.socket = socket #socket(AF_INET, SOCK_DGRAM)
        self.ack_probability = ack_probability
        self.total_bytes_received = 0
        self.retransmissions_received = 0
        self.total_clients = total_clients
        self.clients_finished = 0
        self.socket_closed = False

    def unreliable_send_ack(self, packet_id, server_address, client_id):
        if random.uniform(0, 1) < self.ack_probability:
            print(f"Packet with ID : {client_id}.{packet_id} lost")
            return False


        acknowledgment = str(packet_id).encode()
        self.socket.sendto(acknowledgment, server_address)
        print(f"Received packet with ID: {client_id}.{packet_id}")
        print(f"Acknowledgment for Packet ID {client_id}.{packet_id} sent successfully")
        return True

    def receive_data(self, client_id):
        try:
            while True:
                modified_message, server_address = self.socket.recvfrom(4096)  # Adjusted buffer size

                if modified_message == b'finished':
                    print(f"Transfer finished for Client {client_id}")
                    self.clients_finished += 1
                    if self.clients_finished == self.total_clients:
                        self.close_socket()
                        break
                    continue

                packet_id = int(modified_message[:4])
                data = modified_message[4:]

                if not self.unreliable_send_ack(packet_id, server_address):
                    print("Retransmission received")
                    self.retransmissions_received += 1
                self.total_bytes_received += len(modified_message)

        except OSError as e:
            if not self.socket_close:
                print(f"Error in receive_data for Client {client_id}: {e}")
                self.close_socket()

        print(f"Total Bytes Received: {self.total_bytes_received}")
        print(f"Total Retransmission Received: {self.retransmissions_received}")

    def close_socket(self):
        if not self.socket_closed:
            self.socket.close()
            self.socket_closed = True


def main():
    server_name = "localhost"
    server_port = 12000
    ack_probability = 0
    num_clients = 5

    receivers = []

    try: 
        for client_id in range(1, num_clients + 1):
            client_socket = socket(AF_INET, SOCK_DGRAM)

            message = '1'  # Indicate to the server that the client is ready to receive
            client_socket.sendto(message.encode(), (server_name, server_port))

            receiver = UnreliableReceiver(client_socket, ack_probability, num_clients)
            receivers.append(receiver)

            thread = threading.Thread(target=receiver.receive_data, args=(client_id,))
            thread.start()

        # Wait for all threads to finish
        for thread in threading.enumerate():
            if thread != threading.current_thread():
                thread.join()

    except Exception as e:
        print(f"An error occurred: {e}")
    
    finally:
        # Close sockets for all UnreliableReceiver objects
        for receiver in receivers:
            receiver.close_socket()

if __name__ == "__main__":
    main()
