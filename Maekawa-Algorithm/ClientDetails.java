public class ClientDetails {
        
    int id, port;
    String address;

    ClientDetails(int id, String address, int port) {
        this.id = id;
        this.address = address;
        this.port = port;
    }

    public int getClientID() {
        return id;
    }

    public String getClientAddress() {
        return address;
    }

    public int getClientPort() {
        return port;
    }
}
