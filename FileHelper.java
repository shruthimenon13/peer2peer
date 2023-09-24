package socketprogramming;



import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.*;

public class FileHelper {
    String file_name;
    int file_size;
    int piece_size;

    int num_chunks;
    BitSet file_bitSet;

    FileLogger fileLogger;

    ArrayList<Integer> pending_chunks = new ArrayList<Integer>();
    HashMap<Integer, Integer> chunks_in_progress = new HashMap<Integer, Integer>();

    String dir_name;

    ArrayList<PeerThread> peer_threads;
    PeerProcess parent;

    //Constructor
    public FileHelper(String file_name, int file_size, int piece_size, PeerProcess parent){
        this.file_name = file_name;
        this.file_size = file_size;
        this.piece_size = piece_size;
        this.parent = parent;
        this.dir_name = parent.dir_name;
        this.peer_threads = parent.peer_threads;

        File source_file = new File(dir_name + "/" + file_name);
        float num_chunks_float = (float)file_size/(float)piece_size;
        num_chunks = (int)Math.ceil(num_chunks_float);

        System.out.println("NUM_CHUNKS=>" + num_chunks);
        file_bitSet = new BitSet(num_chunks);

    }

    public void generate_pending_chunks(){
        for(int i = 0; i< num_chunks; i++){
            if(file_bitSet.get(i) == false){
                // This means this chunk is not available with this client. Add this to pending list
                pending_chunks.add(i);
            }
        }
        // This is to shuffle all the pending stuff so that we ask for random pieces
        Collections.shuffle(pending_chunks);
        print();
    }


    public synchronized boolean has_required_piece(BitSet bitSet_in){
        int chunk_I_require = file_bitSet.nextClearBit(0);

        while(chunk_I_require < num_chunks){
            if(bitSet_in.get(chunk_I_require) == true) {
                // This means this bitset has something which I don't have
                return true;
            }
            chunk_I_require = file_bitSet.nextClearBit(chunk_I_require+1);
        }
        return false;
    }

    public boolean i_have_all_pieces(){
        return has_all_pieces(file_bitSet);
    }

    public synchronized boolean has_all_pieces(BitSet bitSet_in){
        if(bitSet_in.nextClearBit(0) < num_chunks){
            return false;
        }else{
            return true;
        }
    }



    public void split_file(String dest_dir) throws IOException {
        //File source = new File(file_name);
        //File dest = new File(dest_dir + "/" + file_name);
        //Files.copy(source.toPath(), dest.toPath());

        SplitFile fileSplitter = new SplitFile();
        String source_file_path = dest_dir + "/" + file_name;

        fileSplitter.split_file(piece_size,source_file_path,dest_dir);

        // Set all the bits to true since we have the file
        file_bitSet.set(0,num_chunks,true);
    }

    public synchronized byte[] get_bitSet_in_bytes(){
        return file_bitSet.toByteArray();
    }


    public synchronized int get_next_piece_to_request(BitSet bitSet_in){
        // If this bitset has something interesting and not already requested, send this piece
        // Note that this is synchronized to make sure there is no race between threads
        parent.print_msg("get_next_piece called, current_bitset => " + file_bitSet + ", pending=> " +  Arrays.toString(pending_chunks.toArray()) + ", i_have_all_pieces =>" + i_have_all_pieces());

        for(int i=0; i<pending_chunks.size();i++){
            int pending_chunk = pending_chunks.get(i);

            if(bitSet_in.get(pending_chunk) == true){
                pending_chunks.remove(i);
                parent.print_msg("get_next_piece returning " + pending_chunk + " current_bitset => " + file_bitSet + ", pending=> " +  Arrays.toString(pending_chunks.toArray()));
                return pending_chunk;
            }
        }
        return -1;
    }

    // This will be called for some reason the thread couldnt grab the piece from neighbor
    public synchronized void add_to_pending(int piece){
        pending_chunks.add(piece);
    }

    public synchronized void save_piece(int nbr_id, int chunk, byte[] data) throws IOException, InterruptedException {
        BitSet file_bitset_before = (BitSet)file_bitSet.clone();

        String file_path = dir_name + "/" + chunk;
        FileOutputStream fos = new FileOutputStream(file_path);
        file_bitSet.set(chunk); // Update the bitset
        fos.write(data);
        fos.close();

        fileLogger.print_received_piece(nbr_id, chunk, file_bitset_before, file_bitSet);

        //Also inform every thread that we received the piece so that they can send HAVE message to their neighbors
        for (PeerThread peer_thread: peer_threads){
            peer_thread.new_piece_received(chunk);
        }
    }


    public byte[] get_chunk_bytes(int chunk) throws IOException {
        String file_path = dir_name + "/" + chunk;

        // Get the length from the file itself (since the last chunk will not have piece_size)
        File f = new File(file_path);
        int file_size = (int) f.length();
        byte[] chunk_data = new byte[file_size];
        //parent.print_msg("Chunk: " + chunk + ", File_size =" + file_size);

        // First make sure that we have this file. If not error out saying this is not expected
        FileInputStream fis = new FileInputStream(file_path);

        //parent.print_msg("Reading chunk:" + chunk + "Expected bytes => " + piece_size);
        // Read all the bytes and store in byte array
        fis.read(chunk_data);
        //parent.print_msg("Read chunk" + chunk + "Actual bytes => " + chunk_data.length );

        fis.close();
        // Return this
        return chunk_data;
    }






    public void print(){
        StringBuilder s = new StringBuilder();
        for( int i = 0; i < file_bitSet.length();  i++ )
        {
            s.append( file_bitSet.get( i ) == true ? 1: 0 );
        }

        parent.print_msg("num_chunks => =>" + num_chunks);
        parent.print_msg("BitSet => " + s);
        parent.print_msg("Pending chunks: " + Arrays.toString(pending_chunks.toArray()));
    }


    public boolean MergeFile() throws IOException {


        String path_header = "peer"+ '_'+ parent.peer_id;


        byte b[] = new byte[1000];
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dir_name + "/output_final", false);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < num_chunks; i++) {


            try {

                FileInputStream fis = new FileInputStream(path_header + "/" + i);
                int read_bytes = 0;

                while (fis.available() != 0) {
                    read_bytes = fis.read(b, 0, 1000);
                    fos.write(b, 0, read_bytes);
                }

                fis.close();


            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        fos.close();

        return true;
    }



}
