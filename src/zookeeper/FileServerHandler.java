import java.io.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.security.SecureRandom;
import java.io.Serializable;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class FileServerHandler extends Thread {

  // debug
  static boolean print_details = true;
  public static void print_detail(String a) {
    if (print_details)
      System.out.println("FileServer: " + a);
  }
  public static void print_general(String a) {
    System.out.println("FileServer: " + a);
  }

  Lock lock;

  Socket socket;

  ObjectInputStream in;
  ObjectOutputStream out;

  Connection connection;
  ZooKeeper zk;
  List<String> dictionary;

  public FileServerHandler(Socket soc, Connection con, ZooKeeper zok,
                           List<String> dict) {
    print_detail("Start File Server Handler Thread");
    this.socket = soc;
    this.connection = con;
    this.zk = zok;
    this.dictionary = dict;

    this.in = new ObjectInputStream(soc.getInputStream());
    this.out = new ObjectOutputStream(soc.getOutputStream());
  }

  public void run() {
    PPacket from_worker = (PPacket)in.readObject();
    if (from_worker != null) {
      int pid = from_worker.id;
      int pworkers = from_worker.workers;
      int size = dictionary.size();
      int psize = size / pworkers;
      int start = (pid - 1) * psize;
      int end = pid * psize;

      PPacket to_worker = new PPacket(PPacket.REPLY);
      if (to_worker.end > (size - 1)) {
        to_worker.end = size - 1;
      }
      to_worker.dictionary = new ArrayList(dictionary.subList(start, end));
      to_worker.size = end - start;

      out.writeObject(to_worker);
    }
    print_detail("Done with job.");
  }
}