import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TwoPCInterface extends Remote {

    boolean TwoPhaseCommit(String operation, String key, String value) throws RemoteException;

}
