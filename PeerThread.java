package socketprogramming;


import javax.swing.plaf.TableHeaderUI;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.BitSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerThread extends Thread implements Comparable<PeerThread> {

    // Global details
    int is_server;                  // ie, this thread is the server for neighbor
    PeerInfoCfg peerInfoCfg;        // Cfg which has the info from input file
    ServerSocket serverSocket;
    PeerProcess parent;
    int my_id;                      // id of this peer
    Thread nbr_response_handler_thread;

    // Neighbor details
    Socket nbr_socket;              // Socket to the neighbor
    int neighbor_id;                // Peer ID of the neighbor we are communicating with
    ObjectInputStream in_stream;    // For receiving messages
    ObjectOutputStream out_stream;  // For sending

    FileHelper fileHelper;
    FileLogger fileLogger;

    public volatile boolean handshake_received = false;  // Handshake received from neighbor
    public volatile boolean bitfield_received = false;   // Bitfield received from neighbor
    BitSet nbr_bitfield;


    private static Object sharedLock = new Object();  // Lock shared with all threads
    public static Object privateLock = new Object();  // Private lock (this could also be used)

    // State of choking/interested for me and neighbor
    public volatile boolean i_am_choking_neighbor = true;
    public volatile boolean neighbor_choking_me  = true;
    public volatile boolean i_am_interested_in_neighbor = false;
    public volatile boolean neighbor_interested_in_me = false;
    public volatile boolean start_main = false;
    public volatile boolean all_done = false;

    // For other threads to communicate (ie, to tell me to send CHOKE/UNCHOKE)
    public volatile boolean selected_for_unchoke = false;
    public volatile boolean selected_for_opt_unchoke = false;

    private BlockingQueue<Integer> parent_msg_q = new ArrayBlockingQueue<Integer>(10);  // Queue for anything from peerProcess (ie, to SEND_CHOKE / UNCHOKE)
    private BlockingQueue<DataMessage> nbr_request_q = new ArrayBlockingQueue<DataMessage>(10);   // Queue of requests from neighbor
    private BlockingQueue<DataMessage> nbr_piece_q = new ArrayBlockingQueue<DataMessage>(10);     // Queue of responses/ pieces from neighbor
    private BlockingQueue<Integer> new_chunks_I_received = new LinkedBlockingQueue<>();   // These are new chunks I received from all neighbors (pending HAVE message to send)
    public volatile int piece_requested = -1;

    int nbr_bw = 0;
    public volatile double step = 0;


    public PeerThread(int is_server, ServerSocket serverSocket, int neighbor_id, PeerInfoCfg peerInfoCfg, PeerProcess parent){
        this.is_server = is_server;
        this.peerInfoCfg = peerInfoCfg;
        this.serverSocket = serverSocket;
        this.parent = parent;
        this.neighbor_id = neighbor_id;
        this.my_id = parent.peer_id;
        this.fileHelper = parent.fileHelper;
        this.fileLogger = parent.fileLogger;
    }

    @Override
    public void run() {
        try {
            setup_nbr_response_handler_thread();  // Handler for getting responses
            setup_connections();                  // Set connections to the neighbors
            exchange_handshake();                 // Send and receive handshakes from neighbor
            exchange_bitfield();                  // Send and receive bitfields from neighbor
            main_loop();

            all_done = true;
            Thread.sleep(500);              // Just for pending tasks to drain (like timer thread)
            print_msg("Closing socket");
            nbr_socket.close();
        } catch(IOException | InterruptedException e){
            e.printStackTrace();
        }
    }

    private void main_loop() throws IOException, InterruptedException {

        // Wait for parent to say start
        while(start_main == false)
            Thread.sleep(10);

        timer_loop(2);                      // Just a loop to print out pendng ones for debug

        // Continue until both I and have full file and there are no outstanding requests/responses to be handled
        while(neighbor_or_i_pending()) {
            step = 0;

            // If parent says we need to choke/unchoke neighbor (by unchok tasks) and I am not already doing that, send choke/unchoke message to neighbor
            step = 1;
            handle_parent_message();

            // If neighbor has something which I don't have and neighbor is not choking us, send request to neighbor if there is nothing already sent
            if (i_am_interested_in_neighbor == true && neighbor_choking_me == false
                && piece_requested == -1) {
                step = 2;
                send_request_to_neighbor();
            }

            // If we got responses and there are outstanding requests, remove the request (so that next loop can send again)
            if(nbr_piece_q.size() > 0 || neighbor_choking_me==true){
                step = 3;
                handle_neighbor_response_piece();
            }

            // If we received any new chunks, send HAVE message so that neighbor so that neighbor updates bitset and interest
            if(new_chunks_I_received.size() > 0) {
                step = 4;
                send_have_message();
            }

            // If neighbor is requesting a piece and we are not choking the neighbor, send the piece. Else squash the request
            if(nbr_request_q.size() > 0){
                step = 5;
                if(i_am_choking_neighbor==false) {
                    send_piece_to_neighbor();
                }else{
                    squash_request_from_neighbor();
                }
            }

            // Based on the latest Bitset and pieces we got so far if there has been a change in interest to neighbor, inform neighbor
            if (fileHelper.has_required_piece(nbr_bitfield) != i_am_interested_in_neighbor){
                step = 6;
                send_interested_or_not();
            }

            // If we received some new piece inform every other thread so that they can send HAVE messages
            Thread.sleep(20); // Just to make sure there is not a terrible set of messages
        }

    }




    // If either I or nbr has any missing pieces, this returns true. Else this returns false
    private boolean neighbor_or_i_pending(){
        if(fileHelper.has_all_pieces(nbr_bitfield) == false ||
           fileHelper.i_have_all_pieces() == false ||
           parent_msg_q.size() > 0 ||
           nbr_request_q.size() > 0 ||
           nbr_piece_q.size() > 0 ||
           new_chunks_I_received.size() > 0) {

            //print_msg("Retuning true because : " + parent_msg_q.size() + " " + nbr_request_q.size() + " " + nbr_piece_q.size() + " " + new_chunks_I_received.size());

            return true;
        }
        return false;
    }


    //Based on the latest Bitset if there has been a change in interest to neighbor, inform neighbor
    private void send_interested_or_not() throws IOException, InterruptedException {
        byte[] message_data;

        if(fileHelper.has_required_piece(nbr_bitfield) != i_am_interested_in_neighbor){
            i_am_interested_in_neighbor = fileHelper.has_required_piece(nbr_bitfield);
            if(i_am_interested_in_neighbor)
                send_message(Message.INTERESTED,null);
            else
                send_message(Message.NOT_INTERESTED, null);
        }
    }

    // Handle parent message
    // If parent says we need to choke/unchoke neighbor and I am not already doing that, send choke/unchoke message to neighbor
    void handle_parent_message() throws InterruptedException, IOException {
        if(selected_for_unchoke == true || selected_for_opt_unchoke == true){
            if(i_am_choking_neighbor == true){
                send_message(Message.UNCHOKE, null);
                i_am_choking_neighbor = false;
            }
        }else{
            // ie, it is not selected for unchoke
            if(i_am_choking_neighbor == false){
                send_message(Message.CHOKE,null);
                i_am_choking_neighbor = true;
            }
        }
    }


    void send_request_to_neighbor() throws InterruptedException, IOException {

            // Check if there is anything interesting from the neighbor
            int request_piece = fileHelper.get_next_piece_to_request(nbr_bitfield);

            if(request_piece != -1){
                // If yes, send request and wait for response
                print_msg("I will request piece: " + request_piece + " From" + neighbor_id);
                send_data_message(Message.REQUEST,null,request_piece);

                piece_requested = request_piece;
            } // we have piece to request
    }


    void send_piece_to_neighbor() throws IOException, InterruptedException {

        // Send if we decided not to choke neighbor and neighbor requested something
        DataMessage msg = nbr_request_q.remove();
        print_msg("Got request from neighbor " + neighbor_id + " for piece: " +  msg.piece_index);
        byte[] data = fileHelper.get_chunk_bytes(msg.piece_index);
        print_msg("Sending piece to " + neighbor_id + " piece: " +  msg.piece_index);

        send_data_message(Message.PIECE,data,msg.piece_index);
    }

    void squash_request_from_neighbor(){
        while(nbr_request_q.size() > 0){
            nbr_request_q.remove();
        }
    }


    void handle_neighbor_response_piece() throws IOException, InterruptedException {
        while(nbr_piece_q.size() > 0){
            DataMessage msg = nbr_piece_q.remove();
            if(msg.piece_index == piece_requested) { ;
                // ie, we got the requested piece
                fileLogger.log_downloaded_piece(neighbor_id, msg.piece_index);
                fileHelper.save_piece(neighbor_id, piece_requested, msg.message_data);
                piece_requested = -1;
                nbr_bw ++;
            }
        }
        ;
        // If for any reason we got choked, add the piece back to main pending so that someone else can pick it up
        if(neighbor_choking_me == true){
            if(piece_requested != -1){
                fileHelper.add_to_pending(piece_requested);
                piece_requested = -1;
            }
        }
    }



    void send_have_message() throws IOException, InterruptedException {
        while(new_chunks_I_received.size() > 0){
            int new_chunk = new_chunks_I_received.remove();
            send_data_message(Message.HAVE, null, new_chunk);
        }
    }

    // ======================================================================
    // Setup socket to connect to neighbors. Check if we need lock synchronization
    // ======================================================================
    private void setup_connections() throws IOException {
        print_msg("Setting up connections");
        if(is_server == 1) {
                print_msg("Accepting connections from anyone ");
                nbr_socket = serverSocket.accept();
        }else{
                print_msg(" Connecting to: " + peerInfoCfg.get_hostname(neighbor_id) + ": port :" + peerInfoCfg.get_port_id(neighbor_id));
                fileLogger.log_makes_connection(neighbor_id);
                nbr_socket = new Socket(peerInfoCfg.get_hostname(neighbor_id), peerInfoCfg.get_port_id(neighbor_id));
        }
        out_stream = new ObjectOutputStream(nbr_socket.getOutputStream());
        in_stream = new ObjectInputStream(nbr_socket.getInputStream());
    }



    // ========================================================================
    // To send handshake message and wait until handshake response is received
    // ========================================================================

    private void exchange_handshake() throws IOException, InterruptedException {
        // Send handshake to neighbor
        print_msg("Sending handshake message to neighbor");
        HandShake handshake = new HandShake(my_id);
        print_msg("Handshake_sent: " + handshake.sprint());

        out_stream.writeObject(handshake);
        // Wait for handshake to be received from neighbor
        while(handshake_received == false) {
            Thread.sleep(1000);
        }
    }

    // ========================================================================
    // To send and receive bitfield
    // ========================================================================
     private void exchange_bitfield() throws IOException, InterruptedException {
        byte[] bitSet_in_bytes = fileHelper.get_bitSet_in_bytes();
        send_message(Message.BITFIELD,bitSet_in_bytes);

        fileLogger.print_sending_bitfield(neighbor_id, fileHelper.file_bitSet);
        print_msg("Bitfield sent: ");
        while(bitfield_received == false){
            Thread.sleep(1000);
        }
        print_msg("Bitfield received: ");
    }


    // ========================================================================
    // Generic send and receive messages
    // ========================================================================

    private void send_message(byte type, byte[] data) throws IOException, InterruptedException {
        // Send handshake to neighbor
        Message msg = new Message(type,data);
        print_msg("Send Message to: " + neighbor_id + ", Msg: " + msg.sprint());
        fileLogger.print_send_message(neighbor_id,msg);
        out_stream.writeObject(msg);
    }

    private void send_data_message(byte type, byte[] data, int index) throws IOException, InterruptedException {
        // Send handshake to neighbor
        DataMessage msg = new DataMessage(type,data, index);
        print_msg("Send Message to: " + neighbor_id + ", Msg: " + msg.sprint());
        fileLogger.print_send_message(neighbor_id,msg);
        out_stream.writeObject(msg);
    }


    // ===============================================================================================
    // Neighbor Response
    // ==============================================================================================

    // =============================
    // Main response handler thread
    // ============================
    private void nbr_response_handler() throws IOException, ClassNotFoundException, InterruptedException {
        Object read_object;

        while(in_stream == null)
            Thread.sleep(10);   // This is just to get the main thread upto this stage

        while(true) {
            //print_msg("Waiting for read_object");
            try{
                read_object = in_stream.readObject();
            } catch (EOFException | SocketException e) {
                // This is okay... this means we closed the socket. So we can end it
                return;
            }

            if (read_object instanceof HandShake) {
                handle_handshake_from_nbr(read_object);
            } else if (read_object instanceof DataMessage){
                handle_data_message_from_nbr(read_object);
            } else if (read_object instanceof  Message) {
                handle_message_from_nbr(read_object);
            } else {
                throw new RuntimeException("Received unknown message from neighbor socket");
            }
        }
    }


    public void handle_handshake_from_nbr(Object obj) throws IOException {
        HandShake received_hs = (HandShake)obj;
        print_msg("Handshake received: " + received_hs.sprint());


        if (is_server == 1) {
            this.neighbor_id = received_hs.get_peer_id();
            fileLogger.log_accepts_connection(neighbor_id);     // Since only now I'll know which ID I neighbor I am communcating with
        } else {
            if (this.neighbor_id != received_hs.get_peer_id()) {
                throw new RuntimeException("Expecting message from" + neighbor_id + " but got from " + received_hs.get_peer_id());
            }
        }

        fileLogger.print_handshake_recieved(received_hs);
        handshake_received = true;
    }


    public void handle_message_from_nbr(Object obj) throws IOException {
        Message msg = (Message)obj;
        print_msg("handle_message_from_nbr: Message received from : " + neighbor_id + " Message:" + ((Message) obj).sprint());

        switch(msg.message_type){

            case Message.BITFIELD:
                                    nbr_bitfield = msg.get_bitfield(fileHelper.num_chunks);
                                    fileLogger.print_received_bitfield(neighbor_id, nbr_bitfield);
                                    bitfield_received = true;
                                    break;

            case Message.INTERESTED:
                                    fileLogger.log_interested(neighbor_id);
                                    neighbor_interested_in_me = true;
                                    break;

            case Message.NOT_INTERESTED:
                                    fileLogger.log_not_interested(neighbor_id);
                                    neighbor_interested_in_me = false;
                                    break;

            case Message.CHOKE:     neighbor_choking_me = true;
                                    fileLogger.log_choke(neighbor_id);
                                    break;

            case Message.UNCHOKE:   neighbor_choking_me = false;
                                    fileLogger.log_unchoke(neighbor_id);
                                    break;

        }

    }

    public void handle_data_message_from_nbr(Object obj) throws InterruptedException {
        DataMessage msg = (DataMessage)obj;
        print_msg("handle_data_message_from_nbr: Message received from : " + neighbor_id + "Message:" + ((Message) obj).sprint());

        switch(msg.message_type){
            case Message.REQUEST:
                                    nbr_request_q.put(msg);
                                    break;

            case Message.PIECE:
                                    nbr_piece_q.put(msg);
                                    break;

            case Message.HAVE:
                                    nbr_bitfield.set(msg.piece_index);
                                    fileLogger.log_have(neighbor_id, msg.piece_index);
                                    break;
        }
    }



    private void setup_nbr_response_handler_thread(){
        print_msg("Setting up response handler thread");
        nbr_response_handler_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    nbr_response_handler();
                } catch (IOException | ClassNotFoundException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        nbr_response_handler_thread.start();
    }

    Thread timer_thread;
    private void timer_loop(int sec){
        timer_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(all_done == false){
                    try {
                        Thread.sleep(1000 * sec);
                        if(fileHelper.has_all_pieces(nbr_bitfield) == false){
                            print_msg("Neighbor missing piece: " + nbr_bitfield.nextClearBit(0));
                        }
                        if(fileHelper.has_all_pieces(fileHelper.file_bitSet) == false){
                            print_msg("I am missing piece:" + fileHelper.file_bitSet.nextClearBit(0));
                        }
                        print_msg("Not complete yet. In step = " + step);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        timer_thread.start();
    }

    // ======================================
    // To communicate with main process
    // ====================================
    public boolean is_neighbor_interested_in_me(){
        return neighbor_interested_in_me;
    }
    public boolean nbr_is_choked(){
        return i_am_choking_neighbor;
    }

    public void set_selected_for_unchoke(boolean input){
        this.selected_for_unchoke = input;
    }
    public void set_selected_for_opt_unchoke(boolean input){
        this.selected_for_opt_unchoke = input;
    }

    public int get_nbr_bw(){
        return this.nbr_bw;
    }

    @Override
    public int compareTo(PeerThread other) {
        if(this.nbr_bw > other.nbr_bw){
            return -1; // => Higher priority
        }else if(this.nbr_bw == other.nbr_bw) {
            if(Math.random() < 0.5) {
                return -1;
            }else{
                return 1;
            }
        }else{
            return 1;
        }
    }


    @Override
    public boolean equals(Object object)
    {
        boolean equal = false;
        if (object != null && object instanceof PeerThread)
        {
            equal = this.neighbor_id == ((PeerThread) object).neighbor_id;
        }
        return equal;
    }

    public void wait_for_bitfield_exchange() throws InterruptedException {
        while(bitfield_received == false){
            Thread.sleep(10);
        }
        print_msg("Wait for main loop returned: ");
    }

    public void start_main() {
        start_main = true;
    }

    // ========================================
    //
    public void new_piece_received(int chunk) throws InterruptedException {
        new_chunks_I_received.put(chunk);
    }

    public void print_msg (String input) {
        parent.print_msg("<-> [" + neighbor_id + "] :" + input);
    }
}
