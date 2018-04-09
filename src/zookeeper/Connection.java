package zookeeper;

import java.awt.List;
import java.io.*;
import java.util.concurrent.*;

import javax.swing.event.DocumentEvent.EventType;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.*;
import org.apache.zookeeper.ZooDefs;

public class Connection {
	
	ZooKeeper zk;
	
	static final List<ACL> acl = Ids.OPEN_ACL_UNSAFE;
	CountDownLatch connect_signal = new CountDownLatch(1);
	
	boolean print_details = true;
	
	public void print_detail (String a)
	{
		if(print_details)
			System.out.println("Connection: "+a);
	}
	
	public void open (String host)
	{
		zk = new ZooKeeper(host, 1000, (Watcher)this);
		connect_signal.await();
		print_detail("Zookeeper is now opened.");
	}
	
	public void close ()
	{
		print_detail("Zookeeper is now closed.");
		zk.close();
	}
	
	public ZooKeeper get_zk ()
	{
		if(zk == null)
			print_detail("ZooKeeper is not connected.");
		return zk;
	}
	
	public Stat exists (String path, Watcher watch)
	{
		Stat stat = zk.exists(path, watch);
		print_detail("returning Status");
		return stat;
	}
	
	public KeeperException.Code establish (String path, String data, CreateMode mode)
	{
		byte[] a = data.getBytes();
		zk.create(path, a, acl, mode);
		return KeeperException.Code.OK;
	}
	
	public void processing (WatchedEvent event)
	{
		if(event.getState() == KeeperState.SyncConnected)
			connect_signal.countDown();
	}
	
	public String b_to_s (byte[] a)
	{
		String ret = new String(a,"UTF-8");
		return ret;
	}
	
	public void path_listener (final String path)
	{
		CountDownLatch node_signal = new CountDownLatch(1);
		try{
				zk.exists(path, new Watcher(){
					@Override
					public void processing (WatchedEvent e)
					{
						boolean create = e.getType().equals(EventType.NodeCreated);
						if(create && e.getPath().equals(path))
						{
							node_signal.countDown();
						}
					}
				});
		}
		
		try{
			node_signal.await();
		}
	}
	
}
