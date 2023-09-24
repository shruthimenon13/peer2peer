package socketprogramming;

import java.io.Serializable;

public class HandShake implements Serializable {

    String header = "P2PFILESHARINGPROJ";
    byte[] zero_bits = new byte[10];
    int peer_id;

    public HandShake(int peer_id) {
        this.peer_id = peer_id;
    }

    public int get_peer_id() {
        return peer_id;
    }

    public String sprint(){
        String sprint = "Header: " + header + "  peer_id:" + peer_id;
        return sprint;
    }
}
