### Description

Multi-threaded Key-Value Store using RPC

### Instructions to run:
1. Ensure you have Docker installed
2. Open a terminal window and navigate to the src/ directory
3. Run the below commands:

   $ docker compose up

   $ docker stop rmi-client

   $ docker start -ai rmi-client

4. The server will stay operational until explicitly shut down, whereas the client will shut down after the completion of an operation.
5. To run the client again, use $ docker start -ai rmi-client

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
