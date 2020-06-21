package sample;

import org.jetbrains.annotations.Contract;

import java.io.*;
import java.text.*;
import java.util.*;
import java.net.*;

// Server class
public class Serwer
{

    private static TreeMap<String,Thread> klient=new TreeMap<>();
    static Vector<ClientHandler> ar = new Vector<>();
    public static void main(String[] args) throws IOException
    {
        // server is listening on port 5056
        ServerSocket ss = new ServerSocket(1234);
        String name;
        int val=0;



        while (true)
        {
            Socket s = null;
            val=0;
            try
            {
                // socket object to receive incoming client requests
                s = ss.accept();

                System.out.println("A new client is connected : " + s);

                // obtaining input and out streams
                DataInputStream dis = new DataInputStream(s.getInputStream());
                DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                name = dis.readUTF();
                if(checkIfExists(name)){

                    if(checkIfConnected(name)){
                        System.out.println("username already taken");
                        val=1;
                        Thread.sleep(100);
                        //dos.flush();
                        continue;
                    }


                }
                dos.writeInt(val);


                System.out.println("Assigning new thread for this client");

                // create a new thread object
                ClientHandler mtch = new ClientHandler(s,name, dis, dos);

                Thread t = new Thread(mtch);

                System.out.println("Adding this client to active client list");
                ar.add(mtch);
                klient.put(name,t);

                // Invoking the start() method
                t.start();

            }
            catch (Exception e){
                s.close();
                e.printStackTrace();
            }
        }
    }

    @Contract(pure = true)
    private static boolean checkIfExists(String name){

        if(klient.containsKey(name)){
            return true;
        }
        else{
            return false;
        }
    }

    private static boolean checkIfConnected(String name){
        Thread th=klient.get(name);
        if(th.getState()== Thread.State.TERMINATED){
            return false;
        }

        System.out.println(th.getState());
        return true;

    }
}



// ClientHandler class
class ClientHandler extends Thread
{
    volatile boolean streamInUse=false;

    final DataInputStream dis;
    final DataOutputStream dos;
    final Socket s;
    private String name;
    private String path="C:\\Users\\szymo\\Desktop\\Java Projekt\\Serwer";
    volatile int op=0;
    boolean isloggedin;




    // Constructor
    public ClientHandler(Socket s,String name, DataInputStream dis, DataOutputStream dos)
    {
        this.s = s;
        this.name=name;
        this.dis = dis;
        this.dos = dos;
        this.path=path+"\\"+name;
        this.isloggedin=true;


    }

    public void createFolder(){
        new File(path).mkdirs();
        path+="\\"+name;
        System.out.println("create current path: "+path);
    }

    @Override
    public void run()
    {


        // sendFileList();

        Thread th = new Thread(() -> {
            try {
                commandCenter();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        th.start();

        while(true) {




        }


//        try
//        {
//            // closing resources
//            this.dis.close();
//            this.dos.close();
//
//        }catch(IOException e){
//            e.printStackTrace();
//        }
    }

    private void commandCenter() throws IOException, InterruptedException {

       int op;//odpalic w watku
        System.out.println("weszło");
       while (true) {

           op=-1;
           //if (!streamInUse) {
               op = dis.readInt();
               System.out.println("op: "+op);
           //}
           if(op!= -1){
               executeCommand(op);
           }
           Thread.sleep(100);

       }
    }

    private void executeCommand(int num) throws IOException, InterruptedException {
        try {
            if(num==0){
                sendFileList();

            }else if (num == 1) {
                receiveFile(path);

            } else if (num == 2) {
                sendFilesToClient(getRequestedFiles());

            } else if (num == 3) {
                transferFileToDesignatedUser();

            }
        }catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }

    }



    private ArrayList<String> getRequestedFiles() throws IOException, ClassNotFoundException {
        ArrayList<String> reqFiles=new ArrayList<>();
        if(!streamInUse){
            streamInUse=true;
            ObjectInputStream ois= new ObjectInputStream(s.getInputStream());
            reqFiles= (ArrayList<String>) ois.readObject();
            System.out.println("Odebrano liste potzrebnych plików od klienta:\n" +reqFiles);

            streamInUse=false;
        }
        return reqFiles;
    }

    private void receiveFile(String userPath) throws IOException, InterruptedException {
        if(!streamInUse) {
            int bytesRead;
            streamInUse = true;
            int numberOfFiles = dis.readInt();
            System.out.println(numberOfFiles);
            for (int x = 0; x < numberOfFiles; x++) {

                String fileName = dis.readUTF();
                OutputStream output = new FileOutputStream(userPath + "\\" + fileName);
                long size = dis.readLong();
                byte[] buffer = new byte[1024];
                while (size > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
                    output.write(buffer, 0, bytesRead);
                    size -= bytesRead;
                }
                System.out.println(fileName);
                output.flush();

            }
            System.out.println("otrzymano wszystkie pliki");
            streamInUse = false;
            Thread.sleep(300);
        }
    }


    private void sendFileList()throws IOException{
        streamInUse=true;
        File f = new File(path);
        System.out.println("send file list: "+path);
        ObjectOutputStream  oos= new ObjectOutputStream(s.getOutputStream());
        if(f.list()!=null){
            ArrayList<String> names = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
            oos.writeObject(names);
            System.out.println("File list transferred: "+names);
        }
        oos.flush();
        streamInUse=false;

    }

    private void sendFilesToClient(ArrayList<String> files) throws IOException {                                        //-------------przerobic????

        if(!streamInUse) {
            dos.writeInt(files.size());
            dos.flush();
            for (String file: files) {

                File myFile = new File(path+"\\"+file);
                byte[] mybytearray = new byte[(int) myFile.length()];

                FileInputStream fis = new FileInputStream(myFile);
                BufferedInputStream bis = new BufferedInputStream(fis);

                DataInputStream dis = new DataInputStream(bis);
                dis.readFully(mybytearray, 0, mybytearray.length);

                OutputStream os = s.getOutputStream();

                //Sending file name and file size to the server
                DataOutputStream dos = new DataOutputStream(os);
                dos.writeUTF(myFile.getName());

                dos.writeLong(mybytearray.length);

                dos.write(mybytearray, 0, mybytearray.length);
                dos.flush();

            }
        }


    }

    private void transferFileToDesignatedUser() throws IOException, InterruptedException {
        System.out.println("Dodawanie pliku");
        String name=dis.readUTF();
        System.out.println(name);
        for (ClientHandler mc : Serwer.ar)
        {
            if (mc.name.equals(name) && mc.isloggedin)
            {
                receiveFile("C:\\Users\\szymo\\Desktop\\Java Projekt\\Serwer\\"+name);
                break;
            }
        }

    }

}

