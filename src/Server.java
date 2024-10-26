import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Java RMI Server implementing the RemoteOperations interface.
 * Communicates with the Client using Remote Method invocation to perform
 *   PUT, GET, DELETE operations over key/value pairs stored on the Server.
 */
public class Server implements RemoteOperations{
    private HashMap<String, String> hMap;


    public Server(HashMap<String, String> hMap) throws RemoteException {
        this.hMap = hMap;
    }

    /**
     * Getter for this.hMap
     * @return The HashMap used by the server.
     */
    public HashMap<String, String> getHMap() {
        return this.hMap;
    }

    /**
     * Setter for this.hMap.
     * @param newMap The new HashMap to be used by the server.
     */
    public void setHMap(HashMap<String, String> newMap) {
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
     * Returns the IP Address or hostname the Server is running on.
     * @return The IP or hostname the Server is using.
     * @throws RemoteException
     */
    @Override
    public String getServerIP() throws RemoteException{
        return System.getProperty("java.rmi.server.hostname");
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
        if(args.length != 2){
            logMessage("Proper input format must be 'java Server.java <server_ip> <port>'");
            return;
        }

        String serverIP = null;
        int port = -1;

        // Stores all keys, values provided by client
        HashMap<String, String> hMap = new HashMap<>();

        try {
            serverIP = args[0];
            port = Integer.parseInt(args[1]);

        } catch (Exception e) {
            logMessage("<server_ip> must be type String and <port> must be type int");
        }


        try {
            // Set server IP to specified value

            // **** Uncomment to run in local terminal ****
            //System.setProperty("java.rmi.server.hostname", serverIP);

            // **** Comment out to run in local terminal ****
            System.setProperty("java.rmi.server.hostname", "rmi-server");


            // Create remote object providing RMI service
            Server srv = new Server(hMap);
            // Export srv to Java RMI runtime to accept incoming RMI calls on specified port
            RemoteOperations stub = (RemoteOperations) UnicastRemoteObject.exportObject(srv, port);

            // Create registry on port 1099
            Registry registry = LocateRegistry.createRegistry(1099);

            // Bind server reference to registry for client accessibility
            registry.bind("RemoteOperations", stub);
            logMessage("Server initialized on host " + System.getProperty("java.rmi.server.hostname") + " port " + port);
        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }

    }

}
