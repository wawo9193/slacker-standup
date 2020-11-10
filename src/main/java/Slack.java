public class Slack {
    private String SLACK_BOT_TOKEN="xoxb-1342824380833-1491088995860-uXD4xZf5sdWeopPZI6qHaJDP";
    private String SLACK_SIGNING_SECRET="c4bc66b49a798ffc1a0d90d2f4a55a86";

    public Slack() {}

    public String getBotToken() {
        return SLACK_BOT_TOKEN;
    }

    public String getSigningSecret() {
        return SLACK_SIGNING_SECRET;
    }
}