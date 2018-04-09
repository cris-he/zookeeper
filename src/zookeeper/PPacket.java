import java.io.*;
import java.util.*;

public class PPacket implements Serializable {

  public static final int REQUEST = 300;
  public static final int REPLY = 301;

  int type;
  int id;
  int workers;
  List dictionary;

  int start;
  int end;
  int size;

  public PPacket(int type, int id, int workers) {
    this.type = type;
    this.id = id;
    this.workers = workers;
  }

  public PPacket(int type) { this.type = type; }
}
