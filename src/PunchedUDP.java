import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PunchedUDP
{
    private DatagramSocket socket = null;
    private InetAddress peerAddress;
    private int peerPort;

    private AtomicBoolean alive;
    private AtomicLong lastSentKeepAlive;
    private AtomicLong lastReceivedKeepAlive;

    private Thread receiveThread;
    private Thread sendThread;

    private BlockingQueue<byte[]> receiveQueue;
    private BlockingQueue<byte[]> sendQueue;

    PunchedUDP(int ownPort, InetAddress address, int port)
    {
        this.peerAddress = address;
        this.peerPort = port;
        this.alive = new AtomicBoolean(false);
        this.lastSentKeepAlive = new AtomicLong(0);
        this.lastReceivedKeepAlive = new AtomicLong(0);
        this.receiveThread = new Thread(new ReceiveThread());
        this.sendThread = new Thread(new SendThread());
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.sendQueue = new LinkedBlockingQueue<>();
        try
        {
            this.socket = new DatagramSocket(new InetSocketAddress(Inet6Address.getByName("localhost"), ownPort));
        }
        catch(UnknownHostException uhe)
        {
            System.err.println("Encountered UnknownHostException while creating punched UDP socket");
            uhe.printStackTrace(System.err);
        }
        catch(SocketException se)
        {
            System.err.println("Encountered SocketException while creating punched UDP socket");
            se.printStackTrace(System.err);
        }
    }

    /**
     * Sends an initiation packet to the peer at the address of the socket, and waits for a response.
     *
     * @return True if a response was received, false if timed out.
     */
    private boolean initiation()
    {
        try
        {
            // Send initiation packet
            byte[] data = ("hello").getBytes(StandardCharsets.UTF_8);
            DatagramPacket initiationPacket = new DatagramPacket(data, data.length);
            initiationPacket.setAddress(this.peerAddress);
            initiationPacket.setPort(this.peerPort);
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

    /**
     * Connects the socket to the peer at the address of the socket.
     * The method makes 10 attempts to send an initiation packet and receive a response,
     * and returns true if it succeeds.
     *
     * @return True if the socket connected successfully, and false otherwise.
     */
    public boolean connect()
    {
        // Enable socket timeout
        try
        {
            this.socket.setSoTimeout(1000);
        }
        catch(SocketException se)
        {
            System.err.println("Encountered IOException while trying to set socket timeout for punched UDP socket.");
            se.printStackTrace(System.err);
            return this.isAlive();
        }

        // Try to initiate connection 10 times
        for(int i = 0; i < 10; i++)
        {
            if(initiation())
            {
                // Set alive to true if initiation was successful
                this.alive.set(true);
                break;
            }
            else
            {
                System.out.println("Punched UDP connection initiation attempt #" + (i+1) + " failed.");
            }
        }
        // Start send and receive threads if connection is alive
        if(this.isAlive())
        {
            System.out.println("Punched UDP socket connected");
            this.sendThread.start();
            this.receiveThread.start();

            // Disable socket timeout
            try
            {
                this.socket.setSoTimeout(0);
            }
            catch(SocketException se)
            {
                System.err.println("Encountered IOException while trying to set socket timeout for punched UDP socket.");
                se.printStackTrace(System.err);
            }
        }
        return this.isAlive();
    }

    /**
     * Returns whether the connection is alive.
     *
     * @return True if connection alive, false if not.
     */
    public boolean isAlive()
    {
        return this.alive.get();
    }

    /**
     * Closes connection. Terminates send thread and receive thread, and closes UDP socket.
     */
    public void close()
    {
        try
        {
            this.alive.set(false);
            this.send(("close").getBytes(StandardCharsets.UTF_8));
            while (this.receiveThread.isAlive() | this.sendThread.isAlive())
            {
                Thread.sleep(100);
            }
            this.socket.close();
        }
        catch(InterruptedException ie)
        {
            System.err.println("Encountered InterruptedException while attempting to close punched UDP socket.");
            ie.printStackTrace(System.err);
        }
    }

    /**
     * Sends a UDP packet with the specified data.
     *
     * @param data The data to send; must be <=512 bytes in size.
     */
    public void send(byte[] data)
    {
        try
        {
            this.sendQueue.put(data);
        }
        catch(InterruptedException ie)
        {
            System.err.println("Encountered InterruptedException while attempting to add data to send queue.");
            ie.printStackTrace(System.err);
        }
    }

    /**
     * Receives a UDP packet and returns its data.
     *
     * @return The data received.
     */
    public byte[] receive()
    {
        byte[] data = null;
        try
        {
            data = this.receiveQueue.take();
        }
        catch(InterruptedException ie)
        {
            System.err.println("Encountered InterruptedException while attempting to take data from the receive queue.");
        }
        return data;
    }

    private class ReceiveThread implements Runnable
    {
        @Override
        public void run()
        {
            while(isAlive())
            {
                try
                {
                    DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                    socket.receive(packet);

                    byte[] data = packet.getData();
                    String dataString = new String(data, StandardCharsets.UTF_8);
                    if(dataString.equals("keep-alive"))
                    {
                        long newTime = System.currentTimeMillis();
                        long deltaTime = newTime - lastReceivedKeepAlive.get();
                        if(deltaTime >= 10000)
                        {
                            alive.set(false);
                            System.out.println("The punched UDP connection has died from keep-alive timeout.");
                        }
                        lastReceivedKeepAlive.set(newTime);
                        continue;
                    }

                    receiveQueue.put(data);
                    if(dataString.equals("close"))
                    {
                        break;
                    }
                }
                catch(IOException ioe)
                {
                    System.err.println("Encountered IOException while attempting to receive packet on punched UDP socket.");
                    ioe.printStackTrace(System.err);
                }
                catch(InterruptedException ie)
                {
                    System.err.println("Encountered InterruptedException while attempting to receive packet on punched UDP socket.");
                    ie.printStackTrace(System.err);
                }
            }
        }
    }

    private class SendThread implements Runnable
    {
        @Override
        public void run()
        {
            while(isAlive())
            {
                try
                {
                    byte[] data = sendQueue.take();
                    DatagramPacket packet = new DatagramPacket(data, data.length);
                    packet.setAddress(peerAddress);
                    packet.setPort(peerPort);
                    socket.send(packet);

                    String stringData = new String(data, StandardCharsets.UTF_8);
                    if(stringData.equals("keep-alive"))
                    {
                        long newTime = System.currentTimeMillis();
                        long deltaTime = newTime - lastSentKeepAlive.get();
                        if(deltaTime >= 10000)
                        {
                            alive.set(false);
                            System.out.println("The punched UDP connection has died from keep-alive timeout.");
                        }
                        lastSentKeepAlive.set(newTime);
                    }
                    else if(stringData.equals("close"))
                    {
                        break;
                    }
                }
                catch(InterruptedException ie)
                {
                    System.err.println("Encountered InterruptedException while attempting to send packet on punched UDP socket.");
                    ie.printStackTrace(System.err);
                }
                catch(IOException ioe)
                {
                    System.err.println("Encountered IOException while attempting to send packet on punched UDP socket.");
                    ioe.printStackTrace(System.err);
                }
            }
        }
    }
}
