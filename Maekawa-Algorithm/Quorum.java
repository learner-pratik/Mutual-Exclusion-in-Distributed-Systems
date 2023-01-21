import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Quorum {

    Map<Integer, List<ClientDetails>> clientDetails = new HashMap<>();
    List<Integer> l;

    Quorum() {

        ClientDetails client0 = new ClientDetails(0, "dc21.utdallas.edu", 1110);
        ClientDetails client1 = new ClientDetails(1, "dc22.utdallas.edu", 1111);
        ClientDetails client2 = new ClientDetails(2, "dc23.utdallas.edu", 1112);
        ClientDetails client3 = new ClientDetails(3, "dc24.utdallas.edu", 1113);
        ClientDetails client4 = new ClientDetails(4, "dc25.utdallas.edu", 1114);

        Map<Integer, ClientDetails> clients = new HashMap<>();
        clients.put(0, client0);
        clients.put(1, client1);
        clients.put(2, client2);
        clients.put(3, client3);
        clients.put(4, client4);

        for (int i = 0; i <= 4; i++) {
            int c1 = (i+1)%5;
            int c2 = (i+2)%5;

            List<ClientDetails> l = new ArrayList<>();
            l.add(clients.get(c1));
            l.add(clients.get(c2));

            clientDetails.put(i, l);
        }
    }

    public Map<Integer, List<ClientDetails>> getClientDetails() {
        return clientDetails;
    }
}
