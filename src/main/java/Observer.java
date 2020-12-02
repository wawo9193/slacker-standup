import java.util.ArrayList;
import java.util.List;
/* observer pattern is used to kep track of the days, time and time zone when user sets it for their standups
    observer updates it after being notified by the subject Controller
* */
public interface Observer {
    public void update(ArrayList<String> days, ArrayList<String> users, String time, String timeZone);
}