import com.slack.api.bolt.App;
import com.slack.api.bolt.jetty.SlackAppServer;

public class MyApp {
    public static void main(String[] args) throws Exception {
        App app = new App();

        app.command("/ping", (req, ctx) -> {
            return ctx.ack(":wave: pong");
        });

        SlackAppServer server = new SlackAppServer(app);
        server.start();
    }
}