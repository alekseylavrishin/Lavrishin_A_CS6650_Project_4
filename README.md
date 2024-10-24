### Description

Single Server, Key-Value Store (TCP and UDP)

- Proper input format for the server: java Client.java <server_ip> <port>
- Proper input format for the client: java Server.java <server_ip> <port>

### Instructions to run:
1. Start the server using "java TCPServer.java 127.0.0.1 1300"
   1. In the command line, enter '1' to use TCP or '2' to use UDP
   
2. Start the client using "java TCPClient.java 127.0.0.1 1300"
   1. In the command line, enter '1' to use TCP or '2' to use UDP. 
   2. Ensure you are using the same protocol as the server
2. The client will then automatically run 5 test operations of each type (PUT, GET, DELETE)
3. Afterwards, on the client, select the type of operation (PUT, GET, DELETE) you'd like to run
4. If you would like to change the communication protocol (i.e. TCP to UDP), you must stop both the client and server and re-launch them with the desired protocol.

### Some considerations
You are able to specify the IP Address and Port Number that both the client and server interact on. Please choose an IP 
Address in the localhost range (127.0.0.0/8) and a valid Port Number.
Ensure the client and server are launched with the same IP Address and Port Number.

Additionally, make sure to start the server first prior to starting the client.

### Assignment Overview: 
The purpose of this assignment was to implement a client and server that could communicate over both TCP and UDP protocols.
The client and server needed to reliably communicate to perform three basic operations over a HashMap stored on the server: 
1.	PUT: Create a new Key-Value entry on the server 
2.	GET: Return the Value associated with the provided Key stored on the server 
3.	DELETE: Remove a Key-Value pair stored on the server. 

The client must be designed to handle an unresponsive or failed server via the use of timeouts and error messages. 
Meanwhile, the server must be able to gracefully handle malformed packets without interruptions in service. 
Since the implementation of the server-side HashMap operations are simple, the focus of this assignment was to introduce 
students to the differences of reliably implementing communication over TCP and UDP protocols between a client and server.

### Technical Impression: 
The process of implementing this project required an understanding of socket programming and the nitty-gritty details of 
implementing TCP and UDP communication.

UDP communication was implemented via the use of Java’s DatagramPacket and DatagramSocket classes. DatagramPackets were 
used to package messages into packets containing the message (in bytes), the length of the message, the IP Address, and 
the port number of the destination socket. These DatagramPackets were sent to the server by a DatagramSocket, used for 
sending and receiving UDP datagrams. 

UDP’s stateless nature allowed for faster communication at the cost of reliability. Since there is a chance packets can 
be lost, duplicated, or arrive out of order, the server was designed to log malformed, missing, or duplicate packets to 
ensure reliability and robustness. 

TCP communication was implemented through the use of Java’s Socket and ServerSocket classes, and the passing of messages
was done through DataInput/DataOutputStreams. Once a TCP connection was established between a client’s socket and a server’s socket, message delivery was simpler and safer without the fear of lost, dropped, or out of order packets.

With TCP, it was crucial to properly manage connections and ensure they were closed to avoid memory leaks, or “Address already in use” errors. 

Another challenge I had was ensuring the server remained unaffected by malformed data, such as corrupted or missing packets. 
To avoid crashes and interruptions in service, the server would catch these malformed packets and log them in the server log, 
allowing the server to be ready to handle the next request.  

A use case similar to this type of application would be a NoSQL database, like MongoDB. NoSQL databases are typically 
built around a key-value store model, similar enough to the HashMap residing on our server. NoSQL databases are designed 
to be used in applications that require easily scalable storage solutions. For example, social media applications like 
Twitter or Facebook require scalable databases to store a user’s posts, likes, follows, etc. 

I enjoyed working on this assignment as I have never had the opportunity to programmatically implement TCP and UDP 
communication before. Working with socket programming was different to the type of programming that I’m used to, as it 
required a deeper understanding of networking protocols and real-time data transmission. The low-level nature of socket 
programming – manually managing connections, sending/receiving packets, and handling communication errors provided a new 
kind of challenge that pushed me outside my comfort zone. 

