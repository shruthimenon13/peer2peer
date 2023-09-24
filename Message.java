package socketprogramming;

import java.io.Serializable;
import java.util.BitSet;


public class Message implements Serializable {

    public static final byte CHOKE = 0;
    public static final byte UNCHOKE = 1;
    public static final byte INTERESTED = 2;
    public static final byte NOT_INTERESTED = 3;
    public static final byte HAVE = 4;
    public static final byte BITFIELD = 5;
    public static final byte REQUEST = 6;
    public static final byte PIECE = 7;

    // Class variables
    int message_length;
    byte message_type;
    byte[] message_data;

    // Constructor
    public Message(byte type, byte[] message_data){
        this.message_type = type;
        this.message_data = message_data;
        if(message_data != null) {
            this.message_length = message_data.length;
        }else{
            this.message_length = 0;
        }
    }

    // BitSet
    public BitSet get_bitfield(int bits){
        BitSet bitSet_length_adj;
        BitSet bitSet_rcvd;
        bitSet_rcvd = BitSet.valueOf(message_data);

        bitSet_length_adj = new BitSet(bits);
        for(int i=0; i<bits; i++){
            if(i < bitSet_rcvd.length() && bitSet_rcvd.get(i) == true){
                    bitSet_length_adj.set(i);
            }
        }
        return bitSet_length_adj;
    }


    public String get_msg_type_string(){
        switch (message_type){
            case CHOKE:             return "CHOKE";
            case UNCHOKE:           return "UNCHOKE";
            case INTERESTED:        return "INTERESTED";
            case NOT_INTERESTED:    return "NOT_INTERESTED";
            case HAVE:              return "HAVE";
            case BITFIELD:          return "BITFIELD";
            case REQUEST:           return "REQUEST";
            case PIECE:             return "PIECE";
        }
        return "UNKNOWN";
    }

    // For printing
    public String sprint(){
        return "Message: message_type:" + get_msg_type_string() + ", message_length:" + message_length;
    }
}
