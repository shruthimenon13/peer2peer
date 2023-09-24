package socketprogramming;
/*
 *                     CEN5501C Project2
 * This is the program starting remote processes.
 * This program was only tested on CISE SunOS environment.
 * If you use another environment, for example, linux environment in CISE
 * or other environments not in CISE, it is not guaranteed to work properly.
 * It is your responsibility to adapt this program to your running environment.
 */

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import com.jcraft.jsch.*;
/*
 * The StartRemotePeers class begins remote peer processes.
 * It reads configuration file PeerInfo.cfg and starts remote peer processes.
 * You must modify this program a little bit if your peer processes are written in C or C++.
 * Please look at the lines below the comment saying IMPORTANT.
 */
public class StartRemotePeers {
	/**
	 * @param args
	 */
    static PeerInfoCfg peerinfo_cfg;
    static CommonCfg commonCfg;
    static ArrayList<Thread> processes = new ArrayList<Thread>();
    static HashMap<Thread,Integer> process_map = new HashMap<Thread, Integer>();
    static String path = System.getProperty("user.dir");
    static String username_input;
    static String password_input;
    static String directory;
    static String file_name;

	public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println(path);
        String password = "" ;

        int got_username_from_console = get_username_password_from_console();
        if(got_username_from_console == -1){
            get_username_password();  // Get it from Java Panel window
        }

        directory = System.getProperty("user.dir");
        peerinfo_cfg = new PeerInfoCfg("PeerInfo.cfg");
        commonCfg = new CommonCfg("Common.cfg");
        file_name = commonCfg.filename;

        create_dir_and_copy_files();

        for (Integer peer : peerinfo_cfg.peer_ids) {
            start_peer(peer);
            Thread.sleep(1000);
        }
        //start_peer(1);

        for (Thread process : processes) {
            System.out.println("Waiting for peer: " + process_map.get(process) + " to complete");
            process.join();
        }

	}


	public static void create_dir_and_copy_files() throws IOException {

        String user_input = null;
        Scanner myObj = new Scanner(System.in);  // Create a Scanner object

        do {
             System.out.println("Would you like to create peer directories and copy the input file to the peers that has the file initially ? (Enter YES/NO) : ");
             user_input = myObj.nextLine();  // Read user input
        } while(user_input.equals("YES")==false && user_input.equals("NO")==false);

        if(user_input.equals("YES")){
            for (Integer peer : peerinfo_cfg.peer_ids) {
                String dir_name = "peer_" + peer;
                System.out.println("Creating directory for " + peer);
                create_directory(dir_name);
                if(peerinfo_cfg.has_file(peer) == 1){
                    // This means this peer should have the file initially
                    System.out.println("Copying " + file_name + " to " + peer + "'s directory");
                    File source = new File(file_name);
                    File dest = new File(dir_name + "/" + file_name);
                    Files.copy(source.toPath(), dest.toPath());
                }
            }

            System.out.println("Press Enter to kickoff remote peers ");
            user_input = myObj.nextLine();  // Read user input
        }
    }

    public static void start_peer(int peer_id) throws InterruptedException{
        Thread peer_thread = new Thread(new Runnable() {
            @Override
            public void run() {
               String host_name =  peerinfo_cfg.get_hostname(peer_id);
               start_peer_with_ssh(peer_id, host_name);
            }
        });
        peer_thread.start();
        processes.add(peer_thread);
        process_map.put(peer_thread, peer_id);
	
	Thread.sleep(500);
        //String host_name =  peerinfo_cfg.get_hostname(peer_id);
        //start_peer_with_ssh(peer_id, host_name);
    }

	public static void start_peer_with_ssh(int peer_id, String host){
        try {

            JSch jsch = new JSch();
            // host = "thunder.cise.ufl.edu";

            Session session = jsch.getSession(username_input, host, 22);
            session.setPassword(password_input);
            session.setConfig("StrictHostKeyChecking", "no");
            System.out.println("Connecting to: " + host  + " for peer:" + peer_id);
            session.connect();

            File file = new File("process_output_" + peer_id);
            if (file.exists()){
                file.delete();
            }

            String command = "cd " + directory
                              + ";" + "java socketprogramming.PeerProcess " + peer_id
                              + " >& process_output_" + peer_id
                              ;
            System.out.println("Connected, now executing command : " + command);
            ChannelExec channelExec=(ChannelExec) session.openChannel("exec");
            InputStream in = channelExec.getInputStream();
            channelExec.setCommand(command);
            channelExec.connect();

            //BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            // String line;
            //while ((line = reader.readLine()) != null)
            //{
            //    System.out.println(line);
            //}
            while(!channelExec.isClosed()){
                Thread.sleep(100);
            }

            channelExec.disconnect();
            session.disconnect();
            System.out.println("Peer: " + peer_id + " terminated");
        }
        catch (Exception ex) {
            System.out.println(ex);
        }
    }


    public static void get_username_password(){
        JPanel panel = new JPanel(new BorderLayout(50, 50));

        JPanel label = new JPanel(new GridLayout(0, 1, 30, 10));
        label.add(new JLabel("Username :", SwingConstants.RIGHT));
        label.add(new JLabel("Password :", SwingConstants.RIGHT));
        panel.add(label, BorderLayout.WEST);

        JPanel controls = new JPanel(new GridLayout(0, 1, 10, 10));
        JTextField username = new JTextField(20);
        controls.add(username);
        JPasswordField password = new JPasswordField();
        controls.add(password);
        panel.add(controls, BorderLayout.CENTER);
        JFrame frame = new JFrame();
        JOptionPane.showMessageDialog(frame, panel, "LOGIN_DETAILS", JOptionPane.QUESTION_MESSAGE);

        username_input = username.getText();
        password_input = new String(password.getPassword());

        //System.out.println("Username = " + username_input + ", Password = " + password_input);
        frame.dispose();
    }


    public static int get_username_password_from_console(){
        Console cnsl = null;
        cnsl = System.console();
        // if console is not null
        if (cnsl != null) {

            // Get username
            username_input = cnsl.readLine("Username: ");

            // read password
            char[] pwd = cnsl.readPassword("Password: ");
            password_input = String.valueOf(pwd);;

            //System.out.println("Username = " + username_input + ", Password = " + password_input);

            return 0;
        }
        return -1;
    }


    public static void create_directory(String dir_name){
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
