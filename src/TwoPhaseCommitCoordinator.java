import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Multithreaded coordinator utilizes RMI to implement the Two Phase Commit protocol.
 * This coordinator is instantiated as a separate docker container and is reachable by the 5 server containers over the docker
 *    "rmi-network" bridge .
 * This coordinator receives PUT and DELETE operations from the server which are then executed using the 2PC algorithm
 *    to ensure atomicity and integrity of all operations.
 */
public class TwoPhaseCommitCoordinator implements TwoPCInterface{
    private static ArrayList<RemoteOperations> serverRefs = new ArrayList<>();
    private final ExecutorService excecutorService = Executors.newCachedThreadPool(); // Utilizes multithreading for efficiency

    public TwoPhaseCommitCoordinator() {
        super();
    }

    /**
     * Establishes a connection to the other servers by getting a RemoteOperations reference to each server
     *    and storing it in an ArrayList for use in future 2PC communication.
     * Utilizes retries in order to re-attempt connection to servers that haven't been established yet.
     * @throws InterruptedException
     */
    public void connectToRemoteServers() throws InterruptedException {
        // Connect to each server
        String[] serverNames = {"rmi-server-1", "rmi-server-2", "rmi-server-3", "rmi-server-4", "rmi-server-5"};
        for (String sName : serverNames) {
            boolean connected = false; // Use timeout to retry connection
            for(int i = 0; i < 5 && !connected; i++) {
                try {
                    Registry registry = LocateRegistry.getRegistry(sName, 1099); // Get local registry of remote server
                    RemoteOperations server = (RemoteOperations) registry.lookup(sName); // Get reference to remote server
                    addServerRef(server); // Add remote reference of server to ArrayList
                    connected = true;
                    logMessage("Connected to " + server.getServerName());
                } catch (Exception e) { // Retry if server hasn't been initialized yet
                    logMessage("Retrying connection to " + sName + " (" + (i+1) + "/5");
                    Thread.sleep(2000); // Wait 2 seconds before retrying
                }
            }

        }
    }

    /**
     * Used when connecting to the servers in our 5 server cluster.
     * Adds a given server reference to a list containing references to all servers.
     * @param sRef The server reference to be added to the reference list.
     */
    public static void addServerRef(RemoteOperations sRef) {
        serverRefs.add(sRef);
    }

    /**
     * Gets current system time and prints output in MM-dd-yyyy HH:mm:ss.SSS format.
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

    /**
     * This is invoked by the Server upon a requested PUT or DELETE operation by the Client.
     * Executes in Two Phases: Prepare and Commit.
     * Prepare Phase - Ensures each server is ready to commit the transaction. Listens for ACK response from Server.
     *    If ACK response not received, or timeout exceeded, the transaction stops and does not move forward to Commit Phase.
     * Commit Phase - Attempts to commit the transaction to all servers. If transaction fails to commit, it is aborted on all servers.
     * @param operation The type of operation to be committed - PUT or DELETE
     * @param key The key to be committed.
     * @param value The value associated with the above key.
     * @return True if transaction is successfully prepared and committed on all servers. False if transaction fails.
     */
    @Override
    public boolean TwoPhaseCommit(String operation, String key, String value) {
        boolean prepareSuccess = true; // Tracks success of server.prepareOperation()
        String transactionId = UUID.randomUUID().toString(); // Create random ID for use as transaction ID
        logMessage("Transaction " + transactionId + " Initiating Prepare phase for " + operation + " operation on key " + key);

        // Phase 1: Prepare all servers for Commit
        for (RemoteOperations server : serverRefs) {
            Future<Boolean> future = excecutorService.submit(() -> sendPrepare(server, transactionId, operation, key, value));
            try {
                boolean ack = future.get(5, TimeUnit.SECONDS); // Timeout if unresponsive
                if (!ack) { // If all servers don't respond with ACK, do not move forward with 2PC transaction
                    prepareSuccess = false;
                }
            } catch (Exception e) { // If all servers don't respond with ACK, do not move forward with 2PC transaction
                prepareSuccess = false;
            }
        }
        // Phase 2: Commit changes to all servers
        if (prepareSuccess) { // If all servers successfully respond with ACK, move forward to Commit 2PC transaction
            boolean commitSuccess = true; // Track success of server.commitOperation()
            for(RemoteOperations server : serverRefs) {
                Future<Boolean> future = excecutorService.submit(() -> sendCommit(server, transactionId, operation, key, value));
                try {
                    boolean ack = future.get(5, TimeUnit.SECONDS); // Timeout if sendCommit unresponsive
                    if (!ack) {
                        logMessage("Commit ACK failed for transaction " + transactionId + " on server " + server.getServerIP());
                        commitSuccess = false;
                    }

                } catch (Exception e) {
                    logMessage("ERROR: Commit failed for transaction " + transactionId);
                    commitSuccess = false;
                }
            }

            // If any commit fails, trigger abort
            if (!commitSuccess) {
                logMessage("Commit phase failed, instantiating Abort");
                for (RemoteOperations abortServer : serverRefs) {
                    excecutorService.submit(() -> sendAbort(abortServer, transactionId));
                }
                return false; // Abort the operation
            }

            return true; // Operation successfully committed

        } else { // If not all servers return ACK, abort the transaction
            for(RemoteOperations server : serverRefs) {
                excecutorService.submit(() -> sendAbort(server, transactionId));
            }
            return false; // Abort the operation
        }
    }

    /**
     * Phase 1 of 2PC Protocol.
     * Uses RMI to prepare a PUT/DELETE transaction to be committed on a remote server.
     * @param server The server to prepare the transaction on.
     * @param transactionId The unique ID of this transaction.
     * @param operation The type of operation to be prepared - PUT or DELETE.
     * @param key The key associated with the transaction.
     * @param value The value associated with the above key.
     * @return True if an ACK message is received from the server. False otherwise.
     */
    private boolean sendPrepare(RemoteOperations server, String transactionId, String operation, String key, String value) {
        try {
            String response = server.prepareOperation(transactionId, operation, key, value);
            return response.equals("ACK");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Phase 2 of 2PC Protocol.
     * Uses RMI to commit a previously prepared transaction on a remote server.
     * @param server The server to commit the transaction on.
     * @param transactionId The unique ID of this transaction.
     * @param operation The type of operation to be prepared - PUT or DELETE.
     * @param key The key associated with the transaction.
     * @param value The value associated with the above key.
     */
    private boolean sendCommit(RemoteOperations server, String transactionId,  String operation, String key, String value) {
        try {
            String ack = server.commitOperation(transactionId, operation, key, value);
            logMessage("Transaction " + transactionId + " Server " + server.getServerIP() + " ACK MESSAGE: " + ack);
            logMessage("Connection closed to " + server.getServerIP());
            return ack.equals("ACK");
        } catch (Exception e) {
            logMessage("ERROR: sendCommit: " + e.getMessage());
            return false;
        }
    }

    /**
     * Phase 2 of 2PC Protocol.
     * Uses RMI to abort an in-progress transaction on a remote server.
     * @param server The server to abort the transaction on.
     * @param transactionId The unique ID of the transaction to be aborted.
     */
    private void sendAbort(RemoteOperations server, String transactionId) {
        try {
            server.abortOperation(transactionId);
        } catch (UnknownHostException e) {
            logMessage("ERROR: sendAbort -  Host is unreachable " + e.getMessage());
        } catch(Exception e) {
            logMessage("ERROR: sendAbort: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws RemoteException {
        try {
            String serverName = System.getenv("SERVER_NAME");
            // Set hostname to the SERVER_NAME env variable stored in the docker-compose file
            System.setProperty("java.rmi.server.hostname", serverName);
            // Create remote object providing RMI service
            TwoPhaseCommitCoordinator coordinator = new TwoPhaseCommitCoordinator();
            // Export coordinator to Java RMI runtime to accept incoming RMI calls on specified port
            TwoPCInterface stub = (TwoPCInterface) UnicastRemoteObject.exportObject(coordinator, 2099);

            Registry registry = LocateRegistry.createRegistry(2099);
            // Bind server reference to registry for accessibility
            registry.bind(serverName, stub);
            logMessage("Coordinator initialized on host " + System.getProperty("java.rmi.server.hostname") + " port " + 2099);
            coordinator.connectToRemoteServers();



        } catch(Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }
    }
}
