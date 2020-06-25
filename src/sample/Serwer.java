package sample;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jetbrains.annotations.Contract;



import java.io.*;
import java.text.*;
import java.util.*;
import java.net.*;



public class Serwer extends Application
{

    private static TreeMap<String,Thread> klient=new TreeMap<>();
    static Vector<ClientHandler> ar = new Vector<>();
    static String name="default";
    Text text,state;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Metoda obserwuje zmiany w folderach użytkowników na serwerze, oraz na liście zalogowanych użytkowników
     * @param text pole tekstowe na którym wyświetlana jest zawartość poszczególnych plików oraz nazwa ich właścicieli
     * @throws InterruptedException
     */
    private void change(Text text) throws InterruptedException {

        ArrayList<String>  names = new ArrayList<>();
        for (Map.Entry<String,Thread> entry : klient.entrySet()) {
            names.add(entry.getKey());
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Logged Users: ").append("\n\n");
        for (String value : names) {
            File f = new File("C:\\Users\\szymo\\Desktop\\Java Projekt\\Serwer\\"+value);
            ArrayList<String>  files = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
            builder.append("  - ");
            builder.append(value);
            builder.append("\n");           //             for dla pliku
            for(String file:files){
                builder.append("            - ");
                builder.append(file);
                builder.append("\n");
            }
            builder.append("\n");
        }
        builder.append("\n");
        String textpliki = builder.toString();
        text.setText(textpliki);
    }

    /**
     * Metoda odpowiedzialna za włączenie serwera oraz GUI
     * @param primaryStage
     * @throws IOException
     */
    public  void start(Stage primaryStage) throws IOException
    {
        Thread serwer = new Thread(() -> {
            try {
                startSerwer();
            } catch (IOException e ) {
                e.printStackTrace();
            }
        });
        serwer.start();

        text=new Text();
        state= new Text();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), event -> {
            try {
                change(text);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(false);
        timeline.play();

        text.setX(50);
        text.setY(50);

        state.setX(50);
        state.setY(100);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20,20,20,20));
        layout.getChildren().addAll(text,state);

        primaryStage.setTitle("Server");
        primaryStage.setScene(new Scene(layout, 500, 500));
        primaryStage.show();
    }

    /**
     * Metoda odpala serwer oraz oczekuje na nowy użytkowników, którym przypisuje nowe wątki oraz dodaje do listy zalogowanych użytkowników
     * @throws IOException
     */
    static public void startSerwer() throws IOException {
        ServerSocket ss = new ServerSocket(1234);

        int val;

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
                System.out.println(klient);
                //getAcitiveUsers();
                // Invoking the start() method
                t.start();

            }
            catch (Exception e){
                s.close();
                e.printStackTrace();
            }
        }
    }

    /**
     * Metoda sprawdza czy użytkownik jest/był już zalogowany
     * @param name nazwa danego użytkownika
     * @return zalogowany - true
     */
    @Contract(pure = true)
    private static boolean checkIfExists(String name){

        if(klient.containsKey(name)){
            return true;
        }
        else{
            return false;
        }
    }

    /**
     * Metoda sprawdza czy dany użytkownik jest obecnie aktywny
     * @param name nazwa użytkownika
     * @return
     */
    private static boolean checkIfConnected(String name){
        Thread th=klient.get(name);
        if(th.getState()== Thread.State.TERMINATED){
            return false;
        }
        System.out.println(th.getState());
        return true;

    }

//    static public void getAcitiveUsers(){
//        for (Map.Entry<String,Thread> entry : klient.entrySet()) {
//            System.out.println(entry.getKey());
//        }
//
//    }

    /**
     * metoda odpowiada za usunięcie użytkownika z listy klientów
     * @param user nazwa użytkownika
     */
    static void LogUserOut(String user){

        klient.get(user).interrupt();
        klient.remove(user);

        System.out.println("Klient "+user +" został wylogowany");
    }

}


/**
 * Klasa zajmująca się poszczególnymi klientami
 * odpowiada za wszystkie czynnosci jakie klient może wykonać na serwerze
 */
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
    Thread th;


    /**
     * konstruktor klasy
     * @param s     socket
     * @param name  nazwa klienta
     * @param dis   DataInputStream
     * @param dos   DataOutputStream
     */
    public ClientHandler(Socket s,String name, DataInputStream dis, DataOutputStream dos)
    {
        this.s = s;
        this.name=name;
        this.dis = dis;
        this.dos = dos;
        this.path=path+"\\"+name;
        System.out.println("sciezka: "+this.path);
        this.isloggedin=true;
        //createFolder();


    }

//    public void createFolder(){
//        new File(path).mkdirs();
//        System.out.println("creating current path: "+path);
//    }

    /**
     * Funkcja odpowiada za start wątku akceptującego komendy od klienta
     */
    @Override
    public void run()
    {


         th = new Thread(() -> {
            try {
                commandCenter();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        th.start();

        while(true) {


        }


    }

    /**
     * Metoda oczekuje na komendy od klienta
     * @throws IOException
     * @throws InterruptedException
     */
    private void commandCenter() throws IOException, InterruptedException {

       int op;

       while (true) {
           if(Thread.interrupted()){
               return;
           }
           op=-1;
           //if (!streamInUse) {
               op = dis.readInt();
               //System.out.println("op: "+op);
           //}
           if(op!= -1){
               executeCommand(op);
           }

       }
    }

    /**
     * Metoda obsługuje komendy odebrane od klienta
     * @param num numer komendy
     *            1- wysłanie listy plików klienta
     *            2- odebranie plików od klienta
     *            3- odebranie oraz wysłanie plików udostępnionych przez klienta
     *            4- wylogowanie klienta
     *
     * @throws IOException
     * @throws InterruptedException
     */
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

            }else if(num==4 ){
                logOut();
            }
        }catch (IOException | ClassNotFoundException e){
            e.printStackTrace();
        }

    }


    /**
     * Metoda odbiera liste plików potrzebnych klientowi
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private ArrayList<String> getRequestedFiles() throws IOException, ClassNotFoundException {
        ArrayList<String> reqFiles=new ArrayList<>();
        if(!streamInUse){
            streamInUse=true;
            ObjectInputStream ois= new ObjectInputStream(s.getInputStream());
            reqFiles= (ArrayList<String>) ois.readObject();
            System.out.println("Odebrano liste potrzebnych plików od klienta:\n" +reqFiles);

            streamInUse=false;
        }
        return reqFiles;
    }

    /**
     * Metoda służy do odbierania plików oraz zapisywania ich do folderu klienta
     * @param userPath
     * @throws IOException
     * @throws InterruptedException
     */
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

    /**
     * Metoda wysyła klientowi liste plików obecnych na serwerze
     * @throws IOException
     */
    private void sendFileList()throws IOException{
        streamInUse=true;
        File f = new File(this.path);
        //System.out.println("send file list: "+path);
        ObjectOutputStream  oos= new ObjectOutputStream(s.getOutputStream());
        if(f.list()!=null){
            ArrayList<String> names = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
            oos.writeObject(names);
            //System.out.println("File list transferred: "+names);
        }
        oos.flush();
        streamInUse=false;

    }

    /**
     * metoda wysyła pliki do użytkownika
     * @param files lista plików
     * @throws IOException
     */
    private void sendFilesToClient(ArrayList<String> files) throws IOException {

        if(!streamInUse) {
            dos.writeInt(files.size());
            dos.flush();
            for (String file: files) {

                File myFile = new File(this.path+"\\"+file);
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

    /**
     * metoda przekazuje udostępnione pliki do odpowiedzniego użytkownika
     * @throws IOException
     * @throws InterruptedException
     */
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

    /**
     * metoda służy do wylogowanie klienta oraz zamkniecia socketa
     * @throws IOException
     */
    private void logOut() throws IOException {
            // closing resources
            th.interrupt();
            System.out.println(th.getState());
            this.isloggedin=false;
            this.dis.close();
            this.dos.close();
            this.s.close();
            Serwer.LogUserOut(name);
    }

}

