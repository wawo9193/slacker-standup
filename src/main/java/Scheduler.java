import com.slack.api.methods.MethodsClient;
import com.slack.api.model.User;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import org.quartz.SchedulerException;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.*;
import static org.quartz.JobBuilder.newJob;

public class Scheduler implements Job, Observer {

    private ArrayList<String> selectedDays;
    private ArrayList<String> users;
    private String selectedTime;
    private String selectedTimeZone;
    private final Logger logger = LoggerFactory.getLogger("slacker-standup");
    private final MethodsClient client = Slack.getInstance().methods();

    static final String DATA_ARRAY_KEY = "users";
    final String SLACK_BOT_TOKEN = System.getenv("SLACK_BOT_TOKEN");

    public void update(ArrayList<String> selectedDays, ArrayList<String> users, String selectedTime, String selectedTimeZone) {
        /*
        * Functionality: part of the pub/sub pattern used, as this scheduler is a subscriber of
        * changes submitted to the controller
        */
        this.selectedDays = selectedDays;
        this.users = users;
        this.selectedTime = selectedTime;
        this.selectedTimeZone = selectedTimeZone;
    }

    public Scheduler() {
        selectedDays = new ArrayList<>();
        users = new ArrayList<>();
        selectedTime = new String();
        selectedTimeZone = new String();
    }

    @Override
    public void execute(JobExecutionContext jec) throws JobExecutionException {
        /*
        * This is the job that is executed when scheduled to by the 'schedule()' function,
        * which sends all participants stand-ups.
        */
        try {
            JobDataMap data = jec.getMergedJobDataMap();
            ArrayList<String> jobUsers = (ArrayList<String>) data.get(DATA_ARRAY_KEY);
            for (String userId : jobUsers) {
                var post_result = client.chatPostMessage(r -> r
                        .token(SLACK_BOT_TOKEN)
                        .channel(userId)
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText(":wave: Press the button to schedule!"))),
                                actions(actions -> actions
                                        .elements(asElements(
                                                button(b -> b.actionId("standup-modal").text(plainText(pt -> pt.text("Enter Standup")))),
                                                button(b -> b.actionId("schedule-modal-skip").text(plainText(pt -> pt.text("Skip"))))
                                        ))
                                )))
                );
                logger.info("result {}", post_result);
            }

        } catch (IOException e) {
            logger.error("IO Exception: {}", e);
        } catch (SlackApiException e) {
            logger.error("SlackApiException: {}", e);
        }
        logger.info("executing job");
    }

    public void schedule() throws SchedulerException {
        /*
        * This schedules the job (sending out stand-up options to participants)
        * by using the Quartz' cron expression scheduler.
        */

        SchedulerFactory schedFact = new StdSchedulerFactory();
        org.quartz.Scheduler sched = schedFact.getScheduler();
        sched.start();

        // define the job and tie it to our myJob class
        JobDetail job = newJob(Scheduler.class)
                .withIdentity("myJob", "group1")
                .build();

        // Trigger the job to run now
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("myTrigger", "group1")
                .startNow()
                .build();

        // pass job the users as a param
        job.getJobDataMap().put(Scheduler.DATA_ARRAY_KEY, users);

        // Tell quartz to schedule the job using our trigger
        sched.scheduleJob(job, trigger);

        // Trigger job to run when specified
//        for (String day : selectedDays) {
//            // define the job and tie it to our HelloJob class
//            JobDetail job = newJob(Scheduler.class)
//                    .withIdentity("myJob", "group" + day)
//                    .build();

//            // Trigger the job to run at specified selected time at selected day for selected time zone
//            Trigger trigger = TriggerBuilder.newTrigger()
//                    .withIdentity("myTrigger", "group" + day)
//                    .withSchedule(CronScheduleBuilder.cronSchedule(selectedTime + day)
//                    .inTimeZone(TimeZone.getTimeZone(selectedTimeZone)))
//                    .build();
//
//            // Tell quartz to schedule the job using our trigger
//            sched.scheduleJob(job, trigger);
//         }
    }
}

// https://stackoverflow.com/a/23148027/10783453 // for passing array as job parameter
// https://stackoverflow.com/a/12551542/10783453 // for multithreading to avoid operation timeout in slack api
// https://api.slack.com/start/building/bolt-java // for understanding slack's java bolt framework
// https://api.slack.com/reference/block-kit/block-elements#multi_select  // to understand block elements/actions in slack
// https://api.slack.com/surfaces/modals/using // to use modals from the slack api in bolt framework
// https://www.baeldung.com // for looking at quartz examples, but also many other java implemented ideas
// http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html // for understanding cron expressions
// http://www.quartz-scheduler.org/documentation/2.4.0-SNAPSHOT/tutorials/index.html // understanding quartz job scheduler/executor