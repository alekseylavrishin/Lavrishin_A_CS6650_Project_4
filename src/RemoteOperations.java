import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote Interface which declares the necessary remote methods
 *  for use with the Remote Procedure Call functionality of the client/server program.
 */
public interface RemoteOperations extends Remote {
    String createRecord(String key, String value, String serverIP) throws RemoteException;
    String getRecord(String key, String serverIP) throws RemoteException;
    String deleteRecord(String key, String serverIP) throws RemoteException;
    String getServerIP() throws RemoteException;
    String getServerName() throws RemoteException;
    void propagatePut(String key, String value) throws RemoteException;
    public String remotePut(String key, String value, String serverIP) throws RemoteException;
    ConcurrentHashMap<String, String> getHMap() throws RemoteException;

}
