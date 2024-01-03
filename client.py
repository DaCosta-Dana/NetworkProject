import random
import socket
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
        

    def unreliable_send_ack(self, packet_id, server_address):
        if random.uniform(0, 1) < self.ack_probability:
            print(f"Packet with ID : {packet_id} lost")
            return False
        
        print(f"Acknowledgment for Packet ID {packet_id} sent successfully")
        acknowledgment = packet_id
        self.socket.sendto(acknowledgment, server_address)
        
        return True

    def receive_data(self):
        try:
            # Set buffer size outside the loop
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 8192)  # Set buffer size to 8192 bytes
            
            while True:
                modified_message, server_address = self.socket.recvfrom(8192)  # Increase buffer size

                if modified_message == b'finished':
                    print("Transfer finished")
                    self.clients_finished += 1
                    if self.clients_finished == self.total_clients:
                        self.close_socket()
                        break

                    continue

                modified_message.decode()

                packet_id1 = modified_message[:6]
                packet_id2 = int(packet_id1)

                print(f"Received packet with ID: {packet_id1}")

                # Obtain the server address correctly
                server_address = (server_address[0], server_address[1])

                if not self.unreliable_send_ack(packet_id1, server_address):
                    print("Retransmission received")
                    self.retransmissions_received += 1
                self.total_bytes_received += len(modified_message)

        except OSError as e:
            if not self.socket_closed:
                print(f"Error in received data from a Client: {e}")
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
    ack_probability = 0.2
    num_clients = 2
    receivers = []

    try:
        for client_id in range (1, num_clients + 1):
            client_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            message = '1'  # Indicate to the server that the client is ready to receive
            client_socket.sendto(message.encode(), (server_name, server_port))

            receiver = UnreliableReceiver(client_socket, ack_probability, num_clients)
            receivers.append(receiver)

            thread = threading.Thread(target=receiver.receive_data)
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
