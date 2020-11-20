import java.util.ArrayList;

public interface Observer {
    public void update(ArrayList<String> days, String time, String timeZone);
}