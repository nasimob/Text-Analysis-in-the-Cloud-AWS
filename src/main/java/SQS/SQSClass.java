package SQS;

import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQSClass {

    public static String managerToAppQueueName = "managerToAppQueue";
    public static String appToManagerQueueName = "appToManagerQueue";
    public static String managerToWorkerQueueName = "managerToWorkerQueue";
    public static String workerToManagerQueueName = "workerToManagerQueue";
    public static String terminateMessage = "Terminate";

    public static void main(String[] args) {
        String queueName = "queue" + System.currentTimeMillis();
        SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();

        // Perform various tasks on the Amazon SQS queue
        String queueUrl= createQueue(sqsClient, queueName );
        listQueues(sqsClient);
        listQueuesFilter(sqsClient, queueUrl);
        List<Message> messages = receiveMessages(sqsClient, queueUrl);
        sendBatchMessages(sqsClient, queueUrl);
        changeMessages(sqsClient, queueUrl, messages);
        deleteMessages(sqsClient, queueUrl,  messages) ;
        sqsClient.close();
    }

    // snippet-start:[sqs.java2.sqs_example.main]
    public static String createQueue(SqsClient sqsClient,String queueName ) {

        try {
            System.out.println("\nCreate Queue");
            // snippet-start:[sqs.java2.sqs_example.create_queue]

            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();

            sqsClient.createQueue(createQueueRequest);
            // snippet-end:[sqs.java2.sqs_example.create_queue]

            System.out.println("\nGet queue url");

            // snippet-start:[sqs.java2.sqs_example.get_queue]
            GetQueueUrlResponse getQueueUrlResponse =
                    sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            String queueUrl = getQueueUrlResponse.queueUrl();
            return queueUrl;

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return "";
        // snippet-end:[sqs.java2.sqs_example.get_queue]
    }

    public static void listQueues(SqsClient sqsClient) {

        System.out.println("\nList Queues");
        // snippet-start:[sqs.java2.sqs_example.list_queues]
        String prefix = "queue";

        try {
            ListQueuesRequest listQueuesRequest = ListQueuesRequest.builder().queueNamePrefix(prefix).build();
            ListQueuesResponse listQueuesResponse = sqsClient.listQueues(listQueuesRequest);

            for (String url : listQueuesResponse.queueUrls()) {
                System.out.println(url);
            }

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        // snippet-end:[sqs.java2.sqs_example.list_queues]
    }

    public static String getQueueByName(SqsClient sqsClient, String queueName) { // returns a queueURL
        ListQueuesRequest filterListRequest = ListQueuesRequest.builder()
                .queueNamePrefix(queueName).build();
        ListQueuesResponse listQueuesFilteredResponse = sqsClient.listQueues(filterListRequest);
        if (listQueuesFilteredResponse.hasQueueUrls()) {
            return listQueuesFilteredResponse.queueUrls().get(0);
        } else {
            return null;
        }
    }
    public static void listQueuesFilter(SqsClient sqsClient, String queueUrl ) {
        // List queues with filters
        String namePrefix = "queue";
        ListQueuesRequest filterListRequest = ListQueuesRequest.builder()
                .queueNamePrefix(namePrefix).build();

        ListQueuesResponse listQueuesFilteredResponse = sqsClient.listQueues(filterListRequest);
        System.out.println("Queue URLs with prefix: " + namePrefix);
        for (String url : listQueuesFilteredResponse.queueUrls()) {
            System.out.println(url);
        }

        System.out.println("\nSend message");

        try {
            // snippet-start:[sqs.java2.sqs_example.send_message]
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody("Hello world!")
                    .delaySeconds(10)
                    .build());
            // snippet-end:[sqs.java2.sqs_example.send_message]

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static String sendMessageFromString(SqsClient sqsClient, String queueUrl, String messageString) {
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageString).build();
            return sqsClient.sendMessage(sendMessageRequest).messageId();
    }

    public static void sendBatchMessages(SqsClient sqsClient, String queueUrl) {

        System.out.println("\nSend multiple messages");

        try {
            // snippet-start:[sqs.java2.sqs_example.send__multiple_messages]
            SendMessageBatchRequest sendMessageBatchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(SendMessageBatchRequestEntry.builder().id("id1").messageBody("msg 1").build(),
                            SendMessageBatchRequestEntry.builder().id("id2").messageBody("msg 2").delaySeconds(10).build())
                    .build();
            sqsClient.sendMessageBatch(sendMessageBatchRequest);
            // snippet-end:[sqs.java2.sqs_example.send__multiple_messages]

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static  List<Message> receiveMessages(SqsClient sqsClient, String queueUrl) {
        try {
            // snippet-start:[sqs.java2.sqs_example.retrieve_messages]
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .build();
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            return messages;
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }
        return null;
        // snippet-end:[sqs.java2.sqs_example.retrieve_messages]
    }

    public static void changeMessages(SqsClient sqsClient, String queueUrl, List<Message> messages) {

        System.out.println("\nChange Message Visibility");

        try {

            for (Message message : messages) {
                ChangeMessageVisibilityRequest req = ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(100)
                        .build();
                sqsClient.changeMessageVisibility(req);
            }
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void deleteMessages(SqsClient sqsClient, String queueUrl,  List<Message> messages) {
        System.out.println("\nDelete Messages");
        // snippet-start:[sqs.java2.sqs_example.delete_message]

        try {
            for (Message message : messages) {
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
            }
            // snippet-end:[sqs.java2.sqs_example.delete_message]

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void deleteMessage(SqsClient sqsClient, String queueURL, Message message){
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueURL)
                .receiptHandle(message.receiptHandle())
                .build();
        sqsClient.deleteMessage(deleteMessageRequest);
    }

    public static List<Message> receiveOneMessage(SqsClient sqsClient, String queueUrl) {


        try {
            // snippet-start:[sqs.java2.sqs_example.retrieve_messages]
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .build();
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            return messages;
        } catch (AbortedException e){
            return null;
        }catch (SqsException e) {
            e.printStackTrace();
            return null;
        }
    }

    // snippet-start:[sqs.java2.sqs_example.delete_queue]
    public static void deleteSQSQueue(SqsClient sqsClient, String queueName) {

        try {

            GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            String queueUrl = sqsClient.getQueueUrl(getQueueRequest).queueUrl();

            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                    .queueUrl(queueUrl)
                    .build();

            sqsClient.deleteQueue(deleteQueueRequest);

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
    // snippet-end:[sqs.java2.sqs_example.delete_queue]

    public static void deleteQueue(SqsClient sqsClient, String queueUrl){
        try {
            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                    .queueUrl(queueUrl)
                    .build();
            DeleteQueueResponse deleteQueueResponse = sqsClient.deleteQueue(deleteQueueRequest);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static int getNumberOfMessagesInQueue(SqsClient sqsClient, String queueUrl){
        List<QueueAttributeName> atts = new ArrayList();
        atts.add(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        atts.add(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);
        atts.add(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED);

        GetQueueAttributesRequest attributesRequest= GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(atts)
                .build();

        GetQueueAttributesResponse response = sqsClient.getQueueAttributes(attributesRequest);

        int requestsNum = 0;
        for (Map.Entry attr : response.attributesAsStrings().entrySet()){
             requestsNum+=Integer.parseInt(attr.getValue().toString());
        }

        return requestsNum;
    }
    // snippet-end:[sqs.java2.sqs_example.main]
}