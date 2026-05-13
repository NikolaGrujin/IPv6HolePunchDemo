# About
This is a simple demo of how modern peer-to-peer networking can be done with IPv6 and hole punching.

# How to Run
The Main class is the entrypoint, just compile all the files in the src folder with a working version of JDK,
and run the Main class.
I used IntelliJ IDEA to make this, so the project config in the repo is for IntelliJ, so you can easily use
it to make running this easier.

# How it works
Normally, the issue with making a peer-to-peer connection is that NAT on routers gets in the way by translating the traffic
coming from multiple ports from local IPs to multiple ports from a single public IP, the router's IP. This solves the limited
address space problem of IPv4, by letting there be multiple duplicate address spaces, like 10.0.0.1 -> 10.255.255.255, or
192.168.0.1 -> 192.168.255.255, and still having it all be routed correctly without major changes to the IPv4 specification.
But because the port assignment is controlled by the router and hidden from the user, this makes it harder to establish a
peer-to-peer connection, as NAT does all the translation and port assignment silently, so the client only sees their traffic
as coming from their local IP and going to the public IP of the server, making it impossible to directly know what port you
need to send to or will be opened by the router to talk to the other user without port forwarding.

This can be overcome with STUN assisted NAT traversal and hole punching, making a hole in the NAT by making a NAT routing by
sending an initiation packet to the public address and port provided by the STUN server.

However, IPv6 has no NAT, as its address space is much larger, allowing for global routability, meaning that any address can
be routed to any address, but it doesn't have global reachability, meaning some addresses have stricter firewalls than others.
By default, most IPv6 capable routers and systems will decline all incoming IPv6 traffic, so a peer can't directly send data
to another peer as the traffic carrying their data would be seen by the firewall as untrusted, resulting in their packets
being dropped. However, IPv6 firewalls will trust addresses that have been sent to, as when you send data to an address, you
are most likely expecting a response from the same address, so they will let the traffic from that address through while that
routing through the firewall, or so-called "pinhole", exists. This means you can't have a traditional client-server model
approach to connection, but instead a network screaming match between two clients where they both have to exchange addresses
somehow, and then establish a connection simultaneously.

1. Clients A and B send initiation packets to each other.
- This opens two pinholes; A->B and B->A
- Both sides have their initiation packets dropped as their respective pinholes aren't yet open to receive them.
2. Clients A and B send connection test packets to each other.
- This tests connection integrity, making sure the routing is made, and the connection is stable.
3. Clients A and B track and maintain connection.
- IPv6 firewalls close pinholes after periods of inactivity to free up resources.
- Each client keeps track of when it last sent a packet, sending a "keep-alive" packet every 10ish seconds.