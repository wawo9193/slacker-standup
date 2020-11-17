import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import java.io.IOException;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

public class Server{

    private static final Logger logger = LoggerFactory.getLogger("slacker-standup");
    private static final App app = new App();
//
//    static View buildScheduleView() {
//        return view(view -> view
//                .callbackId("schedule-standups")
//                .type("modal")
//                .notifyOnClose(true)
//                .title(viewTitle(title -> title.type("plain_text").text("Standup").emoji(true)))
//                .submit(viewSubmit(submit -> submit.type("plain_text").text("Submit").emoji(true)))
//                .close(viewClose(close -> close.type("plain_text").text("Cancel").emoji(true)))
//                .blocks(asBlocks(
//                        input(input -> input
//                                .label(plainText("Select days for standup:"))
//                                .blockId("days-block")
//                                .element(checkboxes(i -> i
//                                        .actionId("select-days")
//                                        .options(Arrays.asList(
//                                                option(plainText("Monday"), "2"), // value represents cron job day of week value
//                                                option(plainText("Tuesday"), "3"),
//                                                option(plainText("Wednesday"), "4"),
//                                                option(plainText("Thursday"), "5"),
//                                                option(plainText("Friday"), "6")
//                                        ))
//
//                                ))
//                        )
//                ))
//        );
//    }
//
//    static View buildStandupView() {
//        return view(view -> view
//                .callbackId("submission-standups")
//                .type("modal")
//                .notifyOnClose(true)
//                .title(viewTitle(title -> title.type("plain_text").text("Standup").emoji(true)))
//                .submit(viewSubmit(submit -> submit.type("plain_text").text("Submit").emoji(true)))
//                .close(viewClose(close -> close.type("plain_text").text("Cancel").emoji(true)))
//                .blocks(asBlocks(
//                        input(input -> input
//                                .blockId("prev-tasks")
//                                .element(plainTextInput(pti -> pti.actionId("agenda-1").multiline(true)))
//                                .label(plainText(pt -> pt.text("What have you been working on?").emoji(true)))
//                        ),
//                        input(input -> input
//                                .blockId("to-do")
//                                .element(plainTextInput(pti -> pti.actionId("agenda-2").multiline(true)))
//                                .label(plainText(pt -> pt.text("What will you work on?").emoji(true)))
//                        ),
//                        input(input -> input
//                                .blockId("blockers")
//                                .element(plainTextInput(pti -> pti.actionId("agenda-3").multiline(true)))
//                                .label(plainText(pt -> pt.text("Do you have any blockers?").emoji(true)))
//                        )
//                ))
//        );
//    }

    public static void main(String[] args) throws Exception {
        var config = new AppConfig();
        Views view = new Views();
        config.setSingleTeamBotToken(System.getenv("SLACK_BOT_TOKEN"));
        config.setSigningSecret(System.getenv("SLACK_SIGNING_SECRET"));

        App app = Server.app;
        Logger logger = Server.logger;

        app.command("/schedule", (req, ctx) -> {
            String commandArgText = req.getPayload().getText();
            String channelId = req.getPayload().getChannelId();
            String channelName = req.getPayload().getChannelName();
            String text = "You said " + commandArgText + " at <#" + channelId + "|" + channelName + ">";

            var client = Slack.getInstance().methods();

            try {
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                                // The token you used to initialize your app
                                .token(System.getenv("SLACK_BOT_TOKEN"))
                                .channel(channelId)
                                .blocks(asBlocks(
                                    section(section -> section.text(markdownText(":wave: Press the button to schedule!"))),
                                    actions(actions -> actions
                                            .elements(asElements(
                                                    button(b -> b.actionId("schedule-modal").text(plainText(pt -> pt.text("Schedule Standups"))))
                                            ))
                                    )))
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack(text); // respond with 200 OK
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
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(System.getenv("SLACK_BOT_TOKEN"))
                        .channel("D01E8T8L6DQ")
                        .text("You skipped your standup today :pensive:, see you next time!:smile:")
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack();
        });

        app.viewSubmission("schedule-standups", (req, ctx) -> {
            Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
            List<ViewState.SelectedOption> days = stateValues.get("days-block").get("select-days").getSelectedOptions();

            ArrayList<String> selectedDays = new ArrayList<>();
            for (ViewState.SelectedOption element : days) {
                selectedDays.add(element.getValue());
            }

            try {
                Scheduler scheduler = new Scheduler(selectedDays);
                scheduler.schedule();
            } catch (SchedulerException e) {
                e.printStackTrace();
            }
            return ctx.ack();
        });
        app.viewClosed("submission-standups", (req, ctx) -> {
            var client = Slack.getInstance().methods();
            try {
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(System.getenv("SLACK_BOT_TOKEN"))
                        .channel("D01E8T8L6DQ")
                        .text("You cancelled your standup:unamused:")
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack();
        });

        app.viewClosed("schedule-standups", (req, ctx) -> {
            var client = Slack.getInstance().methods();
            try {
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(System.getenv("SLACK_BOT_TOKEN"))
                        .channel("D01E8T8L6DQ")
                        .text("You cancelled scheduling for your standup.:confused:")
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack();
        });


        app.viewSubmission("submission-standups", (req, ctx) -> {
            System.out.println(req.getPayload());
            String username = req.getPayload().getUser().getUsername();

            try {
                MethodsClient client = Slack.getInstance().methods();
                Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
                String inp1 = stateValues.get("prev-tasks").get("agenda-1").getValue();
                String inp2 = stateValues.get("to-do").get("agenda-2").getValue();
                String inp3 = stateValues.get("blockers").get("agenda-3").getValue();

                ChatPostMessageResponse response = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(System.getenv("SLACK_BOT_TOKEN"))
                        .channel(System.getenv("SLACK_CHANNEL_ID"))
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText("*@" + username + " submitted a standup! :rocket:*"))),
                                divider(),
                                section(section -> section.text(markdownText("*What have you been working on?*\n"))),
                                section(section -> section.text(markdownText(inp1 + "\n"))),
                                divider(),
                                section(section -> section.text(markdownText("*What will you work on?*\n"))),
                                section(section -> section.text(markdownText(inp2 + "\n"))),
                                divider(),
                                section(section -> section.text(markdownText("*Do you have any blockers?*\n"))),
                                section(section -> section.text(markdownText(inp3 + "\n")))
                        ))
                );
                // Print response
                logger.info("response: {}", response);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        var port = Integer.valueOf(System.getenv("PORT"));
        logger.info("RUNNING NOW ON : " + port);
        var slack_server = new SlackAppServer(app, port);
        slack_server.start();
    }
}