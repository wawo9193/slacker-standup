import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.google.gson.Gson;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.service.InstallationService;
import com.slack.api.bolt.service.OAuthStateService;
import com.slack.api.bolt.service.builtin.AmazonS3InstallationService;
import com.slack.api.bolt.service.builtin.AmazonS3OAuthStateService;
import com.slack.api.bolt.model.Installer;
import com.slack.api.bolt.service.builtin.FileInstallationService;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;
import com.amazonaws.services.s3.AmazonS3;
import java.io.File;
import java.io.IOException;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

class Res {
    boolean ok;
    String app_id;
    OAuthV2AccessResponse.AuthedUser authed_user;
    String scope;
    String token_type;
    String access_token;
    String bot_user_id;
    OAuthV2AccessResponse.Team team;
    OAuthV2AccessResponse.Enterprise enterprise;
}

public class Controller implements Subject {
    static final Logger logger = LoggerFactory.getLogger("slacker-standup");
    static final Scheduler scheduler = new Scheduler();
    static final Controller controller = new Controller();
    static final Views view = new Views();
    static String SLACK_CHANNEL_ID = "";

    // Env variables
    static final String SLACK_BOT_TOKEN = System.getenv("SLACK_BOT_TOKEN");
    static final String SLACK_SIGNING_SECRET = System.getenv("SLACK_SIGNING_SECRET");
    static final String SLACK_CLIENT_SECRET = System.getenv("SLACK_CLIENT_SECRET");
    static final String SLACK_CLIENT_ID = System.getenv("SLACK_CLIENT_ID");
    static final String SLACK_REDIRECT_URI = System.getenv("SLACK_REDIRECT_URI");
    static final String SLACK_SCOPES = System.getenv("SLACK_SCOPES");
    static final String SLACK_USER_SCOPES = System.getenv("SLACK_USER_SCOPES");
    static final String SLACK_INSTALL_PATH = System.getenv("SLACK_INSTALL_PATH");
    static final String SLACK_REDIRECT_URI_PATH = System.getenv("SLACK_REDIRECT_URI_PATH");
    static final String SLACK_OAUTH_COMPLETION_URL = System.getenv("SLACK_OAUTH_COMPLETION_URL");
    static final String SLACK_OAUTH_CANCELLATION_URL = System.getenv("SLACK_OAUTH_CANCELLATION_URL");
    static final String REDIS_URL = System.getenv("REDIS_URL");
    static final Integer PORT = Integer.valueOf(System.getenv("PORT"));

    private static Jedis getConnection() throws URISyntaxException {
        URI redisURI = new URI(REDIS_URL);
        Jedis jedis = new Jedis(redisURI, 0);
        return jedis;
    }

    // Observer is the scheduler
    public void notifyObservers(ArrayList<String> days, ArrayList<String> users, String times, String timeZone){
        scheduler.update(days, users, times, timeZone);
    }

    public static HashMap<String,String> parseQueryString(String query) {
        HashMap<String,String> hm = new HashMap<>();

        String[] qs = query.split("&");

        for (String s : qs) {
            String[] e = s.split("=");
            if (e.length>1) {
                hm.put(e[0],e[1]);
            }
        }
        return hm;
    }

    static String authorize(String teamId, String enterpriseId, Jedis jedis) {
        teamId = "id" + teamId; // prepend to avoid a key hash type error in Jedis

        if (jedis.exists(teamId)) {
            return jedis.hget(teamId, "bot_access_token");
        } else if (jedis.exists(enterpriseId)) {
            return jedis.hget(enterpriseId, "bot_access_token");
        }

        throw new RuntimeException(); // no such team/enterprise saved
    }

    private static void save(AmazonS3 s3, String s3Key, String json, String bucketName) {
        PutObjectResult botPutResult = s3.putObject(bucketName, s3Key, json);
        logger.info("S3 save result: {}", botPutResult.getMetadata());
    }

    public static void main(String[] args) throws Exception {
        /* The main instantiates the app variable which has the ability
         * to start a servlet server. In-between the app and server
         * instantiation are all the controller route handlers.
         */

        // Public access is blocked on this bucket
        String awsS3BucketName = "slacker-standup-config";
        InstallationService installationService = new AmazonS3InstallationService(awsS3BucketName);

        // Set true to store every single installation as a different record
        // installationService.setHistoricalDataEnabled(true);

        // Configuring/instantiating the slack api app object
        App apiApp = new App();
        apiApp.service(installationService);
        Jedis jedis = getConnection();

        /**************** ROUTE HANDLERS ****************/

        /***** SLASH COMMAND HANDLERS *****/
        apiApp.command("/slack/events/schedule", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();
            String teamId = req.getPayload().getTeamId();
            MethodsClient client = Slack.getInstance().methods();

            Runnable r = () -> {
                try {
                    var result = client.chatPostMessage(r1 -> r1
                            // The token you used to initialize your app
                            .token(authorize(req.getPayload().getTeamId(), req.getPayload().getEnterpriseId(), jedis))
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

        /***** BLOCK ACTION HANDLERS *****/
        apiApp.blockAction("schedule-modal", (req, ctx) -> {
            ViewsOpenResponse viewsOpenRes = ctx.client().viewsOpen(r -> r
                    .triggerId(ctx.getTriggerId())
                    .view(view.buildScheduleView()));
            if (viewsOpenRes.isOk()) return ctx.ack();
            else return Response.builder().statusCode(500).body(viewsOpenRes.getError()).build();
        });

        apiApp.blockAction("standup-modal", (req, ctx) -> {
            ViewsOpenResponse viewsOpenRes = ctx.client().viewsOpen(r -> r
                    .triggerId(ctx.getTriggerId())
                    .view(view.buildStandupView()));
            if (viewsOpenRes.isOk()) return ctx.ack();
            else return Response.builder().statusCode(500).body(viewsOpenRes.getError()).build();
        });

        apiApp.blockAction("standup-modal-skip", (req, ctx) -> {
            String teamId = req.getPayload().getTeam().getId();

            var client = Slack.getInstance().methods();
            try {
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(authorize(teamId, "", jedis))
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
        apiApp.viewSubmission("schedule-standups", (req, ctx) -> {
            /*
            * Functionality: when the stand-up scheduler view is submitted, the callback id, block id's, and action id's
            * can be used to parse the request body so that all variables can be obtained and used to notify the scheduler.
            */

            // Parse request body payload into values
            Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues(); // all values are in this portion of payload
            List<ViewState.SelectedOption> days = stateValues.get("days-block").get("select-days").getSelectedOptions();
            String teamId = req.getPayload().getTeam().getId();
            ArrayList<String> users = (ArrayList<String>) stateValues.get("user-block").get("select-user").getSelectedUsers();
            String cronTime = stateValues.get("time-block").get("select-time").getSelectedOption().getValue();
            String time = stateValues.get("time-block").get("select-time").getSelectedOption().getText().getText();
            String timeZone = stateValues.get("timezone-block").get("select-timezone").getSelectedOption().getText().getText();
            String quartzTimeZone = stateValues.get("timezone-block").get("select-timezone").getSelectedOption().getValue();
            String channelId = req.getPayload().getUser().getId();
            ArrayList<String> selectedD = new ArrayList<>();
            ArrayList<String> selectedDays = new ArrayList<>();
            SLACK_CHANNEL_ID = stateValues.get("channel-block").get("select-channel").getSelectedChannel(); // shared class value, selected channel for bot output

            for (ViewState.SelectedOption element : days) {
                selectedDays.add(element.getValue());
                selectedD.add(element.getText().getText());
            }

            try {
                controller.notifyObservers(selectedDays, users, cronTime, quartzTimeZone);
                scheduler.schedule();

                MethodsClient client = Slack.getInstance().methods();
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(authorize(teamId, "", jedis))
                        .channel(channelId)
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText("You scheduled your standup for " + selectedD.toString() + " at " + time + " " + timeZone)))
                        ))
                );
                logger.info("result: {}", result);
            } catch (SchedulerException e) {
                logger.error("error: {}",e);
            }
            return ctx.ack();
        });


        apiApp.viewSubmission("submission-standups", (req, ctx) -> {
            /*
            * Functionality: when the stand-up view is submitted by the user, the inputs are block formatted
            * and sent to the designated <SLACK_CHANNEL_ID>
            */
            String teamId = req.getPayload().getTeam().getId();
            String username = req.getPayload().getUser().getUsername();

            try {
                MethodsClient client = Slack.getInstance().methods();
                Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
                String inp1 = stateValues.get("prev-tasks").get("agenda-1").getValue();
                String inp2 = stateValues.get("to-do").get("agenda-2").getValue();
                String inp3 = stateValues.get("blockers").get("agenda-3").getValue();

                ChatPostMessageResponse response = client.chatPostMessage(r -> r
                        .token(authorize(teamId, "", jedis))
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

        apiApp.viewSubmission("submission-standups", (req, ctx) -> {
            String username = req.getPayload().getUser().getUsername();
            String teamId = req.getPayload().getTeam().getId();
            String channelId = req.getPayload().getUser().getId();

            try {
                MethodsClient client = Slack.getInstance().methods();
                Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
                String inp1 = stateValues.get("prev-tasks").get("agenda-1").getValue();
                String inp2 = stateValues.get("to-do").get("agenda-2").getValue();
                String inp3 = stateValues.get("blockers").get("agenda-3").getValue();

                ChatPostMessageResponse response = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(authorize(teamId, "", jedis))
                        .channel(channelId)
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
                // Print response
                logger.info("response: {}", response);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });


        /***** VIEW CLOSURE HANDLERS *****/
        apiApp.viewClosed("submission-standups", (req, ctx) -> {
            String channelId = req.getPayload().getUser().getId();
            String teamId = req.getPayload().getTeam().getId();

            try {
                MethodsClient client = Slack.getInstance().methods();
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(authorize(teamId, "", jedis))
                        .channel(channelId)
                        .text("You cancelled your standup :unamused:")
                );
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        apiApp.viewClosed("schedule-standups", (req, ctx) -> {
            MethodsClient client = Slack.getInstance().methods();
            String channelId = req.getPayload().getUser().getId();
            String teamId = req.getPayload().getTeam().getId();

            try {
                ChatPostMessageResponse result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(authorize(teamId, "", jedis))
                        .channel(channelId)
                        .text("You cancelled scheduling for your standup.:confused:")
                );
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        // Configure the oauth app route handler
        final App oauthApp = new App().asOAuthApp(true);
        oauthApp.service(installationService);

        oauthApp.endpoint("/slack/oauth/start", (req, ctx) -> {
            /*
             * Functionality: when a new workspace clicks the 'Allow' button on granular scope permissions,
             * this is the redirect uri endpoint that is hit. Once it goes in here it sends a curl command to
             * exchange for the access token for that specific workspace. Objects are then saved into the S3
             * bucket, and a k,v pair is created in the Jedis in-memory store for authorization (in the short term,
             * eventually will implement 7-layer OSI model).
             * Sources:
             * * https://www.baeldung.com/java-curl
             * * https://stackoverflow.com/a/19177892/10783453 // GSON to/from JSON using .Class
             */

            try {
                HashMap<String,String> queryMap = new HashMap<>(parseQueryString(req.getQueryString()));
                String code = queryMap.get("code");
                String command = "curl -F code=" + code + " -F client_id=" + SLACK_CLIENT_ID + " -F client_secret=" + SLACK_CLIENT_SECRET + " https://slack.com/api/oauth.v2.access";

                Process process = Runtime.getRuntime().exec(command);
                InputStream is = process.getInputStream();
                StringBuilder sb = new StringBuilder();
                int c;

                while((c = is.read()) != -1) {
                    sb.append((char)c);
                }
                String jsonStr = sb.toString();

                // parse json string response to get team id and access token
                Gson g = new Gson();
                Res res = g.fromJson(jsonStr, Res.class);
                System.out.println(":::: " + res.access_token);
                System.out.println(res);
                String botJson = new JSONObject()
                        .put("appId", res.app_id)
                        .put("enterpriseId", "")
                        .put("teamId", res.team.getId())
                        .put("teamName", res.team.getName())
                        .put("scope", res.scope)
                        .put("botId", "")
                        .put("botUserId", res.bot_user_id)
                        .put("botScope", "")
                        .put("botAccessToken", res.access_token)
                        .put("installedAt", 0)
                        .toString();

                String installerJson = new JSONObject()
                        .put("appId", res.app_id)
                        .put("enterpriseId", "")
                        .put("teamId", res.team.getId())
                        .put("teamName", res.team.getName())
                        .put("installerUserId", "")
                        .put("installerUserScope", "")
                        .put("installerUserAccessToken", "")
                        .put("scope", res.scope)
                        .put("botScope", "")
                        .put("botId", "")
                        .put("botUserId", res.bot_user_id)
                        .put("botAccessToken", res.access_token)
                        .put("incomingWebhookUrl", "")
                        .put("incomingWebhookChannelId", "")
                        .put("incomingWebhookConfigurationUrl", "")
                        .put("installedAt", 0)
                        .toString();

                jedis.hset("id" + res.team.getId(), "bot_access_token", res.access_token);
                jedis.hset("id" + res.team.getId(), "bot_user_id", res.bot_user_id);

                // save oauth info into S3
                AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
                save(s3, "installer/none-" + res.team.getId() + "-" + res.authed_user.getId(), installerJson, "slacker-standup-config");
                save(s3, "bot/none-" + res.team.getId(), botJson, "slacker-standup-config");
                save(s3, "bot/none-" + res.bot_user_id, botJson, "slacker-standup-config");

                // post an initial message in home channel
                var client = Slack.getInstance().methods();
                try {
                    var result = client.chatPostMessage(r -> r
                                    // The token you used to initialize your app
                                    .token(authorize(res.team.getId(), "", jedis))
                                    .channel(res.authed_user.getId())
                                    .text("Hello :wave: this is the beginning of your slacker stand-up chat. Use the slash command, \"/schedule\" to schedule your stand-up!")
                    );
                    // Print result, which includes information about the message (like TS)
                    logger.info("result {}", result);
                } catch (IOException | SlackApiException e) {
                    logger.error("error: {}", e.getMessage(), e);
                }

                process.destroy();

            } catch (JedisConnectionException | IOException e) {
                logger.error("error {}", e);
                System.out.println("ERR: " + e);
            }

            return Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body("successfully authorized")
                    .build();
        });

        oauthApp.endpoint("/favicon.ico", (req, ctx) -> {
            System.out.println(req.getQueryString());
            return Response.builder()
                   .statusCode(200)
                   .contentType("text/html")
                   .build();
        });

        // Store valid state parameter values in Amazon S3 storage
        OAuthStateService stateService = new AmazonS3OAuthStateService(awsS3BucketName);
        oauthApp.service(stateService);

        logger.info("RUNNING NOW ON : " + PORT);
        Map<String, App> apps = new HashMap<>();
        apps.put("/slack/events", apiApp);
        apps.put("/slack/oauth", oauthApp);
        SlackAppServer server = new SlackAppServer(apps, PORT);
        server.start();
    }
}
