import java.util.ArrayList;
import java.util.List;

public interface Observer {
    public void update(ArrayList<String> days, ArrayList<String> users, String time, String timeZone);
}