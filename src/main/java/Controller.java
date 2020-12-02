import com.slack.api.app_backend.slash_commands.SlashCommandResponseSender;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import java.io.IOException;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.webhook.WebhookResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

public class Controller implements Subject {
    static final Logger logger = LoggerFactory.getLogger("slacker-standup");
    static final Scheduler scheduler = new Scheduler();
    static final Controller controller = new Controller();
    static final Views view = new Views();

    // Env variables
    private static final String SLACK_BOT_TOKEN = System.getenv("SLACK_BOT_TOKEN");
    private static final String SLACK_SIGNING_SECRET = System.getenv("SLACK_SIGNING_SECRET");
    private static String SLACK_CHANNEL_ID = "";
    private static final Integer PORT = Integer.valueOf(System.getenv("PORT"));

    // Observer is the scheduler
    public void notifyObservers(ArrayList<String> days, ArrayList<String> users, String times, String timeZone){
        scheduler.update(days, users, times, timeZone);
    }

    public static void main(String[] args) throws Exception {
        /* The main instantiates the app variable which has the ability
         * to start a servlet server. In-between the app and server
         * instantiation are all the controller route handlers.
         */

        // Configuring/instantiating the slack api app object
        AppConfig config = new AppConfig();
        config.setSingleTeamBotToken(SLACK_BOT_TOKEN);
        config.setSigningSecret(SLACK_SIGNING_SECRET);
        App app = new App(config);

        /**************** ROUTE HANDLERS ****************/

        /***** SLASH COMMAND HANDLERS *****/
        app.command("/schedule", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();

            var client = Slack.getInstance().methods();
            Runnable r = () -> {
                try {
                    var result = client.chatPostMessage(r1 -> r1
                            .token(SLACK_BOT_TOKEN)
                            .channel(channelId)
                            .text("Schedule your standup!")
                            .blocks(asBlocks(
                                    section(section -> section.text(markdownText(":wave: Press the button to schedule!"))),
                                    actions(actions -> actions
                                            .elements(asElements(
                                                    button(b -> b.actionId("schedule-modal").text(plainText(pt -> pt.text("Schedule Standups"))))
                                            ))
                                    )))
                    );
                    logger.info("result {}", result);
                } catch (IOException | SlackApiException e) {
                    logger.error("error: {}", e.getMessage(), e);
                }
            };

            // to overcome the 3 second operation timeout message, multiple threads
            // are deployed to return 200 response and submit message to channel
            ExecutorService executor = Executors.newCachedThreadPool();
            executor.submit(r);
            executor.shutdown();

            return ctx.ack(); // respond with 200 OK
        });

        app.blockAction("schedule-modal", (req, ctx) -> {
            ViewsOpenResponse viewsOpenRes = ctx.client().viewsOpen(r -> r
                    .triggerId(ctx.getTriggerId())
                    .view(view.buildScheduleView()));
            if (viewsOpenRes.isOk()) return ctx.ack();
            else return Response.builder().statusCode(500).body(viewsOpenRes.getError()).build();
        });

        app.blockAction("standup-modal", (req, ctx) -> {
            ViewsOpenResponse viewsOpenRes = ctx.client().viewsOpen(r -> r
                    .triggerId(ctx.getTriggerId())
                    .view(view.buildStandupView()));
            if (viewsOpenRes.isOk()) return ctx.ack();
            else return Response.builder().statusCode(500).body(viewsOpenRes.getError()).build();
        });

        app.blockAction("standup-modal-skip", (req, ctx) -> {
            var client = Slack.getInstance().methods();
            try {
                var result = client.chatPostMessage(r -> r
                        .token(SLACK_BOT_TOKEN)
                        .channel(req.getPayload().getUser().getId())
                        .text("You skipped your standup today :pensive:, see you next time!:smile:")
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack();
        });

        /***** VIEW SUBMISSION HANDLERS *****/
        app.viewSubmission("schedule-standups", (req, ctx) -> {
            /*
            * Functionality: when the stand-up scheduler view is submitted, the callback id, block id's, and action id's
            * can be used to parse the request body so that all variables can be obtained and used to notify the scheduler.
            */

            // Parse request body payload into values
            Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues(); // all values are in this portion of payload
            List<ViewState.SelectedOption> days = stateValues.get("days-block").get("select-days").getSelectedOptions();
            ArrayList<String> users = (ArrayList<String>) stateValues.get("user-block").get("select-user").getSelectedUsers();
            String cronTime = stateValues.get("time-block").get("select-time").getSelectedOption().getValue();
            String timeZone = stateValues.get("timezone-block").get("select-timezone").getSelectedOption().getValue();
            String userId = req.getPayload().getUser().getId();
            ArrayList<String> selectedD = new ArrayList<>();
            ArrayList<String> selectedDays = new ArrayList<>();
            SLACK_CHANNEL_ID = stateValues.get("channel-block").get("select-channel").getSelectedChannel(); // shared class value, selected channel for bot output

            for (ViewState.SelectedOption element : days) {
                selectedDays.add(element.getValue());
                selectedD.add(element.getText().getText());
            }

            try {
                controller.notifyObservers(selectedDays, users, cronTime, timeZone);
                scheduler.schedule();

                MethodsClient client = Slack.getInstance().methods();
                var result = client.chatPostMessage(r -> r
                        .token(SLACK_BOT_TOKEN)
                        .channel(userId)
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText("You scheduled your standup for " + selectedD.toString() + " at " + timeZone)))
                        ))
                );
                logger.info("result: {}", result);
            } catch (SchedulerException e) {
                logger.error("error: {}",e);
            }
            return ctx.ack();
        });

        app.viewSubmission("submission-standups", (req, ctx) -> {
            /*
            * Functionality: when the stand-up view is submitted by the user, the inputs are block formatted
            * and sent to the designated <SLACK_CHANNEL_ID>
            */
            String username = req.getPayload().getUser().getUsername();
            try {
                MethodsClient client = Slack.getInstance().methods();
                Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
                String inp1 = stateValues.get("prev-tasks").get("agenda-1").getValue();
                String inp2 = stateValues.get("to-do").get("agenda-2").getValue();
                String inp3 = stateValues.get("blockers").get("agenda-3").getValue();

                ChatPostMessageResponse response = client.chatPostMessage(r -> r
                        .token(SLACK_BOT_TOKEN)
                        .channel(SLACK_CHANNEL_ID)
                        .text("A Standup was Submitted!")
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText("*@" + username + " submitted a standup! :rocket:*"))),
                                divider(),
                                section(section -> section.text(markdownText("*What have you been working on?*\n"))),
                                section(section -> section.text(markdownText(inp1 + "\n"))),
                                divider(),
                                section(section -> section.text(markdownText("*What will you be working on?*\n"))),
                                section(section -> section.text(markdownText(inp2 + "\n"))),
                                divider(),
                                section(section -> section.text(markdownText("*Do you have any blockers?*\n"))),
                                section(section -> section.text(markdownText(inp3 + "\n")))
                        ))
                );
                logger.info("response: {}", response);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        /***** VIEW CLOSURE HANDLERS *****/
        app.viewClosed("submission-standups", (req, ctx) -> {
            var client = Slack.getInstance().methods();
            var channelId = req.getPayload().getUser().getId();
            try {
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(SLACK_BOT_TOKEN)
                        .channel(channelId)
                        .text("You cancelled your standup :unamused:")
                );
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        app.viewClosed("schedule-standups", (req, ctx) -> {
            MethodsClient client = Slack.getInstance().methods();
            String channelId = req.getPayload().getUser().getId();
            try {
                ChatPostMessageResponse result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(SLACK_BOT_TOKEN)
                        .channel(channelId)
                        .text("You cancelled scheduling for your standup.:confused:")
                );
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        // Creating server instance on env port
        logger.info("RUNNING NOW ON : " + PORT);
        SlackAppServer server = new SlackAppServer(app, PORT);
        server.start();
    }
}