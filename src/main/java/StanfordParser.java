import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.*;
import java.io.*;
import java.util.List;

public class StanfordParser {
    public static String parse(String inputPath, String outputPath, String job) throws IOException {
        try {
            LexicalizedParser p = LexicalizedParser.loadModel("englishPCFG.ser.gz");
            FileInputStream fstream = new FileInputStream(inputPath);
            DataInputStream in = new DataInputStream(fstream); // Get the object of DataInputStream
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringReader sr;
            PTBTokenizer tkzr;
            String strLine;
            PrintWriter pw = new PrintWriter(outputPath);
            String jobType = "";

            if (job.equals("POS")) {
                jobType = "wordsAndTags";
            } else if (job.equals("DEPENDENCY")) {
                jobType = "typedDependencies";
            } else if (job.equals("CONSTITUENCY")) {
                jobType = "penn";
            }

            System.out.println("Starting parsing: " + jobType);
            while ((strLine = br.readLine()) != null) {
                // We read each line independently
                sr = new StringReader(strLine);
                tkzr = PTBTokenizer.newPTBTokenizer(sr);
                List toks = tkzr.tokenize();


                Tree parse = p.apply(toks);
                TreePrint tp = new TreePrint(jobType);
                tp.printTree(parse, pw);
            }
            fstream.close();
            return "1";
        }
        catch(Exception e){
            return e.toString();
        }
    }

    public static void main(String[] args) throws IOException {
        //parse("src/input.txt", "src/output.txt","POS");
    }

}