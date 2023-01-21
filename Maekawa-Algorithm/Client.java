import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;

public class Client extends Thread {

    private static volatile int id, port;
    private static volatile List<Integer> ports = new ArrayList<>();
    private static volatile Map<Integer, List<ClientDetails>> clientDetails;
    private static volatile List<ClientDetails> clients;
    private static volatile List<Maekawa> connectedClients = new ArrayList<>();
    private static volatile Queue<List<String>> waitingQueue = new PriorityQueue<List<String>>(100, new reqComparator());

    private static class reqComparator implements Comparator<List<String>> {

        @Override
        public int compare(List<String> o1, List<String> o2) {
            Timestamp t1 = Timestamp.valueOf(o1.get(0));
            Timestamp t2 = Timestamp.valueOf(o2.get(0));
            int c1 = Integer.parseInt(o1.get(1));
            int c2 = Integer.parseInt(o2.get(1));
            if (t1.compareTo(t2) < 0) return -1;
            else if (t1.compareTo(t2) > 0) return 1;
            else {
                if (c1 < c2) return -1;
                else return 1;
            }
        }
        
    }

    private static Map<Integer, ServerDetails> serverMap = new HashMap<>();
    private static ClientServer clientServer;

    // Maekawa'a Implementation Variables
    private static volatile int cnt = 0;
    private static volatile boolean lock = false;
    private static volatile boolean cs = false;
    private static volatile int lockedClient;
    private static volatile boolean inquireSent = false, failedReceived = false, inquireReceived = false, releaseSent = false;
    private static volatile int receivedResponses;
    private static volatile Timestamp lockedClientSeq;
    private static volatile int msgWriteNum = 0;

    Client(int id) {
        addPorts();

        Client.id = id;
        port = ports.get(id);

        Quorum quorum = new Quorum();
        clientDetails = quorum.getClientDetails();

        createServerMap();
    }

    private void createServerMap() {
        ServerDetails server0 = new ServerDetails(0, 2220, "dc11.utdallas.edu");
        ServerDetails server1 = new ServerDetails(1, 2221, "dc12.utdallas.edu");
        ServerDetails server2 = new ServerDetails(2, 2222, "dc13.utdallas.edu");

        serverMap.put(0, server0);
        serverMap.put(1, server1);
        serverMap.put(2, server2);
    }

    private void addPorts() {
        ports.add(1110);
        ports.add(1111);
        ports.add(1112);
        ports.add(1113);
        ports.add(1114);
    }

    @Override
    public void run() {

        try {

            // establish connection with all the servers
            for (int s = 0; s < 3; s++) {
                ServerDetails sDetails = serverMap.get(s);
                String sAdd = sDetails.getAddress();
                int sPort = sDetails.getPort();

                Socket socketServer = new Socket(sAdd, sPort);
                System.out.println((++cnt)+" Connected to server-"+s);

                PrintWriter sOut = new PrintWriter(socketServer.getOutputStream(), true);
                sOut.println(Integer.toString(id));
                
                // add socket to server list
                sDetails.setSocketServer(socketServer);
            }

            sleep(15000);

            clients = clientDetails.get(id);

            // establishes connection with other quorum clients
            for (ClientDetails client : clients) {
                String address = client.getClientAddress();
                int port = client.getClientPort();

                Socket socket = new Socket(address, port);
                System.out.println((++cnt)+" Connected to quorum client-"+client.getClientID());
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(Integer.toString(id));

                Maekawa maekawa = new Maekawa(socket, client.getClientID(), true);
                connectedClients.add(maekawa);
                maekawa.start();
            }

            while (connectedClients.size() != 4) {
                // while loop just to ensure that clients are connected to each other
            }

            while (msgWriteNum != 5) {
                // performing request for critical section
                // sending request
                // Algorithm Step 1
                // number of responses required to enter cs - equal to quorum size
                receivedResponses = 3;  
                Timestamp reqT = new Timestamp(System.currentTimeMillis());
                
                // send request messages to quorum clients
                for (Maekawa m : connectedClients) {
                    if (m.isQuorum()) m.sendRequestMessage(reqT);
                }
                
                // if client is not locked for any other client
                // assuming client itself received its own request message
                // Algorithm Step 2
                if (!lock) {
                    System.out.println((++cnt)+" LOCKED for itself that is client-"+id);
                    sendLockedMessage(reqT, id);
                } else {
                    // add request to the waiting queue
                    List<String> l = new ArrayList<>();
                    l.add(reqT.toString());
                    l.add(String.valueOf(id));
                    waitingQueue.add(l);
                    System.out.println((++cnt)+" Request with seq-"+reqT+" by client-"+id+" added to queue");
                    
                    // check if incoming request precedes the locked request
                    boolean check = false;
                    if (reqT.compareTo(lockedClientSeq) < 0) check = true;
                    if (check) sendInquireMessage(id); // inquire sent to node with locking request
                    else failedReceived = true; // assuming the client sent the failed message to itself
                }

                // waits for receives lock responses from other quorum clients
                while (true) {
                    // Algorithm Step 5
                    if (receivedResponses == 0 && lockedClient == id) {
                        msgWriteNum++; // this is for keeping track of messages per client
                        cs = true; // set critical section to true
                        System.out.println((++cnt)+" Client-"+id+" Accessing Critical Section...");
                        
                        sendDataToServer();
                        System.out.println((++cnt)+" Client-"+id+" Done Working on Critical Section!");
                        
                        String writeData = "Client-"+id+" has written msg-"+msgWriteNum+" to file\n";
                        performFileWrite(writeData);
                        cs = false; // set critical section to false
                        
                        // Algorithm Step 6
                        // send release message to all quorum clients
                        // Algorithm Step 6
                        for (Maekawa m : connectedClients) {
                            if (m.isQuorum()) m.sendReleaseMessage();
                        }
                        releaseSent = true; // set release var to true indicating it has already released itself from cs
                        inquireReceived = false; // set inquire received to false 
                        
                        // Algorithm Step 7
                        if (waitingQueue.isEmpty()) lock = false; // if there are no outstanding requests, unset lock
                        else {
                            List<String> l = waitingQueue.poll();
                            Timestamp lt = Timestamp.valueOf(l.get(0));
                            int lid = Integer.parseInt(l.get(1));
                            sendLockedMessage(lt, lid);
                        }
                        break;
                    }
                }
                
                sleep(20000);
                
                // reset all variables for next cs execution
                inquireSent = false; inquireReceived = false; failedReceived = false; releaseSent = false;
            }
            
        } catch(IOException | InterruptedException e) {
            e.printStackTrace();
        } 
    }

    private void sendDataToServer() {
        try {
            int commits = 3;
            
            BufferedReader sIn;
            PrintWriter sOut;
            Socket sSocket;

            for (int s = 0; s < 3; s++) {
                ServerDetails sDetails = serverMap.get(s);
                sSocket = sDetails.getSocketServer();
                
                sIn = new BufferedReader(new InputStreamReader(sSocket.getInputStream()));
                sOut = new PrintWriter(sSocket.getOutputStream(), true);
                sOut.println("commit");
                System.out.println((++cnt)+" Sent commit message to server-"+s);
                
                String res = sIn.readLine();
                if (res.equals("accept")) {
                    System.out.println((++cnt)+" Received accept message from server-"+s);
                    commits--;
                }
                else {
                    System.out.println((++cnt)+" Received abort message from server-"+s);
                    break;
                }
            }

            if (commits == 0) {
                String t = new Timestamp(System.currentTimeMillis()).toString();
                String data = "Client-"+id+" writes message-"+msgWriteNum+" at time-"+t;
                
                for (int s = 0; s < 3; s++) {
                    ServerDetails sDetails = serverMap.get(s);
                    sSocket = sDetails.getSocketServer();
                    
                    sIn = new BufferedReader(new InputStreamReader(sSocket.getInputStream()));
                    sOut = new PrintWriter(sSocket.getOutputStream(), true);
                    sOut.println(data);
                    
                    String res = sIn.readLine();
                    if (res.equals("done")) System.out.println((++cnt)+" Written to file-"+s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performFileWrite(String data) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("file.txt", true));
            writer.append(data);
            writer.close();
            System.out.println((++cnt)+" Successfully written to file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendInquireMessage(int inquiredClient) {
        // send inquire only when it has not been sent before for same locking request
        if (!inquireSent) {
            inquireSent = true;
            
            // if inquire message is for the client itself
            // Algorithm Step 3
            if (lockedClient == id) {
                System.out.println((++cnt)+" Sent INQUIRE message from client-"+id+" to client-"+lockedClient);
                inquireReceived = true;
                
                // sent yield message if received failed message from other client
                if (failedReceived) {
                    inquireSent = false;
                    receivedResponses++; // cancel the locked request received from client
                    inquireReceived = false; // set to false, as we are now sending yield message
                    System.out.println((++cnt)+" Received YIELD message from client-"+id);
                    
                    // current locking request placed in the waiting queue
                    // Algorithm Step 4
                    List<String> l = new ArrayList<>();
                    l.add(lockedClientSeq.toString());
                    l.add(String.valueOf(lockedClient));
                    waitingQueue.add(l);
                    System.out.println((++cnt)+" Request with seq-"+lockedClientSeq+" by client-"+lockedClient+" added to queue");
                    
                    // most preceding request is removed from queue
                    List<String> l1 = waitingQueue.poll();
                    Timestamp l1t = Timestamp.valueOf(l1.get(0));
                    int l1id = Integer.parseInt(l1.get(1));
                    
                    sendLockedMessage(l1t, l1id);
                }
            } else {
                Maekawa c = null;
                for (Maekawa client : connectedClients) {
                    if (client.getCId()==lockedClient) c = client;
                }
                c.sendInquire();
            }
        }
    }

    private void sendLockedMessage(Timestamp time, int clientID) {
        // set locking request
        lock = true;
        lockedClient = clientID;
        lockedClientSeq = time;
        
        if (clientID != id) {
            Maekawa c = null;
            System.out.println((++cnt)+" LOCKED client id - "+clientID);
            
            for (Maekawa client : connectedClients) {
                if (client.getCId()==clientID) c = client;
            }

            c.sendLock();
        } else receivedResponses--;
        // if lock is sent to itself, then we decrement the received response
    }

    class Maekawa extends Thread {

        private int cID;
        private Socket socket;
        private BufferedReader in = null;
        private PrintWriter out = null;
        private boolean quorum;
        private boolean exit;

        Maekawa(Socket socket, int cID, boolean quorum) {
            try {
                this.socket = socket;
                this.cID = cID;
                this.quorum = quorum;
                
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                exit = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private int getCId() {
            return cID;
        }

        public BufferedReader getInputStream() {
            return in;
        }

        public PrintWriter getOutputStream() {
            return out;
        }

        public Socket getSocket() {
            return socket;
        }

        boolean isQuorum() {
            return quorum;
        }

        private void sendReleaseMessage() {
            try {
                String msg = "release;";
                out.println(msg);
                System.out.println((++cnt)+" Sent RELEASE message from client-"+id+" to client-"+cID);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void sendRequestMessage(Timestamp time) {
            try {
                String msg = "request;"+time.toString();
                out.println(msg);
                System.out.println((++cnt)+" Sent REQUEST message with time-"+time+" from client-"+id+" to client-"+cID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                System.out.println((++cnt)+" Thread started for client-"+cID);
                
                while (!exit) {
                    String msg = in.readLine();
                    if (msg==null) break;
                    
                    String[] res = msg.split(";");
                    System.out.println((++cnt)+" Msg from client-"+cID+" : "+msg);
    
                    if (res[0].equals("request")) {
                        // Algorithm Step 2
                        // lock if not locked for any other incoming client request
                        System.out.println((++cnt)+" Received REQUEST message from client-"+cID);
                        if (!lock) {
                            sendLockedMessage(Timestamp.valueOf(res[1]), cID);
                        } else {
                            // add request to waiting queue
                            List<String> l = new ArrayList<>();
                            l.add(res[1]); l.add(String.valueOf(cID));
                            waitingQueue.add(l);
                            System.out.println((++cnt)+" Request with seq-"+res[1]+" by client-"+cID+" added to queue");

                            // check if incoming request precedes the locked request
                            boolean check = false;
                            if (Timestamp.valueOf(res[1]).compareTo(lockedClientSeq) < 0) check = true;
                            if (check) sendInquireMessage(cID);
                            else sendFailedMessage();
                        }
                    } else if (res[0].equals("locked")) {
                        System.out.println((++cnt)+" Received LOCKED message from client-"+cID);
                        receivedResponses--;

                    } else if (res[0].equals("inquire")) {
                        // Algorithm Step 3
                        System.out.println((++cnt)+" Received INQUIRE message from client-"+cID);
                        inquireReceived = true; // this is used when we do not know in advance if client has received any failed messages
                        
                        // if node has already sent release message then no need to send yield
                        // if node is in critical section, defer yield reply
                        // if it receievd a failed message, then send the yield message
                        if (!releaseSent && !cs && failedReceived) sendYieldMessage();

                    } else if (res[0].equals("failed")) {
                        // Algorithm Step 3
                        System.out.println((++cnt)+" Received FAILED message from client-"+cID);
                        failedReceived = true;
                        if (inquireReceived) sendYieldMessage();

                    } else if (res[0].equals("yield")) {
                        // Algorithm Step 4
                        // this is used when a new inquire needs to be sent
                        if (cID == lockedClient) inquireSent = false; // to know it has received respose for its inquire
                        System.out.println((++cnt)+" Received YIELD message from client-"+cID);
                        
                        // current locking request placed in the waiting queue
                        List<String> l = new ArrayList<>();
                        l.add(lockedClientSeq.toString());
                        l.add(String.valueOf(lockedClient));
                        waitingQueue.add(l);
                        System.out.println((++cnt)+" Request with seq-"+lockedClientSeq+" by client-"+lockedClient+" added to queue");
                        
                        // most preceding request is removed from queue
                        List<String> l1 = waitingQueue.poll();
                        Timestamp l1t = Timestamp.valueOf(l1.get(0));
                        int l1id = Integer.parseInt(l1.get(1));
                        
                        sendLockedMessage(l1t, l1id);

                    } else if (res[0].equals("release")) {
                        if (cID == lockedClient) inquireSent = false;
                        System.out.println((++cnt)+" Received RELEASE message from client-"+cID);
                        
                        if (!waitingQueue.isEmpty()) {
                            List<String> l = waitingQueue.poll();
                            Timestamp lt = Timestamp.valueOf(l.get(0));
                            int lid = Integer.parseInt(l.get(1));
                            sendLockedMessage(lt, lid);
                        } else lock = false;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendYieldMessage() {
            receivedResponses++; // cancel the locked request received from client
            inquireReceived = false; // set to false, as we are now sending yield message
            
            try {
                String msg = "yield;";
                out.println(msg);
                System.out.println((++cnt)+" Sent YIELD message from client-"+id+" to client-"+cID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendInquire() {
            try {
                String msg = "inquire;";
                out.println(msg);
                System.out.println((++cnt)+" Sent INQUIRE message from client-"+id+" to client-"+cID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendLock() {
            try {
                String msg = "locked;";
                out.println(msg);
                System.out.println((++cnt)+" Sent LOCKED message from client-"+id+" to client-"+cID);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void sendFailedMessage() {
            try {
                String msg = "failed;";
                out.println(msg);
                System.out.println((++cnt)+" Sent FAILED message from client-"+id+" to client-"+cID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    class ClientServer extends Thread {

        private boolean exit;

        @Override
        public void run() {
            try {
                exit = false;
                
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println((++cnt)+" Socket server started");    
                
                while (!exit) {
                    Socket socket = serverSocket.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    int id3 = Integer.parseInt(input.readLine());
                    System.out.println((++cnt)+" Server Socket accepted connection from client-"+id3);
                    
                    Maekawa maekawa = new Maekawa(socket, id3, false);
                    connectedClients.add(maekawa);
                    
                    maekawa.start();
                }

                serverSocket.close();
    
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int id2 = sc.nextInt();
        
        Client client = new Client(id2);
        
        sc.close();
        
        client.start();
        
        clientServer = client.new ClientServer();
        clientServer.start();
    }
}
