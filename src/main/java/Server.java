import com.slack.api.bolt.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Server {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/command", new CommandHandler());
        server.createContext("/oauth", new OauthHandler());

        var config = new AppConfig();
        config.setSingleTeamBotToken(System.getenv("SLACK_BOT_TOKEN"));
        config.setSigningSecret(System.getenv("SLACK_SIGNING_SECRET"));
        System.out.println("*********Hi");
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Server is working!";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class OauthHandler implements HttpHandler {

        protected static String createUrl(int query_code) {
            String query_string = "https://slack.com/api/oauth.access?";
            query_string += "code=" + query_code + "&client_id=" + System.getenv("CLIENT_ID") + "&client_secret=" + System.getenv("CLIENT_SECRET");
            return query_string;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println(t.getResponseCode());

            // parse request body to get code
            try (InputStream fis = t.getRequestBody();
                 InputStreamReader isr = new InputStreamReader(fis,
                         StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {

                br.lines().forEach(line -> System.out.println("*" + line + "*"));
            }

            if (t.getResponseCode() == -1) {
                String response = "Error, no code received.";
                t.sendResponseHeaders(500, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                try {
                    // create get request to Slack
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(createUrl(t.getResponseCode())))
                            .build();

                    // get response
                    HttpResponse<String> response = null;
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String res_body = response.body();
                    System.out.println(response.body());

                    // send response back in exchange
                    t.sendResponseHeaders(200, res_body.length());
                    OutputStream os = t.getResponseBody();
                    os.write(res_body.getBytes());
                    os.close();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}