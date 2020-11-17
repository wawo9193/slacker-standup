import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.App;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.methods.MethodsClient;
import com.slack.api.model.User;
import org.quartz.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.CronScheduleBuilder.*;
import static org.quartz.DateBuilder.*;

import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import org.quartz.SchedulerException;
import org.springframework.scheduling.support.CronTrigger;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.*;
import static org.quartz.JobBuilder.newJob;

public class Scheduler implements Job {

    private static ArrayList<String> selectedDays;
    private final Logger logger = LoggerFactory.getLogger("slacker-standup");
    private final MethodsClient client = Slack.getInstance().methods();

    public Scheduler(){
        selectedDays = new ArrayList<>();
    }

    public Scheduler(ArrayList<String> selectedDays) {
        this.selectedDays = selectedDays;
    }

    @Override
    public void execute(JobExecutionContext jec) throws JobExecutionException {

        System.out.println("******");
        try {
            var user_result = client.usersList(r -> r
                .token(System.getenv("SLACK_BOT_TOKEN"))
            );

            // Call the chat.postMessage method using the built-in WebClient
            var post_result = client.chatPostMessage(r -> r
                    // The token you used to initialize your app
                    .token(System.getenv("SLACK_BOT_TOKEN"))
                    .channel("U019Q5UCUCS")
                    .blocks(asBlocks(
                            section(section -> section.text(markdownText(":wave: Press the button to fill out standup!"))),
                            actions(actions -> actions
                                    .elements(asElements(
                                            button(b -> b.actionId("standup-modal").text(plainText(pt -> pt.text("Enter Standup"))))
                                    ))
                            )))
            );

            logger.info("result {}", post_result);

//            for (User user : user_result.getMembers()) {
//                // Call the chat.postMessage method using the built-in WebClient
//                var post_result = client.chatPostMessage(r -> r
//                        // The token you used to initialize your app
//                        .token(System.getenv("SLACK_BOT_TOKEN"))
//                        .channel(user.getId())
//                        .blocks(asBlocks(
//                                section(section -> section.text(markdownText(":wave: Press the button to schedule!"))),
//                                actions(actions -> actions
//                                        .elements(asElements(
//                                                button(b -> b.actionId("standup-modal").text(plainText(pt -> pt.text("Enter Standup"))))
//                                        ))
//                                )))
//                );
//                // Print result, which includes information about the message (like TS)
//                logger.info("result {}", post_result);
//                // Store the entire user object (you may not need all of the info)
////                usersStore.put(user.getId(), user);
////                System.out.println("1: " + user.getId());
////                System.out.println("2: " + user.getName());
//            }

        } catch (IOException e) {
            logger.error("IO Exception", e);
        } catch (SlackApiException e) {
            logger.error("SlackApiException", e);
        }

        logger.info("executing job");
    }

    public static void schedule() throws SchedulerException {
        SchedulerFactory schedFact = new StdSchedulerFactory();
        
        org.quartz.Scheduler sched = schedFact.getScheduler();

        sched.start();

        // Delete the job with the trigger
        sched.interrupt("myJob");
        sched.deleteJob(JobKey.jobKey("myJob", "group1"));

        // define the job and tie it to our myJob class
        JobDetail job = newJob(Scheduler.class)
                .withIdentity("myJob", "group1")
                .build();

        // Trigger the job to run now
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("myTrigger", "group1")
                .startNow()
                .build();

        // Tell quartz to schedule the job using our trigger
        sched.scheduleJob(job, trigger);

//        // Trigger job to run when specified
//        for (String day : selectedDays) {
//            // define the job and tie it to our HelloJob class
//            JobDetail job = newJob(Scheduler.class)
//                    .withIdentity("myJob", "group" + day)
//                    .build();
//
//            // Trigger the job to run at 10am every day specified
//            Trigger trigger = newTrigger()
//                    .withIdentity("myTrigger", "group" + day)
//                    .withSchedule(cronSchedule("0 0 10 ? * " + day)
//                    .inTimeZone(TimeZone.getTimeZone("America/Denver")))
//                    .build();
//
//            // Tell quartz to schedule the job using our trigger
//            sched.scheduleJob(job, trigger);
        }
    }
}