import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PunchedUDP
{
    private DatagramSocket socket = null;
    private final InetAddress peerAddress;
    private final int peerPort;

    private final AtomicBoolean initiated;
    private final AtomicBoolean alive;
    private final AtomicBoolean closed;

    private final AtomicLong lastSentPacket;
    private final AtomicLong lastReceivedPacket;

    private final Thread receiveThread;

    private final BlockingQueue<byte[]> receiveQueue;

    private final ScheduledExecutorService watchdog;

    PunchedUDP(int ownPort, InetAddress address, int port)
    {
        this.peerAddress = address;
        this.peerPort = port;
        this.initiated = new AtomicBoolean(false);
        this.alive = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.lastSentPacket = new AtomicLong(0);
        this.lastReceivedPacket = new AtomicLong(0);
        this.receiveThread = new Thread(new ReceiveThread());
        this.receiveQueue = new LinkedBlockingQueue<>();
        this.watchdog = Executors.newSingleThreadScheduledExecutor();
        try
        {
            this.socket = new DatagramSocket(new InetSocketAddress(ownPort));
        }
        catch(SocketException se)
        {
            System.err.println("Encountered SocketException while creating punched UDP socket");
            se.printStackTrace(System.err);
        }
    }

    /**
     * Sends an initiation packet to the peer at the address of the socket.
     */
    private void initiation()
    {
        try
        {
            // Send initiation packet
            byte[] data = ("hello").getBytes(StandardCharsets.UTF_8);
            DatagramPacket initiationPacket = new DatagramPacket(data, data.length);
            initiationPacket.setAddress(this.peerAddress);
            initiationPacket.setPort(this.peerPort);
            this.socket.send(initiationPacket);
        }
        catch(IOException ioe)
        {
            System.err.println("Encountered IOException while attempting to establish punched UDP connection.");
            ioe.printStackTrace(System.err);
        }
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
        // Start receive thread
        this.receiveThread.start();

        // Try to initiate connection 10 times
        for(int i = 0; i < 10; i++)
        {
            this.initiation();

            if(this.initiated.get())
            {
                this.alive.set(true);
                break;
            }

            try
            {
                System.out.println("Punched UDP connection initiation attempt #" + (i + 1) + " failed.");
                Thread.sleep(100);
            }
            catch(InterruptedException ie)
            {
                System.err.println("Encountered InterruptedException while attempting to initiation punched UDP connection.");
                ie.printStackTrace(System.err);
            }
        }
        // Start send and receive threads if connection is alive
        if(this.isAlive())
        {
            System.out.println("Punched UDP socket connected");
            this.watchdog.scheduleAtFixedRate(new WatchdogThread(), 1, 1, TimeUnit.SECONDS);
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
     * Sets the connection's alive boolean to false,
     * indicating the connection is dead.
     */
    private void markDead()
    {
        this.alive.set(false);
        this.socket.close();
    }

    /**
     * Closes connection. Terminates send thread and receive thread, and closes UDP socket.
     */
    public void close()
    {
        try
        {
            if(!closed.compareAndSet(false, true))
            {
                return;
            }

            this.alive.set(false);
            this.send(("close").getBytes(StandardCharsets.UTF_8));
            watchdog.shutdownNow();
            this.socket.close();
            this.receiveThread.join();
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
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setAddress(this.peerAddress);
            packet.setPort(this.peerPort);
            this.socket.send(packet);
            this.lastSentPacket.set(System.currentTimeMillis());
        }
        catch(IOException ioe)
        {
            System.err.println("Encountered IOException while attempting to send packet over punched socket.");
            ioe.printStackTrace(System.err);
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
            while(!closed.get())
            {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                    socket.receive(packet);

                    // Make sure the packet is coming from the correct address
                    if (!packet.getAddress().equals(peerAddress) || packet.getPort() != peerPort)
                    {
                        continue;
                    }

                    lastReceivedPacket.set(System.currentTimeMillis());

                    byte[] data = packet.getData();
                    String dataString = new String(data, 0, packet.getLength(), StandardCharsets.UTF_8);
                    if(dataString.equals("hello"))
                    {
                        initiated.set(true);
                        alive.set(true);
                        continue;
                    }
                    else if(dataString.equals("keep-alive"))
                    {
                        continue;
                    }
                    else if(dataString.equals("close"))
                    {
                        break;
                    }

                    byte[] trimmedData = new byte[packet.getLength()];
                    System.arraycopy(data, 0, trimmedData, 0, packet.getLength());
                    receiveQueue.put(trimmedData);
                }
                catch(IOException ioe)
                {
                    if(closed.get())
                    {
                        break;
                    }
                    System.err.println("Encountered IOException while attempting to receive packet on punched UDP socket.");
                    ioe.printStackTrace(System.err);
                }
                catch(InterruptedException ie)
                {
                    System.err.println("Encountered InterruptedException while attempting to receive packet on punched UDP socket.");
                    ie.printStackTrace(System.err);
                }
            }

            markDead();
        }
    }

    private class WatchdogThread implements Runnable
    {
        private final byte[] KEEP_ALIVE_PACKET = ("keep-alive").getBytes(StandardCharsets.UTF_8);

        @Override
        public void run()
        {
            if(closed.get() || !isAlive())
            {
                return;
            }

            long now = System.currentTimeMillis();
            long sinceLastSend = now - lastSentPacket.get();
            long sinceLastReceive = now - lastReceivedPacket.get();

            if(sinceLastReceive > 20000)
            {
                System.out.println("UDP punched connection timed out.");
                markDead();
            }

            if(sinceLastSend > 5000)
            {
                if(isAlive())
                {
                    send(KEEP_ALIVE_PACKET);
                }
            }
        }
    }
}
