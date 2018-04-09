package zookeeper;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;

import javax.swing.event.DocumentEvent.EventType;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
public class ClientDriver {

	/********************************/	
	ZKConnector zkcon;
	static ZooKeeper zk;
	static String zk_host;
	static int zk_port;
	static String path;
	
	/********************************/
	static String z_clients = "/clients";
	static String z_tasks = "/tasks";
	static String z_subtask = "/t";
	
	static String id;
	
	CountDownLatch regSig = new CountDownLatch(1);
	
	static boolean print_details = true;
	
	private static void print_detail (String a)
	{
		if(print_details)
			System.out.println("ClientDriver: "+ a);
	}
	
	private static void print_general(String a)
	{
		System.out.println(a);
	}
	
	public void ClientDriver()
	{
		zkcon = new ZKConnector();
		
		try{
				print_detail("Connectring to ZooKeeper");
				String host = String.format("%s:%d", zk_host, zk_port);
				print_detail(host);
				zkcon.connect(host);
				zk = zkcon.getZooKeeper();
				print_detail("Connected to ZooKeeper");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void regiesteration() {
		try {
				Stat stat = zk.exists(z_clients, new Watcher()
						{
							@Override
							public void process(WatchedEvent event)
							{
								boolean created = event.getType().equals(EventType.NodeCreated);
								
								if(created)
									regSig.countDown();
								else
									print_general("registeration fails");
							}
						});
				if(stat == null)
					regSig.await();
				
				String path = zk.create(z_clients+"/", null, 
											ZooDefs.Ids.OPEN_ACL_UNSAFE, 
												CreateMode.EPHEMERAL_SEQUENTIAL);
				
				id = path.split("/")[2];
				
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void sender (TaskPacket packet)
	{
		String data = packet.taskToString();
		
		Code rcv = generate_path(data);
		
		if (ret != Code.Ok)
		{
			print_general("request cannot be sent");
			return;
		}
		
		if (packet.packet_type == TaskPacket.SUBMIT)
		{
			print_general("job has been submitted");
		}
		
	}

	private KeeperException.Code generate_path (String a)
	{
		try{
				byte[] data = null;
				if(a != null)
				{
					print_detail("generate_path: data is not NULL");
					data = a.getBytes();
				}
				else
				{
					print_detail("generate_path: data is NULL");
				}
				
				path = zk.create(z_tasks+"/t", data, 
									ZooDefs.Ids.OPEN_ACL_UNSAFE, 
										CreateMode.PERSISTENT_SEQUENTIAL);
				
		}catch(KeeperException e) {
            return e.code();
        } catch(Exception e) {
            return KeeperException.Code.SYSTEMERROR;
        }
		
        return KeeperException.Code.OK;
	}

	private String wait_status () {
		String ws_path = path + "/res";
		
		byte[] data;
		String result = null;
		Stat stat = null;
		
		try {
				zkcon.listenToPath(ws_path);
				
				data = zk.getData(ws_path, false, stat);
				result = byteToString(data);
				zk.delete(ws_path, 0);
				zk.delete(path, 0);
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	public String byteToString (byte[] a)
	{
		String s = null;
		if(a != null)
		{
			try {
					s = new String(a, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return s;
	}

	public static void main (String[] args) throws IOException
	{
		if(args.length == 2)
		{
			zk_host = args[0];
			zk_port = Integer.parseInt(args[1]);
			
			ClientDriver cd = new ClientDriver();
			cd.regiesteration();
			
			BufferedReader read = new BufferedReader(new InputStreamReader(System.in));
			
			print_general("ClientDriver Start...");
			cmd_style();
			
			String rcv = read.readLine();
			while(rcv != null)
			{
				String[] arg = rcv.split("[ ]+");
				String command = arg[0].toLowerCase();
				
				if (arg.length < 2)
				{
					print_general("Help: refer to readme.txt");
					cmd_style();
					continue;
				}
				
				if(command.equals("q") || command.equals("quit"))
				{
					print_general("Goodbye!");
					return;
				}

				if (command.equals("job"))
				{
					print_detail("Try to send a job to ZooKeeper");
					TaskPacket job_pkt = new TaskPacket(id, TaskPacket.SUBMIT, arg[1]);
					cd.sender(job_pkt);
					cmd_style();
				}
				
				if (command.equals("status"))
				{
					print_detail("Try to get status of a job");
					TaskPacket status_pkt =  new TaskPacket(id, TaskPacket.SUBMIT, arg[1]);
					cd.sender(status_pkt);
					print_general(cd.wait_status());
					cmd_style();
				}
				
				rcv = read.readLine();	
			}
		} else
			{
				System.err.println("INVAILD ARGUMENTS & GOODBYE");
				System.exit(-1);
			}
	}

	private static void cmd_style()
	{
		System.out.println("> ");
	}


































}
