package socketprogramming;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class CommonCfg {
    int num_preferred_nbrs;
    int unchock_interval;
    int opt_unchok_interval;
    String filename;
    int filesize;
    int pieceSize;

    public CommonCfg(String input_filename) {
        get_data_from_file(input_filename);
    }

    private void get_data_from_file(String cfg_filename)  {
        try {

            File myObj = new File(cfg_filename);
            Scanner fileReader = new Scanner(myObj);
            while (fileReader.hasNextLine()) {
                parse_data_from_str(fileReader.nextLine());
            }
        } catch (FileNotFoundException e){
            System.out.println("File: " + filename + " does not exist");
        }
    }


    private void parse_data_from_str(String str){
        String[] split_str = str.split("\\s+");
        String var_name;
        String val;

        if(split_str.length != 2) {
            throw new RuntimeException("Check the input file... This line does not look correct => " + str);
        }else{
            var_name = split_str[0];
            val = split_str[1];
        }


        switch(var_name){
            case "NumberOfPreferredNeighbors" :  num_preferred_nbrs = Integer.parseInt(val);
                                                    break;
            case "UnchokingInterval"            : unchock_interval = Integer.parseInt(val);
                                                    break;
            case "OptimisticUnchokingInterval" : opt_unchok_interval = Integer.parseInt(val);
                                                    break;
            case "FileName" :  filename = val;
                                break;

            case "FileSize": filesize = Integer.parseInt(val);
                                break;

            case "PieceSize": pieceSize = Integer.parseInt(val);
                                break;

            default: throw new RuntimeException("Unknown Input in the config file => " + var_name);
        }
    }

}
