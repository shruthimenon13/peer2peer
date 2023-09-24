
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketprogramming;

/**
 *
 * @author nikunj
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SplitFile {

    public SplitFile () {

    }

    public void split_file(int chunk_size, String source_file_path, String destination_path) {
        System.out.println("Splitting started. Please wait...");

        int current = 0;

        try {

            // have.txt file contains the paths of all the chunks
            String path_list_of_chunks_file = destination_path + "/have.txt";
            PrintWriter pw = new PrintWriter(path_list_of_chunks_file);
            pw.close();

            // Initialize streams
            InputStreamReader ins = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(ins);
            FileInputStream fis = new FileInputStream(source_file_path);

            // Variables to be used
            int read_bytes;
            int j = 0;
            String s = "";
            byte b[] = new byte[10];

            while (fis.available() != 0)

            {
                j = 0;
                s = destination_path + "/" + current;

                FileOutputStream fos = new FileOutputStream(s);
                BufferedWriter out = new BufferedWriter(new FileWriter(path_list_of_chunks_file, true));

                while (j < chunk_size && fis.available() != 0)

                {
                    read_bytes = fis.read(b, 0, 1);
                    j = j + read_bytes;
                    fos.write(b, 0, read_bytes);
                }
                //System.out.println("Part " + current + " Created");
                out.write("Chunk " + current + "\n");
                out.close();
                current++;
            }
            //System.out.println("File split successful!");
            fis.close();
            System.out.println("Splitting successfully completed!");

        }

        catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
