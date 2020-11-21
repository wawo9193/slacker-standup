import java.util.ArrayList;
import java.util.List;

public interface Subject {
    public void notifyObservers(ArrayList<String> days, ArrayList<String> users, String time, String timeZone);
}
