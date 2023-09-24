package socketprogramming;

import java.io.IOException;
import java.util.*;

public class UnchokingTask  implements Runnable{
    FileHelper fileHelper;
    FileLogger fileLogger;
    ArrayList<PeerThread> peer_threads;
    PeerProcess parent;
    int num_preferred_peers;

    ArrayList<PeerThread> interested_threads;
    ArrayList<PeerThread> selected_threads;

    ArrayList<Integer> interested_peer_ids;
    ArrayList<Integer> selected_peer_ids;

    public UnchokingTask(FileHelper fileHelper, ArrayList<PeerThread> peer_threads, int num_preferred_peers, PeerProcess parent){
        this.fileHelper = fileHelper;
        this.peer_threads = peer_threads;
        this.parent = parent;
        this.num_preferred_peers = num_preferred_peers;
        this.fileLogger = parent.fileLogger;
    }

    public void initialize_vars(){
        interested_threads = new ArrayList<PeerThread>();
        selected_threads = new ArrayList<PeerThread>();
        selected_peer_ids = new ArrayList<Integer>();
        interested_peer_ids = new ArrayList<Integer>();
    }

    @Override
    public void run() {

        initialize_vars();
        parent.print_msg("UnchokingTask: Started: ");

        // Get a list of interested peers
        for(PeerThread peerThread: peer_threads){
            if(peerThread.is_neighbor_interested_in_me() == true){
                interested_threads.add(peerThread);
                interested_peer_ids.add(peerThread.neighbor_id);
            }
        }
        if(interested_threads.size() <= num_preferred_peers){
            // If num of threads interested is less than num_preferred_peers, all of these can be unchoked
            selected_threads = interested_threads;
        }else if (fileHelper.i_have_all_pieces()){
            // If we have full file select random peers to unchoke
            select_random_peers();
        } else {
            // If we don't have the full file, check which peers are interested and select the ones which fed us at the highest rate
            select_peers_based_on_bw();
        }

        // Update the logger with the new peer IDs selected
        for(PeerThread peerThread: peer_threads){
            if(selected_threads.contains(peerThread)){
                selected_peer_ids.add(peerThread.neighbor_id);
            }
        }
        fileLogger.log_change_pref_neigh(selected_peer_ids);

        parent.print_msg("UnchokingTask: selected peers: " + Arrays.toString(selected_peer_ids.toArray()) + ", Interested ones:" + Arrays.toString(interested_peer_ids.toArray()));
        try {
            fileLogger.print_msg(" Unchoking thread:: selected peers to unchoke: " + Arrays.toString(selected_peer_ids.toArray()) + ", Interested ones:" + Arrays.toString(interested_peer_ids.toArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Now send unchoke to all selected peers and choke to others
        for(PeerThread peerThread: peer_threads){
            if(selected_threads.contains(peerThread)){
                peerThread.set_selected_for_unchoke(true);
            }else{
                peerThread.set_selected_for_unchoke(false);
            }
        }
    }


    public void select_random_peers(){
        //parent.print_msg("UnchokingTask: CASE2");
        ArrayList<Integer> thread_index = new ArrayList<Integer>();
        for(int i=0; i<interested_threads.size(); i++){
            thread_index.add(i);
        }
        Collections.shuffle(thread_index);
        for(int i=0; i<num_preferred_peers; i++){
            selected_threads.add(interested_threads.get(thread_index.get(i)));
        }
    }

    public void select_peers_based_on_bw(){
        //parent.print_msg("UnchokingTask: CASE3");
        PriorityQueue<PeerThread> peerThreadPriorityQueue = new PriorityQueue<PeerThread>();
        // Note: We have compare2 method added in Peerthread to compare different threads using bandwidth
        for(PeerThread peerThread: interested_threads){
            peerThreadPriorityQueue.add(peerThread);
        }

        // Now pick the first num_preffered_peers from the priority queue
        for(int i =0; i<num_preferred_peers; i++){
            selected_threads.add(peerThreadPriorityQueue.remove());
        }
    }
}
