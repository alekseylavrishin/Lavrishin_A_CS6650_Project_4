import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

/**
 * Remote Interface which declares the necessary remote methods
 *  for use with the Remote Procedure Call functionality of the client/server program.
 */
public interface RemoteOperations extends Remote {
    String createRecord(String key, String value, String serverIP) throws RemoteException;
    String getRecord(String key, String serverIP) throws RemoteException;
    String deleteRecord(String key, String serverIP) throws RemoteException;
    String getServerIP() throws RemoteException;
}
