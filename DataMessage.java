package socketprogramming;

import java.io.Serializable;

public class DataMessage extends Message implements Serializable {
    int piece_index;

    public DataMessage(byte type, byte[] message_data, int piece_index) {
        super(type, message_data);
        this.piece_index = piece_index;
    }

    // For printing
    public String sprint(){
        return super.sprint() + ", Piece_index:" + piece_index;
    }

}
