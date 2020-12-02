import java.util.ArrayList;
import java.util.List;
/* Subject (Controller) notifies observer (Scheduler) of days, times and time zone that are set by user for standup
* */
public interface Subject {
    public void notifyObservers(ArrayList<String> days, ArrayList<String> users, String time, String timeZone);
}
