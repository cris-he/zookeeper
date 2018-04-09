import java.io.*;

public class TPacket implements Serializable {

  public static final int SUBMIT = 200;
  public static final int QUERY = 201;

  int type;
  String id;
  String key;

  public TPacket(String id, int type, String key) {
    this.id = id;
    this.type = type;
    this.key = key;
  }

  public TPacket(String a) {
    if (a != null) {
      this.id = a.split(":")[0];
      this.type = Integer.parseInt(a.split(":")[1]);
      this.key = a.split(":")[2];
    }
  }

  public String t_to_s() {
    String a = null;
    if (type != 0 && key != null) {
      a = String.format("%s:%d:%s", id, type, key);
    }
    return a;
  }
}
