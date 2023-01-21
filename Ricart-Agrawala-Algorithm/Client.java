import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Client {
    
    private ClientThread cThread0 = null;
    private ClientThread cThread1 = null;
    private ClientThread cThread2 = null;
    private ClientThread cThread3 = null;
    private ClientThread cThread4 = null;

    public static void main(String[] args) {

        // start client threads
        Client client = new Client();
        client.cThread0 = new ClientThread("0");
        client.cThread1 = new ClientThread("1");
        client.cThread2 = new ClientThread("2");
        client.cThread3 = new ClientThread("3");
        client.cThread4 = new ClientThread("4");

        client.cThread0.start();
        client.cThread1.start();
        client.cThread2.start();
        client.cThread3.start();
        client.cThread4.start();
    }
    
}

class ClientThread extends Thread {

    // client details
    private String clientId;
    private int serverId;
    int serverPort = 5000;
    final static Map<Integer, String> serverMap = new HashMap<>();
    
    // socket and stream connections for ports
    private Socket socket = null;
    private DataInputStream input = null;                   
	private DataOutputStream output	 = null;  
    private String message = null;              
    private ByteArrayOutputStream bOutputStream = null;     
    private byte buffer[] = null;                           
    private byte result[] = null;   
    private String response = null;  
    private String[] files = null;    

    // establish connection with the servers
    ClientThread(String clientId) {
        this.clientId = clientId;

        serverMap.put(0, "dc21.utdallas.edu");
        serverMap.put(1, "dc22.utdallas.edu");
        serverMap.put(2, "dc23.utdallas.edu");

        serverId = new Random().nextInt(3);

        try {
            socket = new Socket(serverMap.get(serverId), serverPort);
            System.out.println("Client" + clientId + " connected");
			
            input = new DataInputStream(socket.getInputStream());
			output = new DataOutputStream(socket.getOutputStream());
            bOutputStream = new ByteArrayOutputStream();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {

        try {
            // send enquire message
            message = "enquire";
            output.write(message.getBytes());
            System.out.println(clientId + " client sent enquire message");

            // receive enquire message
            buffer = new byte[2048];
            bOutputStream.write(buffer, 0 , input.read(buffer));
            result = bOutputStream.toByteArray();
            response = new String(result, StandardCharsets.UTF_8);
            buffer = null;
            System.out.println(clientId + " client received enquire response");

            files = response.split(";")[1].split(",");

            // send 5 write messages and receive responses
            int msgCount = 0;
            while (msgCount != 5) {

                int fileId = new Random().nextInt(files.length);
                String timestamp = new Timestamp(System.currentTimeMillis()).toString();
                message = files[fileId]+";<"+clientId+","+timestamp+">";
                output.write(message.getBytes());
                System.out.println(clientId + " client sent write message");

                buffer = new byte[2048];
                bOutputStream.write(buffer, 0 , input.read(buffer));
                result = bOutputStream.toByteArray();
                response = new String(result, StandardCharsets.UTF_8);
                buffer = null;
                System.out.println(clientId + " client received write response");

                msgCount++;
            }

            // close client socket.
            output.write("close".getBytes());
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
