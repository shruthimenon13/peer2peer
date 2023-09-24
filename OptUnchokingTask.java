package socketprogramming;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class OptUnchokingTask extends UnchokingTask {

    public OptUnchokingTask(FileHelper fileHelper, ArrayList<PeerThread> peer_threads, int num_preferred_peers, PeerProcess parent) {
        super(fileHelper, peer_threads, num_preferred_peers, parent);
    }

    @Override
    public void run() {

        initialize_vars();

        // Get a list of interested peers. Note: In this case we only select amongst the ones which are already choked
        for(PeerThread peerThread: peer_threads){
            if(peerThread.is_neighbor_interested_in_me() == true  && peerThread.nbr_is_choked()==true){
                interested_threads.add(peerThread);
                interested_peer_ids.add(peerThread.neighbor_id);
            }
        }

        if(interested_threads.size() <= num_preferred_peers) {
            // If num of threads interested is less than num_preferred_peers, all of these can be optimistically unchoked
            selected_threads = interested_threads;
        }else{
            select_random_peers();
        }

        // Update the logger with the new peer ID selected
        for(PeerThread peerThread: peer_threads){
            if(selected_threads.contains(peerThread)){
                selected_peer_ids.add(peerThread.neighbor_id);
            }
        }

        parent.print_msg("OptUnchokingTask: selected peers: " + Arrays.toString(selected_peer_ids.toArray()) + ", Interested ones:" + Arrays.toString(interested_peer_ids.toArray()));
        try {
            fileLogger.print_msg(" OptimisticUnchoking thread:: selected peers to optimistically unchoke: " + Arrays.toString(selected_peer_ids.toArray()) + ", Contenders:" + Arrays.toString(interested_peer_ids.toArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Integer sel_id: selected_peer_ids) {
           fileLogger.log_optimistic_unchoke(sel_id);
        }

        // Now send unchoke to all selected peers and choke to others
        for(PeerThread peerThread: peer_threads){
            if(selected_threads.contains(peerThread)){
                peerThread.set_selected_for_opt_unchoke(true);
            }else{
                peerThread.set_selected_for_opt_unchoke(false);
            }
        }
    }
}
