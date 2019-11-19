import java.io.BufferedReader;
import java.io.InputStreamReader;


public class HTTPServerDriver {
    public static void main(String[] args) {
       try {
           System.out.println("Please select one option from below:\n" +
                   "1> HTTPC client\n" +
                   "2>HTTPFS client");
           System.out.println("Enter your choice(1/2):");
           BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
           String choice = br.readLine();
           if(choice.startsWith("1")){
                   System.out.print(">>");
                   String userInput = br.readLine();
               HTTPC client = new HTTPC(userInput);
                   //client.parseUserInput();
           }
           else if (choice.startsWith("2")){

           }
           else
               System.out.println("Wrong INPUT, Please start the program again.");
       }catch (Exception e){e.printStackTrace();}
    }
}
