import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Java RMI Server implementing the RemoteOperations interface.
 * Communicates with the Client using Remote Method invocation to perform
 *   PUT, GET, DELETE operations over key/value pairs stored on the Server.
 */
public class Server implements RemoteOperations{
    private ConcurrentHashMap<String, String> hMap;
    private ArrayList<RemoteOperations> serverRefs = new ArrayList<>();
    private static TwoPCInterface serverRef;
    private HashMap<String, ArrayList<String>> operationsMap = new HashMap<>(); // Stores prepared operations for future commit

    public Server(ConcurrentHashMap<String, String> hMap) throws RemoteException {
        this.hMap = hMap;
    }

    /**
     * Getter for this.hMap
     * @return The HashMap stored locally on the server.
     */
    public ConcurrentHashMap<String, String> getHMap() {
        return this.hMap;
    }

    /**
     * Setter for this.hMap.
     * @param newMap The new HashMap to be used by the server.
     */
    public void setHMap(ConcurrentHashMap<String, String> newMap) {
        this.hMap = newMap;
    }

    /**
     * Passes the key and value to the 2PC Coordinator to create a new record in all server's hashmaps.
     * @param key The key of the record.
     * @param value The value corresponding to the above key.
     * @param serverIP The IP address or hostname of the client corresponding to this transaction.
     * @return A String confirming the creation of the record.
     * @throws RemoteException
     */
    @Override
    public String createRecord(String key, String value, String serverIP) throws RemoteException {
        logMessage("Connected to Client on " + serverIP);
        logMessage("Client: " + serverIP + " - Server initializing PUT operation");
        logMessage(("Client: " + serverIP + " - Key " + key + " received by server"));

        // Get value from client
        logMessage("Client: " + serverIP + " - Value " + value + " received by server");

        //TODO: *****
        serverRef.TwoPhaseCommit("PUT", key, value);

        logMessage("PUT operation Key: " + key + " Value: " +  value + " has been invoked");
        logMessage("Connection closed to " + serverIP);
        return "PUT operation Key: " + key + " Value: " +  value + " has been invoked";
    }

    /**
     * Returns the value of an existing record stored in the Server's hashmap.
     * @param key The key of the record to be returned.
     * @param serverIP The IP address or hostname of the client corresponding to this transaction.
     * @return A String containing either the value of the corresponding key or a "cannot be found"
     *   message if the key is invalid.
     * @throws RemoteException
     */
    @Override
    public String getRecord(String key, String serverIP) throws RemoteException {
        String result = "";
        logMessage("Client: " + serverIP + " - Server initializing GET operation");
        logMessage(("Client: " + serverIP + " - Key " + key + " received by server"));

        if(hMap.containsKey(key)) {
            // Return value to client
            String value = hMap.get(key);
            result = "Value for " + key + ": " + value;
        } else { // If key cannot be found in hMap
            // Return 'cannot be found' message to client
            result = "Key " + key + " cannot be found";
        }
        logMessage("Client: " + serverIP + " - " + result);
        logMessage("Connection closed to " + serverIP);
        return result;
    }

    /**
     * Deletes an existing record stored in the server's hashmap.
     * If a record doesn't exist, returns "cannot be found" message.
     * @param key The key of the record to be deleted
     * @param serverIP The IP address or hostname of the client corresponding to this transaction.
     * @return A String containing either the confirmation of deletion or
     *   a "cannot be found" message if key is invalid.
     * @throws RemoteException
     */
    @Override
    public String deleteRecord(String key, String serverIP) throws RemoteException {
        String result = "";
        // confirm to server that DELETE operation is commencing
        logMessage("Client: " + serverIP + " Server initializing DELETE operation");

        logMessage("Client: " + serverIP + " Key " + key + " received by server");

        if (hMap.containsKey(key)) {
            // If key exists, delete key from hMap
            serverRef.TwoPhaseCommit("DELETE", key, "");
            result = "DELETE operation Key: " + key + " has been invoked";

        } else {
            // If key is not found in server
            result = "Key " + key + " cannot be found in server";

        }
        logMessage(result);
        logMessage("Connection closed to " + serverIP);
        return result;
    }

    /**
     * Returns the IP Address or hostname the Server is running on.
     * @return The IP or hostname the Server is using.
     * @throws RemoteException
     */
    @Override
    public String getServerIP() throws RemoteException{
        return System.getProperty("java.rmi.server.hostname");
    }

    /**
     * Returns the $SERVER_NAME environment variable associated with the particular server.
     * @return The $SERVER_NAME env variable tied with this server.
     * @throws RemoteException
     */
    @Override
    public String getServerName() throws RemoteException {
        return System.getenv("SERVER_NAME");
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

    /**
     * First part of 2 Phase Commit Protocol.
     * If successful, prepares an operation to be committed to the server by adding the transaction's info into the coordinator's
     *    operationsMap hashmap.
     * Returns ACK to 2PC coordinator if preparation is successful.
     * If preparation fails, the operation/transaction does not move forward to commit on any server.
     * @param transactionID A unique String to identify the specific transaction being prepared.
     * @param operation The type of operation being prepared: a PUT or DELETE
     * @param key The key to be PUT or DELETE-ed
     * @param value The value corresponding to the above key
     * @return A boolean value - true signifies an ACK message allowing the operation to successfully proceed.
     *    false signifies the operation cannot proceed, aborting the entire operation on all servers.
     */
    @Override
    public String prepareOperation(String transactionID, String operation, String key, String value) {
        try {
            logMessage("Transaction " + transactionID + " preparing " + operation + " operation on Key" + key);
            ArrayList<String> transactionList = new ArrayList<>(); // Add transaction info to log
            transactionList.add(0, operation);
            transactionList.add(1, key);
            transactionList.add(2, value);
            operationsMap.put(transactionID, transactionList); // Put operation in operation log
            return "ACK"; // ACK message, signifies readiness to commit
        } catch (Exception e) {
            logMessage("ERROR: Failed to prepare " + transactionID + " " + e.getMessage());
            return "false"; // Unable to proceed with 2pc process
        }
    }

    /**
     * Second part of 2 Phase Commit Protocol.
     * If prepareOperation successfully returns an ACK message from all server to the 2PC coordinator,
     *    the coordinator then invokes this function to attempt to commit the PUT/DELETE operation to all servers.
     * @param transactionID
     * @param operation
     * @param key
     * @param value
     * @return
     * @throws RemoteException
     */
    @Override
    public String commitOperation(String transactionID, String operation, String key, String value) throws RemoteException {
        try {
            if (operationsMap.containsKey(transactionID)) {
                logMessage("Transaction " + transactionID + " committing " + operation + " operation");
                if (operation.equals("PUT")) {
                    hMap.put(key, value);
                    logMessage("Transaction " + transactionID + " on Key " + key + " successfully committed");
                    operationsMap.remove(transactionID); // Remove transaction from log
                    return "ACK";
                } else if (operation.equals("DELETE")) {
                    hMap.remove(key);
                    logMessage("Transaction " + transactionID + " on Key " + key + " successfully committed");
                    operationsMap.remove(transactionID); // Remove transaction from log
                    return "ACK";
                } else {
                    logMessage("ERROR: transaction " + transactionID + " No operation committed");
                    operationsMap.remove(transactionID); // Remove transaction from log
                    return "false";
                }
            } else {
                return "false";

            }
        } catch (Exception e) {
            logMessage("ERROR: commitOperation " + e.getMessage());
            return "false";
        }
    }

    /**
     * Invoked by the 2PC Coordinator to abort an operation in progress.
     * @param transactionID A unique ID associated with the transaction to be aborted.
     * @throws RemoteException
     */
    @Override
    public void abortOperation(String transactionID) throws RemoteException {
        operationsMap.remove(transactionID);
        logMessage("ERROR: Transaction " + transactionID + " Abort phase received. Rolling back any prepared state.");
    }

    /**
     * Connects this server to the 2PC Coordinator for Two Phase Commit functionality.
     * @throws InterruptedException
     */
    public static void connectToCoordinator() throws InterruptedException {
        boolean connected = false; // Use timeout to retry connection
        String cName = "coordinator";
        for(int i = 0; i < 5 && !connected; i++) {
            try {
                Registry registry = LocateRegistry.getRegistry(cName, 2099); // Get local registry of remote server
                TwoPCInterface server = (TwoPCInterface) registry.lookup(cName); // Get reference to coordinator on port 2099
                //addServerRef(server); // Add remote reference of server to ArrayList
                serverRef = server; // Add remote reference of coordinator
                connected = true;
                logMessage("Connected to " + cName);
            } catch (Exception e) { // Retry if server hasn't been initialized yet
                logMessage("Retrying connection to " + cName + " (" + (i+1) + "/5");
                Thread.sleep(2000); // Wait 2 seconds before retrying
            }
        }
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

            connectToCoordinator(); // Connect the server to the coordinator for 2PC functionality

        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }

    }

}
