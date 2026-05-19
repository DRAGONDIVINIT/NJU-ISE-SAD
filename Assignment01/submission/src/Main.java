import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) throws Exception {
        MemFs fs = new MemFs();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length == 0) {
                continue;
            }
            String cmd = tokens[0];
            if ("MKDIR".equals(cmd)) {
                if (tokens.length >= 2) {
                    fs.mkdir(tokens[1]);
                }
            } else if ("TOUCH".equals(cmd)) {
                if (tokens.length >= 3) {
                    Integer size = CliParsers.parseSize(tokens[2]);
                    if (size != null) {
                        fs.touch(tokens[1], size);
                    }
                }
            } else if ("LS".equals(cmd)) {
                if (tokens.length >= 2) {
                    for (String name : fs.ls(tokens[1])) {
                        System.out.println(name);
                    }
                }
            } else if ("INFO".equals(cmd)) {
                if (tokens.length >= 2) {
                    Integer size = fs.info(tokens[1]);
                    if (size != null) {
                        System.out.println(size);
                    }
                }
            }
        }
    }
}
