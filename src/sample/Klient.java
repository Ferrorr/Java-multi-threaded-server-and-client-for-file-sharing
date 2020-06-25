package sample;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Klasa Klienta
 */
public class Klient extends Application {

    private Socket s;
    private volatile boolean streamInUse=false;
    private static String userName;
    private static String userPath;

    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private ArrayList<String> currentFiles,newFiles,oldFileNames;
    private ArrayList<String> serverFiles,filestocheck,targetFiles,missingFiles;
    private volatile boolean changeInFiles;
    private volatile boolean deletedFiles;
    private Thread th,th2;
    private Text text,state;

    /**
     * Funkcja main inicjalizuje scieżkę oraz nazwę użytkownika oraz włącza funkcje obsługującą GUI
     *
     * @param args argumenty z konsoli
     */
    public static void main(String[] args) {
        userName=args[0];
        System.out.println(userName);
        userPath=args[1]+" "+args[2];
        System.out.println(userPath);

        launch(args);
    }

    /**
     * Funkcja ustawia pole tekstowe w GUI, tak aby pokazywało aktualne pliki użytkownika
     *
     * @param text  pole tekstowe na panelu graficznym reprezentujące pliki użytkownika
     * @param stan  pole tekstowe monitorujące oraz wyświetlające to co aktualnie robi klient
     */
    private void change(Text text, Text stan)  {

        File f = new File(userPath);
        ArrayList<String>  names = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));

        if( !changeInFiles){
            stan.setText("Checking for new Files...");
        }else{
            stan.setText("New File(s) found\n    Synchronising...");
        }

        if(deletedFiles){
            stan.setText("File was deleted\n    Synchronising...");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Current path: ").append(userPath).append("\n\n");
        for (String value : names) {
            builder.append("  - ");
            builder.append(value);
            builder.append("\n");

        }
        builder.append("\n\n\n");
        String textpliki = builder.toString();
        text.setText(textpliki);
    }

    /**
     * Metoda odpowiedzialna za GUI oraz strukturę wykonywania programu
     *
     * @param primaryStage
     * @throws Exception
     */
    @Override
    public void start(Stage primaryStage) throws Exception{

        startConnection();
        logowanie();
        sendCommandToServer(0,null,null);

        if(!streamInUse){
            Thread th = new Thread(() -> {
                try {
                    getLocalFileList();
                    //        sprawdzanie i wystłanie nowych plików na serwer
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            th.start();
            sendCommandToServer(0,null,null);

            Thread th2= new Thread(() -> {
                try {
                    checkForNewFilesOnServer();
                    //        sprawdzanie czy jest cos nowego na serwerze
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            th2.start();
        }


        text=new Text();
        state= new Text();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1000), event -> change(text,state)));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(false);
        timeline.play();

        text.setX(50);
        text.setY(50);

        state.setX(50);
        state.setY(100);

        Button button = new Button("send file");
        Button exit = new Button("exit");

        button.setOnAction(event -> {
            try {
                noweOkienko();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        exit.setOnAction(event -> {
            try {
                sendCommandToServer(4,null,null);
                primaryStage.close();
                s.close();
                System.exit(0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });


        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20,20,20,20));
        layout.getChildren().addAll(text,state,button,exit);

        primaryStage.setTitle("Client");
        primaryStage.setScene(new Scene(layout, 500, 500));
        primaryStage.show();
    }

    /**
     * Metoda wyświetla rugie okno GUI w przypaku gy użytkownik chce udostępnić plik
     */
    private void noweOkienko() {

        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("Sending files");
        window.setMinWidth(400);
        window.setMinHeight(300);
        TextField txtfd=new TextField("user#filename");
        Button wyjdz = new Button("Exit");
        Button send = new Button("Send File");

        txtfd.setLayoutX(100);
        txtfd.setLayoutY(50);

        send.setLayoutX(150);
        send.setLayoutY(100);

        wyjdz.setLayoutX(170);
        wyjdz.setLayoutY(150);
        wyjdz.setOnAction(e -> {
            window.close();
        });

        send.setOnAction(e -> {
            if(!txtfd.getText().equals("user#filename")&&txtfd.getText().contains("#")) {
                try {
                    sendCommandToServer(3, null,txtfd.getText() );
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        Pane pane = new Pane();
        pane.getChildren().add(wyjdz);
        pane.getChildren().add(txtfd);
        pane.getChildren().add(send);
        Scene scene = new Scene(pane);

        wyjdz.setFocusTraversable(false);

        window.setScene(scene);
        window.showAndWait();
    }

    /**Metoda opowiedzialna za połączenie z serwerem
     * Utworzenie socketa oraz init strumienia wejścia oraz wyjścia
     * @throws IOException
     */
    private void startConnection() throws IOException {
        s= new Socket("localhost", 1234);
        dataOut = new DataOutputStream(s.getOutputStream());
        dataIn = new DataInputStream(s.getInputStream());
    }

    /**
     * Metoda loguje sie na serwer za pośrednictwem nazwy użytkownika poanej w konsoli
     *
     * @throws IOException
     */
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
    }

    /**
     * Metoda odpowiada za przesłanie odpowiedniej komendy na serwer
     * @param commandNumber numer komendy:
     *                      0 - żądanie listy plików od serwera
     *                      1 - wysyłanie plków (parametr files)
     *                      2 - żądanie plików od serwera oraz ich pobranie
     *                      3 - udostępnienie pliku innemu użytkownikowi
     *                      4 - wylogowanie oraz poinformowanie o tym serwera
     *
     * @param files
     * @param targetUser
     * @throws IOException
     */
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

    /**
     * Metoda pobiera pliki z lokalnego folderu użytkownika oraz obsługuje zmianę w tym folderze
     *
     * @throws IOException
     * @throws InterruptedException
     */
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
     * Metoda urachamiana w wątku, co 1000ms pobiera listę plików z serwera
     * wywołuje metode sprawdzającą czy nastąpiła zmiana między zawartością lok. foleru oraz tego na serwerze
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void checkForNewFilesOnServer() throws IOException, InterruptedException {
        while(true){
            Thread.sleep(1000);
            if(Thread.interrupted()){
                break;
            }
            sendCommandToServer(0,null,null);
            checkForNewFilesFromServer();

        }
    }

    /**
     * Metoda sprawdza czy nastąpiła zmiana w lokalnych plikach klienta na podstawie zapamiętanej listy plików oraz nowo pobranej lity plików
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

            deletedFiles=false;
            if (old.equals(current)) {
                return false;
            }
            else
            {
                if (current.size() > old.size()) {              //if new > old  -->  new file was added
                    newF.removeAll(pom);
                    System.out.println("Dodano plik: " + newF);
                    newFiles = newF;
                    if(!newFiles.equals(missingFiles))
                        sendCommandToServer(1,newF,null);

                } else if (old.size() > current.size()) {                //if old > new --> file was removed
                    pom.removeAll(newF);
                    newF = pom;
                    System.out.println("Usunięto plik: " + newF);
                    deletedFiles=true;
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
     * @throws IOException
     */
    private void checkForNewFilesFromServer() throws IOException {
        File f = new File(userPath);
        filestocheck = new ArrayList<>(Arrays.asList(Objects.requireNonNull(f.list())));
        if(serverFiles!=null && filestocheck!=null){

            if(serverFiles.equals(filestocheck)){
                return ;
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
                    missingFiles=pom;
                    return ;

                }else{
                    System.out.println("Za mało plików na serwerze");
                    sendCommandToServer(1,filestocheck,null);
                    //sendFilesToServer(currentFiles);
                }

            }

        }

    }

    /**
     * Metoda wysyłająca żądanie plików z serwera
     *
     * @param missingFiles Lista plików, które użytkownik chce otrzymać
     * @throws IOException
     */
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

    /**
     * Metoda odpawiadająca za wysyłanie listy plików na serwer
     * Wysyłane są kolejna:
     *
     *
     * -ilość przesyłanych plików
     *      -nazwa pojedyńczego pliku
     *      -rozmiar pliku
     *      -plik
     *
     * @param files lista z nazwami plików do wysłania
     * @throws IOException
     */
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

    /**
     * Metoda pobiera do lokalnego folderu nadesłane przez serwer pliki
     *
     * Pobierane są kolejna:
     *
     *            -ilość odbieranych plików
     *            -nazwa pojedyńczego pliku
     *            -rozmiar pliku
     *            -plik
     *
     * @throws IOException
     * @throws InterruptedException
     */
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


    /**
     * Funkcja wysyła podany w okienku plik do użytkownika, którego nazwa została podana w okienku
     *
     * @param name  nazwa użytkownika oraz pliku w formacie    użytkownik#plik
     * @throws IOException
     */
    private void sendFileToUser(String name) throws IOException {

        StringTokenizer st = new StringTokenizer(name, "#");
        String username = st.nextToken();
        String file = st.nextToken();
        System.out.println("Sending file: "+file +" to " +username);
        targetFiles=new ArrayList<String>(1);
        targetFiles.add(file);
        if(name!=null)
            dataOut.writeUTF(username);
        if(targetFiles!=null)
            sendFilesToServer(targetFiles);
    }
}