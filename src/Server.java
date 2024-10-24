import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Server {

    /**
     * Used to communicate with TCPClient to perform PUT, GET, DELETE operations over TCP protocol.
     * @param serverIP The IP Address or hostname the server will be hosted on.
     * @param port The port the server will listen on.
     * @param hMap The HashMap used to store and perform operations on Key, Value pairs specified by the TCPClient.
     */
    public static void TCPServer(String serverIP, int port, HashMap<String, String> hMap) {

        try {
            // Translate String IP or hostname to InetAddress type
            InetAddress ip = InetAddress.getByName(serverIP);

            ServerSocket listenSocket = new ServerSocket(port, 50, ip);
            logMessage("Server listening on IP " + ip + " port " + port);

            while(true) { // Server listens until ctrl-c is pressed or exception occurs
                // Look for and accept single incoming connection
                Socket clientSocket = listenSocket.accept();
                logMessage("Connection accepted on IP " + ip + " port " + port + " over TCP");

                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                try {
                    // listen for type of operation: PUT, GET, DELETE
                    String operation = in.readUTF();

                    logMessage("Received " + operation + " request from " + ip + " port " + port);

                    if(operation.equals("PUT")){
                        // confirm to server that PUT operation is commencing
                        out.writeUTF("Server initializing PUT operation");
                        logMessage("Server initializing PUT operation");

                        // Get key from client
                        String key = in.readUTF();
                        out.writeUTF("Key " + key + " received by server");
                        logMessage(("Key " + key + " received by server"));

                        // Get value from client
                        String value = in.readUTF();
                        out.writeUTF("Value " + value + " received by server");
                        logMessage("Value " + value + " received by server");

                        // Write key, value to hMap
                        hMap.put(key, value);
                        out.writeUTF("Key: " + key + " Value: " +  value + " have been written to the server");
                        logMessage("Key: " + key + " Value: " +  value + " have been written to the server");

                    } else if(operation.equals("GET")) {
                        // confirm to server that GET operation is commencing
                        out.writeUTF("Server initializing GET operation");
                        logMessage("Server initializing GET operation");

                        String key = in.readUTF();
                        out.writeUTF("Key " + key + " received by server");
                        logMessage(("Key " + key + " received by server"));

                        if(hMap.containsKey(key)) {
                            // Return value to client
                            String value = hMap.get(key);
                            out.writeUTF("Value for " + key + ": " + value);
                            logMessage("Value for " + key + ": " + value);

                        } else { // If key cannot be found in hMap
                            // Return 'cannot be found' message to client
                            out.writeUTF("Key " + key + " cannot be found");
                            logMessage("Key " + key + " cannot be found");

                        }

                    } else if(operation.equals("DELETE")) {
                        // confirm to server that DELETE operation is commencing
                        out.writeUTF("Server initializing DELETE operation");
                        logMessage("Server initializing DELETE operation");

                        String key = in.readUTF();
                        out.writeUTF("Key " + key + " received by server");
                        logMessage("Key " + key + " received by server");

                        if(hMap.containsKey(key)) {
                            // If key exists, delete key from hMap
                            hMap.remove(key);
                            out.writeUTF("Key " + key + " deleted from server");
                            logMessage("Key " + key + " deleted from server");
                        } else {
                            // If key is not found in server
                            out.writeUTF("Key " + key +  " cannot be found in server");
                            logMessage("Key " + key +  " cannot be found in server");
                        }

                    } else {
                        // Faulty operation provided, send back error message
                        out.writeUTF("SERVER ERROR: Faulty operation detected");
                        logMessage("SERVER ERROR: Faulty operation detected");
                    }

                } catch (Exception e) {
                    logMessage("Error handling client request: " + e.getMessage());
                } finally {
                    clientSocket.close();
                    logMessage("Client connection to" + ip + " " + port + " closed");
                }
            }
        } catch (UnknownHostException e) {
            logMessage("UnknownHostException: " + e.getMessage());
        } catch (IOException e) {
            logMessage("RuntimeException " + e.getMessage());
        }

    }

    /**
     * Used to communicate with UDPClient to perform PUT, GET, DELETE operations over UDP protocol.
     * @param serverIP The IP Address or hostname the server will be hosted on.
     * @param port The port the server will listen on.
     * @param hMap The HashMap used to store and perform operations on Key, Value pairs specified by the UDPClient.
     */
    public static void UDPServer(String serverIP, int port, HashMap<String, String> hMap) {
        DatagramSocket s = null;
        try {
            String val;
            byte[] byteResponse;

            // Translate String IP or hostname to InetAddress type
            InetAddress ip = InetAddress.getByName(serverIP);

            // Create new socket at provided port and InetAddress
            s = new DatagramSocket(port, ip);

            byte[] buffer = new byte[1024];
            while(true) { // Server listens until ctrl-c is pressed or exception occurs

                // Get type of incoming request from client
                DatagramPacket typePacket = new DatagramPacket(buffer, buffer.length);
                s.receive(typePacket); // Get type of request
                String typeMsg = new String(typePacket.getData(), 0, typePacket.getLength());

                logMessage("Connection accepted on IP " + ip + "port " + port + " over UDP");
                logMessage("Received " + typeMsg + " request from " + typePacket.getAddress() + " port " + typePacket.getPort());

                // Get key from client
                DatagramPacket keyPacket = new DatagramPacket(buffer, buffer.length);
                s.receive(keyPacket); // Get type of request
                String keyMsg = new String(keyPacket.getData(), 0, keyPacket.getLength());

                logMessage("Key " + keyMsg + "from " + typePacket.getAddress() + " port " +
                        typePacket.getPort() + " received by the server");

                if(typeMsg.equals("PUT")) {
                    // If operation is PUT, retrieve value
                    DatagramPacket valPacket = new DatagramPacket(buffer, buffer.length);
                    s.receive(valPacket);
                    String valMsg = new String(valPacket.getData(), 0, valPacket.getLength());
                    logMessage("Value " + valMsg + "from " + typePacket.getAddress() + " port " +
                            typePacket.getPort() + " received by the server");

                    hMap.put(keyMsg, valMsg);

                    logMessage("Key: " + keyMsg + " Value: " +  valMsg + " have been written to the server");

                    // Send confirmation to client of successful operation
                    byteResponse = ("Entry for " + keyMsg + " successfully created").getBytes();
                    DatagramPacket response = new DatagramPacket(byteResponse, byteResponse.length,
                            typePacket.getAddress(), typePacket.getPort());
                    s.send(response);

                } else if (typeMsg.equals("GET")) {
                    if(hMap.containsKey(keyMsg)) { // if key exists, return value
                        val = "Value for " + keyMsg + ": " + hMap.get(keyMsg);
                    } else { // Else return 'cannot be found' message
                        val = ("Key " + keyMsg + " cannot be found in server");
                    }
                    logMessage(val);
                    byteResponse = val.getBytes();
                    DatagramPacket response = new DatagramPacket(byteResponse, byteResponse.length,
                            typePacket.getAddress(), typePacket.getPort());
                    s.send(response);

                } else if(typeMsg.equals("DELETE")) {
                    if(hMap.containsKey(keyMsg)) { // if key exists, return value
                        hMap.remove(keyMsg);
                        val = "Key " + keyMsg + " deleted from server";

                    } else { // Else return 'cannot be found' message
                        val = ("Key " + keyMsg + " cannot be found in server");

                    }
                    logMessage(val);
                    byteResponse = val.getBytes();
                    DatagramPacket response = new DatagramPacket(byteResponse, byteResponse.length,
                            typePacket.getAddress(), typePacket.getPort());
                    s.send(response);

                } else {
                    // Faulty operation provided
                    logMessage("SERVER ERROR: Faulty operation detected");
                    byteResponse = "SERVER ERROR: Faulty operation detected".getBytes();
                    DatagramPacket response = new DatagramPacket(byteResponse, byteResponse.length,
                            typePacket.getAddress(), typePacket.getPort());
                    s.send(response);
                }
            }

        } catch (SocketException e) {
            logMessage(e.getMessage());
        } catch (IOException e) {
            logMessage(e.getMessage());
        }
    }

    /**
     * Asks the user for the communication type they wish to use with the server.
     * User must enter '1' for TCP or '2' for UDP.
     * If provided input is not '1' or '2', function will rerun until appropriate input is given.
     * @param scanner The Scanner used for taking user input from System.in.
     */
    public static void askForCommType(Scanner scanner, String serverIP, int port, HashMap<String,String> hMap) {
        try {
            System.out.println("Enter '1' to use TCP or enter '2' to use UDP");
            int selection = scanner.nextInt();

            if (selection == 1) {
                logMessage("TCP Communication Selected");
                TCPServer(serverIP, port, hMap);

            } else if (selection == 2) {
                logMessage("UDP Communication Selected");
                UDPServer(serverIP, port, hMap);

            } else { // Rerun if input doesn't match '1' or '2'
                logMessage("Invalid Input");
                askForCommType(scanner, serverIP, port, hMap);
            }
        } catch (InputMismatchException e) {
            logMessage("Input mismatch detected: exiting");
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


    public static void main(String[] args) {
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

        // Create scanner for selecting TCP or UDP
        Scanner scanner = new Scanner(System.in);
        askForCommType(scanner, serverIP, port, hMap);

    }
}
