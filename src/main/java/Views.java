import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
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

public class Views {

     public View buildScheduleView() {
        return view(view -> view
                .callbackId("schedule-standups")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle(title -> title.type("plain_text").text("Standup").emoji(true)))
                .submit(viewSubmit(submit -> submit.type("plain_text").text("Submit").emoji(true)))
                .close(viewClose(close -> close.type("plain_text").text("Cancel").emoji(true)))
                .blocks(asBlocks(
                        input(input -> input
                                .label(plainText("Select days for standup:"))
                                .blockId("days-block")
                                .element(checkboxes(i -> i
                                        .actionId("select-days")
                                        .options(Arrays.asList(
                                                option(plainText("Monday"), "2"), // value represents cron job day of week value
                                                option(plainText("Tuesday"), "3"),
                                                option(plainText("Wednesday"), "4"),
                                                option(plainText("Thursday"), "5"),
                                                option(plainText("Friday"), "6")
                                        ))

                                ))
                        )
                ))
        );
    }

     public View buildStandupView() {
        return view(view -> view
                .callbackId("submission-standups")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle(title -> title.type("plain_text").text("Standup").emoji(true)))
                .submit(viewSubmit(submit -> submit.type("plain_text").text("Submit").emoji(true)))
                .close(viewClose(close -> close.type("plain_text").text("Cancel").emoji(true)))
                .blocks(asBlocks(
                        input(input -> input
                                .blockId("prev-tasks")
                                .element(plainTextInput(pti -> pti.actionId("agenda-1").multiline(true)))
                                .label(plainText(pt -> pt.text("What have you been working on?").emoji(true)))
                        ),
                        input(input -> input
                                .blockId("to-do")
                                .element(plainTextInput(pti -> pti.actionId("agenda-2").multiline(true)))
                                .label(plainText(pt -> pt.text("What will you be working on?").emoji(true)))
                        ),
                        input(input -> input
                                .blockId("blockers")
                                .element(plainTextInput(pti -> pti.actionId("agenda-3").multiline(true)))
                                .label(plainText(pt -> pt.text("Do you have any blockers?").emoji(true)))
                        )
                ))
        );
    }
}
