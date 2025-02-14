import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps track of the state of the Paxos environment for a single Paxos run.
 */
class PaxosState {
    public int highestPromisedID = -1; // The highest proposal ID promised
    public int acceptedProposalID = -1; // ID of the accepted proposal
    public String acceptedValue = null; // Value of the accepted proposal
}

/**
 * Java RMI Server implementing the RemoteOperations interface.
 * Communicates with the Client using Remote Method invocation to receive input on key/value pairs and the type
 *    of operation to perform.
 * GET operations are performed locally on this server.
 * PUT and DELETE operations are performed using Paxos to ensure the consistency of transactions.
 */
public class Server implements RemoteOperations{
    private ConcurrentHashMap<String, String> hMap;
    private static ArrayList<RemoteOperations> serverRefs = new ArrayList<>();
    private int proposalID; // Proposer ID for new proposers
    private boolean active = true; // Simulates the failure of an Acceptor
    private static ScheduledExecutorService acceptorFailure = Executors.newScheduledThreadPool(2);
    // Tracks the Paxos state of individual keys. Can be cleared to simulate a new Paxos run.
    private ConcurrentHashMap<String, PaxosState> paxosStateMap = new ConcurrentHashMap<>();


    public Server(ConcurrentHashMap<String, String> hMap ) throws RemoteException {
        this.hMap = hMap;
    }

    /**
     * Simulates a new Paxos round, where previously accepted keys can be operated on again.
     * @return A "success" message indicating the paxosStateMap for all servers has been cleared.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public String initiateNewPaxosRun() throws RemoteException {
        try {
            for (RemoteOperations srv : serverRefs) {
                srv.clearPaxosStateMap();
            }
            logMessage("paxosStateMaps are cleared. " + getServerName() + " has initiated new Paxos run");
            return "paxosStateMaps are cleared. " + getServerName() + " has initiated new Paxos run";
        } catch (Exception e) {
            logMessage("ERROR: Issue in initiateNewPaxosRun() " + e.getMessage());
            return "ERROR: Issue in initiateNewPaxosRun()";
        }

    }

    /**
     * Clears the paxosStateMap for a given server. Once the StateMap is cleared, previously accepted
     *    keys can be operated on again with Paxos.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public void clearPaxosStateMap() throws RemoteException {
        try {
            paxosStateMap.clear();
            logMessage("paxosStateMap is cleared for " + getServerName());
        } catch (Exception e) {
            logMessage("ERROR: Issue in clearPaxosStateMap() " + e.getMessage());
        }
    }

    /**
     * Implements the Proposer functionality.
     * The backbone of the Paxos protocol, is separated into 3 main phases.
     *
     * Prepare Phase:
     * Sends a prepare message with a unique proposal number (proposalID) to all acceptors.
     * Inquires if a value has been previously accepted for a particular key during this specific Paxos round.
     * If nothing was accepted for the key, the acceptor returns a promise containing the current proposalID.
     * If something has been accepted for the key, acceptor returns a promise containing the previously
     *    accepted proposalID and previously accepted value, and the currently proposed changes for that key are
     *    discarded in favor of the previously accepted changes.
     *
     * Promise Phase:
     * Evaluates the returned promises from the acceptors. If a previously accepted value is returned for a specific key,
     *    the proposer will perform the operation associated with the previously accepted value.
     * If no value has been accepted for a particular key, the proposer will perform the currently proposed operation.
     *
     * Accept Phase:
     * Acceptors attempt to ACCEPT the above value. If accepted by a majority of acceptors, the operation is
     *    propagated to all learners to be performed.
     * @param value A String in the format of "$operation,$key,$value".
     * @return If successful, returns a message signifying the reaching of a PAXOS consensus.
     *    If unsuccessful, returns a message signifying the failure to reach a consensus.
     * @throws RemoteException For RMI-related errors.
     */
    public String propose(String value) throws RemoteException {
        try {
            String key = value.split(",")[1]; // Associate Paxos operation with key of object being operated on
            // Check for PAXOS state object associated with key, if not, then create one
            PaxosState state = paxosStateMap.computeIfAbsent(key, k -> new PaxosState());

            proposalID = state.acceptedProposalID + 1; // Generate a unique proposal ID
            int promises = 0; // Keep track of number of promises returned
            logMessage("ID: " + proposalID + " Proposer " + getServerName() + " proposing " + value + " for key " + key);

            // Keep track of ACCEPT messages returned from acceptors
            HashMap<String, String> acceptMap = new HashMap<>();

            // Prepare Phase: Send PREPARE message to all nodes (Acceptors)
            for (RemoteOperations srv : serverRefs) {
                String[] responseList = srv.prepare(proposalID, key).split(",");
                if (responseList[0].equals("PROMISE")) {
                    if (!responseList[2].equals("null")) { // If PAXOS instance has previously accepted a value
                        // [$operation, $key, $value]
                        String respProposal = responseList[1];
                        String respVal = responseList[3] + "," + responseList[4] + "," + responseList[5];
                        logMessage("RESPVAL: " + respVal);
                        acceptMap.put(respProposal, respVal);
                    }
                    promises++; // Keep track of promises for below Promise Phase
                }
            }

            // Promise Phase: Evaluate replies from acceptors
            if (promises > serverRefs.size() / 2) { // Quorum has been reached
                String finalValue = value; // If no value returned by the acceptors, proposer uses initial value
                for (String acceptedVal : acceptMap.values()) {
                    if (acceptedVal != null) {  // If previously accepted value is returned by the acceptors, proposer uses it
                        finalValue = acceptedVal;
                        logMessage("ACCEPTEDVAL " + acceptedVal);
                        break;
                    }
                }

                logMessage("ID: " + proposalID + " Proposer " + getServerName() + " accepting " + finalValue);
                int successCount = 0;

                // Accept Phase
                for (RemoteOperations srv : serverRefs) {
                    String[] responseList = srv.acceptRequest(proposalID, finalValue, key).split(",");
                    if (responseList[0].equals("ACCEPT")) {
                        successCount++;
                    }
                }

                if (successCount > serverRefs.size() / 2) {
                    // Success - kick off learner to perform PUT/DELETE operation
                    for (RemoteOperations srv : serverRefs) {
                        //srv.learn(value);
                        srv.learn(finalValue);
                    }

                    logMessage("ID: " + proposalID + " Proposer " + getServerName() + " reached consensus on value " + finalValue);
                    return "ID: " + proposalID + " Proposer " + getServerName() + " reached consensus on value " + finalValue;

                } else { // Trigger if not enough accepts obtained
                    logMessage("ID: " + proposalID + " Proposer " + getServerName() + " failed to reach consensus");
                    return "ID: " + proposalID + " Proposer " + getServerName() + " failed to reach consensus";
                }
            } else { // Trigger if not enough promises obtained
                logMessage("ID: " + proposalID + " Proposer " + getServerName() + " did not receive a majority of promises");
                return "ID: " + proposalID + " Proposer " + getServerName() + " Proposal rejected";
            }
        } catch (Exception e) {
            logMessage("ERROR: Issue in propose method " + e.getMessage());
            return "ERROR: ID: " + proposalID + " Proposer " + getServerName() + " Proposal rejected due to error in propose method";
        }
    }

    /**
     * Part of the Acceptor functionality. Proposer -> Acceptor.
     * Receives a PREPARE message from the Proposer's propose method.
     * @param proposalID A unique identifier for the specific operation being proposed.
     * @return A PROMISE if the proposalID is greater than the ID previously proposed.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public synchronized String prepare(int proposalID, String key) throws RemoteException {
        try {
            if (!active) { // Simulates if Acceptor fails
                logMessage("ACCEPTOR FAILURE: " + getServerName() + " is inactive. Rejecting PREPARE request.");
                return "REJECT";
            } else { // Simulates properly functioning Acceptor
                PaxosState state = paxosStateMap.computeIfAbsent(key, k -> new PaxosState());
                if (proposalID > state.highestPromisedID) {
                    state.highestPromisedID = proposalID;
                    if (state.acceptedProposalID == -1) { // If PAXOS instance has not previously accepted a value
                        logMessage("Promise ID " + state.highestPromisedID);
                        return "PROMISE," + state.highestPromisedID + "," + "null";

                    } else { // If PAXOS instance has previously accepted a value
                        logMessage("PROMISE ID " + state.highestPromisedID + " accepted ID " + state.acceptedProposalID + " " + state.acceptedValue);
                        return "PROMISE," + state.highestPromisedID + "," + state.acceptedProposalID + "," + state.acceptedValue;
                    }
                }
                // Ignore if proposalID < highestPromisedID
                return "REJECT";
            }
        } catch (Exception e) {
            logMessage("ERROR: Issue in Acceptor's prepare method " + e.getMessage());
            return "REJECT";
        }
    }

    /**
     * Part of the Acceptor functionality. Acceptor -> Proposer.
     * Attempts to ACCEPT the proposalID and value provided by Proposer.
     * @param proposalID A unique identifier for the specific operation being accepted.
     * @param value A String in the format of $operation,$key,$value.
     * @return An ACCEPT message if the request's proposalID >= highestPromisedID.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public synchronized String acceptRequest(int proposalID, String value, String key) throws RemoteException {
        try {
            if (!active){ // Simulates if Acceptor fails
                logMessage("ACCEPTOR FAILURE: " + getServerName() + " is inactive. Rejecting ACCEPT request.");
                return "REJECT";
            } else { // Simulates properly functioning Acceptor
                PaxosState state = paxosStateMap.computeIfAbsent(key, k -> new PaxosState());
                if (proposalID >= state.highestPromisedID) { // if proposalID is the largest, accept the request
                    state.highestPromisedID = proposalID;
                    state.acceptedProposalID = proposalID; // Update proposalId to use for future PAXOS requests
                    state.acceptedValue = value;

                    logMessage("ACCEPT ID " + proposalID + " value " + value);
                    return "ACCEPT," + proposalID + "," + value;
                }
                // Otherwise reject request
                logMessage("REJECT ID " + proposalID + " value " + value);
                return "REJECT," + proposalID + "," + value;
            }
        } catch (Exception e) {
            logMessage("ERROR: Issue in Acceptor's acceptRequest method " + e.getMessage());
            return "REJECT," + proposalID + "," + value;
        }
    }

    /**
     * Part of the Learner functionality, Acceptor -> Learner.
     * Once a request has been accepted by a quorum of Acceptors, this method begins the process
     *    of performing a PUT or DELETE operation.
     * @param value A String in the format of $operation,$key,$value.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public void learn(String value) throws RemoteException {
        try {
            // [$operation, $key, $value]
            String[] responseList = value.split(",");
            if (responseList[0].equals("PUT")) {
                String result = createRecord(responseList[1], responseList[2]);
                logMessage(result);
            } else if (responseList[0].equals("DELETE")) {
                String result = deleteRecord(responseList[1]);
                hMap.remove(responseList[1]);
                logMessage(result);
            } else {
                logMessage("Learner " + getServerName() + " invalid operation detected, aborting");
            }
        } catch (Exception e) {
            logMessage("ERROR: Issue in Learner's learn method " + e.getMessage());
        }
    }

    /**
     * Simulates an acceptor failure by having a 20% chance of changing the 'active' boolean to false.
     * When the 'active' boolean is changed to false, the server's Acceptor methods (prepare, acceptRequest) will REJECT
     *    a proposer's incoming request.
     * This method runs every 15 seconds.
     */
    public void simulateAcceptorFailure() {
        acceptorFailure.scheduleAtFixedRate(() -> {
            try {
                // Simulate failure on Acceptor
                if (Math.random() < 0.2) { // Acceptor fails 20% of the time
                    active = false;
                    logMessage("Acceptor " + getServerName() + " has failed");
                } else {
                    // Recover the failed Acceptor
                    if (!active) {
                        active = true;
                        logMessage("Acceptor " + getServerName() + " has recovered");
                    }

                }
            } catch (Exception e) {
                logMessage("ERROR: Issue in simulateAcceptorFailure()" + e.getMessage());
            }
        }, 5, 15, TimeUnit.SECONDS);
    }

    /**
     * Establishes a connection to the other servers by getting a RemoteOperations reference to each server
     *    and storing it in an ArrayList for use in future RMI communication.
     * Utilizes retries in order to re-attempt connection to servers that haven't been established yet.
     * @throws InterruptedException For thread-related issues.
     */
    public static void connectToPaxosNodes() throws InterruptedException {
        try {
            // Connect to each PAXOS server
            String[] serverNames = {"rmi-server-1", "rmi-server-2", "rmi-server-3", "rmi-server-4", "rmi-server-5"};
            // Initialize Paxos cluster
            for (String sName : serverNames) {
                boolean connected = false; // Use timeout to retry connection
                for (int i = 0; i < 5 && !connected; i++) {
                    try {
                        Registry registry = LocateRegistry.getRegistry(sName, 1099); // Get local registry of remote server
                        RemoteOperations server = (RemoteOperations) registry.lookup(sName); // Get reference to remote server
                        serverRefs.add(server); // Add remote reference of server to ArrayList
                        connected = true;
                        logMessage("Connected to " + server.getServerName());

                    } catch (Exception e) { // Retry if server hasn't been initialized yet
                        logMessage("Retrying connection to " + sName + " (" + (i + 1) + "/5");
                        Thread.sleep(2000); // Wait 2 seconds before retrying
                    }
                }

            }
        } catch (Exception e) {
            logMessage("ERROR: Issue in connectToPaxosNodes() " + e.getMessage());
        }
    }

    /**
     * Is called by the PAXOS learn() method to create a new record in a server's hashmap.
     * @param key The key of the record.
     * @param value The value corresponding to the above key.
     * @return A String confirming the creation of the record.
     * @throws RemoteException For RMI-related errors.
     */
    public String createRecord(String key, String value) throws RemoteException {
        try {
            logMessage(" - Server initializing PUT operation for key " + key + " value " + value);
            hMap.put(key, value);
            String msg = "";
            if (hMap.get(key).equals(value)) {
                msg = "PUT operation Key: " + key + " Value: " + value + " successful";
            } else {
                msg = "PUT operation Key: " + key + " Value: " + value + " is not successful";
            }
            //logMessage(msg);
            logMessage("Connection closed");
            return msg;

        } catch (Exception e) {
            String msg = "ERROR: createRecord failed";
            logMessage(msg + " " + e.getMessage());
            return msg;
        }
    }

    /**
     * Is called locally on the server without involving the PAXOS protocol.
     * Returns the value of an existing record stored in the Server's hashmap.
     * @param key The key of the record to be returned.
     * @param serverIP The IP address or hostname of the client corresponding to this transaction.
     * @return A String containing either the value of the corresponding key or a "cannot be found"
     *   message if the key is invalid.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public String getRecord(String key, String serverIP) throws RemoteException {
        try {
            String result = "";
            logMessage(" - Server initializing GET operation");
            logMessage(" - Key " + key + " received by server");

            if (hMap.containsKey(key)) {
                String value = hMap.get(key);
                result = "Value for " + key + ": " + value;
            } else { // If key cannot be found in hMap
                result = "Key " + key + " cannot be found";
            }

            logMessage("Client: " + serverIP + " - " + result);
            logMessage("Connection closed to " + serverIP);
            return result;
        }
        catch (Exception e) {
            logMessage("ERROR: Issue in getRecord " + e.getMessage());
            return "ERROR: Issue in getRecord Key " + key + " cannot be found";
        }
    }

    /**
     * Is called by the PAXOS learn() methods to delete an existing record stored in the server's hashmap.
     * If a record doesn't exist, returns "cannot be found" message.
     * @param key The key of the record to be deleted
     * @return A String containing either the confirmation of deletion or
     *   a "cannot be found" message if key is invalid.
     * @throws RemoteException For RMI-related errors.
     */
    public String deleteRecord(String key) throws RemoteException {
        try {
            // confirm to server that DELETE operation is commencing
            logMessage(" - Server initializing DELETE operation");
            logMessage(" - Key " + key + " received by server");
            String msg = "";

            if (hMap.containsKey(key)) {
                hMap.remove(key);
                if (!hMap.containsKey(key)) {
                    msg = "DELETE operation on Key: " + key + " successful";

                } else {
                    msg = "DELETE operation on Key: " + key + " was not successful";
                }
            } else {
                // If key is not found in server
                msg = "Key " + key + " cannot be found in server";
            }

            //logMessage(msg);
            logMessage("Connection closed");
            return msg;
        } catch (Exception e) {
            String msg = "ERROR: deleteRecord failed";
            logMessage(msg + " " + e.getMessage());
            return msg;
        }
    }

    /**
     * Returns the IP Address or hostname the Server is running on.
     * @return The IP or hostname the Server is using.
     * @throws RemoteException for RMI-related errors.
     */
    @Override
    public String getServerIP() throws RemoteException {
        try {
            return System.getProperty("java.rmi.server.hostname");
        } catch (Exception e) {
            logMessage("ERROR: Issue in getServerIP() " + e.getMessage());
            return "ERROR";
        }
    }

    /**
     * Returns the $SERVER_NAME environment variable associated with the particular server.
     * @return The $SERVER_NAME env variable tied with this server.
     * @throws RemoteException For RMI-related errors.
     */
    @Override
    public String getServerName() throws RemoteException {
        try {
            return System.getenv("SERVER_NAME");
        } catch (Exception e) {
            logMessage("ERROR: Issue in getServerName() " + e.getMessage());
            return "ERROR";
        }
    }

    /**
     * Gets current system time and prints Client output in MM-dd-yyyy HH:mm:ss.SSS format.
     * @param message The message to be printed.
     */
    public static void logMessage(String message) {
        long currSystemTime = System.currentTimeMillis(); // Get current system time

        // Convert system time to human-readable format
        Date date = new Date(currSystemTime);
        SimpleDateFormat SDF = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS");
        String time = SDF.format(date);

        System.out.println(time + " -- " + message);
    }



    public static void main(String[] args) throws RemoteException {
        if(args.length != 3){
            logMessage("Proper input format must be 'java Server.java <server_ip> <port> <server_name>'");
            return;
        }

        String serverIP = null;
        int port = -1;
        String serverName = null;

        // Stores all keys, values provided by client
        ConcurrentHashMap<String, String> hMap = new ConcurrentHashMap<>();

        try {
            serverIP = args[0];
            port = Integer.parseInt(args[1]);
            serverName = args[2];

        } catch (Exception e) {
            logMessage("<server_ip> and <server_name> must be type String and <port> must be type int");
        }


        try {
            serverName = System.getenv("SERVER_NAME");
            // Set hostname to the SERVER_NAME env variable stored in the docker-compose file
            System.setProperty("java.rmi.server.hostname", serverName);
            // Create remote object providing RMI service
            Server srv = new Server(hMap);
            // Export srv to Java RMI runtime to accept incoming RMI calls on specified port
            RemoteOperations stub = (RemoteOperations) UnicastRemoteObject.exportObject(srv, port);

            Registry registry = LocateRegistry.createRegistry(1099);
            // Bind server reference to registry for accessibility
            registry.bind(serverName, stub);
            logMessage("Server initialized on host " + System.getProperty("java.rmi.server.hostname") + " port " + port);

            connectToPaxosNodes(); // Connect to all PAXOS nodes
            srv.simulateAcceptorFailure();

        } catch (Exception e) {
            logMessage("Failed to connect to PAXOS nodes: " + e.getMessage());
        }

    }

}
