import sys
import threading
from server import Server  # Assuming you have a module named server_module with Server class
from client import UnreliableReceiver  # Assuming you have a module named client_module with UnreliableReceiver class
from server import FileSender  # Assuming you have a module named file_sender_module with FileSender class
from socket import socket, AF_INET, SOCK_DGRAM

def start_server(port, client_number, size, filename):
    server = Server(port, client_number)
    print('The server is ready to send')

    server.wait_for_connections()

    threads = []

    file_sender = FileSender(filename, server.server_socket, size, client_number, server.client_addresses)
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

def start_client(server_name, server_port, ack_probability):
    client_socket = socket(AF_INET, SOCK_DGRAM)

    message = '1'  # Indicate to the server that the client is ready to receive
    client_socket.sendto(message.encode(), (server_name, server_port))

    receiver = UnreliableReceiver(client_socket, ack_probability)
    receiver.receive_data()

def main():
    if len(sys.argv) != 8:
        print("Usage: python main.py <port> <client_number> <size> <server_name> <server_port> <ack_probability> <filename>")
        sys.exit(1)

    port = int(sys.argv[1])
    client_number = int(sys.argv[2])
    size = int(sys.argv[3])
    server_name = sys.argv[4]
    server_port = int(sys.argv[5])
    ack_probability = float(sys.argv[6])
    filename = sys.argv[7]

    server_thread = threading.Thread(target=start_server, args=(port, client_number, size, filename))


    client_threads = []
    for i in range(client_number):
        client_thread = threading.Thread(target=start_client, args=(server_name, server_port, ack_probability))
        client_threads.append(client_thread)

    server_thread.start()

    for client_thread in client_threads:
        client_thread.start()

    server_thread.join()

    for client_thread in client_threads:
        client_thread.join()

if __name__ == "__main__":
    main()
