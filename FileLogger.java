/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketprogramming;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FileLogger {
    int my_peer_id;
    String filename;
    BufferedWriter out;
    int total_pieces = 0;
    CommonCfg commonCfg;
    PeerInfoCfg peerInfoCfg;
    int total_num_chunks;


    public FileLogger(int peer_id, CommonCfg commonCfg, PeerInfoCfg peerInfoCfg, int total_num_chunks) throws IOException {
       // Let it open the file
        filename = "log_" + peer_id + ".log";
        out = new BufferedWriter(new FileWriter(filename, false));
        my_peer_id = peer_id;
        this.peerInfoCfg = peerInfoCfg;
        this.commonCfg = commonCfg;
        this.total_num_chunks = total_num_chunks;
        print_cfgs();
    }

    public synchronized void print_cfgs() throws IOException {
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        s = dtf.format(now) + ": Setting following variables from Common.cfg: \n";
        s = s + "\t NumberOfPreferredNeighbors = " + commonCfg.num_preferred_nbrs;
        s = s + "\n\t UnchokingInterval = " + commonCfg.unchock_interval;
        s = s + "\n\t OptimisticUnchokingInterval = " + commonCfg.opt_unchok_interval;
        s = s + "\n\t Filename = " + commonCfg.filename;
        s = s + "\n\t FileSize = " + commonCfg.filesize;
        s = s + "\n\t PieceSize = " + commonCfg.pieceSize;


        out.write(s + "\n");
        out.flush();
    }

    public synchronized void print_handshake_recieved(HandShake received_hs) throws IOException {
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        s = dtf.format(now) + ": Received Handshake message: Header: " + received_hs.header + ", peer_id: " + received_hs.peer_id;
        out.write(s + "\n");
        out.flush();
    }

    public synchronized void print_sending_bitfield(int nbr_id, BitSet input_bitset) throws IOException {
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        StringBuilder str = new StringBuilder();
        for( int i = 0; i < total_num_chunks;  i++ )
        {
            str.append( input_bitset.get( i ) == true ? 1: 0 );
        }

        s = dtf.format(now) + " Sending Bitfield message to neighbor: " + nbr_id + " Bitfield: " + str;
        out.write(s + "\n");
        out.flush();
    }

    public synchronized void print_received_bitfield(int nbr_id, BitSet input_bitset) throws IOException {
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        StringBuilder str = new StringBuilder();
        for( int i = 0; i < total_num_chunks;  i++ )
        {
            str.append( input_bitset.get( i ) == true ? 1: 0 );
        }

        s = dtf.format(now) + " Received bitfield message from neighbor: " + nbr_id + " Bitfield: " + str;
        out.write(s + "\n");
        out.flush();
    }


    public  synchronized void print_bitset(BitSet input_bitset) throws IOException {
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        s = dtf.format(now) + ": Setting following variables derived from PeerInfo.cfg: \n";
        s = s + "\t has_file => " + peerInfoCfg.has_file(my_peer_id);
        s = s  + "\n\t bitSet => " + get_bitset_in_bits(input_bitset);
        out.write(s + "\n");
        out.flush();
    }

    public StringBuilder get_bitset_in_bits(BitSet bitSet_in){
        StringBuilder str = new StringBuilder();
        for( int i = 0; i < total_num_chunks;  i++ )
        {
            str.append( bitSet_in.get( i ) == true ? 1: 0 );
        }
        return str;
    }

    public synchronized void log_completion() throws IOException {
                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " has downloaded the complete file.";
                    out.write(s + "\n");
                }

                catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }

                out.close();

    }

    public synchronized void print_send_message(Integer nbr_id, Message msg_in) throws IOException {
        String s = null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

	DataMessage dataMessage;

        switch (msg_in.message_type) {
            case Message.REQUEST:
                dataMessage = (DataMessage) msg_in;
                s = "Sending REQUEST message to neighbor " + nbr_id + " with picece index => " + dataMessage.piece_index;
                break;
            case Message.HAVE:
                dataMessage = (DataMessage) msg_in;
                s = "Sending HAVE message to neighbor + " + nbr_id + " with piece_index => " + dataMessage.piece_index;
                break;
            case Message.NOT_INTERESTED:
                s = "Sending NOT INTERESTED to neighbor " + nbr_id;
                break;
            case Message.INTERESTED:
                s = "Sending INTERESTED to neighbor " + nbr_id;
                break;
	    case Message.CHOKE:
		s = "Sending CHOKE to neighbor " + nbr_id;
		break;
	    case Message.UNCHOKE:
		s = "Sending UNCHOKE to neighbor " + nbr_id;
		break;

            default:
                break;
        }

        if (s != null) {
            s = dtf.format(now) + "\t" + s;
            out.write(s + "\n");
            out.flush();
        }
    }


    public synchronized void print_received_piece(Integer nbr_id, int chunk, BitSet file_bitset_before, BitSet file_bitSet) throws IOException {
        String s = null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        s = dtf.format(now) + "\tReceived PIECE" + chunk + " from " + nbr_id + ", BitSet before: " + get_bitset_in_bits(file_bitset_before) + ", Bitset NOW: " + get_bitset_in_bits(file_bitSet);
        out.write(s + "\n");
        out.flush();
    }

    public synchronized void log_downloaded_piece(int nbr_id, int piece_idx){
                total_pieces++ ;

                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " has downloaded the piece " + piece_idx + " from " + nbr_id + ". Now the number "
                            + "of pieces it has is " + total_pieces + ".";
                        out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }


    public synchronized void log_not_interested(int nbr_id){
                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " received the 'not interested' message from " + nbr_id + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }


    public synchronized void log_interested(int nbr_id){
                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                     s = dtf.format(now) + ": Peer " + my_peer_id + " received the 'interested' message from " + nbr_id + ".";
                     out.write(s + "\n");
                    out.flush();
                }

                catch (IOException ex) {
                     Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }


    public synchronized void log_have(int nbr_id , int piece_idx){


                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " received the 'have' message from " + nbr_id + " for the piece " + piece_idx + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }

    public synchronized void print_msg(String msg) throws IOException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        out.write(dtf.format(now) + msg + "\n");
        out.flush();
    }

    public synchronized void log_choke(int nbr_id){


                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " is choked by " + nbr_id + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }

    public synchronized void log_unchoke(int nbr_id){
                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " is unchoked by " + nbr_id + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }

    public synchronized void log_optimistic_unchoke(int nbr_id){
                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " has the optimistically unchoked neighbor " + nbr_id + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }

    public synchronized void log_makes_connection(int nbr_id){

                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                     s = dtf.format(now) + ": Peer " + my_peer_id + " makes a connection to Peer " + nbr_id+ ".";
                     out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }


    public synchronized void log_accepts_connection(int nbr_id){
        String s;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        try {
            s = dtf.format(now) + ": Peer " + my_peer_id + " is connected from Peer " + nbr_id+ ".";
            out.write(s + "\n");
            out.flush();
        } catch (IOException ex) {
            Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public synchronized void log_change_pref_neigh( ArrayList<Integer> new_nbr){

                String pref_nbr = "";

                for(int i=0; i <new_nbr.size(); i++)
                {
                    pref_nbr = pref_nbr + new_nbr.get(i);
                    if (i != (new_nbr.size()-1))
                        pref_nbr = pref_nbr + ",";
                    else
                        pref_nbr = pref_nbr + "";
                }

                String s;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();

                try {
                    s = dtf.format(now) + ": Peer " + my_peer_id + " has the preferred neighbors " + pref_nbr + ".";
                    out.write(s + "\n");
                    out.flush();
                } catch (IOException ex) {
                    Logger.getLogger(FileLogger.class.getName()).log(Level.SEVERE, null, ex);
                }
    }

}
