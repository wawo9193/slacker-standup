## Team Members
#### Wayne Wood 
* email: wawo9193@colorado.edu
 
#### Artem Nekrasov
*  email: arne1063@colorado.edu

## slacker-standup
virtual standup meetings through the Slack API

## To build/run
**Note:** this requires having heroku and a slack app setup. The slack app setup can be referenced at: [Slack API docs](https://api.slack.com/start)
* clone repo
* setup necessary environment variables in Heroku config using  
    ```
    heroku config:set <VAR_NAME>=<value>
    ```
* build/compile using  
    ```
    mvn clean compile assembly:single
    ```
* deploy to Heroku using  
    ```
    deploy:jar target/<your-pom.xml-artifact-id>-1.0-SNAPSHOT-jar-with-dependencies.jar --app <your-heroku-app-name>
    ```
* refer to procfile on Heroku web command to run the jar file created.


## Sources used:
* [Passing array as job parameter](https://stackoverflow.com/a/23148027/10783453)
* [Multithreading to avoid operation timeout in Slack API](https://stackoverflow.com/a/12551542/10783453)
* [Slack's Java Bolt framework](https://api.slack.com/start/building/bolt-java)
    * [Block elements/actions in Slack API](https://api.slack.com/reference/block-kit/block-elements#multi_select )
    * [Modals from the Slack API in Bolt framework](https://api.slack.com/surfaces/modals/using)
* [Quartz scheduler examples, but also many other Java implemented examples](https://www.baeldung.com)
* [Quartz job scheduler/executor tutorial](http://www.quartz-scheduler.org/documentation/2.4.0-SNAPSHOT/tutorials/index.html)
* [Cron expression tutorial (Quartz)](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html)
