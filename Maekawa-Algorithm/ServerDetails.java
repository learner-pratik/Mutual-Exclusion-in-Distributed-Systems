import java.net.Socket;

public class ServerDetails {
    
    private int sID, sPort;
    private String address;
    private Socket socketServer;

    ServerDetails(int sID, int sPort, String address) {
        this.sID = sID;
        this.sPort = sPort;
        this.address = address;
    }

    public int getID() {
        return sID;
    }

    public int getPort() {
        return sPort;
    }

    public String getAddress() {
        return address;
    }

    public void setSocketServer(Socket socketServer) {
        this.socketServer = socketServer;
    }

    public Socket getSocketServer() {
        return socketServer;
    }
}
