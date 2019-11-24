import java.io.*;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ParallelServer extends Thread {

    //Boolean checks
    private boolean isCT = false;       //content Type
    private boolean isD = false;        //Disposition
    private boolean isHFC = false;     // Http File Client

    //Data related
    private String pathToDir;
    private String content;
    private String clientRequest;
    private StringBuilder httpfsResponse = new StringBuilder();

    private DatagramChannel channel;
    private PacketMaker packet;
    private SocketAddress router;
    private BufferedWriter out = null; // output stream send response to client
    private HttpModel model;

    ParallelServer(DatagramChannel channel, PacketMaker packet, String dir, SocketAddress router) {
        this.channel = channel;
        this.packet = packet;
        this.pathToDir = dir;
        this.router = router;
    }

    @Override
    public void run() {
        try{
            model = new HttpModel();
            String payload = new String(packet.getPayload(), UTF_8);
            System.out.println("payload=="+payload);
            BufferedReader in = new BufferedReader(new StringReader(payload));

            String request;
            while((request = in.readLine()) != null){

                if(request.matches("(GET|POST)/(.*)")) {
                    isHFC = true;
                    clientRequest = request;
                }

                if(isHFC) {
                    model.addfileHeaders(request);
                    if(request.startsWith("Content-type:"))
                        isCT = true;
                    if(request.startsWith("Content-Disposition:")) {
                        isD = true;
                    }
                    if(request.startsWith("-d")) {
                        content = request.substring(2);
                    }
                }

                if(isHFC && request.isEmpty())
                    break;
            }
            if(isHFC) {
                System.out.println("Client requested command..."+clientRequest);

                if(clientRequest.startsWith("GET")) {
                    this.getServerRequest(clientRequest.substring(4));
                }else if(clientRequest.startsWith("POST")) {
                    System.out.println(clientRequest.substring(5));
                    String fileName = clientRequest.substring(5);
                    postServerRequest(fileName, content);
                }
            }

            httpfsResponse.append("\n");
            //create packet of Response
            PacketMaker p = packet.toBuilder().setPayload(httpfsResponse.toString().getBytes()).create();
            channel.send(p.toBuffer(), router);
            System.out.println("sending " + httpfsResponse.toString() + " to router " + 3000);
        }catch (Exception e){e.printStackTrace();}
    }



    private synchronized void postServerRequest(String fileName, String content) throws IOException{
        File filePath;
        BufferedWriter postWriter;
        if(isCT)
            filePath = new File(pathToDir+"/"+fileName+model.getExtension());
        else
            filePath = new File(pathToDir+"/"+fileName);

        if(!fileName.contains("/")) {
            try {
                httpfsResponse.append("CODE 202 OK \r\n");
                postWriter = new BufferedWriter(new FileWriter(filePath));
                postWriter.write(content);
                postWriter.flush();

                httpfsResponse.append("POST : Done...");
                postWriter.close();

                model.setFiles(fileName);
            } catch (FileNotFoundException e) {
                httpfsResponse.append("ERROR 404 FILE NOT FOUND");
            }
        }else {
            System.out.println("Access Denied");
            httpfsResponse.append("ERROR 502 Access Denied");
        }
    }

    private synchronized void getServerRequest(String fileNam) throws IOException{

        File filePath;
        String fileName = fileNam;
        if(isCT) {
            fileName = fileName+model.getExtension();
            filePath = new File(pathToDir+"/"+fileName);
        }else
            filePath = new File(pathToDir + "/" + fileName);

        if(!fileName.contains("/")) {

            if(filePath.exists()) {
                if(filePath.isDirectory()) {
                    File[] listOfFiles = filePath.listFiles();
                    out.write("CODE 202 OK \r\n");
                    for(File file : listOfFiles) {
                        if(file.isFile()) {
                            System.out.println("File  : "+file.getName());
                            httpfsResponse.append("File  : "+file.getName()+"\r\n");
                        }else if(file.isDirectory()) {
                            System.out.println("Directory >> "+file.getName());
                            httpfsResponse.append("Directory >> "+file.getName()+"\r\n");
                        }
                    }
                }else if(filePath.isFile()) {
                    System.out.println("Path: "+pathToDir+"/"+fileName);
                    FileReader fileReader;
                    PrintWriter fileWriter = new PrintWriter("abc");
                    File downloadPath = new File("src/Download");
                    String fileDownloadName = "";
                    if(isD) {
                        fileDownloadName = model.getFileName();
                        if(model.dispAttachment) {
                            if(!downloadPath.exists())
                                downloadPath.mkdir();
                        }
                    }
                    try {
                        if(model.dispAttachment) {
                            if(model.dispWithFile)
                                fileWriter = new PrintWriter(downloadPath+"/"+fileDownloadName);
                            else
                                fileWriter = new PrintWriter(downloadPath+"/"+fileName);
                        }
                        fileReader = new FileReader(filePath);
                        BufferedReader bufferedReader = new BufferedReader(fileReader);
                        String currentLine;
                        String fileData = null;
                        httpfsResponse.append("CODE 202 OK \n");
                        while ((currentLine = bufferedReader.readLine()) != null) {
                            fileData = fileData + currentLine;
                            if(isD) {
                                if(model.dispInline) {
                                    httpfsResponse.append(currentLine);
                                }else if(model.dispAttachment) {
                                    fileWriter.println(currentLine);
                                }
                            }else
                                httpfsResponse.append(currentLine+"\n");
                        }
                        if(model.dispAttachment)
                            fileWriter.close();
                        httpfsResponse.append("Operation Successful"+"\n");
                        System.out.println("Operation Successful");
                    } catch (FileNotFoundException e) {
                        System.out.println("ERROR HTTP 404");
                        httpfsResponse.append("ERROR HTTP 404 : File Not Found"+"\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("ERROR HTTP 404");
                httpfsResponse.append("ERROR HTTP 404");
            }

        }else {
            System.out.println("Access Denied");
            httpfsResponse.append("Error: access denied");
        }
    }
}
