import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class Server {
    
    private int sID, sPort, cnt;
    Map<Integer, Integer> mapPorts = new HashMap<>();
    Map<Integer, String> filePaths = new HashMap<>();

    Server(int sID) {
        addPorts();
        setFilePaths();
        this.sID = sID;
        this.sPort = mapPorts.get(sID);
        startServerSocket();
    }

    private void setFilePaths() {
        filePaths.put(0, "file0.txt");
        filePaths.put(1, "file1.txt");
        filePaths.put(2, "file2.txt");
    }

    private void addPorts() {
        mapPorts.put(0, 2220);
        mapPorts.put(1, 2221);
        mapPorts.put(2, 2222);
    }

    private void startServerSocket() {

        try {
            ServerSocket serverSocket = new ServerSocket(sPort);
            System.out.println((++cnt)+" Server Socket started for server-"+sID);
            
            while (true) {
                Socket socket = serverSocket.accept();
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                int id = Integer.parseInt(input.readLine());
                System.out.println((++cnt)+" Server Socket accepted connection from client-"+id);
                
                ClientServer cs = new ClientServer(socket, id);
                cs.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void writeToFile(String msg) {
        try {
            String path = filePaths.get(sID);
            BufferedWriter writer = new BufferedWriter(new FileWriter(path, true));
            writer.append(msg+"\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ClientServer extends Thread {

        int cID;
        Socket cSocket;
        BufferedReader cInput = null;
        PrintWriter cOutput = null;

        ClientServer(Socket cSocket, int cID) {
            try {
                this.cSocket = cSocket;
                this.cID = cID;
                
                cInput = new BufferedReader(new InputStreamReader(cSocket.getInputStream()));
                cOutput = new PrintWriter(cSocket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                Random random = new Random();
                String msg = "open";
                System.out.println((++cnt)+" Thread started for server communication with client-"+cID);
                
                while (true) {
                    msg = cInput.readLine();
                    
                    if (msg.equals("commit")) {
                        if (random.nextDouble() < 0.1) {
                            System.out.println((++cnt)+" ABORTED write request from client-"+cID);
                            cOutput.println("abort");
                        } else {
                            System.out.println((++cnt)+" ACCEPTED write request from client-"+cID);
                            cOutput.println("accept");
                        }
                    } else {
                        writeToFile(msg);
                        
                        System.out.println((++cnt)+" WRITTEN msg to file-"+sID+" from client-"+cID);
                        cOutput.println("done");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int SID = sc.nextInt();
        
        new Server(SID);
        
        sc.close();
    }
}
