import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class HTMLCreator {
    public static void main(String[] args) throws IOException {

    }

    public static void createHTML(LocalApplication.ResultEntry[] results, String msgID, String outputFile) throws IOException {
        String bodies = getAllEntries(results);
        String finalResult = String.format("<html>\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" +
                "<title>Results for %s</title>\n" +
                "</head>\n" +
                "%s\n" +
                "</html>", msgID, bodies);
        File newHtmlFile = new File(outputFile);
        if(!newHtmlFile.createNewFile()) {
            newHtmlFile.delete();
            newHtmlFile.createNewFile();
        }
        try (PrintWriter out = new PrintWriter(newHtmlFile)) {
            out.println(finalResult);
        }


    }

    private static String getAllEntries(LocalApplication.ResultEntry[] results) {
        StringBuilder sb = new StringBuilder();
        for(LocalApplication.ResultEntry result : results){
            if(result.outputLink.startsWith("https://")) {
                sb.append(String.format("<body style=\"font-size:150%%;\"> %s: \n" +
                        "<a href=\"%s\">input</a>\n" +
                        "<a href=\"%s\">output</a>\n" +
                        "<br></body>", result.job, result.inputLink, result.outputLink));
            }
            else
                sb.append(String.format("<body style=\"font-size:150%%;\"> %s: \n" +
                        "<a href=\"%s\">input</a>\n" +
                        " %s\n" +
                        "<br></body>", result.job, result.inputLink, result.outputLink));
        }
        return sb.toString();
    }
}
