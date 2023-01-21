This project implement mutual exclusion in distributed systems using Maekawa's Algorithm.

# Project Description

1. There are three servers in the system, numbered from zero to two
2. There are five clients in the system, numbered from zero to four
3. Each file is replicated on all the servers, and all replicas of a file are consistent in the beginning. To
simulate separate file systems for each server, we create a separate sub-directory for each server within the home
directory. Initially, all the sub-directories are identical in terms of the list of files they contain, and the contents
of the corresponding files
4. Mutual exclusion algorithm is implemented among the clients. The clients, numbered C0 through C4 , execute
Maekawa’s mutual exclusion algorithm among them to decide which client gets to enter the critical section to access a file.
5. Quorum size is equal to three
6. Quorum of client Ci consists of the set {Ci , C(i+1)mod5 , C(i+2)mod5 }
7. Once a client enters the critical section, it communicates with the three servers and writes to all replicas of the
corresponding file. Once all three replicas of the file have been updated, the client exits the critical section
8. If any server is unwilling to perform the write, then the write should be aborted
