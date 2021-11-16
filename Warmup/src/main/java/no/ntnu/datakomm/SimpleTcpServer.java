package no.ntnu.datakomm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Simple TCP server, used as a warm-up exercise for assignment A4.
 */
public class SimpleTcpServer {
    private static final int PORT = 1301;

    private final Logger logger;

    public SimpleTcpServer() {
        this.logger = Logger.getLogger(getClass().toString());
    }

    public static void main(String[] args) {
        SimpleTcpServer server = new SimpleTcpServer();
        log("Simple TCP server starting");
        server.run();
        log("ERROR: the server should never go out of the run() method! After handling one client");
    }

    public void run() {
        // TODO - implement the logic of the server, according to the protocol.
        // Take a look at the tutorial to understand the basic blocks: creating a listening socket,
        // accepting the next client connection, sending and receiving messages and closing the connection

        try {
            ServerSocket welcomeSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            String response;

            boolean mustRun = true;
            while (mustRun) {
                Socket clientSocket = welcomeSocket.accept();


                InputStreamReader reader = new InputStreamReader(clientSocket.getInputStream());
                BufferedReader bufReader = new BufferedReader(reader);

                String clientInput = bufReader.readLine();
                System.out.println("Client sent: " + clientInput);
                String[] parts = clientInput.split(" ");

                if (parts.length == 3) {
                    response = parts[0] + " " + parts[1].toUpperCase() + " " + parts[2];
                } else {
                    response = "ERROR";
                }
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                writer.println(response);

                clientSocket.close();

                welcomeSocket.close();

            }


        } catch (IOException e) {
            logger.log(Level.WARNING, "Error, not able to open socket");
            e.printStackTrace();
        } catch (SecurityException s) {
            logger.log(Level.WARNING, "The operation is not allowed to run");
            s.printStackTrace();
        } catch (IllegalArgumentException i) {
            logger.log(Level.WARNING, "The port is invalid");
            i.printStackTrace();
        }
    }

    /**
     * Log a message to the system console.
     *
     * @param message The message to be logged (printed).
     */
    private static void log (String message){
        System.out.println(message);
    }
}
