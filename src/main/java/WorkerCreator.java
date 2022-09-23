import Manager.Main.ManagerMain;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;


import java.io.*;
import java.util.Base64;


public class WorkerCreator {
    static String credentialsPath = "C:\\Users\\nasim\\.aws\\credentials";

    public static void main(String[] args) throws InterruptedException, IOException {


        String name = "Worker_EC2";
        String amiId = "ami-0cff7528ff583bf9a";
        Ec2Client ec2 = GetEc2();
        String inputSQS = "https://sqs.us-east-1.amazonaws.com/012182824699/InputQueue";
        String outputSQS = "https://sqs.us.east.1.amazonaws.com/012182824699/OutputQueue";
        String bucketName = LocalApplication.bucketName;
        String instanceId = createWorkerInstance(ec2,name, amiId, inputSQS, outputSQS, bucketName) ;
        System.out.println("The Amazon EC2 Instance ID is "+instanceId);
        describeEC2Instances(ec2);
        ec2.close();
    }

    public static Ec2Client GetEc2(){
        Region region = Region.US_EAST_1;
        return Ec2Client.builder()
                .region(region)
                .build();
    }

    private static String getECuserData(String inputSQS, String outputSQS, String bucketName) throws IOException {
        String userData = "";
        userData = userData + "#!/bin/bash" + "\n";
        userData = userData + "cd /home/ec2-user\n";
        userData = userData + "mkdir ~/.aws\n";
        userData = userData + "cd ~/.aws\n";
        userData = userData + String.format("echo \"%s\" > credentials\n", getCredentials());
        userData = userData + "cd -\n";
        userData = userData + "wget https://download.oracle.com/java/18/latest/jdk-18_linux-x64_bin.rpm\n";
        userData = userData + "sudo rpm -Uvh jdk-18_linux-x64_bin.rpm\n";
        userData = userData + String.format("sudo _Ass1-exaws s3 cp s3://nastut/Nase.jar Text-Analysis.jar\n");
        userData = userData + String.format("sudo java -cp Text-Analysis.jar Worker %s %s %s\n", inputSQS, outputSQS, bucketName);
        String base64UserData = null;
        try {
            base64UserData = new String( Base64.getEncoder().encode(userData.getBytes("UTF-8")), "UTF-8" );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return base64UserData;
    }

    public static void describeEC2Instances( Ec2Client ec2){


        String nextToken = null;

        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        System.out.println("Instance Id is " + instance.instanceId());
                        System.out.println("Image id is "+  instance.imageId());
                        System.out.println("Instance type is "+  instance.instanceType());
                        System.out.println("Instance state name is "+  instance.state().name());
                        System.out.println("monitoring information is "+  instance.monitoring().state());

                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void stopEC2Instance(Ec2Client ec2, String id) {
        StopInstancesRequest runRequest = StopInstancesRequest.builder().instanceIds(id).build();

        ec2.stopInstances(runRequest);
    }

    public static String getCredentials() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(credentialsPath));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    public static String createWorkerInstance(Ec2Client ec2, String name, String amiId, String inputSQS, String outputSQS, String bucketName) throws IOException {

        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T2_MICRO)
                .userData(getECuserData(inputSQS, outputSQS, bucketName))
                .maxCount(1)
                .minCount(1)
                .securityGroups("launch-wizard-2")
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 Instance %s based on AMI %s",
                    instanceId, amiId);

            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }
}