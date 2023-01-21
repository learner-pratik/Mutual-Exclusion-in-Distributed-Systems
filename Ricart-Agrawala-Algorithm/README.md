This project implements mutual exclusion using Ricart Agrawala Algorithm

# Project Description

1. There are three servers in the system, numbered from zero to two.
2. There are five clients in the system, numbered from zero to four.
3. A file is replicated on all the servers, and all replicas of a file are consistent in the beginning. To
simulate separate file systems for each server, we create a separate sub-directory for each server within the home
directory. Initially, all the sub-directories are identical in terms of the list of files they contain, and the contents
of the corresponding files.
4. A client can perform WRITE operations on files. The client randomly selects one of the three servers
and send the WRITE request with the appropriate information (as described below) to that server. The server,
acting as the proxy for the client, then broadcasts the WRITE request to all other servers. 
5. The WRITE operation to a specified file is performed as a critical section execution by the proxy server 
using the Ricart-Agrawala algorithm.
6. Once the WRITE is performed (i.e., the proxy server exits the critical section), the proxy server sends a message
to the client informing it of the completion of the WRITE.
7. A client issues only one WRITE request at a time.
8. Different servers (acting as proxies for different clients) can concurrently perform WRITE on different files.
9. The operations supported by servers are as follows:
(a) ENQUIRY: A request from a client for information about the list of hosted files.
(b) WRITE: A request from a client to append a string to a given file.
The servers replies to the clients with appropriate messages after receiving each request.
