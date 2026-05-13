import javax.swing.*;
import java.awt.*;
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
        textArea.setEditable(false);
        JTextField textField = new JTextField();
        JButton sendButton = new JButton("Send");

        sendButton.addActionListener((e) -> {
            this.socket.send(textField.getText().getBytes(StandardCharsets.UTF_8));
        });

        JPanel messagePanel = new JPanel(new FlowLayout());
        messagePanel.add(textField);
        messagePanel.add(sendButton);

        this.add(textArea, BorderLayout.CENTER);
        this.add(messagePanel, BorderLayout.SOUTH);

        this.setVisible(true);

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
                SwingUtilities.invokeLater(() -> {
                    textArea.append(message);
                });
            }
        }
    }
}
