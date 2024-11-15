## Multi-threaded Key-Value Store using RPC

### Instructions to run:
1. Ensure you have Docker installed
2. Open a terminal window and navigate to the src/ directory
3. Run the below commands:

   * $ docker compose up --build

   * $ docker stop rmi-client

   * $ docker start -ai rmi-client

4. The server will stay operational until explicitly shut down, whereas the client will shut down after the completion of an operation.
5. To run the client again, use $ docker start -ai rmi-client
6. To restart the server, use $ docker start rmi-server

The reason for stopping the client after running docker compose is that docker compose launches containers in detached mode, causing them to run as a background service and not allowing for interaction via the terminal. 

### Running the Client/Server Locally
It's possible to run the Client and Server locally, as opposed to using Docker. 

To run this locally:
1. In Server.java:
   * Uncomment line 174
   * Comment Out line 177
2. In Client.java
   * Uncomment line 174
   * Comment Out line 177
3. Open a terminal window and run the following commands in the src/ directory:
   * $ javac *.java
   * $ java Server 127.0.0.1 1300
   * $ java Client 127.0.0.2 1300


## Executive Summary

### Assignment Overview: 
The purpose of this assignment was to extend Project 1’s Key-Value store by replacing our previously implemented socket-based communication with Remote Procedure Call (RPC) communication and server-side multi-threading. The client and server, which previously used socket-based communication, were now updated to use RPC via the Java RMI package, allowing the client the ability to call a method stored on the server as if it were calling a locally stored method. The purpose of this change was to tie-in with the concepts of remote invocation we learned in Chapters 5 and 6 of Colouris, showcasing client-server interactions that occurred more abstractly than with the previous lower-level socket approach.  
As with Project 1, the client and server required reliable communication to perform the operations below.
1.	PUT: Create a new Key-Value entry on the server 
2.	GET: Return the Value associated with the provided Key stored on the server 
3.	DELETE: Remove a Key-Value pair stored on the server. 
However, this time, the nitty-gritty details of establishing and maintaining an active network connection were abstracted away via Java RMI.  Additionally, to handle concurrent client requests, the server was required to be multi-threaded. This was also primarily handled by Java RMI, as it is multi-threaded by default. Lastly, to simplify testing and grading our client and server, it was necessary to “dockerize” them, ensuring they ran in a consistent environment across different hardware/software platforms. 

### Technical Impression: 
The process of implementing this project required an understanding of Remote Procedure Calls (RPC). Switching from a socket-based implementation to RPC greatly impacted the overall complexity of the project. By utilizing Java RMI, the client-server communication was greatly simplified and abstracted, allowing for cleaner and more efficient interactions, and noticeably reducing the size of the codebase, as we were no longer required to implement the functionality of TCP/UDP communication. 
A significant advantage of Java RMI is its inherently multi-threaded nature. When a client makes a call to a server, Java RMI immediately creates a new thread to deal with that client’s request. As such, just like with the network communication, the multi-threaded requirement of this project was also abstracted away by Java RMI. Additionally, to ensure thread safety when handling multiple interactions with shared data, I replaced the HashMap originally used by the server in Project 1 with a ConcurrentHashMap, allowing for execution of PUT, GET, DELETE commands without the fear of data loss or corruption. 
Lastly, this project required the use of Docker. While I have a small degree of familiarity with Docker, I faced challenges in properly structuring my Dockerfiles and understanding the complexities of Docker compose for building and running my containers. I also faced challenges with establishing communication between the separate client and server containers, leading me to create a docker-compose file to link the two together on a shared network.  

