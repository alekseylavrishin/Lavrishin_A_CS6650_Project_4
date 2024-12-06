import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remote Interface which declares the necessary remote methods
 *  for use with the Remote Procedure Call functionality of the client/server program.
 */
public interface RemoteOperations extends Remote {
    String getRecord(String key, String serverIP) throws RemoteException;
    String getServerIP() throws RemoteException;
    String getServerName() throws RemoteException;
    String prepare(int proposalId) throws RemoteException;
    String acceptRequest(int proposalId, String value) throws RemoteException;
    void learn(String value) throws RemoteException;
    String propose(String value) throws RemoteException;
}
