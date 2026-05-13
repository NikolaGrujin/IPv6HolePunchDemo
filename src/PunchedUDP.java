import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PunchedUDP implements Runnable
{
    private DatagramSocket socket = null;
    private Thread keepAliveThread = null;
    private AtomicBoolean alive;
    private AtomicLong lastSent;

    PunchedUDP(InetAddress address, int port)
    {
        try
        {
            this.socket = new DatagramSocket(new InetSocketAddress(address, port));
            this.socket.setSoTimeout(1000);
            this.alive = new AtomicBoolean(false);
            this.lastSent = new AtomicLong(0);
        }
        catch(SocketException se)
        {
            System.err.println("Encountered SocketException while creating punched UDP socket");
        }
    }

    private boolean initiation()
    {
        try
        {
            // Send initiation packet
            byte[] data = ("hello").getBytes(StandardCharsets.UTF_8);
            DatagramPacket initiationPacket = new DatagramPacket(data, data.length);
            this.socket.send(initiationPacket);

            // Receive initiation packet
            DatagramPacket initiationResponse = new DatagramPacket(new byte[512], 512);
            this.socket.receive(initiationResponse);

            // Parse response and check if it's initiation response
            String response = new String(initiationResponse.getData(), StandardCharsets.UTF_8);
            if(response.equals("hello"))
            {
                return true;
            }
        }
        catch(SocketTimeoutException ste)
        {
            return false;
        }
        catch(IOException ioe)
        {
            System.err.println("Encountered IOException while attempting to establish punched UDP connection.");
            ioe.printStackTrace();
        }
        return false;
    }

    public void send(byte[] data)
    {
        try
        {
            if(!this.alive.get())
            {
                System.err.println("Connection not alive.");
                return;
            }

            if (data.length > 512)
            {
                System.err.println("Data too large.");
                return;
            }

            DatagramPacket packet = new DatagramPacket(data, data.length);
            this.socket.send(packet);
            this.lastSent.set(System.currentTimeMillis());
        }
        catch(IOException ioe)
        {
            System.err.println("Encountered IOException while attempting to send data over punched UDP socket.");
            ioe.printStackTrace();
        }
    }

    public byte[] receive()
    {
        try
        {
            if(!this.alive.get())
            {
                System.err.println("Connection not alive.");
                return null;
            }

            DatagramPacket packet = new DatagramPacket(new byte[512], 512);
            this.socket.receive(packet);
            return packet.getData();
        }
        catch(SocketTimeoutException ste)
        {
            return null;
        }
        catch(IOException ioe)
        {
            System.err.println("Encountered IOException while attempting to receive packet on punched UDP.");
        }
        return null;
    }

    public boolean connect()
    {
        // Try to initiate connection 10 times
        for(int i = 0; i < 10; i++)
        {
            if(initiation())
            {
                this.alive.set(true);
                break;
            }
            else
            {
                System.out.println("Punched UDP connection initiation attempt #" + (i+1) + " failed.");
            }
        }
        // Start keep alive thread if connection alive
        if(!this.alive.get())
        {
            System.out.println("Punched UDP socket connected");
            this.keepAliveThread = new Thread(this);
            this.keepAliveThread.start();
        }
        return this.alive.get();
    }

    @Override
    public void run()
    {
        while(this.alive.get())
        {
            if (System.currentTimeMillis() - this.lastSent.get() > 10000)
            {
                this.send(("keep-alive").getBytes(StandardCharsets.UTF_8));
                this.lastSent.set(System.currentTimeMillis());
            }
        }
    }

    public boolean isAlive()
    {
        return this.alive.get();
    }

    public void close()
    {
        try
        {
            this.alive.set(false);
            while (this.keepAliveThread.isAlive())
            {
                Thread.sleep(100);
            }
            this.socket.close();
        }
        catch(InterruptedException ie)
        {
            System.err.println("Encountered InterruptedException while attempting to close punched UDP socket.");
            ie.printStackTrace();
        }
    }
}
