import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Client interacts with the Java RMI server to perform PUT, GET, DELETE operations
 *   over key/value pairs stored on the Server.
 */
public class Client {

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
     * Programmatically tests PUT, GET, DELETE operations on the RMI server.
     * The test operations are hardcoded in this function.
     * @param serverIP The IP Address or hostname of the RMI server.
     * @param stub The reference to the RMI server
     */
    public static void testOperations(String serverIP, RemoteOperations stub) throws RemoteException {
        // Programmatically perform 5 PUT operations
        PUTOperation("Key1", "Value1", serverIP, stub);
        PUTOperation("Key2", "Value2", serverIP, stub);
        PUTOperation("Key3", "Value3", serverIP, stub);
        PUTOperation("Key4", "Value4", serverIP, stub);
        PUTOperation("Key5", "Value5", serverIP, stub);

        // Programmatically perform 5 GET operations
        GETOperation("Key1", serverIP, stub);
        GETOperation("Key2", serverIP, stub);
        GETOperation("Key3", serverIP, stub);
        GETOperation("Key4", serverIP, stub);
        GETOperation("Key5", serverIP, stub);

        // Programmatically perform 5 DELETE operations
        DELETEOperation("Key1", serverIP, stub);
        DELETEOperation("Key2", serverIP, stub);
        DELETEOperation("Key3", serverIP, stub);
        DELETEOperation("Key4", serverIP, stub);
        DELETEOperation("Key5", serverIP, stub);

        // Programmatically populate RMI server with 5 key/value pairs
        PUTOperation("Key1", "Value1", serverIP, stub);
        PUTOperation("Key2", "Value2", serverIP, stub);
        PUTOperation("Key3", "Value3", serverIP, stub);
        PUTOperation("Key4", "Value4", serverIP, stub);
        PUTOperation("Key5", "Value5", serverIP, stub);
    }

    /**
     * Utilizes RMI to perform a PUT operation for a key/value pair on the server.
     * @param key The key to be saved to the server.
     * @param value The corresponding value to be saved to the server.
     * @param serverIP The IP Address or hostname of the server.
     * @param stub The reference to the RMI server.
     * @throws RemoteException
     */
    public static void PUTOperation(String key, String value, String serverIP, RemoteOperations stub) throws RemoteException {
        String result = stub.createRecord(key, value, serverIP);
        logMessage(result);
        logMessage("Connection closed to " + stub.getServerIP());
    }

    /**
     * Utilizes RMI to perform a GET operation on a key stored on the RMI server.
     * @param key The key to retrieve the corresponding value for.
     * @param serverIP The IP Address or hostname of the server.
     * @param stub The reference to the RMI server.
     * @throws RemoteException
     */
    public static void GETOperation(String key, String serverIP, RemoteOperations stub) throws RemoteException {
        String result = stub.getRecord(key, serverIP);
        logMessage(result);
        logMessage("Connection closed to " + stub.getServerIP());
    }

    /**
     * Utilizes RMI to perform a DELETE operation on a key stored on the RMI server.
     * @param key The key corresponding with the record to be deleted on the RMI server.
     * @param serverIP The IP Address or hostname of the server.
     * @param stub The reference to the RMI server.
     * @throws RemoteException
     */
    public static void DELETEOperation(String key, String serverIP, RemoteOperations stub) throws RemoteException {
        String result = stub.deleteRecord(key, serverIP);
        logMessage(result);
        logMessage("Connection closed to " + stub.getServerIP());
    }

    /**
     * Interacts with user to select the type of operation to perform on the RMI server.
     * Gathers necessary key/value information and passes it to the respective
     *   GET/PUT/DELETEOperation functions.
     * User enters '1' for PUT, '2' for GET, '3' for DELETE.
     * @param scanner Gets command line input from user.
     * @param stub The reference to the RMI server.
     * @param serverIP The IP Address or hostname of the server.
     */
    public static void askForOperationType(Scanner scanner, RemoteOperations stub, String serverIP) {
        try {
            System.out.println("Enter '1' to perform PUT");
            System.out.println("Enter '2' to perform GET");
            System.out.println("Enter '3' to perform DELETE");
            System.out.println("Enter '4' to programmatically test 5 of each operation");
            int selection = scanner.nextInt();
            scanner.nextLine(); // deal with \n left by scanner.nextInt()

            if(selection == 1) {
                logMessage("PUT operation selected");
                logMessage("Enter key to PUT: ");
                String key = scanner.nextLine();
                logMessage("Enter value to PUT: ");
                String value = scanner.nextLine();
                PUTOperation(key, value, serverIP, stub);

            } else if(selection == 2) {
                logMessage("GET operation selected");
                logMessage("Enter key to GET: ");
                String key = scanner.nextLine();
                GETOperation(key, serverIP, stub);

            } else if(selection == 3) {
                logMessage("DELETE operation selected");
                logMessage("Enter key to DELETE");
                String key = scanner.nextLine();
                DELETEOperation(key, serverIP, stub);

            } else if (selection == 4){
                testOperations(serverIP, stub);
            } else { // rerun function if input not '1', '2', '3', or '4'
                logMessage("Invalid input detected");
                askForOperationType(scanner, stub, serverIP);
            }

        } catch (InputMismatchException e) {
            logMessage("ERROR: Input mismatch detected: exiting");
        } catch (RemoteException e) {
            logMessage("ERROR: " + e.getMessage());
        }
    }

    /**
     * Allows the client to select which server it wishes to connect to.
     * @param scanner Takes user input in the form of an int from 1-5 to determine which server to connect to.
     * @return An int from 1-5 specifying which server to connect to.
     */
    public static int askForServer(Scanner scanner) {
        try {
            while(true) {
                System.out.println("Enter '1' to access Server #1");
                System.out.println("Enter '2' to access Server #2");
                System.out.println("Enter '3' to access Server #3");
                System.out.println("Enter '4' to access Server #4");
                System.out.println("Enter '5' to access Server #5");
                int selection = scanner.nextInt();
                scanner.nextLine(); // deal with \n character
                if (selection > 0 && selection < 6) {
                    return selection - 1;
                }
            }
        } catch (Exception e) {
            scanner.nextLine(); // deal with invalid input
            logMessage("ERROR: Invalid input detected - defaulting to Server 1");
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2){ // Check that 2 args are provided
            logMessage("ERROR: Proper input format must be 'java Client.java <client_ip> <port>'");
            return;
        }

        String serverIP = null;
        int port = -1;

        // Ensure provided args are correct types
        try{
            serverIP = args[0];
            port = Integer.parseInt(args[1]);
        } catch (Exception e) {
            logMessage("ERROR: <client_ip> must be type String and <port> must be type int");
        }

        try {
            // **** Uncomment to run in local terminal ****
            //Registry registry = LocateRegistry.getRegistry(serverIP);

            // **** Comment out to run in local terminal ****

            String[] serverNames = {"rmi-server-1", "rmi-server-2", "rmi-server-3", "rmi-server-4", "rmi-server-5"};
            // Create scanner for accepting user input
            Scanner scanner = new Scanner(System.in);
            int selection = askForServer(scanner);
            //Registry registry = LocateRegistry.getRegistry("rmi-server", 1099);
            Registry registry = LocateRegistry.getRegistry(serverNames[selection], 1099);

            //RemoteOperations stub = (RemoteOperations) registry.lookup("RemoteOperations");
            // TODO: make server selection way more graceful
            RemoteOperations stub = (RemoteOperations) registry.lookup(serverNames[selection]);
            logMessage("Connected to server on host " + stub.getServerIP());

            // Programmatically test GET, PUT, DELETE operations
            //testOperations(serverIP, stub);


            askForOperationType(scanner, stub, serverIP);

        } catch (UnknownHostException e) {
            logMessage("ERROR: UnknownHostException - Host is unreachable");
        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
        }
    }
}
