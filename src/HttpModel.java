import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HttpModel {
    boolean dispInline = false;
    boolean dispAttachment = false;
    boolean dispWithFile = false;
    private HashMap<String, String> headers;
    private HashMap<String, String> arguments;
    private ArrayList<String> fileNames;
    private String code;
    private String data;
    private String Url;
    private ArrayList<String> fileHeaders;

    HttpModel(){
        this.fileHeaders = new ArrayList<>();
        this.headers = new HashMap<>();
        this.arguments = new HashMap<>();
        this.fileNames = new ArrayList<>();
        headers.put("Connection", "keep-alive");
        headers.put("Host", "Localhost");
        headers.put("Date", Instant.now().toString());
    }

    void addfileHeaders(String header) {
        fileHeaders.add(header);
    }

    String getExtension() {
        String extension = "";
        for (String fileHeader : fileHeaders) {
            if (fileHeader.startsWith("Content-type:")) {
                String[] temp = fileHeader.split(":");
                if (temp[1].equals("application/text"))
                    extension = ".txt";
                if (temp[1].equals("application/json"))
                    extension = ".json";
            }
        }
        return extension;
    }

    String getFileName() {
        String fileName = "";
        for (String fileHeader : fileHeaders) {
            if (fileHeader.startsWith("Content-Disposition:")) {
                String[] temp = fileHeader.split(";");
                String[] temp2 = temp[0].split(":");
                if (temp2[1].equals("inline")) {
                    dispInline = true;
                } else if (temp2[1].equals("attachment")) {
                    dispAttachment = true;
                    if (temp.length == 2) {
                        String[] temp3 = temp[1].split(":");
                        fileName = temp3[1];
                        dispWithFile = true;
                    }
                }
            }
        }
        return fileName;
    }

    public void addHeaders(String key, String value) {
        headers.put(key, value);
    }

    private String getHeaders() {
        String head = "";
        for(Map.Entry<String, String> entry : headers.entrySet()) {
            head += " "+entry.getKey()+": "+entry.getValue()+"\r\n";
        }
        return head;
    }

    public void setStatus(String status) {
        this.code = status;
    }

    private String getStatus() {
        return this.code;
    }

    private String getState() {
        switch (this.code) {
            case "200":
                return "OK";
            case "400":
                return "Bad Request";
            case "404":
                return "Not Found";
            default:
                return "ERROR HTTP";
        }
    }

    public void setArgs(String key, String value) {
        arguments.put(key, value);
    }

    private String getArgs() {
        String head = "\r\n";
        for(Map.Entry<String, String> entry : arguments.entrySet()) {
            head += " \""+entry.getKey()+"\": \""+entry.getValue()+"\",\r\n";
        }
        return head;
    }

    private String getOrigin() {
        return "127.0.0.1";
    }

    public void setUrl(String Url) {
        this.Url = Url;
    }

    private String getUrl() {
        return Url;
    }

    public void setData(String content) {
        this.data = content;
    }

    private String getData() {
        return data;
    }

    public void setFiles(String fileName) {
        fileNames.add(fileName);
    }

    private String getFiles() {
        String listOfFiles = "";
        for(String file : fileNames) {
            listOfFiles += file+"\n";
        }
        return listOfFiles;
    }

    public String getHeader() {
        return "HTTP/1.0 " + this.getStatus() + " " + this.getState() +"\r\n"+ this.getHeaders();
    }

    public String getGETBodyPart() {
        return
                "{\r\n"+
                        " \"args\":{"+
                        this.getArgs()+"},\r\n"+
                        " \"headers\":{\r\n"+
                        this.getHeaders()+"},\r\n"+
                        " \"origin\": "+this.getOrigin()+",\r\n"+
                        " \"url\": "+this.getUrl()+",\r\n"+
                        "}";
    }

    public String getPOSTBodyPart() {
        String space = " ";
        return
                "{\r\n"+ space +
                        "\"args\":{"+ space +
                        this.getArgs()+"},\r\n"+ space +
                        "\"data\":{"+ space +
                        this.getData()+"},\r\n"+ space +
                        "\"files\":{\r\n"+ space +
                        this.getFiles()+"},\r\n"+ space +
                        "\"headers\":{\r\n"+
                        this.getHeaders()+" },\r\n"+ space +
                        "\"json\": { },\r\n"+ space +
                        "\"origin\": "+this.getOrigin()+",\r\n"+ space +
                        "\"url\": "+this.getUrl()+",\r\n"+
                        "}";
    }
}