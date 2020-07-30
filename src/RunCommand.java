import java.io.*;

public class RunCommand {

    public RunCommand() {

        String path;

        try {
                        
        	System.out.println("Trying to fill missing values ");
        	path = "C:/Users/mike/bio_alg/BioCode_v5/";
            Process p = Runtime.getRuntime().exec(path+"jbpca.bat");
            
            BufferedReader stdInput = new BufferedReader(new 
                 InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new 
                 InputStreamReader(p.getErrorStream()));

        }
        catch (Exception e) {
            System.out.println("exception happened: ");
            e.printStackTrace();
        }
        System.out.println("Missing values filled in");
    }
}

