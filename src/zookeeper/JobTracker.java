package zookeeper;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;

public class JobTracker extends Thread implements Watcher {

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

  static Connection connection;
  static ZooKeeper zk;
  static ArrayList<String> client_table;
  static HashMap<String, ArrayList<String>> client_job;

  Semaphore sema_job = new Semaphore(1);
  Semaphore sema_client = new Semaphore(1);
  static CountDownLatch mode_signal = new CountDownLatch(1);

  static String z_host;
  static int z_port;
  static String my_mode;
  static String my_path;

  public JobTracker() {
    connection = new Connection();
    String host = String.format("%s:%d", z_host, z_port);
    connection.open(host);
    zk = connection.get_zk();
    Stat stat = null;
    try {
      stat = zk.exists("/tracker", false);
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (stat != null) {
      if (stat.getNumChildren() == 0) {
        try {
          zk.create("/tracker/primary", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        my_mode = "primary";
        my_path = "/tracker/primary";
      } else {
        try {
          zk.create("/tracker/backup", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        my_mode = "backup";
        my_path = "/tracker/backup";
      }

    } else {
      try {
        zk.create("/tracker", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
        zk.create("/tracker/primary", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      } catch (KeeperException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      my_mode = "primary";
      my_path = "/tracker/primary";
    }

    try {
      if (zk.exists("/clients", false) == null) {
        zk.create("/clients", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      }
    } catch (KeeperException e4) {
      // TODO Auto-generated catch block
      e4.printStackTrace();
    } catch (InterruptedException e4) {
      // TODO Auto-generated catch block
      e4.printStackTrace();
    }

    try {
      if (zk.exists("/workers", false) == null) {
        zk.create("/workers", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      }
    } catch (KeeperException e3) {
      // TODO Auto-generated catch block
      e3.printStackTrace();
    } catch (InterruptedException e3) {
      // TODO Auto-generated catch block
      e3.printStackTrace();
    }

    try {
      if (zk.exists("/tasks", false) == null) {
        zk.create("/tasks", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      }
    } catch (KeeperException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    } catch (InterruptedException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }

    try {
      if (zk.exists("/jobs", false) == null) {
        try {
          zk.create("/jobs", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } catch (KeeperException | InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    try {
      if (zk.exists("/results", false) == null) {

        zk.create("/results", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      }
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public class RunListenForClients implements Runnable, Watcher {
    public void client_listener()
        throws InterruptedException, UnsupportedEncodingException {
      List<String> clients = null;
      sema_client.acquire();
      while (true) {
        try {
          clients = zk.getChildren("/clients", new Watcher() {
            @Override
            public void processing(WatchedEvent e) {
              if (e.getType() == Event.EventType.NodeChildrenChanged)
                sema_client.release();
            }

          });
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

        Collections.sort(clients);

        for (String s : clients) {
          if (!client_table.contains(s)) {
            client_table.add(s);
            try {
              zk.exists("/clients/" + s, this);
            } catch (KeeperException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }

        sema_client.acquire();
      }
    }

    public void processing(WatchedEvent e) {
      String z_node_name = e.getPath().split("/")[2];
      if (e.getType().equals(EventType.NodeDeleted)) {
        if (!z_node_name.equals("/primary")) {
          ArrayList<String> jobs = client_job.get(z_node_name);
          if (jobs != null) {
            for (String s : jobs) {
              UseCount c = new UseCount("/jobs/" + s);
              c.decrementUseCount();
              if (c.count == 0) {
                zk.delete("/jobs/" + s, c.version);
                zk.delete("/results/" + s,
                          zk.exists("/results/" + s, false).getVersion());
              }
            }
          }

          client_job.remove(z_node_name);
          client_table.remove(z_node_name);
        }
      }
    }

    public void run() {
      try {
        client_listener();
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    @Override
    public void process(WatchedEvent event) {
      // TODO Auto-generated method stub
    }
  }

  public class RunListenForTasks implements Runnable {
    public void task_listener()
        throws InterruptedException, UnsupportedEncodingException {
      List<String> tasks = null;
      sema_job.acquire();
      while (true) {
        try {
          tasks = zk.getChildren("/tasks", new Watcher() {
            @Override
            public void processing(WatchedEvent e) {
              if (e.getType() == Event.EventType.NodeChildrenChanged)
                sema_client.release();
            }
          });
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

        Collections.sort(tasks);

        for (String s : tasks) {
          if (!tasks.contains(s)) {
            tasks.add(s);
            try {
              zk.exists("/tasks/" + s, (Watcher)this);
            } catch (KeeperException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }

        sema_client.acquire();
      }
    }

    @Override
    public void run() {
      try {
        task_listener();
      } catch (UnsupportedEncodingException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  void task_handler(TPacket tp, String path) {
    if (tp.type == TPacket.SUBMIT)
      job_helper(tp, path);
    if (tp.type == TPacket.QUERY)
      query_helper(tp, path);
  }

  void job_helper(TPacket tp, String path) {
    try {
      if (zk.exists("/jobs/" + tp.key, false) == null) {
        if (client_table.contains(tp.id)) {
          if (client_job.get(tp.id) == null)
            client_job.put(tp.id, new ArrayList<String>());
          client_job.get(tp.id).add(tp.key);
        }

        zk.create("/jobs/" + tp.key, String.valueOf(1).getBytes(),
                  ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      } else {
        if (!client_job.get(tp.id).contains(tp.key)) {
          if (client_table.contains(tp.id)) {
            if (client_job.get(tp.id) == null)
              client_job.put(tp.id, new ArrayList<String>());
            client_job.get(tp.id).add(tp.key);
          }

          UseCount c = new UseCount("/jobs/" + tp.key);
          c.increament("/jobs/" + tp.key);
        }
      }
    } catch (KeeperException | InterruptedException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }

    try {
      if (zk.exists("/results/" + tp.key, false) == null) {
        zk.create("/results/" + tp.key, String.valueOf(0).getBytes(),
                  ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }
    } catch (KeeperException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    try {
      zk.delete("/tasks/" + path, 0);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  void query_helper(TPacket tp, String path) {
    String res = null;
    try {
      if (zk.exists("/results/" + tp.key, false) != null) {
        byte[] temp;
        try {
          temp = zk.getData("/results/" + tp.key, false, null);
        } catch (KeeperException e2) {
          // TODO Auto-generated catch block
          e2.printStackTrace();
        } catch (InterruptedException e2) {
          // TODO Auto-generated catch block
          e2.printStackTrace();
        }

        while (temp == null)
          try {
            temp = zk.getData("/results/" + tp.key, false, null);
          } catch (KeeperException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
          } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
          }

        String[] result = b_to_s(temp).split(":");
        if (result[0].equals("success"))
          res = "password is found: " + result[1];
        else if (result[0].equals("success"))
          res = "password is not found.";
        else
          res = "job still processing.";

        try {
          zk.create("/tasks/" + path + "/res", res.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (KeeperException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } catch (KeeperException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void processing(WatchedEvent e) {
    if (e.getType().equals(EventType.NodeDeleted) &&
        e.getPath().split("/")[2].equals("primary") &&
        my_mode.equals("backup")) {
      try {
        zk.delete(my_path, 0);
      } catch (InterruptedException | KeeperException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      my_path = "/tracker/primary";
      my_mode = "primary";
      try {
        zk.create("/tracker/primary", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                  CreateMode.EPHEMERAL);
      } catch (KeeperException | InterruptedException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }

      mode_signal.countDown();
    }
  }

  public class UseCount {
    public int count;
    public int version;
    public String j_path;

    public UseCount(String path) {
      String temp = null;
      try {
        temp = b_to_s(zk.getData(path, false, null));
      } catch (KeeperException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        this.version = zk.exists(path, false).getVersion();
      } catch (KeeperException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      this.count = Integer.parseInt(temp);
      this.j_path = path;
    }

    public void set_use_count(int c) {
      this.count = c;
      String temp = String.valueOf(c);
      try {
        this.version =
            zk.setData(j_path, temp.getBytes(), this.version).getVersion();
      } catch (KeeperException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    public void increament(String path) { set_use_count(count + 1); }

    public void decreament(String path) { set_use_count(count - 1); }
  }

  public static void main(String[] arg) {
    if (arg.length != 2) {
      print_general("Invalid Arguments & Goodbye");
      System.exit(-1);
    }

    z_host = arg[0];
    z_port = Integer.parseInt(arg[1]);

    JobTracker jobtracker = new JobTracker();

    if (my_mode == "backup") {
      try {
        zk.exists("/tracker/primary", jobtracker);
      } catch (KeeperException | InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      try {
        mode_signal.await();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    new Thread(jobtracker.new RunListenForClients()).start();
    new Thread(jobtracker.new RunListenForTasks()).start();
  }
}