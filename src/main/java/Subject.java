import java.util.ArrayList;

public interface Subject {
    public void notifyObservers(ArrayList<String> days, String time, String timeZone);
}
