package smartdays.smartdays;

/**
 * Created by hector on 22/01/15.
 */
public class FileNames {
    private String[] names;

    public FileNames(String n0, String n1, String n2, String n3) {
        names = new String[4];

        names[0] = n0;
        names[1] = n1;
        names[2] = n2;
        names[3] = n3;
    }

    public String[] getNames() {
        return names;
    }
}
