import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.block.composition.OptionObject;
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
import java.util.stream.Collectors;
import com.slack.api.model.block.element.*;

import com.slack.api.model.Option;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;
import static com.slack.api.model.block.Blocks.*;

public class Views {

    public View buildScheduleView() {
        var client = Slack.getInstance().methods();

        return view(view -> view
                .callbackId("schedule-standups")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle(title -> title.type("plain_text").text("Standup").emoji(true)))
                .submit(viewSubmit(submit -> submit.type("plain_text").text("Submit").emoji(true)))
                .close(viewClose(close -> close.type("plain_text").text("Cancel").emoji(true)))
                .blocks(asBlocks(
                        input(input -> input
                                .label(plainText("Channel"))
                                .blockId("channel-block")
                                .element(channelsSelect(c -> c
                                        .placeholder(plainText("Select option"))
                                        .actionId("select-channel")
                                ))
                        ),
                        input(input -> input
                                .label(plainText("Users"))
                                .blockId("user-block")
                                .element(multiUsersSelect(u -> u
                                        .placeholder(plainText("Select user"))
                                        .actionId("select-user")
                                ))
                        ),
                        input(input -> input
                                .label(plainText("Select days for standup"))
                                .blockId("days-block")
                                .element(multiStaticSelect(i -> i
                                        .actionId("select-days")
                                        .options(Arrays.asList(
                                                option(plainText("Monday"), "2"), // value represents cron job day of week value
                                                option(plainText("Tuesday"), "3"),
                                                option(plainText("Wednesday"), "4"),
                                                option(plainText("Thursday"), "5"),
                                                option(plainText("Friday"), "6")
                                        ))

                                ))
                        ),
                        input(input -> input
                                .label(plainText("Select a time"))
                                .blockId("time-block")
                                .element(staticSelect(t -> t
                                        .placeholder(plainText("Select time"))
                                        .actionId("select-time")
                                        .options(Arrays.asList(
                                                option(plainText("9:00 AM"), "2"), // value represents cron job day of week value
                                                option(plainText("10:00 AM"), "3"),
                                                option(plainText("11:00 AM"), "4"),
                                                option(plainText("12:00 PM"), "5"),
                                                option(plainText("1:00 PM"), "6"),
                                                option(plainText("2:00 PM"), "7"),
                                                option(plainText("3:00 PM"), "8"),
                                                option(plainText("4:00 PM"), "9"),
                                                option(plainText("5:00 PM"), "10"),
                                                option(plainText("6:00 PM"), "11"),
                                                option(plainText("7:00 PM"), "12"),
                                                option(plainText("8:00 PM"), "13")

                                        ))
                                ))
                        ),
                        input(input -> input
                                .label(plainText("Select a time zone"))
                                .blockId("timezone-block")
                                .element(staticSelect(t -> t
                                        .placeholder(plainText("Select time zone"))
                                        .actionId("select-timezone")
                                        .options(Arrays.asList(
                                                option(plainText("Pacific Time"), "2"), // value represents cron job day of week value
                                                option(plainText("Mountain Time"), "3"),
                                                option(plainText("Central Time"), "4"),
                                                option(plainText("Eastern TIme"), "5")

                                ))
                        )
                ))
        )));
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
