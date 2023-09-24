package socketprogramming;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class PeerInfoCfg {
    ArrayList<Integer> peer_ids = new ArrayList<>();
    HashMap<Integer,String> hostname_map= new HashMap<Integer,String>();
    HashMap<Integer,Integer> portid_map= new HashMap<Integer,Integer>();
    HashMap<Integer,Integer> has_file_map= new HashMap<Integer,Integer>();

    int num_peers = 0;

    public PeerInfoCfg(String cfg_filename) {

        get_data_from_file(cfg_filename);
    }


    public int get_port_id(int peer_id){
        return portid_map.get(peer_id);
    }

    public String get_hostname(int peer_id){
        return hostname_map.get(peer_id);
    }

    public int has_file(int peer_id){
        return has_file_map.get(peer_id);
    }

    private void get_data_from_file(String cfg_filename)  {
        try {

            File myObj = new File(cfg_filename);
            Scanner fileReader = new Scanner(myObj);
            while (fileReader.hasNextLine()) {
                parse_data_from_str(fileReader.nextLine());
            }
        } catch (FileNotFoundException e){
            System.out.println("File: " + cfg_filename + " does not exist");
        }
    }

    private void parse_data_from_str(String str) {
        String[] split_str = str.split("\\s+");
        int peer_id;

        if (split_str.length != 4) {
            throw new RuntimeException("Check the input file... This line does not look correct => " + str);
        } else {
            num_peers ++ ;
            peer_id = Integer.parseInt(split_str[0]);
            peer_ids.add(peer_id);
            hostname_map.put(peer_id,split_str[1]);
            portid_map.put(peer_id, Integer.parseInt(split_str[2]));
            has_file_map.put(peer_id, Integer.parseInt(split_str[3]));
        }
    }

}
