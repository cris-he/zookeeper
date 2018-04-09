package zookeeper;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.ArrayList;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import java.net.ServerSocket;

public class FileServer {

  static boolean print_details = true;
  public static void print_detail(String a) {
    if (print_details)
      System.out.println("FileServer: " + a);
  }

  public static void print_general(String a) {
    System.out.println("FileServer: " + a);
  }

  boolean primary = false;
  static CountDownLatch mode_signal = new CountDownLatch(1);
  static Connection connection;
  static Watcher watcher;
  Semaphore sema = new Semaphore(1);

  File d_file;
  static String d_path;

  List<String> request;
  List<String> prev_request;
  static List<String> dictionary;

  static int port;
  String id;

  ServerSocket serversocket;

  static ZooKeeper zk;
  int z_port;
  Lock z_lock;

  String TRACKER = "/tacker";
  String WOKER = "/worker";
  String FSERVER = "/fserver";
  String REQUEST = "/request";
  String RESULT = "/result";
  String PRIMARY = "primary";
  String BACKUP = "backup";

  String mode;

  public void start() {
    serversocket = new ServerSocket(port);
    boolean listen = true;

    while (listen) {
      new FIleServerHandler(serversocket.accept(), c, zk, dictionary).start();
    }
    serversocket.close();
  }

  public FileServer(String host, String port) {
    connection = new Connection();
    connection.open(host);

    zk = connection.get_zk();

    watcher = new Watcher() {
      @Override
      public void processing(WatchedEvent e) {
        event_handler(e);
      }
    };

    this.port = Integer.parseInt(port);
  }

  static void event_handler(WatchedEvent e) {
    if (e.getPath().equals("/fserver") &&
        e.getType() == EventType.NodeDeleted) {
      print_detail("path - /fserver is deleted");
      set_primary();
    }
  }

  static boolean set_primary() {
    get_dictionary();
    Stat stat = connection.exists("/fserver", watcher);
    if (stat == null) {
      print_detail("Creating - /fserver");
      Code temp = connection.establish("/fserver", null, CreateMode.EPHEMERAL);
      if (temp == Code.OK) {
        print_general("Setting Primary");
        mode_signal.countDown();
        String ip = null;
        try {
          ip = InetAddress.getLocalHost().getHostName() + ":" + port;
        } catch (UnknownHostException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        try {
          stat = zk.setData("/fserver", ip.getBytes(), -1);
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        return true;
      }
    }
    return false;
  }

  public static void get_dictionary() {
    dictionary = new ArrayList<String>();
    BufferedReader read = null;
    try {
      read = new BufferedReader(new FileReader(d_path));
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    int index = 0;
    String temp = null;
    try {
      temp = read.readLine();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    while (temp != null) {
      dictionary.add(index, temp);
      index++;
      try {
        temp = read.readLine();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] arg) {
    if (arg.length != 2)
      return;

    FileServer fs = new FileServer(arg[0], arg[1]);
    boolean i_am_primary = fs.set_primary();

    if (!i_am_primary)
      try {
        mode_signal.await();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    fs.start();
  }
}
