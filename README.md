## Multi-threaded Key-Value Store using RPC

### Instructions to run:
1. Ensure you have Docker installed
2. Open a terminal window and navigate to the src/ directory
3. Run the below commands:

   * $ docker-compose up --build

   * $ docker stop rmi-client

   * $ docker start -ai rmi-client

4. The servers will stay operational until explicitly shut down, whereas the client will shut down after the completion of an operation.
5. To run the client again, use $ docker start -ai rmi-client
6. To restart the server, use $ docker start rmi-server

The reason for stopping the client after running docker compose is that docker compose launches containers in detached mode, causing them to run as a background service and not allowing for interaction via the terminal.

## Executive Summary

### Assignment Overview:
The purpose of this assignment was to extend Project 2’s Single Instance Key-Value store by replicating Project 2’s single server into 5 distinct servers, with each server possessing its own key-value store. When performing PUT or DELETE operations, all key-value stores are required to maintain the availability and consistency of data among the 5 replicas. Clients should be able to interact with any of the 5 servers for PUT, GET, DELETE operations and receive the same information and functionality from all. To prevent inconsistencies in the data, the Two Phase Commit Protocol (2PC) was implemented for PUT and DELETE operations, where changes are either fully committed to all servers or aborted entirely. Since the PUT and DELETE operations reliably propagated changes to the server replicas via 2PC, the GET operation remains largely unchanged from Project 2.



### Technical Impression:
The process of implementing this project required a good understanding of the Two Phase Commit Protocol and some knowledge on replicating and coordinating operations between multiple server replicas. Replicating the servers themselves was straightforward, primarily requiring the modification of the _docker-compose_ file. In the _docker-compose_ file, all 5 servers and the 2PC Coordinator were configured in their own individual containers and given unique names in the form of the _SERVER_NAME_ environment variable. The _SERVER__NAME was used to bind the remote object’s reference to the registry, allowing the Coordinator and servers to establish RMI communication with one another by performing a registry lookup of the _SERVER_NAME_ they wish to communicate with.

Originally, I attempted to implement communication between my 5 replicated servers similarly to a peer-to-peer service. Where the specific server a transaction is first initiated on acts as the Coordinator for the Two Phase Commit Protocol, managing the Preparation and Commit phases of 2PC, and handling any Rollbacks or Aborts that may occur. I had intended on a peer-to-peer architecture to ensure close coupling of the 2PC Protocol with the server behavior and to reduce the amount of network communication between my Docker containers. However, I eventually landed on separating the 2PC Coordinator into its own class to reduce difficulty in debugging, centralize Coordinator-related logging, and facilitate the reusability and scalability of the Coordinator as a whole.

Implementing the Two Phase Commit Protocol was the most challenging part of the project, requiring a detailed and organized implementation to properly gain consensus across the replicas for PUT and DELETE operations. This 2PC algorithm here is implemented using RMI and is divided into 2 phases, the Prepare Phase and the Commit Phase.

In the Prepare Phase, the Coordinator sends Prepare requests to the replicas and receives acknowledgement (ACK) messages if the servers are prepared to move forward to commit the transaction. If any of the replicas do not send back an ACK message, the transaction cannot move forward and is aborted across all 5 replicas. Timeouts were implemented to prevent the stalling of a transaction, helping the server maintain a consistent, fail-free state.

After successfully preparing all 5 replicas, the Coordinator moves on to the Commit Phase. Here, the Coordinator ensures that either all 5 servers successfully commit the result of the PUT/DELETE operation, or none of them do. If any of the replicas fail to commit the changes, the entire operation is rolled back across all 5 servers. Like with the Prepare Phase, timeouts were implemented to prevent the stalling of transactions.