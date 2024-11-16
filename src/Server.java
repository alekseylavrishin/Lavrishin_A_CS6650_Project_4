import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Java RMI Server implementing the RemoteOperations interface.
 * Communicates with the Client using Remote Method invocation to perform
 *   PUT, GET, DELETE operations over key/value pairs stored on the Server.
 */
public class Server implements RemoteOperations{
    private ConcurrentHashMap<String, String> hMap;
    //private HashMap<String, String> hMap;
    private ArrayList<RemoteOperations> serverRefs = new ArrayList<>();



    public Server(ConcurrentHashMap<String, String> hMap) throws RemoteException {
        this.hMap = hMap;
    }


    /**
     * Used when connecting to the remaining servers in our 5 server cluster.
     * Adds a given server reference to a list containing references to all servers except the current one.
     * @param sRef The server reference to be added to the reference list.
     */
    public void addServerRef(RemoteOperations sRef) {
        serverRefs.add(sRef);
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
     * Creates a new record in the Server's hashmap.
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

        // Write key, value to hMap
        hMap.put(key, value);
        propagatePut(key, value); // Propagate to remaining servers in cluster
        logMessage("Client: " + serverIP + " - Key: " + key + " Value: " +  value + " have been written to the server");
        logMessage("Connection closed to " + serverIP);
        return "Key: " + key + " Value: " +  value + " have been written to the server";
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
            hMap.remove(key);
            result = "Key " + key + " deleted from server";
        } else {
            // If key is not found in server
            result = "Key " + key + " cannot be found in server";

        }
        logMessage("Client: " + serverIP + "- " + result);
        logMessage("Connection closed to " + serverIP);
        return result;
    }

    /**
     * Used to send an RMI remotePut operation to every server except itself.
     * When a key val pair is written to this server, this operation propagates the PUT to the remaining servers.
     * @param key The key of the record.
     * @param value The value corresponding to the above key.
     * @throws RemoteException
     */
    @Override
    public void propagatePut(String key, String value) throws RemoteException {
        // TODO: First work on propagating it at all. Then deal with timeouts and acks etc...
        for (RemoteOperations srv : serverRefs) {
            logMessage(srv.remotePut(key, value, getServerIP()));
        }
    }

    /**
     * Replicates a PUT operation on a remote server
     * @param key The key to be propagated to the remote server.
     * @param value The value corresponding to the above key.
     * @param serverIP The hostname of the server the PUT request originated from.
     * @return a String message marking the success of the PUT operation.
     * @throws RemoteException
     */
    @Override
    public String remotePut(String key, String value, String serverIP) throws RemoteException {
        try {
            hMap.put(key, value);
            if (hMap.containsKey(key) && hMap.get(key).equals(value)) {
                logMessage("PUT from " + serverIP + " key: " + key + " val: " + value + " successfully propagated to this server");
                return "PUT key: " + key + " val: " + value + " successfully propagated to " + getServerIP();
            }
            logMessage("ERROR: PUT from " + serverIP + " key: " + key + " val: " + value + " unsuccessfully propagated to this server");
            return "ERROR: PUT key: " + key + " val: " + value + " unsuccessful on " + getServerIP();
        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
            return "ERROR: PUT key: " + key + " val: " + value + " unsuccessful on " + getServerIP();
        }
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
     * Establishes a connection to the other servers by getting a RemoteOperations reference to each server
     *    and storing it in an ArrayList for use in future communication.
     * Utilizes retries in order to re-attempt connection to servers that haven't been established yet.
     * @param srv This specific Server object.
     * @param serverName The name associated with this server object.
     * @throws InterruptedException
     */
    public static void connectToRemoteServers(Server srv, String serverName) throws InterruptedException {
        // Connect to every server except itself
        String[] serverNames = {"rmi-server-1", "rmi-server-2", "rmi-server-3", "rmi-server-4", "rmi-server-5"};
        for (String sName : serverNames) {
            if (!sName.equals(serverName)) {
                boolean connected = false; // Use timeout to retry connection
                for(int i = 0; i < 5 && !connected; i++) {
                    try {
                        Registry registry = LocateRegistry.getRegistry(sName, 1099); // Get local registry of remote server
                        RemoteOperations server = (RemoteOperations) registry.lookup(sName); // Get reference to remote server
                        srv.addServerRef(server); // Add remote reference of server to ArrayList
                        connected = true;
                        logMessage("Connected to " + server.getServerName());
                    } catch (Exception e) { // Retry if server hasn't been initialized yet
                        logMessage("Retrying connection to " + sName + " (" + (i+1) + "/5");
                        Thread.sleep(2000); // Wait 2 seconds before retrying
                    }
                }
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
            // **** Uncomment to run in local terminal ****
            //System.setProperty("java.rmi.server.hostname", serverIP);

            // **** Comment out to run in local terminal ****
            //System.setProperty("java.rmi.server.hostname", "rmi-server");

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

            connectToRemoteServers(srv, serverName);

        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }

    }

}
