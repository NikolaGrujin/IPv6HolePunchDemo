import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;

public class UDPChat extends JFrame
{
    private final PunchedUDP socket;
    private final JTextArea textArea;

    UDPChat(PunchedUDP socket)
    {
        super("UDP Chat");
        this.socket = socket;
        this.setLayout(new BorderLayout());

        this.textArea = new JTextArea();
        this.textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(this.textArea);
        scrollPane.add(textArea);
        JTextField textField = new JTextField();
        JButton sendButton = new JButton("Send");

        sendButton.addActionListener((_) -> this.socket.send(textField.getText().getBytes(StandardCharsets.UTF_8)));

        JPanel messagePanel = new JPanel(new FlowLayout());
        messagePanel.add(textField);
        messagePanel.add(sendButton);

        this.add(scrollPane, BorderLayout.CENTER);
        this.add(messagePanel, BorderLayout.SOUTH);

        this.setVisible(true);

        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                socket.close();
            }
        });

        Thread receiver = new Thread(new ReceiverThread());
        receiver.start();
    }

    private class ReceiverThread implements Runnable
    {
        @Override
        public void run()
        {
            while(socket.isAlive())
            {
                byte[] data = socket.receive();
                String message = new String(data, StandardCharsets.UTF_8);
                if(message.equals("close"))
                {
                    break;
                }
                SwingUtilities.invokeLater(() -> textArea.append("\n" + message));
            }
        }
    }
}
