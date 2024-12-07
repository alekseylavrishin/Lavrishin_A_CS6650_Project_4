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

### Navigating the client
When launching the client, you will first encounter the below:
``````
Enter '1' to access Server #1
Enter '2' to access Server #2
Enter '3' to access Server #3
Enter '4' to access Server #4
Enter '5' to access Server #5
``````
Enter a number from 1 to 5 and press "Enter", then advance to the next dialog.
``````
Enter '1' to perform PUT
Enter '2' to perform GET
Enter '3' to perform DELETE
Enter '4' to programmatically test 5 of each operation
Enter '5' to initiate a new Paxos run
``````
1. Performs a PUT operation on a Key and Value
2. Performs a GET operation on a Key
3. Performs a DELETE operation on a Key
4. Runs a test method that tests the functionality of PUT-ing DELETE-ing and GET-ing Key-Value pairs in the Paxos cluster.
5. Advances Paxos to a new round allowing you to run a new operation on a Key that previously had an Accepted operation.


## Executive Summary

### Assignment Overview:
The purpose of this assignment was to extend Project 3’s replicated Two Phase Commit-enabled Key-Value store and replace the Two Phase Commit (2PC) functionality with the Paxos Protocol. This is because 2PC is not fault tolerant, where it will stall indefinitely in the event of a coordinator failure, whereas Paxos can still reach consensus in the event of a failed or unreachable node.

In order to achieve a degree of fault tolerance in our distributed Key-Value system, we were required to implement the three Paxos roles discussed in class – Proposers, Acceptors, and Learners. These roles must function in the following ways:

* Proposers: Initiate the consensus process by proposing a value provided by the client.

* Acceptors: Respond to Proposer, validating the consensus process and ensuring agreement.

* Learners: Learn the agreed upon value.

An additional requirement was to simulate the failure of a server’s Acceptor role at a random time. The purpose of simulating Acceptor failure is to show how Paxos is designed to overcome server failures.


### Technical Impression:
I chose to implement the Paxos Protocol such that each of the five replicated servers contain the functionality of all three Paxos roles: the Proposer, Acceptor, and Learner.

I decided on this architecture as it makes the overall system more resilient in the event of Acceptor failures. Since each of the 5 servers act as an Acceptor, our system can handle up to 2 server failures at a given time and still maintain consensus.

The main difficulty of this project stemmed from understanding and correctly implementing the Paxos protocol. I primarily had trouble managing the interactions between the Proposers, Acceptors, and Learners.

In my implementation, I broke down Paxos into the below phases:

* Prepare Phase:

  * Send a prepare message with a unique proposal number (proposalID) to all acceptors inquiring if a value has been previously accepted for a particular key during this specific Paxos round.

  * If nothing was accepted for the key, the Acceptor returns a promise containing the current proposalID. If something has been accepted for the key, Acceptor returns a promise containing the previously accepted proposalID and previously accepted value, and the currently proposed changes for that key are discarded in favor of the previously accepted changes.

* Promise Phase:

  * Evaluate the returned promises from the acceptors. If a previously accepted value is returned for a specific key, the proposer will perform the operation associated with the previously accepted value.

  * If no value has been accepted for a particular key, the proposer will perform the currently proposed operation.

* Accept Phase:

  * Acceptors attempt to ACCEPT the above value. If accepted by a majority of acceptors, the operation is propagated to all learners to be performed.

Additionally, I allowed for concurrent operations to be performed on different keys. Where if an operation is Accepted for a specific key, any other operations on that specific key revert to the previously Accepted operation. But a different operation can be performed on another key.

For example, if I perform a PUT: KeyA, ValueA operation and it is Accepted by a majority of Acceptors, no other operations can be done on KeyA in the current Paxos Round. Any other KeyA operations will default to the previously accepted operation, maintaining consistency.  However, another operation can be successfully performed on KeyB, for example, PUT: KeyB, ValueB is a valid operation that will successfully write KeyB to the K-V store.

Also, the client can initiate a new Paxos run via the “Enter '5' to initiate a new Paxos run” dialog. This advances Paxos to a new round allowing you to run a new operation on a Key that previously had an Accepted operation. For example, on KeyA, you could now run a DELETE:KeyA operation.

To implement the Acceptor failure requirement, the Acceptor methods were associated with an “active” boolean. Each server’s “active” boolean has a 20% chance to change from true to false, via a method run every 15 seconds. If an Acceptor “fails”, it is reinitialized after 15 seconds. In the event of an Acceptor failure, any requests sent to that server’s Acceptor methods are automatically REJECTED. In the event of an Acceptor failure or Acceptor recovery, you will receive a log message indicating so.