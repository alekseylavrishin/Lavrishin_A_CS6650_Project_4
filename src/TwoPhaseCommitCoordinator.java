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

public class TwoPhaseCommitCoordinator implements TwoPCInterface{
    private static ArrayList<RemoteOperations> serverRefs = new ArrayList<>();
    private final ExecutorService excecutorService = Executors.newCachedThreadPool();

    public TwoPhaseCommitCoordinator() {
        super();
    }

    /**
     * Establishes a connection to the other servers by getting a RemoteOperations reference to each server
     *    and storing it in an ArrayList for use in future communication.
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
     * Used when connecting to the remaining servers in our 5 server cluster.
     * Adds a given server reference to a list containing references to all servers except the current one.
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

    @Override
    public boolean TwoPhaseCommit(String operation, String key, String value) {
        HashMap<RemoteOperations, Boolean> ackStore = new HashMap<>();
        boolean prepareSuccess = true;
        String transactionId = UUID.randomUUID().toString(); // Create random ID for use as transaction ID
        logMessage("Transaction " + transactionId + " Initiating Prepare phase for " + operation + " operation on key " + key);

        // Prepare all servers for Commit
        for (RemoteOperations server : serverRefs) {
            Future<Boolean> future = excecutorService.submit(() -> sendPrepare(server, transactionId, operation, key, value));
            try {
                boolean ack = future.get(5, TimeUnit.SECONDS); // Timeout if unresponsive
                ackStore.put(server, ack); // can prob delete
                if (!ack) {
                    prepareSuccess = false;
                }
            } catch (Exception e) {
                prepareSuccess = false;
                ackStore.put(server, false);// can prob delete
            }
        }
        if (prepareSuccess) {
            for(RemoteOperations server : serverRefs) {
                excecutorService.submit(() -> sendCommit(server, transactionId, operation, key, value));
            }
            return true; // Operation successfully committed
        } else {
            for(RemoteOperations server : serverRefs) {
                //excecutorService.submit(this::sendAbort);
                excecutorService.submit(() -> sendAbort(server, transactionId));
            }
            return false; // Abort operation
        }
    }

    private boolean sendPrepare(RemoteOperations server, String transactionId, String operation, String key, String value) {
        try {
            String response = server.prepareOperation(transactionId, operation, key, value);
            return response.equals("ACK");
        } catch (Exception e) {
            return false;
        }
    }

    private void sendCommit(RemoteOperations server, String transactionId,  String operation, String key, String value) {
        try {
            String ack = server.commitOperation(transactionId, operation, key, value);
            logMessage("Transaction " + transactionId + " Server " + server.getServerIP() + " ACK MESSAGE: " + ack);
            logMessage("Connection close to " + server.getServerIP());
        } catch (Exception e) {
            logMessage("ERROR: sendCommit: " + e.getMessage());
        }
    }

    private void sendAbort(RemoteOperations server, String transactionId) {
        try {
            server.abortOperation(transactionId);
        } catch (UnknownHostException e) {
            logMessage("ERROR: sendAbort -  Host is unreachable " + e.getMessage());
        } catch(Exception e) {
            logMessage("ERROR: sendAbort: " + e.getMessage());
        }
    }

    public void shutdown() {
        excecutorService.shutdown();
    }

    public static void main(String[] args) throws RemoteException {
        try {
            logMessage("inside main func");
            String serverName = System.getenv("SERVER_NAME");
            // Set hostname to the SERVER_NAME env variable stored in the docker-compose file
            System.setProperty("java.rmi.server.hostname", serverName);
            logMessage("hostname set");
            // Create remote object providing RMI service
            TwoPhaseCommitCoordinator coordinator = new TwoPhaseCommitCoordinator();
            logMessage("2pc coord created");
            // Export coordinator to Java RMI runtime to accept incoming RMI calls on specified port
            TwoPCInterface stub = (TwoPCInterface) UnicastRemoteObject.exportObject(coordinator, 2099);
            logMessage("stub craeated");

            Registry registry = LocateRegistry.createRegistry(2099);
            logMessage("reg created");
            // Bind server reference to registry for accessibility
            registry.bind(serverName, stub);
            logMessage("Coordinator initialized on host " + System.getProperty("java.rmi.server.hostname") + " port " + 2099);
            coordinator.connectToRemoteServers();



        } catch(Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }
    }
}
