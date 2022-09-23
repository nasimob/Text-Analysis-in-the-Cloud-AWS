import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;



class Msg {
    public String container;
    public String job;
    public String inputLink;

    private Msg(String container, String job, String inputLink){
        this.container = container;
        this.job = job;
        this.inputLink = inputLink;
    }

    public static Msg parseMsg(String st) throws Exception {
        // Assuming that the message will be MSGID|JOB|INPUTLINK

        String[] result = st.split("[|]");
        if(result.length != 3){
            throw new Exception("Message is of an unknown pattern");
        }
        String id = result[0]; //msgid
        String job = result[1];//POS|DEPENDENCY|CONSTITUENCY
        String inputLink = result[2];//websiteURL
        return new Msg(id, job, inputLink);
    }
}

public class Worker {

    static String inputSQS;
    static String outputSQS;
    static String bucketName;
    static String messageId;
    static String receiptHandle;

    public static void main(String[] args) throws Exception {
        if(args.length < 3)
            return;

        Worker.inputSQS = args[0];
        Worker.outputSQS = args[1];
        Worker.bucketName = args[2];
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        while(true) {
            String msg = getMsg(sqsClient);//for example :string msg = POS	https://www.gutenberg.org/files/1659/1659-0.txt
            if(!msg.equals("")) {
                Msg m = Msg.parseMsg(msg); // Msg m = (id,JOB,WEBSITE)
                String answer;
                if(m.job.equals("POS") || m.job.equals("DEPENDENCY") || m.job.equals("CONSTITUENCY"))
                    answer = process(m);
                else
                    answer = "Parsing type is illegal!";
                sendResult(answer, sqsClient, m);
                deleteMessage(sqsClient);
                deleteAllFiles();
            }
            else{
                TimeUnit.SECONDS.sleep(5);
            }
        }
    }

    private static void deleteAllFiles(){
        File resultFile = new File(messageId + "_result.txt");
        File inputFile = new File(messageId);
        if(!resultFile.delete())
            System.out.println("Failed deleting " + messageId + "_result.txt");
        else
            System.out.println(messageId + "_result.txt was deleted!");
        if(!inputFile.delete())
            System.out.println("Failed deleting " + messageId);
        else
            System.out.println(messageId + " was deleted!");
    }

    private static void deleteMessage(SqsClient sqsClient){
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(inputSQS)
                .receiptHandle(Worker.receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteMessageRequest);
    }

    private static void sendResult(String resultURL, SqsClient sqsClient, Msg m){
        // Sends the result URL to the output SQS
        String returnMsg = String.format("%s|%s|%s|%s", m.container, m.inputLink, m.job.toString(), resultURL);
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(outputSQS)
                .messageBody(returnMsg).build();
        sqsClient.sendMessage(sendMessageRequest);
        System.out.printf("Result was sent to: %s\n", resultURL);
    }

    private static String getMsg(SqsClient sqsClient){
        // Receives the message from the input SQS

        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(inputSQS)
                .maxNumberOfMessages(1)
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
        if(messages.isEmpty())
            return "";
        Worker.messageId = messages.get(0).messageId();
        Worker.receiptHandle = messages.get(0).receiptHandle();
        String body = messages.get(0).body();
        System.out.printf("Received msg!\nBody: %s\n%n", body);
        ChangeVisibility(sqsClient, receiptHandle, 1800);
        return messages.get(0).body();
    }

    private static void ChangeVisibility(SqsClient sqsClient, String receiptHandle, int timeout){
        try {
            // Get the receipt handle for the first message in the queue.
            ChangeMessageVisibilityRequest visibilityRequest = ChangeMessageVisibilityRequest.builder()
                    .queueUrl(inputSQS)
                    .receiptHandle(receiptHandle)
                    .visibilityTimeout(timeout)
                    .build();
            sqsClient.changeMessageVisibility(visibilityRequest);

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

    }
    private static String process(Msg m) throws IOException {
        // Processes the message from the manager and returns the URL to S3
        String errMsg;
        String file_path = saveFileFromWeb(m);
        if(file_path == null){
            errMsg = "Input link is broken!";
            System.out.println(errMsg);
            return errMsg;
        }

        String resultPath = String.format("%s_result.txt", messageId);
        File resultFile = new File(resultPath);
        if(!resultFile.createNewFile()){
            errMsg = String.format("File named %s already exists", resultFile);
            System.out.println(errMsg);
            return errMsg;
        }
        System.out.printf("Processing the message into file: %s\n", resultPath);
        String result = StanfordParser.parse(file_path, resultPath, m.job);
        System.out.println("Processing Finished!");
        String ObjectKey = String.format("%s/%s.txt", m.container, messageId);
        if(result.equals("1"))
            return S3Helper.putS3Object(Worker.bucketName, ObjectKey, resultPath);
        else
            return result;
    }


    private static String saveFileFromWeb(Msg m) throws IOException {
        // Gets the text from the url and save into messageId.txt
        try {
            URL url = new URL(m.inputLink);//	https://www.gutenberg.org/files/1659/1659-0.txt
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(url.openStream()));

            StringBuilder stringBuilder = new StringBuilder();

            String inputLine;
            while ((inputLine = bufferedReader.readLine()) != null) {
                stringBuilder.append(inputLine);
                stringBuilder.append(System.lineSeparator());
            }

            bufferedReader.close();
            String savedPath = String.format("%s.txt", messageId);
            try (PrintWriter out = new PrintWriter(savedPath)) {
                out.println(stringBuilder.toString());
            }
            return savedPath;
        }
        catch (Exception e) {
            return null;
        }
    }
}
