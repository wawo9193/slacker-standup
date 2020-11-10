import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.methods.SlackApiException;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class ChatScheduleMessage {

    public static void main(String[] args) throws Exception {
        var config = new AppConfig();
        config.setSingleTeamBotToken(System.getenv("SLACK_BOT_TOKEN"));
        config.setSigningSecret(System.getenv("SLACK_SIGNING_SECRET"));
        var app = new App(config); // `new App()` does the same

        app.command("/pingtest", (req, ctx) -> {
            var logger = ctx.logger;
//            var tomorrow = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1).withHour(9);
            try {
                var payload = req.getPayload();
                // Call the chat.scheduleMessage method using the built-in WebClient
                var result = ctx.client().chatScheduleMessage(r -> r
                        // The token you used to initialize your app
                        .token(ctx.getBotToken())
                        .channel(payload.getChannelId())
                        .text(payload.getText())
                        // Time to post message, in Unix Epoch timestamp format
//                        .postAt((int) tomorrow.toInstant().getEpochSecond())
                );
                // Print result
                logger.info("result: {}", result);
            } catch (IOException | SlackApiException e) {
                logger.error("error: {}", e.getMessage(), e);
            }
            // Acknowledge incoming command event
            return ctx.ack();
        });

        var server = new SlackAppServer(app);
        server.start();
    }
}