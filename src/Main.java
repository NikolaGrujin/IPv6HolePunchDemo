import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Main
{
    public static void main(String[] args)
    {
        try
        {
            // Create Scanner for user input
            Scanner reader = new Scanner(System.in);

            // Get all addresses under localhost name
            InetAddress ownAddress = Inet6Address.getByName("localhost");

            // Print IPv6 address to user
            System.out.println("Your address: " + ownAddress.toString());

            // Prompt user for port
            System.out.println("Port?");
            int ownPort = reader.nextInt();
            reader.nextLine();

            // Prompt user for other user's IPv6 address
            System.out.println("Other user's address?");
            InetAddress othersAddress = Inet6Address.getByName(reader.nextLine().trim());

            // Prompt user for other user's port
            System.out.println("Other user's port?");
            int othersPort = reader.nextInt();
            reader.nextLine();

            // Create punched UDP socket
            PunchedUDP udpSocket = new PunchedUDP(ownPort, othersAddress, othersPort);

            // Connect punched UDP socket
            boolean success = udpSocket.connect();
            if(success)
            {
                // Create UDP chat
                new UDPChat(udpSocket);
            }
            else
            {
                // Connection was unsuccessful, exit
                System.out.println("Connection failed. Exiting...");
            }
        }
        catch(UnknownHostException uhe)
        {
            System.err.println("Encountered UnknownHostException.");
            uhe.printStackTrace(System.err);
        }
    }
}