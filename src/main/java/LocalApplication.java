import Manager.ManagerCreator;
import SQS.SQSClass;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class LocalApplication {
    static String managerName = "Manager_EC2";
    static String bucketName = "";
    static int n;
    static String outputFile = "";
    static boolean terminate = false;

    static class ResultEntry{
        public String job;
        public String inputLink;
        public String outputLink;
        public boolean hasFailed;

        public ResultEntry(String job, String inputLink, String outputLink, boolean hasFailed){
            this.job = job;
            this.inputLink = inputLink;
            this.outputLink = outputLink;
            this.hasFailed = hasFailed;
        }
    }
    public static void main(String[] args) throws InterruptedException, IOException {
        if(args.length < 3){
            System.out.println("Make sure to pass InputFile OutputFile n [terminate]");
            return;
        }
        String filePath = getFilePathOrTerminate(args[0]); //getfilepath to input-sample/txt
        outputFile = args[1];
        n = Integer.parseInt(args[2]);
        if(args.length > 3 && args[3].equals("terminate"))
            terminate = true;
        updateInfoFromConfig();
        String fileKey = uploadFile(filePath, bucketName); //filekey = filename = input.txt
        createManagerIfNeeded(n);
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        String outputURL = waitForQueue(sqsClient, "APP2MSQS");//L2M URL
        String inputURL = waitForQueue(sqsClient, "M2APPSQS");//M2L URL

        System.out.printf("Sending %s to outputSQS\n%n", fileKey);
        String id = SQSClass.sendMessageFromString(sqsClient, outputURL, fileKey);

        while(true) {
            List<Message> msgs = SQSClass.receiveMessages(sqsClient, inputURL);
            if(!msgs.isEmpty())
                for(Message msg : msgs) {
                    String s = msg.body();
                    if (s.contains(id)) {
                        ResultEntry[] resultsArray = parseResults(id);
                        HTMLCreator.createHTML(resultsArray, id, outputFile);
                        SQSClass.deleteMessage(sqsClient, inputURL, msg);
                        if(terminate)
                            SQSClass.sendMessageFromString(sqsClient, outputURL, "terminate");
                        return;
                    }
                }
            TimeUnit.SECONDS.sleep(1);
        }

    }

    public static void updateInfoFromConfig(){
        try (BufferedReader br = new BufferedReader(new FileReader("config.txt"))) {
            int line_counter = 0;
            String line;
            while ((line = br.readLine()) != null && line_counter < 2) {
                if(line_counter == 0)
                    ManagerCreator.credentialsPath = line;
                if(line_counter == 1)
                    bucketName = line;
                line_counter++;
            }
        } catch (IOException e) {
            System.out.println("Make sure you are using config.txt!");
            System.exit(1);
        }
    }

    public static String getInput(String output) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));
        System.out.print(output);
        return reader.readLine();
    }

    public static String getFilePathOrTerminate(String filePath){
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.printf("%s was not found!\n", filePath);
            System.exit(0);
        }
        return filePath;
    }

    public static ResultEntry[] parseResults(String id){

        String data = S3Helper.getFileData(id + "/ID-INFO.json");
        JsonObject json = Json.createReader(new StringReader(data)).readObject();
        if(json.get("files").toString().equals("[]"))
            return null;
        JsonArray files = (JsonArray) json.get("files");
        ResultEntry[] results = new ResultEntry[files.size()];
        for(int i = 0; i < files.size(); i++){
            JsonObject temp = (JsonObject) files.get(i);
            String output = temp.getString("output");
            String type = temp.getString("analysisType");
            String input = temp.getString("inputLink");
            results[i] = new ResultEntry(type, input, output, false);
        }
        return results;
    }

    public static boolean isManagerOn( Ec2Client ec2){
        String nextToken = null;
        try {
            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);
                for (Reservation reservation : response.reservations())
                    for (Instance instance : reservation.instances())
                        if(instance.hasTags() && instance.state().name() == InstanceStateName.RUNNING)
                            for(Tag t : instance.tags())
                                if(t.key().equals("DSP") && t.value().equals(managerName))
                                    return true;
                nextToken = response.nextToken();
            } while (nextToken != null);
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return false;
    }

    private static void createManagerIfNeeded(int n) throws IOException {
        Ec2Client ec2 = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .build();
        if (isManagerOn(ec2)){
            System.out.println("Manager EC2 found! No need to create a new one.");
            return;
        }
        System.out.println("Creating a Manager EC2 instance. This can take a while as we need to wait for the instance to start.");
        ManagerCreator.createManagerInstance(managerName, bucketName, n);
    }

    private static String waitForQueue(SqsClient sqsClient, String queueName) throws InterruptedException {
        try {
            System.out.printf("Waiting for %s... This might take a while...\n", queueName);
            String name = SQSClass.getQueueByName(sqsClient, queueName);
            while (name == null) {
                TimeUnit.SECONDS.sleep(1);
                name = SQSClass.getQueueByName(sqsClient, queueName);
            }
            System.out.printf("%s is on!\n", queueName);
            return name;
        } catch (Exception e) {
            System.out.println(e.toString());
            return null;
        }
    }

    private static String uploadFile(String filePath, String bucketName){ //localuploadtos3
        // Split path either by '/' or by '\'
        String[] s = filePath.split("/|\\\\");
        String fileName = s[s.length - 1];

        if(!S3Helper.doesObjectExists(bucketName, fileName)){ //filename = input.txt
            S3Helper.putS3Object(bucketName, fileName, filePath);
            System.out.printf("Uploading %s succeeded!\n", fileName);
            return fileName;
        }
        else{
            int counter = 0;
            String tempFileName = String.format("%s%d", fileName, counter);
            while(S3Helper.doesObjectExists(bucketName, tempFileName)) {
                counter++;
                tempFileName = String.format("%s%d", fileName, counter);
            }
            S3Helper.putS3Object(bucketName, tempFileName, filePath);
            System.out.printf("Uploading %s succeeded under the name: %s!\n", fileName, tempFileName);
            return tempFileName;
        }
    }


}