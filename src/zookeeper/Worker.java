package zookeeper;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.ArrayList;

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

public class Worker {

  // debug
  static boolean print_details = true;
  public static void print_detail(String a) {
    if (print_details)
      System.out.println("FileServer: " + a);
  }
  public static void print_general(String a) {
    System.out.println("FileServer: " + a);
  }

  public String b_to_s(byte[] a) {
    String ret = null;
    try {
      ret = new String(a, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return ret;
  }

  Lock lock;

  Connection connection;
  ZooKeeper zk;

  List<String> jobs;
  List<String> prev_jobs = new ArrayList<String>();

  int counter = 1;
  boolean primary = false;

  Watcher watcher;

  Semaphore sema_worker = new Semaphore(1);

  int wid;
  String wstring;
  static String my_path;
  static ServerSocket serversocket;

  public Worker(String host) {
    connection = new Connection();
    connection.open(host);

    zk = connection.get_zk();
  }

  public void regiesteration() {
    waiting_for_exists(my_path);
    String temp = null;
    try {
      temp = zk.create(my_path + "/", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                       CreateMode.EPHEMERAL_SEQUENTIAL);
    } catch (KeeperException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    wstring = temp.split("/")[2];
    wid = Integer.parseInt(temp.split("/")[2]);
  }

  public void waiting_for_exists(final String path) {
    final CountDownLatch create_signal = new CountDownLatch(1);

    try {
      zk.exists(path, new Watcher() {
        @Override
        public void process(WatchedEvent e) {
          if (e.getType().equals(EventType.NodeCreated) &&
              e.getPath().equals(path))
            create_signal.countDown();
        }
      });
    } catch (KeeperException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void start() {
    waiting_for_exists("/jobs");
    while (true) {
      // ArrayList <String> jobs = new ArrayList();

      listener("/jobs");

      try {
        sema_worker.acquire();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      List<String> new_jobs = get_new_jobs();

      job_handler(new_jobs);
    }
  }

  public void listener(final String path) {
    try {
      jobs = zk.getChildren(path, new Watcher() {
        @Override
        public void processing(WatchedEvent e) {
          sema_worker.release();
        }
      });
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public List<String> get_new_jobs() {
    List<String> new_job = new ArrayList();
    for (String s : jobs) {
      if (!job_complete(s))
        new_job.add(s);
      prev_jobs.remove(s);
    }

    for (String s : new_job) {
      jobs.remove(s);
    }

    return new_job;
  }

  public boolean job_complete(String a) {
    byte[] temp = null;
    try {
      temp = zk.getData("/results/" + a, false, null);
    } catch (KeeperException | InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    while (temp == null)
      try {
        temp = zk.getData("/results/" + a, false, null);
      } catch (KeeperException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    String res = b_to_s(temp);
    res = res.split(":")[0];

    if (res.equals("success") || res.equals("fail"))
      return true;
    else
      return false;
  }

  public void job_handler(List<String> a) {
    for (String s : a) {
      new WorkerHandler(connection, "/jobs/" + s, wstring).start();
      prev_jobs.add(s);
    }
  }

  public static void main(String[] arg) {
    if (arg.length != 1)
      return;
    Worker worker = new Worker(arg[0]);
    worker.regiesteration();
    worker.start();
  }
}
