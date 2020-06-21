package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

public class Klient extends Application {

    private Socket s;
    volatile boolean streamInUse=false;
    private String userName="Szymon";
    private String userPath="C:\\Users\\szymo\\Desktop\\Java Projekt\\Klient";
    private boolean isloggedin=false;
    DataOutputStream dataOut;
    DataInputStream dataIn;
    private ArrayList<String> currentFiles,newFiles,oldFileNames;
    private ArrayList<String> serverFiles,filestocheck,targetFiles;
    private ArrayList<String> filesForUsers;
    private boolean changeInFiles=false;
    String target;


    @Override
    public void start(Stage primaryStage) throws Exception{

        startConnection();
        logowanie();
        sendCommandToServer(0,null,null);



        if(!streamInUse){
            Thread th = new Thread(() -> {
                try {
                    getLocalFileList();
                    checkForNewFilesFromServer();//w wątku co 2 sekundy         sprawdzanie i wystłanie nowych plików na serwer
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            th.start();

        }
        sendCommandToServer(3,targetFiles,"Ktos");






       // sendFilesToServer(currentFiles);

        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 300, 275));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }

    private void startConnection() throws IOException {
        s= new Socket("localhost", 1234);
        dataOut = new DataOutputStream(s.getOutputStream());
        dataIn = new DataInputStream(s.getInputStream());
    }

    private void logowanie() throws IOException {

        streamInUse=true;
        dataOut.writeUTF(userName);
        dataOut.flush();
        int val=0;
        val=dataIn.readInt();
        if(val==1){
            System.out.println("odrzucono połączenie");
            dataOut.flush();
            dataIn.close();
            dataOut.close();
            s=null;
            startConnection();              //ponów połączenie
        }else {
            System.out.println("nawiązano połączenie");
        }
        streamInUse=false;
        isloggedin=true;
    }

    private void getLocalFileList() throws IOException, InterruptedException {

        while(true) {
            File f = new File(userPath);
            currentFiles = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
            changeInFiles = checkForNewLocalFiles(oldFileNames, currentFiles);//sprawdzic czy jest zmiana w zawartosci i ustawić flage

            //System.out.println("Local Files: "+currentFiles);
            oldFileNames = currentFiles;
            Thread.sleep(2000);

        }

    }

    /**
     * Funkcja          Funkcja sprawdza czy nastąpiła zmiana w lokalnych plikach klienta na podstawie zapamiętanej listy plików oraz nowo pobranej lity plików
     * @param old       Poprzednie lokalne pliki klienta
     * @param current   aktualne lokalne pliki klienta.
     * @return          flaga oznajmiająca czy nastąpiła zmiana w plikach czy nie.
     * @throws IOException
     */
    private boolean checkForNewLocalFiles(ArrayList<String> old, ArrayList<String> current) throws IOException {

        if (old != null && current != null) {

            ArrayList<String> pom = new ArrayList<>();
            for (int i = 0; i < old.size(); i++) {
                pom.add("0");
            }
            Collections.copy(pom, old);//old


            ArrayList<String> newF = new ArrayList<>(current.size());
            for (int i = 0; i < current.size(); i++) {
                newF.add("0");
            }
            Collections.copy(newF, current);//new


            if (old.equals(current)) {
                return false;
            }
            else
            {
                if (current.size() > old.size()) {              //if new > old  -->  new file was added
                    newF.removeAll(pom);
                    System.out.println("Dodano plik: " + newF);
                    newFiles = newF;
                    sendCommandToServer(1,newF,null);

                } else if (old.size() > current.size()) {                //if old > new --> file was removed
                    pom.removeAll(newF);
                    newF = pom;
                    System.out.println("Usunięto plik: " + newF);
                }
                newFiles = newF;
                                             //wysyłanie nowych plików na serwer
            }

        }
        return true;
    }

    /**
     * Funkcja pobiera liste plików klienta z serwera
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void getFileListFromServer() throws IOException, ClassNotFoundException {
        streamInUse=true;
        ObjectInputStream ois= new ObjectInputStream(s.getInputStream());
        serverFiles= (ArrayList<String>) ois.readObject();
        System.out.println("Odebrano liste plików z serwera:\n" +serverFiles);

        streamInUse=false;
    }

    /**
     * Funkcja sprawdza czy na serwerze pojawiły sie nowe pliki
     * @return   0- wszystko ok
     *           1- nowe pliki na serwerze
     *           2- za mało plików na serwerze
     *
     * @throws IOException
     */
    private int checkForNewFilesFromServer() throws IOException {
        File f = new File(userPath);
        filestocheck = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
        if(serverFiles!=null && filestocheck!=null){

            if(serverFiles.equals(filestocheck)){
                return 0;
            }
            else{

                if(serverFiles.size()>filestocheck.size()){
                    System.out.println("Nowe pliki na serwerze");
                    //request files from server
                    ArrayList<String> pom = new ArrayList<>();
                    for (int i = 0; i < serverFiles.size(); i++) {
                        pom.add("0");
                    }
                    Collections.copy(pom, serverFiles);
                    pom.removeAll(filestocheck);
                    sendCommandToServer(2,pom,null);
                    //requestFilesFromServer(pom);
                    return 1;

                }else{
                    System.out.println("Za mało plików na serwerze");
                    sendCommandToServer(1,filestocheck,null);
                    //sendFilesToServer(currentFiles);
                }


            }




        }
        return 0;
    }

    private void requestFilesFromServer(ArrayList<String> missingFiles) throws IOException {//-------------------------------------------
        if(!streamInUse){
            streamInUse=true;
            ObjectOutputStream  oos= new ObjectOutputStream(s.getOutputStream());
            if(missingFiles!=null){
                oos.writeObject(missingFiles);
                System.out.println(" sending file request: "+missingFiles);
            }
            oos.flush();
            streamInUse=false;
        }

    }

    private void sendFilesToServer(ArrayList<String> files) throws IOException {

        if(!streamInUse) {
            dataOut.writeInt(files.size());
            dataOut.flush();
            System.out.println("sending files: "+files);
            for (String file: files) {

                File myFile = new File(userPath+"\\"+file);
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

    private void acceptFilesFromServer() throws IOException, InterruptedException {

        if(!streamInUse) {
            int bytesRead;
            streamInUse = true;
            int numberOfFiles = dataIn.readInt();
            System.out.println(numberOfFiles);
            for (int x = 0; x < numberOfFiles; x++) {

                String fileName = dataIn.readUTF();
                OutputStream output = new FileOutputStream(userPath + "\\" + fileName);
                long size = dataIn.readLong();
                byte[] buffer = new byte[1024];
                while (size > 0 && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
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

    private void sendCommandToServer(int commandNumber,ArrayList<String> files,String targetUser) throws IOException {
        System.out.println("command: "+ commandNumber);
        try {

            if(commandNumber==0) {

                dataOut.writeInt(0);
                getFileListFromServer();

            }else if (commandNumber == 1) {               //send files

                dataOut.writeInt(1);
                sendFilesToServer(files);

            } else if (commandNumber == 2) {         //request files

                dataOut.writeInt(2);
                requestFilesFromServer(files);
                acceptFilesFromServer();

            } else if (commandNumber == 3) {         //send file to another user            !!pod przyciskiem!!

                dataOut.writeInt(3);
                sendFileToUser(targetUser);

            }else if(commandNumber==4){
                dataOut.writeInt(4);
            }
        }catch(IOException | InterruptedException | ClassNotFoundException e){
            e.printStackTrace();
        }

        dataOut.flush();
    }






    //wywoływanie na przycisk w okienku
    private void sendFileToUser(String name) throws IOException {
        targetFiles=new ArrayList<String>(1);
        targetFiles.add("plik1.txt");
        if(name!=null)
        dataOut.writeUTF(name);
        if(targetFiles!=null)
        sendFilesToServer(targetFiles);
    }

}


//sprawdzic zerowanie command number!!!!!!!!!