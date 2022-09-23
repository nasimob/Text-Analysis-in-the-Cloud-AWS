nasim obaid 207661638
ami: ami-0cff7528ff583bf9a
Instance type: T2_MICRO
How to run the application?
Before running the application make sure you update the configurations accordingly.
The configuration file is named config.txt.
In the first row enter the credentials path, and in the second row enter your bucket name.
For the excecution run the following command from the jar's directory:
java -jar Nas_Ass1-1.0-SNAPSHOT.jarT InputFile OutPutFile n [terminate]

How the application works?
The implemantation contains 3 modules that comunicate with each other by sqs.

The local application workflow:
Uploading the input file to s3 then Checking if the manager is working, and if not, creating it Sending the location of the
inputfile to the manager in SQS "getAppMessagesSQS" and saving the message id for later use.
the local application waits for a message from the manager in SQS "sendAppMessagesSQS" containing the previous message id and
changing the visibilitytimeout to be 1 second after that it saves the ID-INFO.json from {bucketname}\{messageId} key prefix.
This json file contains a list of links, parsing types an outputs from the workers. Those matching the input file data.
then we Create an HTML containing the relevant data according to the ID-INFO.json file.
Optional - If got a termination argument, sending terminate message in SQS "getAppMessagesSQS" to the manager .

The manager workflow:
Creates the 4 SQS's for communication with application and the workers
-----
SQS bulit:
getAppMessagesSQS - For sending messages from the local applications to the manager.
sendAppMessagesSQS - For sending the results from the manager to the applications.
sendWorkerMessagesSQS - For sending messages from the manager to the workers.
getWorkerMessagesSQS - For sending messages from the workers to the manager.
------

then the manager Waits for messages from the local applications on  SQS "getAppMessagesSQS".
When Getting a txt file message from the local application split the input file and send messages via sendworkersSQS
Creating the ID-INFO.json that will contain the results data then Creating additional workers if needed, according to the
amount of messages in SQS "sendWorkersMessagesSQS".
Waits for messages from the workers on SQS "getWorkersMessagesSQS", When getting a message from a worker:
1. Updating the relevant ID-INFO.json file according to the id mentioned in the message.
2. Checking if the ID-INFO.json file is complete. If so, sending the path to the application as a SQS message in SQS
 "sendAppMessagesSQS".
if given "terminate" message from the application, the manager raises a termination flag an waits for all the opened application
requests to be finished. Later, closing all the SQSs, closing all the workers instances and finally closing itself.

The worker workflow:
 The worker waits for messages from the manager in SQS "sendWorkersMessagesSQS" and changing it's visibility timeout
 be 30 minutes.
 Downloding the file link needed to be analyzed as txt file and parses it according to the analysing type in th message.
 Uploading it to s3 in {bucketname}\{appMessageId}\{currMessageId}.txt.and  Sending to the manager the result path\error message
 if an error occured in SQS "getWorkersMessagesSQS".
 at last Delets the input file and the result file from the local memory.



Performances :
Total running time= 1081 seconds
 n = 1 ( n in the running command)

Regarding security - the credentials are being read from the LocalApplication's .aws directory and insterted directly
into the UserData when creating the manager.

Regarding scalablity - the "heavy lifting" is done by the workers which are created by the manager according to the amount
of job requests.
The manager itself is built from 2 "listeners" threads that take care of the SQS communications. In addition the manager hold
a thread pool which is responsible for handling the local application's requests and for updating the ID-INFO.json with
the responses from the workers.
The way that the manager was built allows it to be duplicated as many times as needed which helps to make the whole system
more scalable.
There is one downside in the way we built our system in regards to scalability - there is only a single SQS for all the
LocalApplication's results, so they are all waiting on the same SQS.

Persistance - Each worker when it recieves a message, it changes its VisibilityTimeout to 30 minutes, so if it dies in
the middle of the parsing, the message can be parsed later on by another worker. The manager makes sure after each request
 to open the right amount of workers needed according to the number of tasks in the SQS, meaning that even if a worker dies
 it will be replaced in the handling on the next request.
