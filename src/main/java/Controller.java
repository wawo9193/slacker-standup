import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slack.api.app_backend.slash_commands.SlashCommandResponseSender;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.view.ViewState;

import java.io.File;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.Map.entry;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import com.slack.api.bolt.servlet.SlackAppServlet;
import com.slack.api.bolt.servlet.SlackOAuthAppServlet;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.servlet.annotation.WebServlet;

//@WebServlet("/slack/events")
//class SlackEventsController extends SlackAppServlet {
//    public SlackEventsController(App app) { super(app); }
//}
//
//@WebServlet("/slack/oauth")
//class SlackOAuthRedirectController extends SlackOAuthAppServlet {
//    public SlackOAuthRedirectController(App app) { super(app); }
//}

class Res {
    boolean ok;
    String app_id;
    OAuthV2AccessResponse.AuthedUser authedUser;
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
    //   SLACK_CLIENT_ID, SLACK_CLIENT_SECRET, SLACK_REDIRECT_URI, SLACK_SCOPES,
    //   SLACK_INSTALL_PATH, SLACK_REDIRECT_URI_PATH
    //   SLACK_OAUTH_COMPLETION_URL, SLACK_OAUTH_CANCELLATION_URL
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

    static String renderCompletionPageHtml(String queryString) { return null; }
    static String renderCancellationPageHtml(String queryString) { return null; }

    public static void main(String[] args) throws Exception {
        Jedis jedis = getConnection();
        AppConfig apiConfig = new AppConfig();
        apiConfig.setAppPath("/slack/events");
        apiConfig.setSigningSecret(SLACK_SIGNING_SECRET);
        final App apiApp = new App(apiConfig);

        apiApp.command("/schedule", (req, ctx) -> {
            String channelId = req.getPayload().getChannelId();
            String teamId = req.getPayload().getTeamId();
            MethodsClient client = Slack.getInstance().methods();

            Runnable r = () -> {
                try {
                    // Call the chat.postMessage method using the built-in WebClient
                    var result = client.chatPostMessage(r1 -> r1
                            // The token you used to initialize your app
                            .token(jedis.get(teamId))
                            .channel(channelId)
                            .text("Schedule your standup!")
                            .blocks(asBlocks(
                                    section(section -> section.text(markdownText(":wave: Press the button to schedule!"))),
                                    actions(actions -> actions
                                            .elements(asElements(
                                                    button(b -> b.actionId("schedule-modal").text(plainText(pt -> pt.text("Schedule Standups"))))
                                                   // button(b -> b.actionId("schedule-modal-skip").text(plainText(pt -> pt.text("Skip Standups"))))
                                            ))
                                    )))
                    );
                    // Print result, which includes information about the message (like TS)
                    logger.info("result {}", result);
                } catch (IOException | SlackApiException e) {
                    logger.error("error: {}", e.getMessage(), e);
                }
            };

            ExecutorService executor = Executors.newCachedThreadPool();
            executor.submit(r);
            executor.shutdown();

            return ctx.ack(); // respond with 200 OK
        });

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
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(jedis.get(teamId))
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

        apiApp.viewSubmission("schedule-standups", (req, ctx) -> {
            String teamId = req.getPayload().getTeam().getId();
            var client = Slack.getInstance().methods();
            Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
            List<ViewState.SelectedOption> days = stateValues.get("days-block").get("select-days").getSelectedOptions();
            ArrayList<String> users = (ArrayList<String>) stateValues.get("user-block").get("select-user").getSelectedUsers();
            String slackChannelId = stateValues.get("channel-block").get("select-channel").getSelectedChannel();
            String time = stateValues.get("time-block").get("select-time").getSelectedOption().getValue();
            String timeZone = stateValues.get("timezone-block").get("select-timezone").getSelectedOption().getValue();

            ArrayList<String> selectedD = new ArrayList<>();
            ArrayList<String> selectedDays = new ArrayList<>();

            for (ViewState.SelectedOption element : days) {
                selectedDays.add(element.getValue());
                selectedD.add(element.getText().getText());
            }

            try {
                SLACK_CHANNEL_ID = slackChannelId;
                controller.notifyObservers(selectedDays, users, time, timeZone);
                scheduler.schedule();

                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(jedis.get(teamId))
                        .channel(SLACK_CHANNEL_ID)
                        .blocks(asBlocks(
                                section(section -> section.text(markdownText("You scheduled your standup for " + selectedD.toString() + " at " + stateValues.get("time-block").get("select-time").getSelectedOption().getText().getText() + stateValues.get("timezone-block").get("select-timezone").getSelectedOption().getText().getText())))
                        ))
                );

                logger.info("result: {}", result);
            } catch (SchedulerException e) {
                logger.error("error: {}",e);
            }
            return ctx.ack();
        });

        apiApp.viewClosed("submission-standups", (req, ctx) -> {
            var client = Slack.getInstance().methods();
            var channelId = req.getPayload().getUser().getId();
            String teamId = req.getPayload().getTeam().getId();

            try {
                // Call the chat.postMessage method using the built-in WebClient
                var result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(jedis.get(teamId))
                        .channel(channelId)
                        .text("You cancelled your standup :unamused:")
                );
                // Print result, which includes information about the message (like TS)
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
                // Call the chat.postMessage method using the built-in WebClient
                ChatPostMessageResponse result = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(jedis.get(teamId))
                        .channel(channelId)
                        .text("You cancelled scheduling for your standup.:confused:")
                );
                // Print result, which includes information about the message (like TS)
                logger.info("result {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }

            return ctx.ack();
        });


        apiApp.viewSubmission("submission-standups", (req, ctx) -> {
            String username = req.getPayload().getUser().getUsername();
            String teamId = req.getPayload().getTeam().getId();

            try {
                MethodsClient client = Slack.getInstance().methods();
                Map<String, Map<String, ViewState.Value>> stateValues = req.getPayload().getView().getState().getValues();
                String inp1 = stateValues.get("prev-tasks").get("agenda-1").getValue();
                String inp2 = stateValues.get("to-do").get("agenda-2").getValue();
                String inp3 = stateValues.get("blockers").get("agenda-3").getValue();

                ChatPostMessageResponse response = client.chatPostMessage(r -> r
                        // The token you used to initialize your app
                        .token(jedis.get(teamId))
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
                // Print response
                logger.info("response: {}", response);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            return ctx.ack();
        });

        AppConfig authConfig = new AppConfig();
        authConfig.setAppPath("/slack/oauth");
        authConfig.setOAuthRedirectUriPathEnabled(true);
        authConfig.setOauthRedirectUriPath("/start");
        authConfig.setSigningSecret(SLACK_SIGNING_SECRET);
        authConfig.setClientId(SLACK_CLIENT_ID);
        authConfig.setRedirectUri(SLACK_REDIRECT_URI);
        authConfig.setScope(SLACK_SCOPES);
        authConfig.setUserScope(SLACK_USER_SCOPES);
        authConfig.setOauthInstallPath(SLACK_INSTALL_PATH);
        authConfig.setOauthRedirectUriPath(SLACK_REDIRECT_URI_PATH);
        authConfig.setOauthCompletionUrl(SLACK_OAUTH_COMPLETION_URL);
        authConfig.setOauthCancellationUrl(SLACK_OAUTH_CANCELLATION_URL);

        final App oauthApp = new App(authConfig).asOAuthApp(true);

        oauthApp.endpoint("/start", (req, ctx) -> {
            // https://www.baeldung.com/java-curl
            // https://stackoverflow.com/a/19177892/10783453
            System.out.println("NOT GOING INTO HERE!");
            try {
                HashMap<String,String> queryMap = new HashMap<>(parseQueryString(req.getQueryString()));
                String code = queryMap.get("code");
                String command = "curl -F code=" + code + " -F client_id=1342824380833.1470182319287 -F client_secret=" + SLACK_CLIENT_SECRET + " https://slack.com/api/oauth.v2.access";

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
                String teamId = res.team.getId();
                String botToken = res.access_token;

                // store team id and access token as k,v pair in redis
                jedis.set(teamId, botToken);

                process.destroy();

            } catch (IOException | JedisConnectionException e) {
                logger.error("error {}", e);
            }

            return Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body(renderCompletionPageHtml(req.getQueryString()))
                    .build();
        });

//        oauthApp.endpoint("/favicon.ico", (req, ctx) -> {
//           return Response.builder()
//                   .statusCode(200)
//                   .contentType("text/html")
//                   .body(renderCompletionPageHtml(req.getQueryString()))
//                   .build();
//        });
//
//        oauthApp.endpoint("GET", "/slack/oauth/completion", (req, ctx) -> {
//            return Response.builder()
//                    .statusCode(200)
//                    .contentType("text/html")
//                    .body(renderCompletionPageHtml(req.getQueryString()))
//                    .build();
//        });
//
//        oauthApp.endpoint("GET", "/slack/oauth/cancellation", (req, ctx) -> {
//            return Response.builder()
//                    .statusCode(200)
//                    .contentType("text/html")
//                    .body(renderCancellationPageHtml(req.getQueryString()))
//                    .build();
//        });

        logger.info("RUNNING NOW ON : " + PORT);
        Map<String, App> apps = new HashMap<>();
        apps.put("/slack/events", apiApp);
        apps.put("/slack/oauth",       AppConfig authConfig = new AppConfig();
        authConfig.setAppPath("/slack/oauth");
        authConfig.setOAuthRedirectUriPathEnabled(true);
        authConfig.setOauthRedirectUriPath("/start");
        authConfig.setSigningSecret(SLACK_SIGNING_SECRET);
        authConfig.setClientId(SLACK_CLIENT_ID);
        authConfig.setRedirectUri(SLACK_REDIRECT_URI);
        authConfig.setScope(SLACK_SCOPES);
        authConfig.setUserScope(SLACK_USER_SCOPES);
        authConfig.setOauthInstallPath(SLACK_INSTALL_PATH);
        authConfig.setOauthRedirectUriPath(SLACK_REDIRECT_URI_PATH);
        authConfig.setOauthCompletionUrl(SLACK_OAUTH_COMPLETION_URL);
        authConfig.setOauthCancellationUrl(SLACK_OAUTH_CANCELLATION_URL);

        final App oauthApp = new App(authConfig).asOAuthApp(true);

        oauthApp.endpoint("/start", (req, ctx) -> {
            // https://www.baeldung.com/java-curl
            // https://stackoverflow.com/a/19177892/10783453
            System.out.println("NOT GOING INTO HERE!");
            try {
                HashMap<String,String> queryMap = new HashMap<>(parseQueryString(req.getQueryString()));
                String code = queryMap.get("code");
                String command = "curl -F code=" + code + " -F client_id=1342824380833.1470182319287 -F client_secret=" + SLACK_CLIENT_SECRET + " https://slack.com/api/oauth.v2.access";

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
                String teamId = res.team.getId();
                String botToken = res.access_token;

                // store team id and access token as k,v pair in redis
                jedis.set(teamId, botToken);

                process.destroy();

            } catch (IOException | JedisConnectionException e) {
                logger.error("error {}", e);
            }

            return Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body(renderCompletionPageHtml(req.getQueryString()))
                    .build();
        });

//        oauthApp.endpoint("/favicon.ico", (req, ctx) -> {
//           return Response.builder()
//                   .statusCode(200)
//                   .contentType("text/html")
//                   .body(renderCompletionPageHtml(req.getQueryString()))
//                   .build();
//        });
//
//        oauthApp.endpoint("GET", "/slack/oauth/completion", (req, ctx) -> {
//            return Response.builder()
//                    .statusCode(200)
//                    .contentType("text/html")
//                    .body(renderCompletionPageHtml(req.getQueryString()))
//                    .build();
//        });
//
//        oauthApp.endpoint("GET", "/slack/oauth/cancellation", (req, ctx) -> {
//            return Response.builder()
//                    .statusCode(200)
//                    .contentType("text/html")
//                    .body(renderCancellationPageHtml(req.getQueryString()))
//                    .build();
//        });

        logger.info("RUNNING NOW ON : " + PORT);
        Map<String, App> apps = new HashMap<>();
        apps.put("/slack/events", apiApp);
        apps.put("/slack/oauth", oauthApp);
        SlackAppServer server = new SlackAppServer(apps, PORT);
        server.start(); oauthApp);
        SlackAppServer server = new SlackAppServer(apps, PORT);
        server.start();
    }
}
