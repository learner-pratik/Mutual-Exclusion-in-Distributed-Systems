import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

public class Server {

    private volatile int serverId;
    
    // socket and stream connections for ports
    private volatile ServerSocket serverSocket = null, clientSocket = null;
    private volatile Socket serverConnection1 = null, serverConnection2 = null;
    private volatile int clientPort = 5000, serverPort = 4000;
    private volatile DataInputStream inputServer1 = null, inputServer2 = null;
    private volatile DataOutputStream outputServer1 = null, outputServer2 = null;

    // variables for mapping server addresses
    final static Map<Integer, String> serverMap = new HashMap<>();
    final static Map<Integer, String> fileMap = new HashMap<>();
    final static Map<Integer, DataOutputStream> connectionMap = new HashMap<>();
    final List<ClientManager1> clientManagerList = new ArrayList<>();

    // list for storing server write requests
    static volatile Queue<WriteRequest> clientRequestList1 = new LinkedList<WriteRequest>();

    // params for mutual exclusion
    private volatile int sequenceNumber;
    private volatile int highestSequenceNumber = 0;
    private volatile int outstandingReplyCount;
    private volatile boolean requestingCriticalSection = false;
    private volatile boolean[] replyDeferred = new boolean[3];

    // params for selecting write request
    private volatile int serverRequestNumber;
    private volatile int highestServerRequestNumber = 0;
    private volatile int serverReplyCount;
    private volatile boolean requestingFileWriteRequest = false;
    private volatile boolean[] writeRequestDeferred = new boolean[3];

    // checking critical section execution
    private volatile boolean criticalSection = false;
    private volatile Integer criticalSectionFile;
    private volatile Integer criticalSectionDone = -1;

    public Server(int serverId) {

        this.serverId = serverId;

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // mapping server address
        mapServerAddresses();
        
        // establishing connection with the servers
        establishServerConnections();

        // mapping file locations
        createFileMap();

        // accepting and processing client connections
        acceptClientConnections();

        // accepting and processing server write requests
        acceptServerWriteRequest();

        // removing clients which have been closed
        removeClosedClients();
    }

    private void createFileMap() {
        String directory = "server"+serverId;
        fileMap.put(0, directory+"file0.txt");
        fileMap.put(1, directory+"file1.txt");
        fileMap.put(2, directory+"file2.txt");
        fileMap.put(3, directory+"file3.txt");
    }

    private void acceptServerWriteRequest() {
        
        // create data streams
        createDataStreams();

        // receive and process all responses from the two servers
        processServerResponses();

        // selecting write request and performing mutual exclusion
        performWriteRequest();
    }

    private void performWriteRequest() {

        WriteRequest cRequest = null;
        try {
            // mapping server id and streams
            String s1 = "check;"+serverId;
            String s2 = "check;"+serverId;
            outputServer1.write(s1.getBytes());
            outputServer2.write(s2.getBytes());
            System.out.println("Sent Server ID");

            while (true) {
                if (!clientRequestList1.isEmpty() && !criticalSection) {
    
                    // sent file write request
                    requestingFileWriteRequest = true;
                    serverRequestNumber = highestServerRequestNumber+1;
                    String request = "server;"+serverId+";"+serverRequestNumber;
                    outputServer1.write(request.getBytes());
                    outputServer2.write(request.getBytes());
                    System.out.println("Sent file write request");
    
                    serverReplyCount = 2;
                    cRequest = clientRequestList1.peek();
    
                    // waiting for reply for the request
                    new Thread(new Runnable() {
                        WriteRequest cRequest1 = clientRequestList1.peek();
                        
                        @Override
                        public void run() {
                            try {
                                // send request starting mutual exclusion
                                while (true) {
                                    if (serverReplyCount == 0) {
                                        String f = cRequest1.getData().split(";")[0];
                                        criticalSectionFile = Integer.parseInt(f);
                                        
                                        String request1 = "cs;"+f;
                                        outputServer1.write(request1.getBytes());
                                        outputServer2.write(request1.getBytes());
                                        
                                        criticalSection = true;
                                        System.out.println("Sent request startig mutual exclusion");
                                        break;
                                    }
                                }
                                
                            } catch (IOException e) {
                                e.printStackTrace();
                            }                                    
                        }
                        
                    }).start();

                }

                // starting mutual exclusion for particular file
                if (criticalSection) {
                    new Thread(new Runnable() {
                        WriteRequest cRequest2 = clientRequestList1.poll();
                        
                        @Override
                        public void run() {
                            try {
                                // send request
                                criticalSectionDone = 3;
                                requestingCriticalSection = true;
                                sequenceNumber = highestSequenceNumber+1;
                                
                                String meRequest = "me;"+serverId+";"+sequenceNumber;
                                outputServer1.write(meRequest.getBytes());
                                outputServer2.write(meRequest.getBytes());
                                System.out.println("Sent request for critical section execution");
                
                                // after getting reply, perform file write
                                outstandingReplyCount = 2;
                                while (true) {
                                    if (outstandingReplyCount == 0) {
                                        performFileWrite(cRequest2.getData());
                                        System.out.println("File write performed");
                                        
                                        for (int index = 0; index < 3; index++) {
                                            if (index != serverId) {
                                                // send deferred replies
                                                if (replyDeferred[index]) {
                                                    connectionMap.get(index).write("me;reply".getBytes());
                                                }
                                            }
                                        }
                                        
                                        criticalSectionDone--;
                                        outputServer1.write("me;close".getBytes());
                                        outputServer2.write("me;close".getBytes());
                                        break;
                                    }
                                }

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        
                    }).start();

                    // once all servers are done, exit critical section
                    while (true) {
                        if (criticalSectionDone == 0) {
                            criticalSection = false;
                            cRequest.requesComplete();
                            
                            for (int index = 0; index < 3; index++) {
                                if (index != serverId) {
                                    if (writeRequestDeferred[index]) {
                                        try {
                                            // send deferred write request
                                            connectionMap.get(index).write("server;reply".getBytes());
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performFileWrite(String data) {
        try {
            FileWriter writerFile = new FileWriter(fileMap.get(criticalSectionFile));
            writerFile.write(data);
            writerFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processServerResponses() {
        Thread threadServer1 = new ServerRequest(1, inputServer1, outputServer1);
        Thread threadServer2 = new ServerRequest(2, inputServer2, outputServer2);

        threadServer1.start();
        threadServer2.start();
    }

    private void createDataStreams() {
        try {
            inputServer1 = new DataInputStream(serverConnection1.getInputStream());
            inputServer2 = new DataInputStream(serverConnection2.getInputStream());

            outputServer1 = new DataOutputStream(serverConnection1.getOutputStream());
            outputServer2 = new DataOutputStream(serverConnection2.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeClosedClients() {

        // thread for removing client threads once all client requests have been processed
        Thread listManagerThread = new Thread(new Runnable() {

            private volatile boolean running = true;
            @Override
            public void run() {
                
                while (running) {
                    Iterator<ClientManager1> itr = clientManagerList.iterator();
                    
                    while(itr.hasNext()) {
                        ClientManager1 manager = itr.next();
                        if (manager.isSocketClosed()) {
                            clientManagerList.remove(manager);
                            manager = null;
                        }
                    }

                    if (clientManagerList.isEmpty()) {
                        stopRunning();
                    }
                }
            }

            // close server sockets once all clients have been disconnected
            public void stopRunning() {
                try {
                    serverConnection1.close();
                    serverConnection2.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                running = false;
            }
        });
        
        listManagerThread.start();
    }

    private void acceptClientConnections() {
        // thread for accepting client connections
        try {
            clientSocket = new ServerSocket(clientPort);
            System.out.println("Accepting Client connections");

            Thread clientManagerThread =  new Thread(new Runnable() {
                @Override
                public void run() {
                    do {
                        Socket socket;
                        
                        // create a new thread when every client connection is created
                        try {
                            socket = clientSocket.accept();
                            ClientManager1 clientManager = new ClientManager1(socket, serverId);
                            clientManagerList.add(clientManager);
                            System.out.println("client connected");
                            
                            clientManager.start();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } while(!clientManagerList.isEmpty()); 
                }
            });
            
            clientManagerThread.start();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mapServerAddresses() {
        serverMap.put(0, "dc21.utdallas.edu");
        serverMap.put(1, "dc22.utdallas.edu");
        serverMap.put(2, "dc23.utdallas.edu");
        
        serverMap.remove(serverId);
    }

    private void establishServerConnections() {

        try {
            if (serverId == 0) {
                serverSocket = new ServerSocket(serverPort);
                
                serverConnection1 = serverSocket.accept();
                serverConnection2 = serverSocket.accept();
            } else if (serverId == 1) {
                serverSocket = new ServerSocket(serverPort);
                
                serverConnection1 = serverSocket.accept();
                serverConnection2 = new Socket(serverMap.get(0), serverPort);
            } else {
                serverConnection1 = new Socket(serverMap.get(0), serverPort);
                serverConnection2 = new Socket(serverMap.get(1), serverPort);
            }
            
            System.out.println("Servers connected");
        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    // thread for accepting server requests
    private class ServerRequest extends Thread {

        private DataInputStream inputStream = null;
        private DataOutputStream outputStream = null;
        private ByteArrayOutputStream bOutputStream = null;
        private byte buffer[] = null;
        private byte result[] = null;
        String response = "";
        
        ServerRequest(int connId, DataInputStream inputStream, DataOutputStream outputStream) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            while (true) {
                
                bOutputStream = new ByteArrayOutputStream();
                buffer = new byte[2048];
                
                try {
                    bOutputStream.write(buffer, 0, inputStream.read(buffer));
                    result = bOutputStream.toByteArray();
                    response = new String(result, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // process server responses
                try {
                    String type = response.split(";")[0];
                    System.out.println("process server response");
                    System.out.println(response);

                    // for server no mapping
                    if (type.equals("check")) {
                        Integer connID = Integer.parseInt(response.split(";")[1]);
                        connectionMap.put(connID, outputStream);
                        System.out.println("received server ids");

                    // for server write requests
                    } else if (type.equals("server")) {
                        String[] data = response.split(";");

                        if (data.length == 3) {
                            int k = Integer.parseInt(data[2]);
                            int j = Integer.parseInt(data[1]);
                            System.out.println("received server write request");
                            
                            highestServerRequestNumber = Math.max(highestServerRequestNumber, k);
                            boolean defer = requestingFileWriteRequest 
                            && ((k > serverRequestNumber)
                            || (k == serverRequestNumber && j > serverId));
                            
                            if (defer) writeRequestDeferred[j] = true;
                            else outputStream.write("server;reply".getBytes());
                            System.out.println("Sent write req reply");
                        } else {
                            serverReplyCount--;
                        }

                        // for critical section
                    } else if (type.equals("cs")) {
                        String id = response.split(";")[1];
                        criticalSection = true;
                        criticalSectionFile = Integer.parseInt(id);
                        System.out.println("received cs response");

                        // for mutual exclusion
                    } else if (type.equals("me")) {
                        String[] data = response.split(";");

                        if (data.length == 3) {
                            int k = Integer.parseInt(data[2]);
                            int j = Integer.parseInt(data[1]);
                            System.out.println("received mutual exclusion response");
                            
                            highestSequenceNumber = Math.max(highestSequenceNumber, k);

                            boolean defer = requestingCriticalSection 
                            && ((k > sequenceNumber)
                            || (k == sequenceNumber && j > serverId));
                            
                            if (defer) replyDeferred[j] = true;
                            else outputStream.write("me;reply".getBytes());
                            System.out.println("sent me reply");
                        } else if (data[1].equals("reply")) {
                            outstandingReplyCount--;
                        } else {
                            criticalSectionDone--;
                        }
                    }
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }

                bOutputStream = null;
                buffer = null;
            }
        }
    }

    // thread for processing client requests
    private class ClientManager1 extends Thread {

        Socket socket = null;
        private InputStream input = null;
        private OutputStream output = null;
        private ByteArrayOutputStream bOutputStream = null;
        private byte buffer[] = null;
        private byte result[] = null;

        ClientManager1(Socket socket, int serverNumber) {
            this.socket = socket;
            createDataStreams();
        }

        @Override
        public void run() {
            receiveProcessResponse();
        }

        private void receiveProcessResponse() {
            String response = "";

            while (!response.equals("close")) {

                bOutputStream = new ByteArrayOutputStream();
                buffer = new byte[2048];
                
                try {
                    bOutputStream.write(buffer, 0, input.read(buffer));
                    result = bOutputStream.toByteArray();
                    response = new String(result, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("response received");

                if (!response.equals("close")) processResponse(response);
                else
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                bOutputStream = null;
                buffer = null;
            }
        }

        // send enquire reuquest
        private void processResponse(String response) {

            if (response.equals("enquire")) {
                sendFileDetails();
                System.out.println("enquire details sent");
            }
            else {
                processWriteRequest(response);
            }

        }

        // process client write request and reply when file write is done
        private void processWriteRequest(String response) {
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            WriteRequest wRequest = new WriteRequest("client", response, timestamp);
            clientRequestList1.add(wRequest);
            System.out.println("client request added");

            while (true) {
                if (wRequest.checkComplete()) {
                    try {
                        output.write("done".getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void sendFileDetails() {
            String fileDetails = "file;0,1,2,3";
            try {
                output.write(fileDetails.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void createDataStreams() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean isSocketClosed() {
            return socket.isClosed();
        }
        
    }

    // data structure for storing file write requests
    class WriteRequest {
        String user;
        String data;
        Timestamp timestamp;
        boolean complete;

        WriteRequest(String user, String data, Timestamp timestamp) {
            this.user = user;
            this.data = data;
            this.timestamp = timestamp;
            complete = false;
        }

        Timestamp getTimestamp() {
            return timestamp;
        }

        String getData() {
            return data;
        }

        void requesComplete() {
            complete = true;
        }

        boolean checkComplete() {
            return complete;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("Enter server id");
        int num = sc.nextInt();
        
        sc.close();

        new Server(num);
    }
}
