package socketprogramming;


import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class PeerProcess {
    int peer_id = 0;
    int port_id = 0;
    int has_file = 0;
    String hostname;
    String dir_name;

    // Configurations
    CommonCfg common_cfg;
    PeerInfoCfg peerinfo_cfg;

    // For file operations
    FileHelper fileHelper;
    FileLogger fileLogger;

    ServerSocket serverSocket;
    ArrayList<PeerThread> peer_threads = new ArrayList<PeerThread>();

    ScheduledExecutorService executerService;
    UnchokingTask unchokingTask;
    OptUnchokingTask optUnchokingTask;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length > 0) {
            try {
                int peer_id_local = Integer.parseInt(args[0]);
                System.out.println("Starting peer => " + peer_id_local);
                PeerProcess peer = new PeerProcess(peer_id_local);
                peer.start();
                System.out.println("peer complete => " + peer_id_local);
            } catch (NumberFormatException e) {
                System.err.println("Argument" + args[0] + " must be an integer.");
                System.exit(1);
            }
        }
    }


    public PeerProcess(int peer_id){
        this.peer_id = peer_id;
    }

    public void start() throws IOException, InterruptedException {
        print_msg("Started");

        // Parse the configs
        common_cfg = new CommonCfg("Common.cfg");
        peerinfo_cfg = new PeerInfoCfg("PeerInfo.cfg");



        this.port_id = peerinfo_cfg.get_port_id(peer_id);
        this.has_file = peerinfo_cfg.has_file(peer_id);
        this.hostname = peerinfo_cfg.get_hostname(peer_id);
        print();

        // Make directory for storing chunks
        dir_name = "peer_" + peer_id;
        //create_directory(); // This is made before the program. So shifted to StartRemotePeers

        // Setup a server for all the peers after me
        //serverSocket = new ServerSocket(port_id);
        print_msg("Hostname =" + hostname + ", PORT_ID = " + port_id);
        InetAddress inet_addr = InetAddress.getByName(hostname);
        serverSocket = new ServerSocket(port_id, 50, inet_addr);

        // Create file helper for all file related operations
        fileHelper = new FileHelper(common_cfg.filename, common_cfg.filesize, common_cfg.pieceSize, this);

        // Logger
        this.fileLogger = new FileLogger(peer_id, common_cfg, peerinfo_cfg, fileHelper.num_chunks);
        fileHelper.fileLogger = fileLogger;

        // If this peer already has the file, split it
        if(has_file == 1){
           fileHelper.split_file(dir_name);
        }
        fileHelper.generate_pending_chunks();
        fileLogger.print_bitset(fileHelper.file_bitSet); // Print bitset to file

        // Crete threads for interacting with all neighbors
        start_peerthreads();

        // Wait for initial connection and bitmap exchanges
        print_msg("Waiting for all connections to be established");
        for(PeerThread peerThread: peer_threads){
            peerThread.wait_for_bitfield_exchange();
        }
        print_msg("All connections established... continuing");
        for(PeerThread peerThread: peer_threads){
            peerThread.start_main();
        }

        // Create unchoking, optimistic unchoking threads
        executerService = Executors.newScheduledThreadPool(2);
       unchokingTask = new UnchokingTask(fileHelper,peer_threads, common_cfg.num_preferred_nbrs, this);
       executerService.scheduleWithFixedDelay(unchokingTask,common_cfg.unchock_interval,common_cfg.unchock_interval, TimeUnit.SECONDS); // Note: Fix delay was added just to make sure we don't create unnecessay threads and jam the core if queue is blocking

        print_msg("Optmisitic unchok interval => " + common_cfg.opt_unchok_interval);
        optUnchokingTask = new OptUnchokingTask(fileHelper, peer_threads, 1, this);
        executerService.scheduleWithFixedDelay(optUnchokingTask, common_cfg.opt_unchok_interval,common_cfg.opt_unchok_interval, TimeUnit.SECONDS);

        print_msg("Waiting for all threads to complete");
        wait_peerthreads();
        executerService.shutdown();
        executerService.awaitTermination(common_cfg.unchock_interval + common_cfg.opt_unchok_interval + 10, TimeUnit.SECONDS);

        fileHelper.MergeFile();
        fileLogger.log_completion();
        print_msg("Peer Process All complete !!!!!");
    }


    public void start_peerthreads(){
        print_msg("Starting peer threads");
        int i_am_server_for_peer = 0;
        for (Integer peer : peerinfo_cfg.peer_ids) {
            if(peer == this.peer_id){
                i_am_server_for_peer = 1;
            }else{
                PeerThread peerThread = new PeerThread(i_am_server_for_peer,serverSocket,peer,peerinfo_cfg,this);
                peerThread.start();
                peer_threads.add(peerThread);
            }
            print_msg("Peer " + peer + "server: " + i_am_server_for_peer);
        }
        print_msg("Peer threads started");
    }

    public void wait_peerthreads() throws InterruptedException {
        for (Thread thread : peer_threads) {
            thread.join();
        }
    }


    public void print_msg(String msg){
        System.out.println("[" + peer_id + "]: " + msg);
    }

    public void print(){
        System.out.println("Peer_id => " + peer_id);
        System.out.println("Port_id => " + port_id);
        System.out.println("Has_file =>" + has_file);
    }

    public void create_directory(){
        File directory = new File(dir_name);

        if(directory.exists()){
            // Delete directory first so that we don't look at previous runs
            String[]entries = directory.list();
            for(String s: entries){
                File currentFile = new File(directory.getPath(),s);
                currentFile.delete();
            }
        }
        directory.mkdir();
    }
}
